#include "usb_audio_stream.h"

#include <android/log.h>
#include <libusb.h>

#include <algorithm>
#include <chrono>
#include <cmath>
#include <cstring>

#define LOG_TAG "UsbAudioStream"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGW(...) __android_log_print(ANDROID_LOG_WARN, LOG_TAG, __VA_ARGS__)

namespace melodia {

namespace {

constexpr int kDataTransfers = 8;
constexpr size_t kNoFlush = SIZE_MAX;

size_t inputSampleBytes(InputEncoding encoding) {
    switch (encoding) {
        case InputEncoding::Pcm16: return 2;
        case InputEncoding::Pcm24Packed: return 3;
        case InputEncoding::PcmFloat:
        case InputEncoding::Pcm32: return 4;
    }
    return 0;
}

// 统一换算成左对齐的 32 位整数，16/24 位源经此再截回原位深不丢信息
int32_t readSample(const uint8_t* p, InputEncoding encoding) {
    switch (encoding) {
        case InputEncoding::Pcm16: {
            int16_t v;
            std::memcpy(&v, p, 2);
            return static_cast<int32_t>(static_cast<uint32_t>(static_cast<int32_t>(v)) << 16);
        }
        case InputEncoding::Pcm24Packed:
            return static_cast<int32_t>((static_cast<uint32_t>(p[0]) << 8) |
                                        (static_cast<uint32_t>(p[1]) << 16) |
                                        (static_cast<uint32_t>(p[2]) << 24));
        case InputEncoding::Pcm32: {
            int32_t v;
            std::memcpy(&v, p, 4);
            return v;
        }
        case InputEncoding::PcmFloat: {
            float f;
            std::memcpy(&f, p, 4);
            if (!(f > -1.0f)) return INT32_MIN;
            if (f >= 1.0f) return INT32_MAX;
            return static_cast<int32_t>(static_cast<double>(f) * 2147483648.0);
        }
    }
    return 0;
}

int32_t applyGain(int32_t sample, float gain) {
    double scaled = static_cast<double>(sample) * gain;
    if (scaled >= 2147483647.0) return INT32_MAX;
    if (scaled <= -2147483648.0) return INT32_MIN;
    return static_cast<int32_t>(std::lrint(scaled));
}

void writeSample(uint8_t* out, int32_t sample, int subslotBytes) {
    auto bits = static_cast<uint32_t>(sample);
    switch (subslotBytes) {
        case 2:
            out[0] = static_cast<uint8_t>(bits >> 16);
            out[1] = static_cast<uint8_t>(bits >> 24);
            break;
        case 3:
            out[0] = static_cast<uint8_t>(bits >> 8);
            out[1] = static_cast<uint8_t>(bits >> 16);
            out[2] = static_cast<uint8_t>(bits >> 24);
            break;
        default:
            out[0] = static_cast<uint8_t>(bits);
            out[1] = static_cast<uint8_t>(bits >> 8);
            out[2] = static_cast<uint8_t>(bits >> 16);
            out[3] = static_cast<uint8_t>(bits >> 24);
            break;
    }
}

}  // namespace

std::unique_ptr<UsbAudioStream> UsbAudioStream::open(int fd, int* error) {
    std::unique_ptr<UsbAudioStream> stream(new UsbAudioStream());
    libusb_init_option option{};
    option.option = LIBUSB_OPTION_NO_DEVICE_DISCOVERY;
    int r = libusb_init_context(&stream->ctx_, &option, 1);
    if (r != LIBUSB_SUCCESS) {
        stream->ctx_ = nullptr;
        *error = r;
        return nullptr;
    }
    r = libusb_wrap_sys_device(stream->ctx_, static_cast<intptr_t>(fd), &stream->handle_);
    if (r != LIBUSB_SUCCESS) {
        stream->handle_ = nullptr;
        *error = r;
        return nullptr;
    }
    stream->highSpeed_ = libusb_get_device_speed(libusb_get_device(stream->handle_)) >= LIBUSB_SPEED_HIGH;
    stream->eventRunning_.store(true);
    stream->eventThread_ = std::thread(&UsbAudioStream::eventLoop, stream.get());
    *error = 0;
    return stream;
}

UsbAudioStream::~UsbAudioStream() {
    stop();
    if (eventThread_.joinable()) {
        eventRunning_.store(false);
        libusb_interrupt_event_handler(ctx_);
        eventThread_.join();
    }
    // 包装的文件描述符由 Java 侧 UsbDeviceConnection 负责关闭
    if (handle_ != nullptr) libusb_close(handle_);
    if (ctx_ != nullptr) libusb_exit(ctx_);
}

void UsbAudioStream::eventLoop() {
    while (eventRunning_.load()) {
        timeval tv{0, 100000};
        libusb_handle_events_timeout_completed(ctx_, &tv, nullptr);
    }
}

int UsbAudioStream::start(const StreamConfig& config) {
    std::lock_guard<std::mutex> lock(controlMutex_);
    stopTransfers();

    size_t sampleBytes = inputSampleBytes(config.inputEncoding);
    if (config.sampleRate <= 0 || config.channels <= 0 || config.dataMaxPacket <= 0 || sampleBytes == 0 ||
        config.subslotBytes < 2 || config.subslotBytes > 4 || config.dataInterval < 1 || config.dataInterval > 4) {
        return LIBUSB_ERROR_INVALID_PARAM;
    }
    config_ = config;
    outFrameBytes_ = static_cast<size_t>(config.subslotBytes) * config.channels;
    inFrameBytes_ = sampleBytes * config.channels;

    int framesPerMs = highSpeed_ ? 8 : 1;
    int packetsPerSecond = framesPerMs * 1000 >> (config.dataInterval - 1);
    if (packetsPerSecond <= 0) return LIBUSB_ERROR_INVALID_PARAM;
    uint64_t nominal = (static_cast<uint64_t>(config.sampleRate) << 16) / packetsPerSecond;
    // 与 Uac2FormatSelector.fitsPacket 一致：名义值向上取整，有反馈时多留一帧
    uint64_t maxFrames = (nominal + 0xFFFF) >> 16;
    if (config.feedbackEndpoint != 0) maxFrames += 1;
    if (maxFrames * outFrameBytes_ > static_cast<size_t>(config.dataMaxPacket)) {
        LOGW("采样率 %d 超出端点包长 %d", config.sampleRate, config.dataMaxPacket);
        return LIBUSB_ERROR_INVALID_PARAM;
    }
    framesPerPacketQ16_.store(static_cast<uint32_t>(nominal));
    packetAccumulatorQ16_ = 0;
    feedbackShift_ = config.dataInterval - 1;
    // 每次提交约 2ms 的包
    packetsPerTransfer_ = std::max(1, packetsPerSecond / 500);

    size_t ringFrames = static_cast<size_t>(config.sampleRate) * std::max(config.bufferMs, 50) / 1000;
    ring_.assign(ringFrames * outFrameBytes_, 0);
    readPos_.store(0);
    writePos_.store(0);
    flushTarget_.store(kNoFlush);
    framesPlayed_.store(0);
    failed_.store(false);
    lastError_.store(0);
    streaming_.store(true);

    size_t transferBytes = static_cast<size_t>(config.dataMaxPacket) * packetsPerTransfer_;
    for (int i = 0; i < kDataTransfers; ++i) {
        libusb_transfer* transfer = libusb_alloc_transfer(packetsPerTransfer_);
        if (transfer == nullptr) {
            stopTransfers();
            return LIBUSB_ERROR_NO_MEM;
        }
        auto* buffer = new uint8_t[transferBytes];
        libusb_fill_iso_transfer(transfer, handle_, static_cast<unsigned char>(config.dataEndpoint), buffer,
                                 static_cast<int>(transferBytes), packetsPerTransfer_, dataCallback, this, 0);
        dataTransfers_.push_back(transfer);
    }

    if (config.feedbackEndpoint != 0 && config.feedbackMaxPacket > 0) {
        feedbackBuffer_.assign(static_cast<size_t>(config.feedbackMaxPacket), 0);
        feedbackTransfer_ = libusb_alloc_transfer(1);
        if (feedbackTransfer_ == nullptr) {
            stopTransfers();
            return LIBUSB_ERROR_NO_MEM;
        }
        libusb_fill_iso_transfer(feedbackTransfer_, handle_, static_cast<unsigned char>(config.feedbackEndpoint),
                                 feedbackBuffer_.data(), config.feedbackMaxPacket, 1, feedbackCallback, this, 0);
        libusb_set_iso_packet_lengths(feedbackTransfer_, static_cast<unsigned int>(config.feedbackMaxPacket));
        inFlight_.fetch_add(1);
        int r = libusb_submit_transfer(feedbackTransfer_);
        if (r != LIBUSB_SUCCESS) {
            inFlight_.fetch_sub(1);
            LOGW("反馈端点提交失败 %s，按名义速率发送", libusb_error_name(r));
        }
    }

    for (libusb_transfer* transfer : dataTransfers_) {
        inFlight_.fetch_add(1);
        fillAndSubmit(transfer);
        if (failed_.load()) {
            int error = lastError_.load();
            stopTransfers();
            return error != 0 ? error : LIBUSB_ERROR_OTHER;
        }
    }
    LOGI("开始输出 %dHz %d声道 子槽%d字节 %s速 每包%.3f帧", config.sampleRate, config.channels, config.subslotBytes,
         highSpeed_ ? "高" : "全", static_cast<double>(nominal) / 65536.0);
    return LIBUSB_SUCCESS;
}

void UsbAudioStream::stop() {
    std::lock_guard<std::mutex> lock(controlMutex_);
    stopTransfers();
}

void UsbAudioStream::stopTransfers() {
    streaming_.store(false);
    for (libusb_transfer* transfer : dataTransfers_) libusb_cancel_transfer(transfer);
    if (feedbackTransfer_ != nullptr) libusb_cancel_transfer(feedbackTransfer_);
    auto deadline = std::chrono::steady_clock::now() + std::chrono::seconds(1);
    while (inFlight_.load() > 0 && std::chrono::steady_clock::now() < deadline) {
        std::this_thread::sleep_for(std::chrono::milliseconds(2));
    }
    if (inFlight_.load() > 0) {
        // 回调仍可能访问传输对象，宁可泄漏也不释放
        LOGW("仍有 %d 个传输未结束，放弃回收", inFlight_.load());
        dataTransfers_.clear();
        feedbackTransfer_ = nullptr;
        inFlight_.store(0);
        return;
    }
    for (libusb_transfer* transfer : dataTransfers_) {
        delete[] transfer->buffer;
        libusb_free_transfer(transfer);
    }
    dataTransfers_.clear();
    if (feedbackTransfer_ != nullptr) {
        libusb_free_transfer(feedbackTransfer_);
        feedbackTransfer_ = nullptr;
    }
}

void UsbAudioStream::fail(int error) {
    if (!failed_.exchange(true)) {
        lastError_.store(error);
        LOGW("输出中断 %s", libusb_error_name(error));
    }
}

int UsbAudioStream::framesForNextPacket() {
    packetAccumulatorQ16_ += framesPerPacketQ16_.load(std::memory_order_relaxed);
    int frames = static_cast<int>(packetAccumulatorQ16_ >> 16);
    packetAccumulatorQ16_ &= 0xFFFF;
    int maxFrames = config_.dataMaxPacket / static_cast<int>(outFrameBytes_);
    return std::min(frames, maxFrames);
}

void UsbAudioStream::fillAndSubmit(libusb_transfer* transfer) {
    size_t flushTarget = flushTarget_.exchange(kNoFlush);
    if (flushTarget != kNoFlush) {
        readPos_.store(flushTarget, std::memory_order_release);
        // 进度从丢弃点重新计数，与 AudioTrack flush 后的播放头一致
        framesPlayed_.store(0, std::memory_order_release);
    }

    bool paused = paused_.load(std::memory_order_acquire);
    size_t read = readPos_.load(std::memory_order_relaxed);
    size_t available = writePos_.load(std::memory_order_acquire) - read;
    size_t capacity = ring_.size();
    uint8_t* out = transfer->buffer;
    size_t total = 0;
    int64_t played = 0;

    for (int i = 0; i < transfer->num_iso_packets; ++i) {
        size_t bytes = static_cast<size_t>(framesForNextPacket()) * outFrameBytes_;
        size_t copy = paused ? 0 : std::min(bytes, available);
        size_t copied = 0;
        while (copied < copy) {
            size_t offset = (read + copied) % capacity;
            size_t chunk = std::min(copy - copied, capacity - offset);
            std::memcpy(out + total + copied, ring_.data() + offset, chunk);
            copied += chunk;
        }
        // 欠载时补静音，保持等时节拍
        if (copy < bytes) std::memset(out + total + copy, 0, bytes - copy);
        read += copy;
        available -= copy;
        played += static_cast<int64_t>(copy / outFrameBytes_);
        transfer->iso_packet_desc[i].length = static_cast<unsigned int>(bytes);
        total += bytes;
    }
    readPos_.store(read, std::memory_order_release);
    framesPlayed_.fetch_add(played, std::memory_order_acq_rel);
    transfer->length = static_cast<int>(total);

    int r = libusb_submit_transfer(transfer);
    if (r != LIBUSB_SUCCESS) {
        inFlight_.fetch_sub(1);
        fail(r);
    }
}

void UsbAudioStream::dataCallback(libusb_transfer* transfer) {
    auto* self = static_cast<UsbAudioStream*>(transfer->user_data);
    if (!self->streaming_.load() || transfer->status == LIBUSB_TRANSFER_CANCELLED) {
        self->inFlight_.fetch_sub(1);
        return;
    }
    if (transfer->status == LIBUSB_TRANSFER_NO_DEVICE || transfer->status == LIBUSB_TRANSFER_ERROR) {
        self->inFlight_.fetch_sub(1);
        self->fail(transfer->status == LIBUSB_TRANSFER_NO_DEVICE ? LIBUSB_ERROR_NO_DEVICE : LIBUSB_ERROR_IO);
        return;
    }
    self->fillAndSubmit(transfer);
}

void UsbAudioStream::feedbackCallback(libusb_transfer* transfer) {
    auto* self = static_cast<UsbAudioStream*>(transfer->user_data);
    if (!self->streaming_.load() || transfer->status == LIBUSB_TRANSFER_CANCELLED ||
        transfer->status == LIBUSB_TRANSFER_NO_DEVICE) {
        self->inFlight_.fetch_sub(1);
        return;
    }
    self->onFeedback(transfer);
    if (libusb_submit_transfer(transfer) != LIBUSB_SUCCESS) self->inFlight_.fetch_sub(1);
}

// 高速为每微帧帧数的 16.16，全速为每帧的 10.14（3 字节）
void UsbAudioStream::onFeedback(libusb_transfer* transfer) {
    if (transfer->status != LIBUSB_TRANSFER_COMPLETED) return;
    const libusb_iso_packet_descriptor& packet = transfer->iso_packet_desc[0];
    if (packet.status != LIBUSB_TRANSFER_COMPLETED) return;
    const uint8_t* p = transfer->buffer;
    uint32_t value;
    if (packet.actual_length >= 4) {
        value = static_cast<uint32_t>(p[0]) | (static_cast<uint32_t>(p[1]) << 8) |
                (static_cast<uint32_t>(p[2]) << 16) | (static_cast<uint32_t>(p[3]) << 24);
    } else if (packet.actual_length == 3) {
        value = (static_cast<uint32_t>(p[0]) | (static_cast<uint32_t>(p[1]) << 8) |
                 (static_cast<uint32_t>(p[2]) << 16)) << 2;
    } else {
        return;
    }
    uint64_t perPacket = static_cast<uint64_t>(value) << feedbackShift_;
    int packetsPerSecond = (highSpeed_ ? 8000 : 1000) >> feedbackShift_;
    uint64_t nominal = (static_cast<uint64_t>(config_.sampleRate) << 16) / packetsPerSecond;
    // 偏离名义值超过 1/8 视为异常读数
    if (perPacket < nominal - nominal / 8 || perPacket > nominal + nominal / 8) return;
    framesPerPacketQ16_.store(static_cast<uint32_t>(perPacket), std::memory_order_relaxed);
}

size_t UsbAudioStream::write(const uint8_t* data, size_t size) {
    if (!streaming_.load(std::memory_order_acquire) || inFrameBytes_ == 0 || ring_.empty()) return 0;
    size_t capacity = ring_.size();
    size_t write = writePos_.load(std::memory_order_relaxed);
    size_t used = write - readPos_.load(std::memory_order_acquire);
    size_t freeFrames = (capacity - used) / outFrameBytes_;
    size_t frames = std::min(size / inFrameBytes_, freeFrames);
    if (frames == 0) return 0;

    float gain = gain_.load(std::memory_order_relaxed);
    bool unity = gain == 1.0f;
    size_t sampleBytes = inputSampleBytes(config_.inputEncoding);
    uint8_t sample[4];
    for (size_t f = 0; f < frames; ++f) {
        for (int c = 0; c < config_.channels; ++c) {
            const uint8_t* in = data + f * inFrameBytes_ + static_cast<size_t>(c) * sampleBytes;
            int32_t value = readSample(in, config_.inputEncoding);
            if (!unity) value = applyGain(value, gain);
            writeSample(sample, value, config_.subslotBytes);
            for (int b = 0; b < config_.subslotBytes; ++b) {
                ring_[(write + static_cast<size_t>(b)) % capacity] = sample[b];
            }
            write += static_cast<size_t>(config_.subslotBytes);
        }
    }
    writePos_.store(write, std::memory_order_release);
    return frames * inFrameBytes_;
}

void UsbAudioStream::setPaused(bool paused) {
    paused_.store(paused, std::memory_order_release);
}

// 由写入方记下目标位置，读取方在下一次填包时丢弃之前的数据，避免两端同时改读指针
void UsbAudioStream::flush() {
    flushTarget_.store(writePos_.load(std::memory_order_acquire), std::memory_order_release);
}

void UsbAudioStream::setGain(float gain) {
    gain_.store(std::clamp(gain, 0.0f, 1.0f), std::memory_order_relaxed);
}

size_t UsbAudioStream::bufferedFrames() const {
    if (outFrameBytes_ == 0) return 0;
    return (writePos_.load(std::memory_order_acquire) - readPos_.load(std::memory_order_acquire)) / outFrameBytes_;
}

int UsbAudioStream::attachKernelDriver(int interfaceNumber) {
    return libusb_attach_kernel_driver(handle_, interfaceNumber);
}

}  // namespace melodia

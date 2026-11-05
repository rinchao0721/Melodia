#pragma once

#include <atomic>
#include <cstddef>
#include <cstdint>
#include <memory>
#include <mutex>
#include <thread>
#include <vector>

struct libusb_context;
struct libusb_device_handle;
struct libusb_transfer;

namespace melodia {

// 输入 PCM 编码，与 Android AudioFormat 的取值一致
enum class InputEncoding : int {
    Pcm16 = 2,
    PcmFloat = 4,
    Pcm24Packed = 21,
    Pcm32 = 22,
};

struct StreamConfig {
    int dataEndpoint = 0;
    int dataMaxPacket = 0;
    // 0 表示无显式反馈端点（自适应或同步端点）
    int feedbackEndpoint = 0;
    int feedbackMaxPacket = 0;
    // 以 2^(bInterval-1) 个（微）帧为一个服务周期
    int dataInterval = 1;
    int sampleRate = 0;
    int channels = 2;
    int subslotBytes = 4;
    int bitResolution = 24;
    InputEncoding inputEncoding = InputEncoding::PcmFloat;
    int bufferMs = 200;
};

// 单个 UAC2 等时输出流：上层写入 PCM，事件线程按反馈速率发包
class UsbAudioStream {
public:
    static std::unique_ptr<UsbAudioStream> open(int fd, int* error);
    ~UsbAudioStream();

    UsbAudioStream(const UsbAudioStream&) = delete;
    UsbAudioStream& operator=(const UsbAudioStream&) = delete;

    int start(const StreamConfig& config);
    void stop();

    // 返回实际接收的输入字节数，缓冲满时可能为 0
    size_t write(const uint8_t* data, size_t size);
    void setPaused(bool paused);
    void flush();
    void setGain(float gain);

    int64_t framesPlayed() const { return framesPlayed_.load(std::memory_order_acquire); }
    size_t bufferedFrames() const;
    bool failed() const { return failed_.load(std::memory_order_acquire); }
    // 释放接口后调用，让系统声卡驱动重新接管
    int attachKernelDriver(int interfaceNumber);
    int lastError() const { return lastError_.load(std::memory_order_acquire); }
    bool highSpeed() const { return highSpeed_; }

private:
    UsbAudioStream() = default;

    void eventLoop();
    void fillAndSubmit(libusb_transfer* transfer);
    void onFeedback(libusb_transfer* transfer);
    int framesForNextPacket();
    void stopTransfers();
    void fail(int error);

    static void dataCallback(libusb_transfer* transfer);
    static void feedbackCallback(libusb_transfer* transfer);

    libusb_context* ctx_ = nullptr;
    libusb_device_handle* handle_ = nullptr;
    bool highSpeed_ = true;

    StreamConfig config_;
    size_t outFrameBytes_ = 0;
    size_t inFrameBytes_ = 0;
    int packetsPerTransfer_ = 0;

    // 单生产者单消费者环形缓冲，存放已转换为设备格式的帧
    std::vector<uint8_t> ring_;
    std::atomic<size_t> readPos_{0};
    std::atomic<size_t> writePos_{0};
    std::atomic<size_t> flushTarget_{SIZE_MAX};

    // 每包帧数，16.16 定点；有反馈时随反馈更新
    std::atomic<uint32_t> framesPerPacketQ16_{0};
    uint32_t packetAccumulatorQ16_ = 0;
    int feedbackShift_ = 0;

    std::atomic<float> gain_{1.0f};
    std::atomic<bool> paused_{true};
    std::atomic<bool> streaming_{false};
    std::atomic<bool> failed_{false};
    std::atomic<int> lastError_{0};
    std::atomic<int> inFlight_{0};
    std::atomic<int64_t> framesPlayed_{0};

    std::vector<libusb_transfer*> dataTransfers_;
    libusb_transfer* feedbackTransfer_ = nullptr;
    std::vector<uint8_t> feedbackBuffer_;

    std::thread eventThread_;
    std::atomic<bool> eventRunning_{false};
    std::mutex controlMutex_;
};

}  // namespace melodia

#include <jni.h>

#include <libusb.h>

#include "usb_audio_stream.h"

using melodia::InputEncoding;
using melodia::StreamConfig;
using melodia::UsbAudioStream;

namespace {

UsbAudioStream* fromHandle(jlong handle) {
    return reinterpret_cast<UsbAudioStream*>(handle);
}

// 与 UsbAudioNative.kt 中的下标一一对应
enum ConfigIndex {
    kDataEndpoint = 0,
    kDataMaxPacket,
    kFeedbackEndpoint,
    kFeedbackMaxPacket,
    kDataInterval,
    kSampleRate,
    kChannels,
    kSubslotBytes,
    kBitResolution,
    kInputEncoding,
    kBufferMs,
    kConfigSize,
};

}  // namespace

extern "C" {

JNIEXPORT jlong JNICALL
Java_com_lin0721_linmusic_core_player_usb_UsbAudioNative_nativeOpen(JNIEnv* env, jobject, jint fd, jintArray errorOut) {
    int error = 0;
    std::unique_ptr<UsbAudioStream> stream = UsbAudioStream::open(fd, &error);
    if (errorOut != nullptr && env->GetArrayLength(errorOut) > 0) {
        jint value = error;
        env->SetIntArrayRegion(errorOut, 0, 1, &value);
    }
    return reinterpret_cast<jlong>(stream.release());
}

JNIEXPORT void JNICALL
Java_com_lin0721_linmusic_core_player_usb_UsbAudioNative_nativeClose(JNIEnv*, jobject, jlong handle) {
    delete fromHandle(handle);
}

JNIEXPORT jint JNICALL
Java_com_lin0721_linmusic_core_player_usb_UsbAudioNative_nativeStart(JNIEnv* env, jobject, jlong handle, jintArray values) {
    if (handle == 0 || values == nullptr || env->GetArrayLength(values) < kConfigSize) return LIBUSB_ERROR_INVALID_PARAM;
    jint v[kConfigSize];
    env->GetIntArrayRegion(values, 0, kConfigSize, v);
    StreamConfig config;
    config.dataEndpoint = v[kDataEndpoint];
    config.dataMaxPacket = v[kDataMaxPacket];
    config.feedbackEndpoint = v[kFeedbackEndpoint];
    config.feedbackMaxPacket = v[kFeedbackMaxPacket];
    config.dataInterval = v[kDataInterval];
    config.sampleRate = v[kSampleRate];
    config.channels = v[kChannels];
    config.subslotBytes = v[kSubslotBytes];
    config.bitResolution = v[kBitResolution];
    config.inputEncoding = static_cast<InputEncoding>(v[kInputEncoding]);
    config.bufferMs = v[kBufferMs];
    return fromHandle(handle)->start(config);
}

JNIEXPORT void JNICALL
Java_com_lin0721_linmusic_core_player_usb_UsbAudioNative_nativeStop(JNIEnv*, jobject, jlong handle) {
    if (handle != 0) fromHandle(handle)->stop();
}

JNIEXPORT jint JNICALL
Java_com_lin0721_linmusic_core_player_usb_UsbAudioNative_nativeWriteDirect(
    JNIEnv* env, jobject, jlong handle, jobject buffer, jint offset, jint size) {
    if (handle == 0 || buffer == nullptr || offset < 0 || size <= 0) return 0;
    auto* base = static_cast<uint8_t*>(env->GetDirectBufferAddress(buffer));
    jlong capacity = env->GetDirectBufferCapacity(buffer);
    if (base == nullptr || static_cast<jlong>(offset) + size > capacity) return 0;
    return static_cast<jint>(fromHandle(handle)->write(base + offset, static_cast<size_t>(size)));
}

JNIEXPORT jint JNICALL
Java_com_lin0721_linmusic_core_player_usb_UsbAudioNative_nativeWriteArray(
    JNIEnv* env, jobject, jlong handle, jbyteArray array, jint offset, jint size) {
    if (handle == 0 || array == nullptr || offset < 0 || size <= 0) return 0;
    if (static_cast<jlong>(offset) + size > env->GetArrayLength(array)) return 0;
    jbyte* bytes = env->GetByteArrayElements(array, nullptr);
    if (bytes == nullptr) return 0;
    size_t written = fromHandle(handle)->write(reinterpret_cast<uint8_t*>(bytes) + offset, static_cast<size_t>(size));
    env->ReleaseByteArrayElements(array, bytes, JNI_ABORT);
    return static_cast<jint>(written);
}

JNIEXPORT void JNICALL
Java_com_lin0721_linmusic_core_player_usb_UsbAudioNative_nativeSetPaused(JNIEnv*, jobject, jlong handle, jboolean paused) {
    if (handle != 0) fromHandle(handle)->setPaused(paused == JNI_TRUE);
}

JNIEXPORT void JNICALL
Java_com_lin0721_linmusic_core_player_usb_UsbAudioNative_nativeFlush(JNIEnv*, jobject, jlong handle) {
    if (handle != 0) fromHandle(handle)->flush();
}

JNIEXPORT void JNICALL
Java_com_lin0721_linmusic_core_player_usb_UsbAudioNative_nativeSetGain(JNIEnv*, jobject, jlong handle, jfloat gain) {
    if (handle != 0) fromHandle(handle)->setGain(gain);
}

JNIEXPORT jlong JNICALL
Java_com_lin0721_linmusic_core_player_usb_UsbAudioNative_nativeFramesPlayed(JNIEnv*, jobject, jlong handle) {
    return handle != 0 ? fromHandle(handle)->framesPlayed() : 0;
}

JNIEXPORT jlong JNICALL
Java_com_lin0721_linmusic_core_player_usb_UsbAudioNative_nativeBufferedFrames(JNIEnv*, jobject, jlong handle) {
    return handle != 0 ? static_cast<jlong>(fromHandle(handle)->bufferedFrames()) : 0;
}

JNIEXPORT jint JNICALL
Java_com_lin0721_linmusic_core_player_usb_UsbAudioNative_nativeLastError(JNIEnv*, jobject, jlong handle) {
    if (handle == 0) return LIBUSB_ERROR_INVALID_PARAM;
    UsbAudioStream* stream = fromHandle(handle);
    return stream->failed() ? stream->lastError() : 0;
}

JNIEXPORT jint JNICALL
Java_com_lin0721_linmusic_core_player_usb_UsbAudioNative_nativeAttachKernelDriver(JNIEnv*, jobject, jlong handle, jint iface) {
    return handle != 0 ? fromHandle(handle)->attachKernelDriver(iface) : LIBUSB_ERROR_INVALID_PARAM;
}

JNIEXPORT jboolean JNICALL
Java_com_lin0721_linmusic_core_player_usb_UsbAudioNative_nativeIsHighSpeed(JNIEnv*, jobject, jlong handle) {
    return handle != 0 && fromHandle(handle)->highSpeed() ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jstring JNICALL
Java_com_lin0721_linmusic_core_player_usb_UsbAudioNative_nativeErrorName(JNIEnv* env, jobject, jint error) {
    return env->NewStringUTF(libusb_error_name(error));
}

}  // extern "C"

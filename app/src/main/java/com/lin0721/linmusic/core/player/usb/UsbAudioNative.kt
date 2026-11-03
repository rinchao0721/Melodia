package com.lin0721.linmusic.core.player.usb

import com.lin0721.linmusic.core.log.AppLogger
import java.nio.ByteBuffer

private const val TAG = "UsbAudioNative"

// 原生等时输出流的 JNI 入口；handle 为 0 表示无效
internal object UsbAudioNative {

    // 下标与 usb_audio_jni.cpp 的 ConfigIndex 一致
    const val CONFIG_DATA_ENDPOINT = 0
    const val CONFIG_DATA_MAX_PACKET = 1
    const val CONFIG_FEEDBACK_ENDPOINT = 2
    const val CONFIG_FEEDBACK_MAX_PACKET = 3
    const val CONFIG_DATA_INTERVAL = 4
    const val CONFIG_SAMPLE_RATE = 5
    const val CONFIG_CHANNELS = 6
    const val CONFIG_SUBSLOT_BYTES = 7
    const val CONFIG_BIT_RESOLUTION = 8
    const val CONFIG_INPUT_ENCODING = 9
    const val CONFIG_BUFFER_MS = 10
    const val CONFIG_SIZE = 11

    val isAvailable: Boolean by lazy {
        try {
            System.loadLibrary("usb-1.0")
            System.loadLibrary("melodia_usbaudio")
            true
        } catch (e: UnsatisfiedLinkError) {
            AppLogger.w(TAG, "原生库加载失败", e)
            false
        }
    }

    // error 第 0 位写入 libusb 错误码
    external fun nativeOpen(fd: Int, error: IntArray): Long
    external fun nativeClose(handle: Long)
    external fun nativeStart(handle: Long, config: IntArray): Int
    external fun nativeStop(handle: Long)
    external fun nativeWriteDirect(handle: Long, buffer: ByteBuffer, offset: Int, size: Int): Int
    external fun nativeWriteArray(handle: Long, array: ByteArray, offset: Int, size: Int): Int
    external fun nativeSetPaused(handle: Long, paused: Boolean)
    external fun nativeFlush(handle: Long)
    external fun nativeSetGain(handle: Long, gain: Float)
    external fun nativeFramesPlayed(handle: Long): Long
    external fun nativeBufferedFrames(handle: Long): Long

    // 0 表示流仍正常
    external fun nativeLastError(handle: Long): Int
    external fun nativeAttachKernelDriver(handle: Long, iface: Int): Int
    external fun nativeIsHighSpeed(handle: Long): Boolean
    external fun nativeErrorName(error: Int): String
}

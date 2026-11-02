package com.lin0721.linmusic.desktop.platform.windows.audio

import com.lin0721.linmusic.core.log.AppLogger
import com.lin0721.linmusic.desktop.platform.windows.ComObject
import com.lin0721.linmusic.desktop.platform.windows.Ole32
import com.lin0721.linmusic.desktop.platform.windows.comGuid
import com.lin0721.linmusic.feature.recognition.domain.RecognitionException
import com.lin0721.linmusic.feature.recognition.domain.RecognitionFailure
import com.lin0721.linmusic.feature.recognition.engine.AudioCapture
import com.lin0721.linmusic.feature.recognition.engine.Downsampler
import com.lin0721.linmusic.feature.recognition.engine.RECOGNITION_SAMPLE_RATE
import com.sun.jna.Platform
import com.sun.jna.Pointer
import com.sun.jna.ptr.IntByReference
import com.sun.jna.ptr.PointerByReference
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.isActive
import java.util.concurrent.Executors

private const val TAG = "WasapiLoopbackCapture"

// 虚表序号：IMMDeviceEnumerator.GetDefaultAudioEndpoint / IMMDevice.Activate
private const val SLOT_GET_DEFAULT_AUDIO_ENDPOINT = 4
private const val SLOT_ACTIVATE = 3

// IAudioClient
private const val SLOT_INITIALIZE = 3
private const val SLOT_GET_MIX_FORMAT = 8
private const val SLOT_START = 10
private const val SLOT_STOP = 11
private const val SLOT_GET_SERVICE = 14

// IAudioCaptureClient
private const val SLOT_GET_BUFFER = 3
private const val SLOT_RELEASE_BUFFER = 4
private const val SLOT_GET_NEXT_PACKET_SIZE = 5

private const val E_RENDER = 0
private const val E_CONSOLE = 0
private const val AUDCLNT_SHAREMODE_SHARED = 0
private const val AUDCLNT_STREAMFLAGS_LOOPBACK = 0x00020000
private const val AUDCLNT_BUFFERFLAGS_SILENT = 0x2

// 1 秒，单位 100 纳秒
private const val BUFFER_DURATION_HNS = 10_000_000L
private const val POLL_INTERVAL_MS = 20L

// 系统无声时 loopback 不出包，超过该间隔按流逝时间补零
private const val SILENCE_GAP_NANOS = 100_000_000L

private const val WAVE_FORMAT_PCM = 1
private const val WAVE_FORMAT_IEEE_FLOAT = 3
private const val WAVE_FORMAT_EXTENSIBLE = 0xFFFE

// {BCDE0395-E52F-467C-8E3D-C4579291692E}
private val ClsidMMDeviceEnumerator = comGuid(0xBCDE0395.toInt(), 0xE52F, 0x467C, 0x8E, 0x3D, 0xC4, 0x57, 0x92, 0x91, 0x69, 0x2E)

// {A95664D2-9614-4F35-A746-DE8DB63617E6}
private val IidIMMDeviceEnumerator = comGuid(0xA95664D2.toInt(), 0x9614, 0x4F35, 0xA7, 0x46, 0xDE, 0x8D, 0xB6, 0x36, 0x17, 0xE6)

// {1CB9AD4C-DBFA-4C32-B178-C2F568A703B2}
private val IidIAudioClient = comGuid(0x1CB9AD4C, 0xDBFA, 0x4C32, 0xB1, 0x78, 0xC2, 0xF5, 0x68, 0xA7, 0x03, 0xB2)

// {C8ADBD64-E71E-48A0-A4DE-185C395CD317}
private val IidIAudioCaptureClient = comGuid(0xC8ADBD64.toInt(), 0xE71E, 0x48A0, 0xA4, 0xDE, 0x18, 0x5C, 0x39, 0x5C, 0xD3, 0x17)

private enum class SampleFormat { FLOAT32, INT16, INT32 }

private class MixFormat(val sampleRate: Int, val channels: Int, val sample: SampleFormat)

// 录制系统默认输出设备正在播放的声音（WASAPI 共享模式 loopback）
class WasapiLoopbackCapture : AudioCapture {

    companion object {
        val isSupported: Boolean get() = Platform.isWindows()
    }

    override fun capture(): Flow<FloatArray> {
        // COM 对象只在创建它的线程上使用
        val dispatcher = Executors.newSingleThreadExecutor { runnable ->
            Thread(runnable, "wasapi-loopback").apply { isDaemon = true }
        }.asCoroutineDispatcher()
        return flow { record(this) }
            .flowOn(dispatcher)
            .onCompletion { dispatcher.close() }
    }

    private suspend fun record(collector: FlowCollector<FloatArray>) {
        if (!isSupported) {
            throw RecognitionException(RecognitionFailure.RECORDER_UNAVAILABLE, "当前系统不支持内录")
        }
        val ole32 = try {
            Ole32.INSTANCE
        } catch (e: LinkageError) {
            throw RecognitionException(RecognitionFailure.RECORDER_UNAVAILABLE, "加载 COM 失败", e)
        }
        val initHr = ole32.CoInitializeEx(null, Ole32.COINIT_MULTITHREADED)
        var enumerator: ComObject? = null
        var device: ComObject? = null
        var client: ComObject? = null
        var captureClient: ComObject? = null
        var mixPointer: Pointer? = null
        var started = false
        try {
            val ref = PointerByReference()
            requireSucceeded(ole32.CoCreateInstance(ClsidMMDeviceEnumerator, null, Ole32.CLSCTX_ALL, IidIMMDeviceEnumerator, ref), "创建设备枚举器")
            enumerator = ComObject(ref.value)
            requireSucceeded(enumerator.call(SLOT_GET_DEFAULT_AUDIO_ENDPOINT, E_RENDER, E_CONSOLE, ref), "获取默认输出设备")
            device = ComObject(ref.value)
            requireSucceeded(device.call(SLOT_ACTIVATE, IidIAudioClient, Ole32.CLSCTX_ALL, null, ref), "激活音频客户端")
            client = ComObject(ref.value)
            requireSucceeded(client.call(SLOT_GET_MIX_FORMAT, ref), "读取混音格式")
            mixPointer = ref.value
            val format = parseMixFormat(mixPointer)
            requireSucceeded(
                client.call(
                    SLOT_INITIALIZE, AUDCLNT_SHAREMODE_SHARED, AUDCLNT_STREAMFLAGS_LOOPBACK,
                    BUFFER_DURATION_HNS, 0L, mixPointer, null
                ),
                "初始化内录"
            )
            requireSucceeded(client.call(SLOT_GET_SERVICE, IidIAudioCaptureClient, ref), "获取采集接口")
            captureClient = ComObject(ref.value)
            requireSucceeded(client.call(SLOT_START), "启动内录")
            started = true
            AppLogger.d(TAG, "内录配置 rate=${format.sampleRate} channels=${format.channels} sample=${format.sample}")
            pump(collector, captureClient, format)
        } finally {
            if (started) runCatching { client?.call(SLOT_STOP) }
            captureClient?.release()
            client?.release()
            device?.release()
            enumerator?.release()
            if (mixPointer != null) ole32.CoTaskMemFree(mixPointer)
            // S_OK 与 S_FALSE 都需要配对反初始化
            if (initHr == 0 || initHr == 1) ole32.CoUninitialize()
        }
    }

    private suspend fun pump(collector: FlowCollector<FloatArray>, captureClient: ComObject, format: MixFormat) {
        val downsampler = Downsampler(format.sampleRate, RECOGNITION_SAMPLE_RATE)
        val dataRef = PointerByReference()
        val framesRef = IntByReference()
        val flagsRef = IntByReference()
        val packetRef = IntByReference()
        val startNanos = System.nanoTime()
        var capturedFrames = 0L
        var lastPacketNanos = startNanos
        while (currentCoroutineContext().isActive) {
            delay(POLL_INTERVAL_MS)
            val chunks = ArrayList<FloatArray>()
            requireSucceeded(captureClient.call(SLOT_GET_NEXT_PACKET_SIZE, packetRef), "读取包大小")
            while (packetRef.value > 0) {
                requireSucceeded(captureClient.call(SLOT_GET_BUFFER, dataRef, framesRef, flagsRef, null, null), "读取内录数据")
                val frames = framesRef.value
                val silent = flagsRef.value and AUDCLNT_BUFFERFLAGS_SILENT != 0
                chunks += if (silent || frames <= 0) FloatArray(frames.coerceAtLeast(0)) else downmix(dataRef.value, frames, format)
                requireSucceeded(captureClient.call(SLOT_RELEASE_BUFFER, frames), "释放内录缓冲")
                capturedFrames += frames
                lastPacketNanos = System.nanoTime()
                requireSucceeded(captureClient.call(SLOT_GET_NEXT_PACKET_SIZE, packetRef), "读取包大小")
            }
            val now = System.nanoTime()
            if (now - lastPacketNanos > SILENCE_GAP_NANOS) {
                val expected = (now - startNanos) * format.sampleRate / 1_000_000_000L
                val gap = expected - capturedFrames
                if (gap > 0) {
                    chunks += FloatArray(gap.toInt())
                    capturedFrames = expected
                }
            }
            if (chunks.isEmpty()) continue
            val merged = FloatArray(chunks.sumOf { it.size })
            var offset = 0
            for (chunk in chunks) {
                chunk.copyInto(merged, offset)
                offset += chunk.size
            }
            val output = downsampler.process(merged)
            if (output.isNotEmpty()) collector.emit(output)
        }
    }

    private fun downmix(data: Pointer, frames: Int, format: MixFormat): FloatArray {
        val channels = format.channels
        val total = frames * channels
        val mono = FloatArray(frames)
        when (format.sample) {
            SampleFormat.FLOAT32 -> {
                val samples = data.getFloatArray(0, total)
                for (frame in 0 until frames) {
                    var sum = 0f
                    for (channel in 0 until channels) sum += samples[frame * channels + channel]
                    mono[frame] = sum / channels
                }
            }
            SampleFormat.INT16 -> {
                val samples = data.getShortArray(0, total)
                for (frame in 0 until frames) {
                    var sum = 0f
                    for (channel in 0 until channels) sum += samples[frame * channels + channel] / 32768f
                    mono[frame] = sum / channels
                }
            }
            SampleFormat.INT32 -> {
                val samples = data.getIntArray(0, total)
                for (frame in 0 until frames) {
                    var sum = 0f
                    for (channel in 0 until channels) sum += samples[frame * channels + channel] / 2_147_483_648f
                    mono[frame] = sum / channels
                }
            }
        }
        return mono
    }

    // WAVEFORMATEX：tag@0 channels@2 rate@4 bits@14；EXTENSIBLE 的 SubFormat 在 24，其 Data1 即实际格式码
    private fun parseMixFormat(pointer: Pointer): MixFormat {
        val tag = pointer.getShort(0).toInt() and 0xFFFF
        val channels = pointer.getShort(2).toInt() and 0xFFFF
        val sampleRate = pointer.getInt(4)
        val bits = pointer.getShort(14).toInt() and 0xFFFF
        val code = if (tag == WAVE_FORMAT_EXTENSIBLE) pointer.getInt(24) else tag
        val sample = when {
            code == WAVE_FORMAT_IEEE_FLOAT && bits == 32 -> SampleFormat.FLOAT32
            code == WAVE_FORMAT_PCM && bits == 16 -> SampleFormat.INT16
            code == WAVE_FORMAT_PCM && bits == 32 -> SampleFormat.INT32
            else -> null
        }
        if (sample == null || channels <= 0 || sampleRate < RECOGNITION_SAMPLE_RATE) {
            throw RecognitionException(
                RecognitionFailure.RECORDER_UNAVAILABLE,
                "不支持的混音格式 tag=$code bits=$bits channels=$channels rate=$sampleRate"
            )
        }
        return MixFormat(sampleRate, channels, sample)
    }

    private fun requireSucceeded(hr: Int, action: String) {
        if (hr < 0) {
            throw RecognitionException(
                RecognitionFailure.RECORDER_UNAVAILABLE,
                "系统内录不可用：$action hr=0x${Integer.toHexString(hr)}"
            )
        }
    }
}

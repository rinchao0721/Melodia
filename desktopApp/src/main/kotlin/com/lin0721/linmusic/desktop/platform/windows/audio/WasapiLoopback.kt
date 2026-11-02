package com.lin0721.linmusic.desktop.platform.windows.audio

import com.lin0721.linmusic.core.log.AppLogger
import com.lin0721.linmusic.desktop.platform.windows.ComObject
import com.lin0721.linmusic.desktop.platform.windows.Ole32
import com.lin0721.linmusic.desktop.platform.windows.comGuid
import com.sun.jna.Pointer
import com.sun.jna.ptr.IntByReference
import com.sun.jna.ptr.PointerByReference
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.isActive
import java.util.concurrent.Executors

private const val TAG = "WasapiLoopback"

// 虚表序号：IMMDeviceEnumerator.GetDefaultAudioEndpoint / IMMDevice.Activate
private const val SLOT_GET_DEFAULT_AUDIO_ENDPOINT = 4
private const val SLOT_ACTIVATE = 3

// IAudioClient
internal const val SLOT_INITIALIZE = 3
private const val SLOT_GET_MIX_FORMAT = 8
private const val SLOT_START = 10
private const val SLOT_STOP = 11
internal const val SLOT_GET_SERVICE = 14

// IAudioCaptureClient
private const val SLOT_GET_BUFFER = 3
private const val SLOT_RELEASE_BUFFER = 4
private const val SLOT_GET_NEXT_PACKET_SIZE = 5

private const val E_RENDER = 0
private const val E_CONSOLE = 0
internal const val AUDCLNT_SHAREMODE_SHARED = 0
internal const val AUDCLNT_STREAMFLAGS_LOOPBACK = 0x00020000
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
internal val IidIAudioClient = comGuid(0x1CB9AD4C, 0xDBFA, 0x4C32, 0xB1, 0x78, 0xC2, 0xF5, 0x68, 0xA7, 0x03, 0xB2)

// {C8ADBD64-E71E-48A0-A4DE-185C395CD317}
internal val IidIAudioCaptureClient = comGuid(0xC8ADBD64.toInt(), 0xE71E, 0x48A0, 0xA4, 0xDE, 0x18, 0x5C, 0x39, 0x5C, 0xD3, 0x17)

internal class WasapiException(message: String, cause: Throwable? = null) : Exception(message, cause)

internal enum class SampleFormat { FLOAT32, INT16, INT32 }

internal class LoopbackFormat(val sampleRate: Int, val channels: Int, val sample: SampleFormat)

// 单声道采样
internal class LoopbackBlock(val samples: FloatArray, val sampleRate: Int)

// 初始化完成、尚未启动的环回流；owned 按释放顺序排列
internal class LoopbackEndpoint(
    val client: ComObject,
    val captureClient: ComObject,
    val format: LoopbackFormat,
    private val owned: List<ComObject>
) {
    fun close() = owned.forEach(ComObject::release)
}

// COM 对象只在创建它的线程上使用，每次收集独占一个线程
internal fun loopbackBlocks(open: (Ole32) -> LoopbackEndpoint): Flow<LoopbackBlock> = flow {
    val dispatcher = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "wasapi-loopback").apply { isDaemon = true }
    }.asCoroutineDispatcher()
    try {
        emitAll(flow { record(open, this) }.flowOn(dispatcher))
    } finally {
        dispatcher.close()
    }
}

private suspend fun record(open: (Ole32) -> LoopbackEndpoint, collector: FlowCollector<LoopbackBlock>) {
    val ole32 = try {
        Ole32.INSTANCE
    } catch (e: LinkageError) {
        throw WasapiException("加载 COM 失败", e)
    }
    val initHr = ole32.CoInitializeEx(null, Ole32.COINIT_MULTITHREADED)
    var endpoint: LoopbackEndpoint? = null
    var started = false
    try {
        val opened = open(ole32)
        endpoint = opened
        requireSucceeded(opened.client.call(SLOT_START), "启动内录")
        started = true
        val format = opened.format
        AppLogger.d(TAG, "内录配置 rate=${format.sampleRate} channels=${format.channels} sample=${format.sample}")
        pump(collector, opened.captureClient, format)
    } finally {
        if (started) runCatching { endpoint?.client?.call(SLOT_STOP) }
        endpoint?.close()
        // S_OK 与 S_FALSE 都需要配对反初始化
        if (initHr == 0 || initHr == 1) ole32.CoUninitialize()
    }
}

// 录制系统默认输出设备的混音，含其他应用的声音
internal fun openDefaultLoopback(ole32: Ole32, minSampleRate: Int = 1): LoopbackEndpoint {
    val owned = ArrayDeque<ComObject>()
    var mixPointer: Pointer? = null
    try {
        val ref = PointerByReference()
        requireSucceeded(ole32.CoCreateInstance(ClsidMMDeviceEnumerator, null, Ole32.CLSCTX_ALL, IidIMMDeviceEnumerator, ref), "创建设备枚举器")
        val enumerator = ComObject(ref.value).also(owned::addFirst)
        requireSucceeded(enumerator.call(SLOT_GET_DEFAULT_AUDIO_ENDPOINT, E_RENDER, E_CONSOLE, ref), "获取默认输出设备")
        val device = ComObject(ref.value).also(owned::addFirst)
        requireSucceeded(device.call(SLOT_ACTIVATE, IidIAudioClient, Ole32.CLSCTX_ALL, null, ref), "激活音频客户端")
        val client = ComObject(ref.value).also(owned::addFirst)
        requireSucceeded(client.call(SLOT_GET_MIX_FORMAT, ref), "读取混音格式")
        val mix = ref.value
        mixPointer = mix
        val format = parseMixFormat(mix, minSampleRate)
        requireSucceeded(
            client.call(
                SLOT_INITIALIZE, AUDCLNT_SHAREMODE_SHARED, AUDCLNT_STREAMFLAGS_LOOPBACK,
                BUFFER_DURATION_HNS, 0L, mix, null
            ),
            "初始化内录"
        )
        requireSucceeded(client.call(SLOT_GET_SERVICE, IidIAudioCaptureClient, ref), "获取采集接口")
        val captureClient = ComObject(ref.value).also(owned::addFirst)
        return LoopbackEndpoint(client, captureClient, format, owned.toList())
    } catch (e: Throwable) {
        owned.forEach(ComObject::release)
        throw e
    } finally {
        if (mixPointer != null) ole32.CoTaskMemFree(mixPointer)
    }
}

private suspend fun pump(collector: FlowCollector<LoopbackBlock>, captureClient: ComObject, format: LoopbackFormat) {
    val dataRef = PointerByReference()
    val framesRef = IntByReference()
    val flagsRef = IntByReference()
    val packetRef = IntByReference()
    val maxGapFrames = format.sampleRate.toLong()
    var anchorNanos = System.nanoTime()
    var capturedFrames = 0L
    var lastPacketNanos = anchorNanos
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
            val expected = (now - anchorNanos) / 1_000L * format.sampleRate / 1_000_000L
            val gap = expected - capturedFrames
            if (gap > 0) {
                chunks += FloatArray(gap.coerceAtMost(maxGapFrames).toInt())
                capturedFrames = expected
            }
            // 缺口过大说明线程被挂起过（如系统休眠），不补这么多静音，从现在重新对齐
            if (gap > maxGapFrames) {
                anchorNanos = now
                capturedFrames = 0L
            }
        }
        if (chunks.isEmpty()) continue
        val merged = FloatArray(chunks.sumOf { it.size })
        var offset = 0
        for (chunk in chunks) {
            chunk.copyInto(merged, offset)
            offset += chunk.size
        }
        if (merged.isNotEmpty()) collector.emit(LoopbackBlock(merged, format.sampleRate))
    }
}

private fun downmix(data: Pointer, frames: Int, format: LoopbackFormat): FloatArray {
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
private fun parseMixFormat(pointer: Pointer, minSampleRate: Int): LoopbackFormat {
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
    if (sample == null || channels <= 0 || sampleRate < minSampleRate) {
        throw WasapiException("不支持的混音格式 tag=$code bits=$bits channels=$channels rate=$sampleRate")
    }
    return LoopbackFormat(sampleRate, channels, sample)
}

internal fun requireSucceeded(hr: Int, action: String) {
    if (hr < 0) throw WasapiException("系统内录不可用：$action hr=0x${Integer.toHexString(hr)}")
}

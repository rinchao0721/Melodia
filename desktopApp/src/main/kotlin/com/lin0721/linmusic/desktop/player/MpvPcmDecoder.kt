package com.lin0721.linmusic.desktop.player

import com.lin0721.linmusic.core.log.AppLogger
import com.lin0721.linmusic.core.player.LoudnessWindow
import com.lin0721.linmusic.core.player.SegmentDecoder
import com.lin0721.linmusic.core.player.TrackEdges
import com.lin0721.linmusic.desktop.player.mpv.MpvEngine
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.Locale

private const val TAG = "MpvPcmDecoder"
private const val DECODE_TIMEOUT_MS = 20_000L

// 分析用实例只解码不出声，ao=pcm 不按实时节奏输出
private val DECODE_OPTIONS = mapOf(
    "ao" to "pcm",
    "ao-pcm-waveheader" to "yes",
    "untimed" to "yes",
    "audio-channels" to "mono",
    "audio-format" to "s16",
    "demuxer-max-bytes" to "4MiB",
    "demuxer-max-back-bytes" to "1MiB"
)

// 用临时 mpv 实例把音频片段导出为 wav 再切成 100ms 响度窗口，首尾分析与响度自测共用；失败或超时返回 null
internal class MpvPcmDecoder : SegmentDecoder {

    // toMs 为空表示解码到结尾
    suspend fun decode(url: String, fromMs: Long, toMs: Long?): List<LoudnessWindow>? {
        val options = if (toMs != null) mapOf("end" to seconds(toMs)) else emptyMap()
        return export(url, start = "+${seconds(fromMs)}", options, timelineStartMs = fromMs)
    }

    // 片段时间戳不参与响度计算，从 0 起算
    override suspend fun decode(uri: String, startFraction: Double, spanMs: Long): List<LoudnessWindow>? {
        val percent = String.format(Locale.ROOT, "%.3f%%", startFraction * 100)
        return export(uri, start = percent, mapOf("length" to seconds(spanMs)), timelineStartMs = 0L)
    }

    private suspend fun export(
        url: String,
        start: String,
        options: Map<String, String>,
        timelineStartMs: Long
    ): List<LoudnessWindow>? = withContext(Dispatchers.IO) {
        val output = runCatching { File.createTempFile("melodia-pcm", ".wav") }
            .onFailure { AppLogger.w(TAG, "创建临时文件失败", it) }
            .getOrNull() ?: return@withContext null
        try {
            if (!runEngine(url, start, options + ("ao-pcm-file" to output.absolutePath))) return@withContext null
            WavLoudness.windows(output.readBytes(), timelineStartMs)
        } catch (e: Exception) {
            AppLogger.w(TAG, "解码失败 start=$start", e)
            null
        } finally {
            output.delete()
        }
    }

    // 释放实例后 wav 才写完并解除占用
    private suspend fun runEngine(url: String, start: String, options: Map<String, String>): Boolean {
        val finished = CompletableDeferred<Boolean>()
        val listener = object : MpvEngine.Listener {
            override fun onPositionChanged(positionMs: Long) = Unit
            override fun onDurationChanged(durationMs: Long) = Unit
            override fun onPauseChanged(paused: Boolean) = Unit
            override fun onFileLoaded() = Unit
            override fun onEnded(isError: Boolean) {
                finished.complete(!isError)
            }
        }
        val engine = try {
            MpvEngine(listener, DECODE_OPTIONS + options)
        } catch (e: LinkageError) {
            AppLogger.w(TAG, "解码实例创建失败", e)
            return false
        } catch (e: IllegalStateException) {
            AppLogger.w(TAG, "解码实例创建失败", e)
            return false
        }
        return try {
            engine.loadSegment(url, start)
            val ok = withTimeoutOrNull(DECODE_TIMEOUT_MS) { finished.await() }
            if (ok == null) AppLogger.w(TAG, "解码超时 start=$start")
            ok == true
        } finally {
            engine.release()
        }
    }

    private fun seconds(ms: Long): String = String.format(Locale.ROOT, "%.3f", ms / 1000.0)
}

// 解析 16 位整数或 32 位浮点 PCM 的 wav，按 100ms 窗口计算响度；首个采样对应 fromMs
internal object WavLoudness {

    private const val FORMAT_PCM = 1
    private const val FORMAT_FLOAT = 3
    private const val FORMAT_EXTENSIBLE = 0xFFFE

    fun windows(bytes: ByteArray, fromMs: Long): List<LoudnessWindow>? {
        val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        if (bytes.size < 12 || ascii(bytes, 0) != "RIFF" || ascii(bytes, 8) != "WAVE") return null
        var offset = 12
        var format = -1
        var channels = 0
        var sampleRate = 0
        var bits = 0
        while (offset + 8 <= bytes.size) {
            val id = ascii(bytes, offset)
            val size = buffer.getInt(offset + 4).toLong() and 0xFFFFFFFFL
            val body = offset + 8
            when (id) {
                "fmt " -> {
                    if (body + 16 > bytes.size) return null
                    format = buffer.getShort(body).toInt() and 0xFFFF
                    channels = buffer.getShort(body + 2).toInt()
                    sampleRate = buffer.getInt(body + 4)
                    bits = buffer.getShort(body + 14).toInt()
                    // 扩展格式的真实编码在子格式 GUID 的前两字节
                    if (format == FORMAT_EXTENSIBLE && body + 26 <= bytes.size) {
                        format = buffer.getShort(body + 24).toInt() and 0xFFFF
                    }
                }
                "data" -> {
                    if (channels <= 0 || sampleRate <= 0) return null
                    // 未正常收尾的文件长度字段可能为 0 或溢出，以实际剩余字节为准
                    val remaining = (bytes.size - body).toLong()
                    val length = if (size in 1..remaining) size.toInt() else remaining.toInt()
                    return when {
                        format == FORMAT_PCM && bits == 16 -> accumulate(buffer, body, length, channels, sampleRate, 2, fromMs) {
                            buffer.getShort(it) / 32768.0
                        }
                        format == FORMAT_FLOAT && bits == 32 -> accumulate(buffer, body, length, channels, sampleRate, 4, fromMs) {
                            buffer.getFloat(it).toDouble()
                        }
                        else -> null
                    }
                }
            }
            if (size > bytes.size - body) return null
            // 块按偶数字节对齐
            offset = body + size.toInt() + (size.toInt() and 1)
        }
        return null
    }

    private inline fun accumulate(
        buffer: ByteBuffer,
        start: Int,
        length: Int,
        channels: Int,
        sampleRate: Int,
        bytesPerSample: Int,
        fromMs: Long,
        read: (Int) -> Double
    ): List<LoudnessWindow> {
        val frameBytes = bytesPerSample * channels
        val frames = length / frameBytes
        val framesPerWindow = (sampleRate * TrackEdges.WINDOW_MS / 1000).toInt().coerceAtLeast(1)
        val windows = ArrayList<LoudnessWindow>(frames / framesPerWindow + 1)
        var frame = 0
        while (frame < frames) {
            val count = minOf(framesPerWindow, frames - frame)
            var sumSquares = 0.0
            for (f in frame until frame + count) {
                var frameSquares = 0.0
                for (ch in 0 until channels) {
                    val sample = read(start + f * frameBytes + ch * bytesPerSample)
                    frameSquares += sample * sample
                }
                sumSquares += frameSquares / channels
            }
            val startMs = fromMs + frame.toLong() * 1000 / sampleRate
            windows += LoudnessWindow(startMs, TrackEdges.dbfs(sumSquares, count.toLong()))
            frame += count
        }
        return windows
    }

    private fun ascii(bytes: ByteArray, offset: Int): String =
        if (offset + 4 > bytes.size) "" else String(bytes, offset, 4, Charsets.US_ASCII)
}

package com.lin0721.linmusic.desktop.player

import com.lin0721.linmusic.core.log.AppLogger
import com.lin0721.linmusic.core.player.LoudnessWindow
import com.lin0721.linmusic.core.player.PcmConsumer
import com.lin0721.linmusic.core.player.SegmentDecoder
import com.lin0721.linmusic.core.player.TrackEdges
import com.lin0721.linmusic.desktop.player.mpv.MpvEngine
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.BufferedInputStream
import java.io.EOFException
import java.io.File
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.Locale

private const val TAG = "MpvPcmDecoder"
private const val DECODE_TIMEOUT_MS = 20_000L
private const val FULL_DECODE_TIMEOUT_MS = 60_000L
private const val STREAM_CHUNK_FRAMES = 4096

// 分析用实例只解码不出声，保留原声道数并输出 float PCM 供 R128 测量与首尾分析使用
private val DECODE_OPTIONS = mapOf(
    "ao" to "pcm",
    "ao-pcm-waveheader" to "yes",
    "untimed" to "yes",
    "audio-format" to "float",
    "demuxer-max-bytes" to "4MiB",
    "demuxer-max-back-bytes" to "1MiB"
)

// 用临时 mpv 实例导出音频 wav 并流式喂给 PcmConsumer 或切为 100ms 响度窗口
internal class MpvPcmDecoder : SegmentDecoder {

    // toMs 为空表示解码到结尾
    suspend fun decode(url: String, fromMs: Long, toMs: Long?): List<LoudnessWindow>? {
        val options = if (toMs != null) mapOf("end" to seconds(toMs)) else emptyMap()
        return exportWindows(url, start = "+${seconds(fromMs)}", options, timelineStartMs = fromMs)
    }

    // 保留供首尾/片段窗口测试直接调用
    suspend fun decode(uri: String, startFraction: Double, spanMs: Long): List<LoudnessWindow>? {
        val percent = String.format(Locale.ROOT, "%.3f%%", startFraction * 100)
        return exportWindows(uri, start = percent, mapOf("length" to seconds(spanMs)), timelineStartMs = 0L)
    }

    // spanMs 为 null 且 startFraction <= 0 时解码整首（用于本地文件 R128 测量）
    override suspend fun decode(uri: String, startFraction: Double, spanMs: Long?, consumer: PcmConsumer): Boolean {
        val start = if (startFraction <= 0.0) "none" else String.format(Locale.ROOT, "%.3f%%", startFraction * 100)
        val options = if (spanMs != null) mapOf("length" to seconds(spanMs)) else emptyMap()
        val timeoutMs = if (spanMs == null) FULL_DECODE_TIMEOUT_MS else DECODE_TIMEOUT_MS
        return exportPcm(uri, start, options, timeoutMs, consumer)
    }

    private suspend fun exportWindows(
        url: String,
        start: String,
        options: Map<String, String>,
        timelineStartMs: Long
    ): List<LoudnessWindow>? = withContext(Dispatchers.IO) {
        val output = runCatching { File.createTempFile("melodia-pcm", ".wav") }
            .onFailure { AppLogger.w(TAG, "创建临时文件失败", it) }
            .getOrNull() ?: return@withContext null
        try {
            if (!runEngine(url, start, options + ("ao-pcm-file" to output.absolutePath), DECODE_TIMEOUT_MS)) {
                return@withContext null
            }
            WavLoudness.windows(output.readBytes(), timelineStartMs)
        } catch (e: Exception) {
            AppLogger.w(TAG, "解码失败 start=$start", e)
            null
        } finally {
            output.delete()
        }
    }

    private suspend fun exportPcm(
        url: String,
        start: String,
        options: Map<String, String>,
        timeoutMs: Long,
        consumer: PcmConsumer
    ): Boolean = withContext(Dispatchers.IO) {
        val output = runCatching { File.createTempFile("melodia-pcm", ".wav") }
            .onFailure { AppLogger.w(TAG, "创建临时文件失败", it) }
            .getOrNull() ?: return@withContext false
        try {
            if (!runEngine(url, start, options + ("ao-pcm-file" to output.absolutePath), timeoutMs)) {
                return@withContext false
            }
            WavLoudness.streamPcm(output, consumer)
        } catch (e: Exception) {
            AppLogger.w(TAG, "解码失败 start=$start", e)
            false
        } finally {
            output.delete()
        }
    }

    // 释放实例后 wav 才写完并解除占用
    private suspend fun runEngine(
        url: String,
        start: String,
        options: Map<String, String>,
        timeoutMs: Long
    ): Boolean {
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
            val ok = withTimeoutOrNull(timeoutMs) { finished.await() }
            if (ok == null) AppLogger.w(TAG, "解码超时 start=$start")
            ok == true
        } finally {
            engine.release()
        }
    }

    private fun seconds(ms: Long): String = String.format(Locale.ROOT, "%.3f", ms / 1000.0)
}

// 解析 16 位整数或 32 位浮点 PCM 的 wav，支持整块切 100ms 窗口或流式输出交错 float PCM
internal object WavLoudness {

    private const val FORMAT_PCM = 1
    private const val FORMAT_FLOAT = 3
    private const val FORMAT_EXTENSIBLE = 0xFFFE

    fun streamPcm(file: File, consumer: PcmConsumer): Boolean {
        val totalLen = file.length()
        if (totalLen < 12L) return false
        BufferedInputStream(file.inputStream()).use { input ->
            val header = readExactOrNull(input, 12) ?: return false
            if (ascii(header, 0) != "RIFF" || ascii(header, 8) != "WAVE") return false
            var offset = 12L
            var format = -1
            var channels = 0
            var sampleRate = 0
            var bits = 0
            val chunkHeader = ByteArray(8)
            val chunkBuf = ByteBuffer.wrap(chunkHeader).order(ByteOrder.LITTLE_ENDIAN)

            while (readFully(input, chunkHeader, 8) == 8) {
                offset += 8L
                val id = ascii(chunkHeader, 0)
                val size = chunkBuf.getInt(4).toLong() and 0xFFFFFFFFL
                when (id) {
                    "fmt " -> {
                        if (size < 16L || size > totalLen - offset) return false
                        val fmtBytes = readExactOrNull(input, minOf(size, 64L).toInt()) ?: return false
                        val fmtBuf = ByteBuffer.wrap(fmtBytes).order(ByteOrder.LITTLE_ENDIAN)
                        format = fmtBuf.getShort(0).toInt() and 0xFFFF
                        channels = fmtBuf.getShort(2).toInt()
                        sampleRate = fmtBuf.getInt(4)
                        bits = fmtBuf.getShort(14).toInt()
                        if (format == FORMAT_EXTENSIBLE && fmtBytes.size >= 26) {
                            format = fmtBuf.getShort(24).toInt() and 0xFFFF
                        }
                        val extra = size - fmtBytes.size
                        if (extra > 0L && !skipFully(input, extra)) return false
                        if ((size and 1L) != 0L && !skipFully(input, 1L)) return false
                        offset += size + (size and 1L)
                    }
                    "data" -> {
                        if (channels <= 0 || sampleRate <= 0) return false
                        val bytesPerSample = when {
                            format == FORMAT_PCM && bits == 16 -> 2
                            format == FORMAT_FLOAT && bits == 32 -> 4
                            else -> return false
                        }
                        val remaining = (totalLen - offset).coerceAtLeast(0L)
                        val targetBytes = if (size in 1..remaining) size else remaining
                        return streamData(input, targetBytes, channels, sampleRate, bytesPerSample, format == FORMAT_FLOAT, consumer)
                    }
                    else -> {
                        if (size > totalLen - offset) return false
                        val skip = size + (size and 1L)
                        if (!skipFully(input, skip)) return false
                        offset += skip
                    }
                }
            }
            return false
        }
    }

    private fun streamData(
        input: InputStream,
        targetBytes: Long,
        channels: Int,
        sampleRate: Int,
        bytesPerSample: Int,
        isFloat: Boolean,
        consumer: PcmConsumer
    ): Boolean {
        val frameBytes = bytesPerSample * channels
        val rawChunk = ByteArray(STREAM_CHUNK_FRAMES * frameBytes)
        val rawBuf = ByteBuffer.wrap(rawChunk).order(ByteOrder.LITTLE_ENDIAN)
        val floatChunk = FloatArray(STREAM_CHUNK_FRAMES * channels)

        var bytesLeft = targetBytes
        var carry = 0
        while (bytesLeft > 0L) {
            val toRead = minOf((rawChunk.size - carry).toLong(), bytesLeft).toInt()
            val read = input.read(rawChunk, carry, toRead)
            if (read <= 0) break
            bytesLeft -= read
            val valid = carry + read
            val frames = valid / frameBytes
            val consumedBytes = frames * frameBytes
            if (frames > 0) {
                val sampleCount = frames * channels
                rawBuf.position(0)
                if (isFloat) {
                    for (i in 0 until sampleCount) {
                        floatChunk[i] = rawBuf.getFloat(i * 4)
                    }
                } else {
                    for (i in 0 until sampleCount) {
                        floatChunk[i] = rawBuf.getShort(i * 2) / 32768f
                    }
                }
                consumer.onPcm(floatChunk, sampleCount, sampleRate, channels)
            }
            carry = valid - consumedBytes
            if (carry > 0) {
                rawChunk.copyInto(rawChunk, destinationOffset = 0, startIndex = consumedBytes, endIndex = valid)
            }
        }
        return true
    }

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

    private fun readExactOrNull(input: InputStream, count: Int): ByteArray? {
        val buf = ByteArray(count)
        return if (readFully(input, buf, count) == count) buf else null
    }

    private fun readFully(input: InputStream, buf: ByteArray, len: Int): Int {
        var total = 0
        while (total < len) {
            val n = input.read(buf, total, len - total)
            if (n < 0) break
            total += n
        }
        return total
    }

    private fun skipFully(input: InputStream, count: Long): Boolean {
        var left = count
        while (left > 0L) {
            val skipped = input.skip(left)
            if (skipped > 0L) {
                left -= skipped
            } else {
                if (input.read() < 0) return false
                left--
            }
        }
        return true
    }

    private fun ascii(bytes: ByteArray, offset: Int): String =
        if (offset + 4 > bytes.size) "" else String(bytes, offset, 4, Charsets.US_ASCII)
}

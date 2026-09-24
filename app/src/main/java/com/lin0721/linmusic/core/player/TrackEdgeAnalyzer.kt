package com.lin0721.linmusic.core.player

import android.content.Context
import android.media.AudioFormat
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import android.util.LruCache
import com.lin0721.linmusic.core.log.AppLogger
import java.nio.ByteOrder
import kotlin.coroutines.coroutineContext
import kotlin.math.log10
import kotlin.math.sqrt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

private const val TAG = "TrackEdgeAnalyzer"
private const val TAIL_SPAN_MS = 15_000L
private const val HEAD_SPAN_MS = 6_000L
// 无损音质在慢速网络下拉取 15 秒片段需十余秒
private const val ANALYSIS_TIMEOUT_MS = 20_000L
private const val CODEC_TIMEOUT_US = 10_000L

// 100ms 窗口的响度
data class LoudnessWindow(val startMs: Long, val dbfs: Double)

// 首尾有效位置判定，与解码解耦便于单测
object TrackEdges {
    const val WINDOW_MS = 100L
    const val AUDIBLE_DBFS = -45.0

    // 起音前留一个窗口，避免切掉起音
    private const val HEAD_PADDING_MS = 100L

    fun dbfs(sumSquares: Double, sampleCount: Long): Double {
        if (sampleCount <= 0L || sumSquares <= 0.0) return Double.NEGATIVE_INFINITY
        return 20 * log10(sqrt(sumSquares / sampleCount))
    }

    fun effectiveEndMs(windows: List<LoudnessWindow>): Long? =
        windows.lastOrNull { it.dbfs > AUDIBLE_DBFS }?.let { it.startMs + WINDOW_MS }

    fun effectiveStartMs(windows: List<LoudnessWindow>): Long? =
        windows.firstOrNull { it.dbfs > AUDIBLE_DBFS }?.let { (it.startMs - HEAD_PADDING_MS).coerceAtLeast(0L) }
}

// 解码歌曲首尾片段，找出真正有声的开头与结尾；失败或超时返回 null，由调用方回退到按时长计算
class TrackEdgeAnalyzer(private val context: Context) {

    private val endCache = LruCache<String, Long>(32)
    private val startCache = LruCache<String, Long>(32)

    suspend fun effectiveEndMs(key: String, uri: String, durationMs: Long): Long? {
        endCache.get(key)?.let { return it }
        if (durationMs <= 0L) return null
        val windows = decodeWindows(uri, (durationMs - TAIL_SPAN_MS).coerceAtLeast(0L), Long.MAX_VALUE) ?: return null
        return TrackEdges.effectiveEndMs(windows)?.also { endCache.put(key, it) }
    }

    suspend fun effectiveStartMs(key: String, uri: String): Long? {
        startCache.get(key)?.let { return it }
        val windows = decodeWindows(uri, 0L, HEAD_SPAN_MS) ?: return null
        return TrackEdges.effectiveStartMs(windows)?.also { startCache.put(key, it) }
    }

    private suspend fun decodeWindows(uri: String, fromMs: Long, toMs: Long): List<LoudnessWindow>? =
        withContext(Dispatchers.IO) {
            withTimeoutOrNull(ANALYSIS_TIMEOUT_MS) {
                runCatching { decode(uri, fromMs, toMs) }
                    .onFailure { AppLogger.w(TAG, "首尾分析失败 from=$fromMs", it) }
                    .getOrNull()
            }
        }

    private suspend fun decode(uri: String, fromMs: Long, toMs: Long): List<LoudnessWindow>? {
        val extractor = MediaExtractor()
        var codec: MediaCodec? = null
        try {
            if (uri.startsWith("http")) {
                extractor.setDataSource(uri, emptyMap())
            } else {
                extractor.setDataSource(context, Uri.parse(uri), null)
            }
            val trackIndex = (0 until extractor.trackCount).firstOrNull {
                extractor.getTrackFormat(it).getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true
            } ?: return null
            val format = extractor.getTrackFormat(trackIndex)
            val mime = format.getString(MediaFormat.KEY_MIME) ?: return null
            extractor.selectTrack(trackIndex)
            extractor.seekTo(fromMs * 1000, MediaExtractor.SEEK_TO_PREVIOUS_SYNC)

            val decoder = MediaCodec.createDecoderByType(mime)
            codec = decoder
            decoder.configure(format, null, null, 0)
            decoder.start()

            val accumulator = WindowAccumulator(fromMs, toMs)
            val info = MediaCodec.BufferInfo()
            var inputDone = false
            var outputDone = false
            var sampleRate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE)
            var channels = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
            var floatPcm = false

            while (!outputDone) {
                coroutineContext.ensureActive()
                if (!inputDone) {
                    val inIndex = decoder.dequeueInputBuffer(CODEC_TIMEOUT_US)
                    if (inIndex >= 0) {
                        val buffer = decoder.getInputBuffer(inIndex) ?: return null
                        val size = extractor.readSampleData(buffer, 0)
                        if (size < 0 || extractor.sampleTime / 1000 > toMs) {
                            decoder.queueInputBuffer(inIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            inputDone = true
                        } else {
                            decoder.queueInputBuffer(inIndex, 0, size, extractor.sampleTime, 0)
                            extractor.advance()
                        }
                    }
                }
                val outIndex = decoder.dequeueOutputBuffer(info, CODEC_TIMEOUT_US)
                when {
                    outIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                        val outFormat = decoder.outputFormat
                        sampleRate = outFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                        channels = outFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                        floatPcm = outFormat.containsKey(MediaFormat.KEY_PCM_ENCODING) &&
                            outFormat.getInteger(MediaFormat.KEY_PCM_ENCODING) == AudioFormat.ENCODING_PCM_FLOAT
                    }
                    outIndex >= 0 -> {
                        val out = decoder.getOutputBuffer(outIndex)
                        if (out != null && info.size > 0 && sampleRate > 0 && channels > 0) {
                            out.position(info.offset)
                            out.limit(info.offset + info.size)
                            out.order(ByteOrder.nativeOrder())
                            val frameCount = if (floatPcm) info.size / 4 / channels else info.size / 2 / channels
                            for (frame in 0 until frameCount) {
                                var frameSquares = 0.0
                                for (ch in 0 until channels) {
                                    val sample = if (floatPcm) {
                                        out.float.toDouble()
                                    } else {
                                        out.short / 32768.0
                                    }
                                    frameSquares += sample * sample
                                }
                                val timeMs = info.presentationTimeUs / 1000 + frame * 1000L / sampleRate
                                accumulator.add(timeMs, frameSquares / channels)
                            }
                        }
                        decoder.releaseOutputBuffer(outIndex, false)
                        if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) outputDone = true
                    }
                }
            }
            return accumulator.finish()
        } finally {
            runCatching { codec?.stop() }
            runCatching { codec?.release() }
            runCatching { extractor.release() }
        }
    }

    private class WindowAccumulator(private val fromMs: Long, private val toMs: Long) {
        private val windows = mutableListOf<LoudnessWindow>()
        private var windowStartMs = -1L
        private var sumSquares = 0.0
        private var count = 0L

        fun add(timeMs: Long, meanSquare: Double) {
            // 同步帧回退可能早于目标起点，丢弃范围外的样本
            if (timeMs < fromMs || timeMs > toMs) return
            val start = timeMs / TrackEdges.WINDOW_MS * TrackEdges.WINDOW_MS
            if (start != windowStartMs) {
                flush()
                windowStartMs = start
            }
            sumSquares += meanSquare
            count++
        }

        fun finish(): List<LoudnessWindow> {
            flush()
            return windows
        }

        private fun flush() {
            if (windowStartMs >= 0 && count > 0) {
                windows += LoudnessWindow(windowStartMs, TrackEdges.dbfs(sumSquares, count))
            }
            sumSquares = 0.0
            count = 0
        }
    }
}

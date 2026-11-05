package com.lin0721.linmusic.core.player

import android.content.Context
import android.media.AudioFormat
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import com.lin0721.linmusic.core.log.AppLogger
import java.nio.ByteOrder
import kotlin.coroutines.coroutineContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

private const val TAG = "PcmWindowDecoder"
// 无损音质在慢速网络下拉取 15 秒片段需十余秒
private const val DECODE_TIMEOUT_MS = 20_000L
private const val CODEC_TIMEOUT_US = 10_000L

// 解码音频片段为 100ms 响度窗口，首尾分析与响度自测共用；失败或超时返回 null
class PcmWindowDecoder(private val context: Context) : SegmentDecoder {

    suspend fun decode(uri: String, fromMs: Long, toMs: Long): List<LoudnessWindow>? =
        decodeRange(uri, "from=$fromMs") { fromMs to toMs }

    // 从曲长的该比例处起取片段，曲长读不到时放弃
    override suspend fun decode(uri: String, startFraction: Double, spanMs: Long): List<LoudnessWindow>? =
        decodeRange(uri, "fraction=$startFraction") { durationMs ->
            durationMs?.takeIf { it > 0L }?.let { total ->
                val fromMs = (total * startFraction).toLong().coerceIn(0L, (total - spanMs).coerceAtLeast(0L))
                fromMs to fromMs + spanMs
            }
        }

    private suspend fun decodeRange(
        uri: String,
        label: String,
        range: (durationMs: Long?) -> Pair<Long, Long>?
    ): List<LoudnessWindow>? =
        withContext(Dispatchers.IO) {
            withTimeoutOrNull(DECODE_TIMEOUT_MS) {
                runCatching { decodeBlocking(uri, range) }
                    .onFailure { AppLogger.w(TAG, "解码失败 $label", it) }
                    .getOrNull()
            }
        }

    private suspend fun decodeBlocking(uri: String, range: (durationMs: Long?) -> Pair<Long, Long>?): List<LoudnessWindow>? {
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
            val durationMs = if (format.containsKey(MediaFormat.KEY_DURATION)) format.getLong(MediaFormat.KEY_DURATION) / 1000 else null
            val (fromMs, toMs) = range(durationMs) ?: return null
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

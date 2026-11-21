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
// 无损音质在慢速网络下拉取片段需十余秒
private const val DECODE_TIMEOUT_MS = 20_000L
private const val FULL_DECODE_TIMEOUT_MS = 60_000L
private const val CODEC_TIMEOUT_US = 10_000L
private const val PCM_CHUNK_FRAMES = 4096

// 解码为 100ms 响度窗口或交错 float PCM，首尾分析与响度测量共用
class PcmWindowDecoder(private val context: Context) : SegmentDecoder {

    suspend fun decode(uri: String, fromMs: Long, toMs: Long): List<LoudnessWindow>? {
        val accumulator = WindowAccumulator(fromMs, toMs)
        val ok = decodeRange(uri, "from=$fromMs", DECODE_TIMEOUT_MS, { fromMs to toMs }) { timeMs, samples, channels ->
            if (timeMs < fromMs || timeMs > toMs) return@decodeRange
            var sumSq = 0.0
            for (ch in 0 until channels) {
                val s = samples[ch].toDouble()
                sumSq += s * s
            }
            accumulator.add(timeMs, sumSq / channels)
        }
        return if (ok) accumulator.finish() else null
    }

    // spanMs 为空且 startFraction <= 0 时解码整首
    override suspend fun decode(uri: String, startFraction: Double, spanMs: Long?, consumer: PcmConsumer): Boolean {
        val timeoutMs = if (spanMs == null) FULL_DECODE_TIMEOUT_MS else DECODE_TIMEOUT_MS
        var chunk = FloatArray(0)
        var chunkSamples = 0
        var chunkSampleRate = 0
        var chunkChannels = 0
        var rangeFromMs = 0L
        var rangeToMs = Long.MAX_VALUE

        val ok = decodeRange(
            uri = uri,
            label = "fraction=$startFraction span=$spanMs",
            timeoutMs = timeoutMs,
            range = { durationMs ->
                when {
                    startFraction <= 0.0 && spanMs == null -> (0L to Long.MAX_VALUE)
                    startFraction <= 0.0 && spanMs != null -> (0L to (durationMs?.let { minOf(spanMs, it) } ?: spanMs))
                    spanMs == null -> durationMs?.takeIf { it > 0L }?.let { total ->
                        val fromMs = (total * startFraction).toLong().coerceIn(0L, total)
                        fromMs to Long.MAX_VALUE
                    }
                    else -> durationMs?.takeIf { it > 0L }?.let { total ->
                        val fromMs = (total * startFraction).toLong().coerceIn(0L, (total - spanMs).coerceAtLeast(0L))
                        fromMs to fromMs + spanMs
                    }
                }?.also {
                    rangeFromMs = it.first
                    rangeToMs = it.second
                }
            }
        ) { timeMs, frameSamples, channels, sampleRate ->
            if ((rangeFromMs > 0L && timeMs < rangeFromMs) || (rangeToMs < Long.MAX_VALUE && timeMs > rangeToMs)) {
                return@decodeRange
            }
            if (chunkSampleRate != sampleRate || chunkChannels != channels) {
                if (chunkSamples > 0 && chunkSampleRate > 0 && chunkChannels > 0) {
                    consumer.onPcm(chunk, chunkSamples, chunkSampleRate, chunkChannels)
                    chunkSamples = 0
                }
                chunkSampleRate = sampleRate
                chunkChannels = channels
                val needed = PCM_CHUNK_FRAMES * channels
                if (chunk.size < needed) chunk = FloatArray(needed)
            }
            if (chunkSamples + channels > chunk.size) {
                consumer.onPcm(chunk, chunkSamples, chunkSampleRate, chunkChannels)
                chunkSamples = 0
            }
            for (ch in 0 until channels) {
                chunk[chunkSamples++] = frameSamples[ch]
            }
        }
        if (ok && chunkSamples > 0 && chunkSampleRate > 0 && chunkChannels > 0) {
            consumer.onPcm(chunk, chunkSamples, chunkSampleRate, chunkChannels)
        }
        return ok
    }

    private suspend fun decodeRange(
        uri: String,
        label: String,
        timeoutMs: Long,
        range: (durationMs: Long?) -> Pair<Long, Long>?,
        onFrame: (timeMs: Long, samples: FloatArray, channels: Int, sampleRate: Int) -> Unit
    ): Boolean = withContext(Dispatchers.IO) {
        withTimeoutOrNull(timeoutMs) {
            runCatching { decodeBlocking(uri, range, onFrame) }
                .onFailure { AppLogger.w(TAG, "解码失败 $label", it) }
                .getOrDefault(false)
        } ?: false
    }

    private suspend fun decodeRange(
        uri: String,
        label: String,
        timeoutMs: Long,
        range: (durationMs: Long?) -> Pair<Long, Long>?,
        onFrame: (timeMs: Long, samples: FloatArray, channels: Int) -> Unit
    ): Boolean = decodeRange(uri, label, timeoutMs, range) { timeMs, samples, channels, _ ->
        onFrame(timeMs, samples, channels)
    }

    private suspend fun decodeBlocking(
        uri: String,
        range: (durationMs: Long?) -> Pair<Long, Long>?,
        onFrame: (timeMs: Long, samples: FloatArray, channels: Int, sampleRate: Int) -> Unit
    ): Boolean {
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
            } ?: return false
            val format = extractor.getTrackFormat(trackIndex)
            val mime = format.getString(MediaFormat.KEY_MIME) ?: return false
            val durationMs = if (format.containsKey(MediaFormat.KEY_DURATION)) format.getLong(MediaFormat.KEY_DURATION) / 1000 else null
            val (fromMs, toMs) = range(durationMs) ?: return false
            extractor.selectTrack(trackIndex)
            if (fromMs > 0L) {
                extractor.seekTo(fromMs * 1000, MediaExtractor.SEEK_TO_PREVIOUS_SYNC)
            }

            val decoder = MediaCodec.createDecoderByType(mime)
            codec = decoder
            decoder.configure(format, null, null, 0)
            decoder.start()

            val info = MediaCodec.BufferInfo()
            var inputDone = false
            var outputDone = false
            var sampleRate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE)
            var channels = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
            var floatPcm = false
            var frameSamples = FloatArray(channels.coerceAtLeast(1))

            while (!outputDone) {
                coroutineContext.ensureActive()
                if (!inputDone) {
                    val inIndex = decoder.dequeueInputBuffer(CODEC_TIMEOUT_US)
                    if (inIndex >= 0) {
                        val buffer = decoder.getInputBuffer(inIndex) ?: return false
                        val size = extractor.readSampleData(buffer, 0)
                        if (size < 0 || (toMs < Long.MAX_VALUE && extractor.sampleTime / 1000 > toMs)) {
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
                        if (frameSamples.size < channels) {
                            frameSamples = FloatArray(channels.coerceAtLeast(1))
                        }
                    }
                    outIndex >= 0 -> {
                        val out = decoder.getOutputBuffer(outIndex)
                        if (out != null && info.size > 0 && sampleRate > 0 && channels > 0) {
                            if (frameSamples.size < channels) frameSamples = FloatArray(channels)
                            out.position(info.offset)
                            out.limit(info.offset + info.size)
                            out.order(ByteOrder.nativeOrder())
                            val frameCount = if (floatPcm) info.size / 4 / channels else info.size / 2 / channels
                            for (frame in 0 until frameCount) {
                                for (ch in 0 until channels) {
                                    frameSamples[ch] = if (floatPcm) out.float else out.short / 32768f
                                }
                                val timeMs = info.presentationTimeUs / 1000 + frame * 1000L / sampleRate
                                onFrame(timeMs, frameSamples, channels, sampleRate)
                            }
                        }
                        decoder.releaseOutputBuffer(outIndex, false)
                        if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) outputDone = true
                    }
                }
            }
            return true
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

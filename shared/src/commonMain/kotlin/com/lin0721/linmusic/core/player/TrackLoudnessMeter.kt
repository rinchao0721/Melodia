package com.lin0721.linmusic.core.player

import com.lin0721.linmusic.core.log.AppLogger
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.coroutineContext

private const val TAG = "TrackLoudnessMeter"

// 交错 float PCM，仅 [0, sampleCount) 有效
fun interface PcmConsumer {
    fun onPcm(samples: FloatArray, sampleCount: Int, sampleRate: Int, channels: Int)
}

// 从曲长 startFraction 处解码 spanMs 毫秒，spanMs 为空表示解码到结尾
fun interface SegmentDecoder {
    suspend fun decode(uri: String, startFraction: Double, spanMs: Long?, consumer: PcmConsumer): Boolean
}

data class LoudnessMeasureRequest(
    val songId: Long,
    val uri: String,
    val fullTrack: Boolean = false
)

// 曲目响度测量与缓存：本地整首、在线三段抽样
class TrackLoudnessMeter(
    private val store: LoudnessStore,
    private val decoder: SegmentDecoder,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
) {
    private val inFlightLock = Mutex()
    private val inFlight = HashMap<Long, CompletableDeferred<LoudnessEntry?>>()

    private val queueLock = Any()
    private val pendingQueue = ArrayDeque<LoudnessMeasureRequest>()
    private var queueWorker: Job? = null

    suspend fun cachedGainDb(songId: Long): Double? =
        store.get(songId)?.let { LoudnessPolicy.gainDb(it.lufs, it.peak) }

    suspend fun cachedEntry(songId: Long): LoudnessEntry? = store.get(songId)

    suspend fun remember(songId: Long, loudness: TrackLoudness, source: LoudnessSource) =
        store.put(songId, loudness.lufs, loudness.peak, source)

    // 同一首正在测量时共享结果
    suspend fun measure(songId: Long, uri: String, fullTrack: Boolean = false): Double? {
        store.get(songId)?.let { return LoudnessPolicy.gainDb(it.lufs, it.peak) }
        val entry = startOrJoin(songId, uri, fullTrack).await() ?: return null
        return LoudnessPolicy.gainDb(entry.lufs, entry.peak)
    }

    // 超时返回 null，测量继续进行，完成后写缓存并回调
    suspend fun measureBeforeStart(
        songId: Long,
        uri: String,
        fullTrack: Boolean = false,
        timeoutMs: Long = LoudnessPolicy.PRE_START_TIMEOUT_MS,
        onDelayedResult: suspend (Double) -> Unit = {}
    ): Double? {
        store.get(songId)?.let { return LoudnessPolicy.gainDb(it.lufs, it.peak) }
        val deferred = startOrJoin(songId, uri, fullTrack)
        var completedInTime = false
        val entry = withTimeoutOrNull(timeoutMs) {
            deferred.await().also { completedInTime = true }
        }
        if (completedInTime) {
            return entry?.let { LoudnessPolicy.gainDb(it.lufs, it.peak) }
        }
        scope.launch {
            val delayed = deferred.await() ?: return@launch
            onDelayedResult(LoudnessPolicy.gainDb(delayed.lufs, delayed.peak))
        }
        return null
    }

    // 逐首串行测量，已缓存或已在队内的跳过
    fun enqueue(requests: List<LoudnessMeasureRequest>) {
        if (requests.isEmpty()) return
        synchronized(queueLock) {
            for (req in requests) {
                if (pendingQueue.none { it.songId == req.songId }) {
                    pendingQueue.addLast(req)
                }
            }
            if (queueWorker?.isActive != true) {
                queueWorker = scope.launch { processQueue() }
            }
        }
    }

    fun cancelAll() {
        val workerToCancel: Job?
        synchronized(queueLock) {
            pendingQueue.clear()
            workerToCancel = queueWorker
            queueWorker = null
        }
        workerToCancel?.cancel()
    }

    private suspend fun processQueue() {
        val self = coroutineContext[Job]
        while (coroutineContext.isActive) {
            val next = synchronized(queueLock) {
                if (queueWorker !== self || pendingQueue.isEmpty()) {
                    if (queueWorker === self) queueWorker = null
                    return
                }
                pendingQueue.removeFirst()
            }
            if (store.get(next.songId) != null) continue
            measure(next.songId, next.uri, next.fullTrack)
        }
    }

    private suspend fun startOrJoin(songId: Long, uri: String, fullTrack: Boolean): CompletableDeferred<LoudnessEntry?> {
        return inFlightLock.withLock {
            inFlight[songId]?.let { return@withLock it }
            val deferred = CompletableDeferred<LoudnessEntry?>()
            inFlight[songId] = deferred
            scope.launch {
                var result: LoudnessEntry? = null
                try {
                    result = runMeasure(songId, uri, fullTrack)
                    if (result != null) {
                        store.put(songId, result)
                        AppLogger.i(TAG, "响度自测完成 songId=$songId lufs=${result.lufs} peak=${result.peak} source=${result.source}")
                    }
                } finally {
                    withContext(NonCancellable) {
                        inFlightLock.withLock {
                            if (inFlight[songId] === deferred) {
                                inFlight.remove(songId)
                            }
                        }
                        deferred.complete(result)
                    }
                }
            }
            deferred
        }
    }

    private suspend fun runMeasure(songId: Long, uri: String, fullTrack: Boolean): LoudnessEntry? {
        // 防与 remember 竞争
        store.get(songId)?.let { return it }
        return if (fullTrack) {
            val segments = decodeToSegments(uri, 0.0, null) ?: return null
            val meter = R128Meter()
            segments.forEach { meter.addSegment(it) }
            val analysis = meter.analyze() ?: return null
            LoudnessEntry(analysis.integratedLufs, analysis.samplePeak, LoudnessSource.MEASURED_FULL)
        } else {
            val segmentLists = coroutineScope {
                LoudnessPolicy.SAMPLE_FRACTIONS
                    .map { fraction -> async { decodeToSegments(uri, fraction, LoudnessPolicy.SAMPLE_SPAN_MS) } }
                    .awaitAll()
            }
            if (segmentLists.any { it == null }) return null
            val meter = R128Meter()
            for (list in segmentLists) {
                list?.forEach { meter.addSegment(it) }
            }
            val analysis = meter.analyze() ?: return null
            LoudnessEntry(analysis.integratedLufs, analysis.samplePeak, LoudnessSource.MEASURED_SAMPLED)
        }
    }

    private suspend fun decodeToSegments(uri: String, startFraction: Double, spanMs: Long?): List<R128Meter.Segment>? {
        val segments = ArrayList<R128Meter.Segment>(1)
        var current: R128Meter.Segment? = null
        val ok = decoder.decode(uri, startFraction, spanMs) { samples, sampleCount, sampleRate, channels ->
            if (sampleCount <= 0 || sampleRate <= 0 || channels <= 0) return@decode
            val target = current?.takeIf { it.sampleRate == sampleRate && it.channels == channels }
                ?: R128Meter.Segment(sampleRate, channels).also {
                    current = it
                    segments += it
                }
            target.process(samples, 0, sampleCount)
        }
        if (!ok || segments.isEmpty()) return null
        return segments
    }
}

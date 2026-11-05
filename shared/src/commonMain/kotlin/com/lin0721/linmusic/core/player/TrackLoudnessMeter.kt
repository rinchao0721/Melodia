package com.lin0721.linmusic.core.player

import com.lin0721.linmusic.core.log.AppLogger
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

private const val TAG = "TrackLoudnessMeter"

// 从曲长的 startFraction 处起截取 spanMs 解码为响度窗口，片段时间戳不参与计算；失败返回 null
fun interface SegmentDecoder {
    suspend fun decode(uri: String, startFraction: Double, spanMs: Long): List<LoudnessWindow>?
}

// 曲目增益：先查缓存，没有再抽样自测并写回缓存
class TrackLoudnessMeter(
    private val store: LoudnessStore,
    private val decoder: SegmentDecoder
) {
    private val inFlightLock = Mutex()
    private val inFlight = HashSet<Long>()

    suspend fun cachedGainDb(songId: Long): Double? = store.get(songId)

    suspend fun remember(songId: Long, gainDb: Double) = store.put(songId, gainDb)

    // 同一首正在测量时直接返回 null，由先发起的一方写回缓存
    suspend fun measure(songId: Long, uri: String): Double? {
        store.get(songId)?.let { return it }
        if (!inFlightLock.withLock { inFlight.add(songId) }) return null
        try {
            val segments = coroutineScope {
                LoudnessPolicy.SAMPLE_FRACTIONS
                    .map { fraction -> async { decoder.decode(uri, fraction, LoudnessPolicy.SAMPLE_SPAN_MS) } }
                    .awaitAll()
            }
            if (segments.any { it == null }) return null
            val gainDb = LoudnessPolicy.measuredGainDb(segments.filterNotNull()) ?: return null
            store.put(songId, gainDb)
            AppLogger.i(TAG, "响度自测完成 songId=$songId gainDb=$gainDb")
            return gainDb
        } finally {
            inFlightLock.withLock { inFlight.remove(songId) }
        }
    }
}

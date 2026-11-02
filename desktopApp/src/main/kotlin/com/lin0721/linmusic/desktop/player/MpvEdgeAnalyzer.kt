package com.lin0721.linmusic.desktop.player

import com.lin0721.linmusic.core.player.TrackEdges

private const val TAIL_SPAN_MS = 15_000L
private const val HEAD_SPAN_MS = 6_000L
private const val CACHE_SIZE = 32

// 解码歌曲首尾片段，找出真正有声的开头与结尾；失败或超时返回 null，由调用方回退到按时长计算
internal class MpvEdgeAnalyzer(private val decoder: MpvPcmDecoder) {

    private val endCache = positionCache()
    private val startCache = positionCache()

    suspend fun effectiveEndMs(key: Long, url: String, durationMs: Long): Long? {
        synchronized(endCache) { endCache[key] }?.let { return it }
        if (durationMs <= 0L) return null
        val windows = decoder.decode(url, (durationMs - TAIL_SPAN_MS).coerceAtLeast(0L), toMs = null) ?: return null
        return TrackEdges.effectiveEndMs(windows)?.also { synchronized(endCache) { endCache[key] = it } }
    }

    suspend fun effectiveStartMs(key: Long, url: String): Long? {
        synchronized(startCache) { startCache[key] }?.let { return it }
        val windows = decoder.decode(url, 0L, toMs = HEAD_SPAN_MS) ?: return null
        return TrackEdges.effectiveStartMs(windows)?.also { synchronized(startCache) { startCache[key] = it } }
    }

    private fun positionCache(): LinkedHashMap<Long, Long> =
        object : LinkedHashMap<Long, Long>(CACHE_SIZE, 0.75f, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Long, Long>?): Boolean = size > CACHE_SIZE
        }
}

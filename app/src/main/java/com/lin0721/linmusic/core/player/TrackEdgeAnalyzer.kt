package com.lin0721.linmusic.core.player

import android.util.LruCache

private const val TAIL_SPAN_MS = 15_000L
private const val HEAD_SPAN_MS = 6_000L

// 解码歌曲首尾片段，找出真正有声的开头与结尾；失败或超时返回 null，由调用方回退到按时长计算
class TrackEdgeAnalyzer(private val decoder: PcmWindowDecoder) {

    private val endCache = LruCache<String, Long>(32)
    private val startCache = LruCache<String, Long>(32)

    suspend fun effectiveEndMs(key: String, uri: String, durationMs: Long): Long? {
        endCache.get(key)?.let { return it }
        if (durationMs <= 0L) return null
        val windows = decoder.decode(uri, (durationMs - TAIL_SPAN_MS).coerceAtLeast(0L), Long.MAX_VALUE) ?: return null
        return TrackEdges.effectiveEndMs(windows)?.also { endCache.put(key, it) }
    }

    suspend fun effectiveStartMs(key: String, uri: String): Long? {
        startCache.get(key)?.let { return it }
        val windows = decoder.decode(uri, 0L, HEAD_SPAN_MS) ?: return null
        return TrackEdges.effectiveStartMs(windows)?.also { startCache.put(key, it) }
    }
}

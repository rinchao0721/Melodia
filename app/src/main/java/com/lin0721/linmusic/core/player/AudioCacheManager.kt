package com.lin0721.linmusic.core.player

import android.content.Context
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import com.lin0721.linmusic.core.log.AppLogger
import java.io.File

private const val TAG = "AudioCacheManager"

@UnstableApi
object AudioCacheManager {
    private var cache: SimpleCache? = null

    @Synchronized
    fun getCache(context: Context, maxSize: Long): SimpleCache {
        if (cache == null) {
            val cacheDir = File(context.cacheDir, "audio_cache")
            val databaseProvider = StandaloneDatabaseProvider(context)
            val evictor = LeastRecentlyUsedCacheEvictor(maxSize)
            cache = SimpleCache(cacheDir, evictor, databaseProvider)
        }
        return cache!!
    }

    @Synchronized
    fun recreateCache(context: Context, newMaxSize: Long) {
        try {
            cache?.release()
            cache = null
            val newCache = getCache(context, newMaxSize)
            trimToSize(newCache, newMaxSize)
        } catch (e: Exception) {
            AppLogger.e(TAG, "音质切换缓存重建失败", e)
        }
    }

    // 上限下调后主动淘汰超出部分，不必等下次播放时才被 LRU 逐步驱逐
    private fun trimToSize(cache: SimpleCache, maxSize: Long) {
        if (cache.cacheSpace <= maxSize) return
        val spans = cache.keys.flatMap { key -> cache.getCachedSpans(key) }
            .sortedBy { it.lastTouchTimestamp }
        for (span in spans) {
            if (cache.cacheSpace <= maxSize) break
            runCatching { cache.removeSpan(span) }
        }
    }

    @Synchronized
    fun clearCache(context: Context) {
        try {
            cache?.release()
            cache = null
            val cacheDir = File(context.cacheDir, "audio_cache")
            if (cacheDir.exists()) {
                cacheDir.deleteRecursively()
            }
        } catch (e: Exception) {
            AppLogger.e(TAG, "清除音频缓存失败", e)
        }
    }

    // 供储存空间页展示占用大小，直接读磁盘目录
    fun getCacheDirSize(context: Context): Long {
        val cacheDir = File(context.cacheDir, "audio_cache")
        return cacheDir.walkTopDown().filter { it.isFile }.sumOf { it.length() }
    }
}

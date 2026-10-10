package com.lin0721.linmusic.desktop.platform

import com.lin0721.linmusic.desktop.platform.native.AppPaths

import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.disk.DiskCache
import coil3.memory.MemoryCache
import com.lin0721.linmusic.core.log.AppLogger
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import okio.Path.Companion.toOkioPath

private const val TAG = "DesktopImageLoader"

private const val MEMORY_CACHE_PERCENT = 0.15
private const val DECODER_PARALLELISM = 4
private const val FETCHER_PARALLELISM = 8

private const val LEGACY_CACHE_NAME = "coil3_disk_cache"

object DesktopImageLoader {

    const val DISK_CACHE_MAX_BYTES = 500L * 1024 * 1024

    // 须在首次加载图片之前调用
    fun install() {
        SingletonImageLoader.setSafe { context -> create(context) }
        deleteLegacyCache()
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    fun create(context: PlatformContext, cacheDir: File = AppPaths.current.imageCacheDir, maxBytes: Long = DISK_CACHE_MAX_BYTES): ImageLoader =
        ImageLoader.Builder(context)
            .memoryCache { MemoryCache.Builder().maxSizePercent(context, MEMORY_CACHE_PERCENT).build() }
            .diskCache { DiskCache.Builder().directory(cacheDir.toOkioPath()).maxSizeBytes(maxBytes).build() }
            .decoderCoroutineContext(Dispatchers.IO.limitedParallelism(DECODER_PARALLELISM))
            .fetcherCoroutineContext(Dispatchers.IO.limitedParallelism(FETCHER_PARALLELISM))
            .build()

    fun diskCacheSize(): Long = SingletonImageLoader.get(PlatformContext.INSTANCE).diskCache?.size ?: 0L

    fun clear() {
        val loader = SingletonImageLoader.get(PlatformContext.INSTANCE)
        loader.memoryCache?.clear()
        loader.diskCache?.clear()
    }

    // Coil 旧默认缓存在临时目录，后台删除
    fun deleteLegacyCache(dir: File = File(System.getProperty("java.io.tmpdir"), LEGACY_CACHE_NAME)) {
        if (!dir.isDirectory) return
        Thread({
            if (!dir.deleteRecursively()) AppLogger.w(TAG, "旧图片缓存未能完全删除：${dir.path}")
        }, "legacy-image-cache-cleanup").apply { isDaemon = true }.start()
    }
}

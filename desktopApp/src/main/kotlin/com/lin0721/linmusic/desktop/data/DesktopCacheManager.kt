package com.lin0721.linmusic.desktop.data

import com.lin0721.linmusic.core.log.AppLogger
import com.lin0721.linmusic.core.player.data.LyricsCache
import com.lin0721.linmusic.desktop.player.cache.AudioCache
import java.io.File

private const val TAG = "DesktopCacheManager"
private const val TEMP_SUFFIX = ".tmp"

// 缓存总量上限的档位，音频、图片、歌词、元数据合计
object CacheLimits {
    const val UNLIMITED = Long.MAX_VALUE
    private const val MIN_BYTES = 1024L * 1024 * 1024

    val options: List<Long> = listOf(1L, 2L, 5L, 10L).map { it * MIN_BYTES } + UNLIMITED

    fun normalize(bytes: Long): Long = if (bytes == UNLIMITED) bytes else bytes.coerceAtLeast(MIN_BYTES)
}

class CacheUsage(val audio: Long, val image: Long, val lyrics: Long, val metadata: Long) {
    val total: Long get() = audio + image + lyrics + metadata
}

class DesktopCacheManager(
    private val audioCache: AudioCache,
    private val lyricsCache: LyricsCache,
    private val lyricsDir: File,
    private val metadataDir: File,
    private val imageCacheSize: () -> Long = DesktopImageLoader::diskCacheSize,
    private val clearImageCache: () -> Unit = DesktopImageLoader::clear
) {

    private class Entry(val file: File, val length: Long, val modified: Long)

    fun usage(): CacheUsage = CacheUsage(
        audio = audioCache.totalSize(),
        image = imageCacheSize(),
        lyrics = lyricsFiles().sumOf { it.length() },
        metadata = metadataFiles().sumOf { it.length() }
    )

    // 音频、歌词、元数据按最近使用从旧到新统一淘汰；图片由 Coil 自管，只计入总量
    fun enforce(maxBytes: Long) {
        if (maxBytes == CacheLimits.UNLIMITED) return
        val entries = (audioCache.completeFiles() + lyricsFiles() + metadataFiles())
            .map { Entry(it, it.length(), it.lastModified()) }
        var total = entries.sumOf { it.length } + imageCacheSize()
        // 删不掉的（正在播放的音频）跳过
        for (entry in entries.sortedBy { it.modified }) {
            if (total <= maxBytes) break
            if (entry.file.delete()) total -= entry.length
        }
    }

    // 元数据是离线回显的数据，不随一键清除
    fun clear() {
        attempt("音频") { audioCache.clear() }
        attempt("歌词") { lyricsCache.clear() }
        attempt("图片") { clearImageCache() }
    }

    private fun attempt(name: String, action: () -> Unit) {
        try {
            action()
        } catch (e: Exception) {
            AppLogger.w(TAG, "清除${name}缓存失败", e)
        }
    }

    private fun lyricsFiles(): List<File> = filesUnder(lyricsDir)

    private fun metadataFiles(): List<File> = filesUnder(metadataDir)

    private fun filesUnder(dir: File): List<File> =
        dir.walkTopDown().filter { it.isFile && !it.name.endsWith(TEMP_SUFFIX) }.toList()
}

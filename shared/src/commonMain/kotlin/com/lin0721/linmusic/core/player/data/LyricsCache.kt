package com.lin0721.linmusic.core.player.data

import com.lin0721.linmusic.core.platform.TrackId
import java.io.File

/**
 * 歌词原文磁盘缓存：支持按 [TrackId] 与命名空间（`amll/`、`krc/`）缓存原文及负缓存，
 * 同时 100% 保留网易云 `Long` 接口与旧版 `lyrics/amll/$songId.ttml` 文件路径兼容。
 */
class LyricsCache(
    cacheRoot: File,
    private val now: () -> Long = System::currentTimeMillis,
    private val rawTtlMs: Long = RAW_TTL_MS,
    private val negativeTtlMs: Long = NEGATIVE_TTL_MS,
    private val maxDiskBytes: Long = MAX_DISK_BYTES
) {
    enum class Namespace(val dirName: String, val extension: String) {
        AMLL("amll", "ttml"),
        KRC("krc", "krc")
    }

    private val lyricsRoot = File(cacheRoot, "lyrics")
    private val amllDirectory = File(lyricsRoot, Namespace.AMLL.dirName)
    private val krcDirectory = File(lyricsRoot, Namespace.KRC.dirName)
    private var writesSinceTrim = 0

    @Synchronized
    fun getRaw(songId: Long): String? =
        if (songId <= 0L) null else getRawInternal(rawFile(songId), negativeFile(songId))

    @Synchronized
    fun getRaw(trackId: TrackId, namespace: Namespace = Namespace.AMLL): String? {
        val primary = getRawInternal(rawFile(trackId, namespace), negativeFile(trackId, namespace))
        if (primary != null) return primary
        // 网易云曲目回退读取旧版 $songId.ttml
        if (namespace == Namespace.AMLL) {
            val legacyId = trackId.neteaseIdOrNull()?.takeIf { it > 0L } ?: return null
            return getRawInternal(rawFile(legacyId), negativeFile(legacyId))
        }
        return null
    }

    @Synchronized
    fun putRaw(songId: Long, xml: String) {
        if (songId <= 0L || xml.isBlank()) return
        putRawInternal(
            target = rawFile(songId),
            negativeTarget = negativeFile(songId),
            content = xml
        )
    }

    @Synchronized
    fun putRaw(trackId: TrackId, content: String, namespace: Namespace = Namespace.AMLL) {
        if (content.isBlank()) return
        val target = if (namespace == Namespace.AMLL) {
            val legacyId = trackId.neteaseIdOrNull()?.takeIf { it > 0L }
            if (legacyId != null) rawFile(legacyId) else rawFile(trackId, namespace)
        } else {
            rawFile(trackId, namespace)
        }
        val negativeTarget = if (namespace == Namespace.AMLL) {
            val legacyId = trackId.neteaseIdOrNull()?.takeIf { it > 0L }
            if (legacyId != null) negativeFile(legacyId) else negativeFile(trackId, namespace)
        } else {
            negativeFile(trackId, namespace)
        }
        putRawInternal(target = target, negativeTarget = negativeTarget, content = content)
    }

    @Synchronized
    fun removeRaw(songId: Long) {
        if (songId <= 0L) return
        rawFile(songId).delete()
    }

    @Synchronized
    fun removeRaw(trackId: TrackId, namespace: Namespace = Namespace.AMLL) {
        rawFile(trackId, namespace).delete()
        if (namespace == Namespace.AMLL) {
            trackId.neteaseIdOrNull()?.takeIf { it > 0L }?.let { rawFile(it).delete() }
        }
    }

    @Synchronized
    fun isNegative(songId: Long): Boolean =
        if (songId <= 0L) false else isNegativeInternal(negativeFile(songId))

    @Synchronized
    fun isNegative(trackId: TrackId, namespace: Namespace = Namespace.AMLL): Boolean {
        if (isNegativeInternal(negativeFile(trackId, namespace))) return true
        if (namespace == Namespace.AMLL) {
            val legacyId = trackId.neteaseIdOrNull()?.takeIf { it > 0L } ?: return false
            return isNegativeInternal(negativeFile(legacyId))
        }
        return false
    }

    @Synchronized
    fun putNegative(songId: Long) {
        if (songId <= 0L) return
        putNegativeInternal(negativeFile(songId))
    }

    @Synchronized
    fun putNegative(trackId: TrackId, namespace: Namespace = Namespace.AMLL) {
        val target = if (namespace == Namespace.AMLL) {
            val legacyId = trackId.neteaseIdOrNull()?.takeIf { it > 0L }
            if (legacyId != null) negativeFile(legacyId) else negativeFile(trackId, namespace)
        } else {
            negativeFile(trackId, namespace)
        }
        putNegativeInternal(target)
    }

    @Synchronized
    fun clear() {
        allDirectories().forEach { dir ->
            dir.listFiles()?.forEach { it.delete() }
        }
    }

    @Synchronized
    fun diskSizeBytes(): Long =
        allDirectories().sumOf { dir -> dir.listFiles()?.sumOf { it.length() } ?: 0L }

    private fun getRawInternal(file: File, @Suppress("UNUSED_PARAMETER") negative: File): String? {
        if (!file.isFile) return null
        if (now() - file.lastModified() > rawTtlMs) {
            file.delete()
            return null
        }
        return runCatching { file.readText() }.getOrNull()?.takeIf(String::isNotBlank)
    }

    private fun putRawInternal(target: File, negativeTarget: File, content: String) {
        target.parentFile?.mkdirs()
        val temporary = File(target.parentFile, "${target.name}.tmp")
        runCatching {
            temporary.writeText(content)
            if (target.exists()) target.delete()
            check(temporary.renameTo(target))
            target.setLastModified(now())
            negativeTarget.delete()
            if (++writesSinceTrim >= TRIM_INTERVAL) {
                writesSinceTrim = 0
                trimDisk()
            }
        }.onFailure { temporary.delete() }
    }

    private fun isNegativeInternal(file: File): Boolean {
        if (!file.isFile) return false
        if (now() - file.lastModified() > negativeTtlMs) {
            file.delete()
            return false
        }
        return true
    }

    private fun putNegativeInternal(target: File) {
        target.parentFile?.mkdirs()
        runCatching {
            target.apply {
                writeText("")
                setLastModified(now())
            }
        }
    }

    private fun allDirectories(): List<File> = listOf(amllDirectory, krcDirectory)

    private fun dirFor(namespace: Namespace): File = when (namespace) {
        Namespace.AMLL -> amllDirectory
        Namespace.KRC -> krcDirectory
    }

    private fun rawFile(songId: Long) = File(amllDirectory, "$songId.ttml")
    private fun negativeFile(songId: Long) = File(amllDirectory, "$songId.miss")

    private fun rawFile(trackId: TrackId, namespace: Namespace): File =
        File(dirFor(namespace), "${encodeSafeStem(trackId)}.${namespace.extension}")

    private fun negativeFile(trackId: TrackId, namespace: Namespace): File =
        File(dirFor(namespace), "${encodeSafeStem(trackId)}.miss")

    private fun encodeSafeStem(trackId: TrackId): String {
        val rawEncoded = buildString(trackId.rawId.length) {
            for (ch in trackId.rawId) {
                if (ch in 'a'..'z' || ch in 'A'..'Z' || ch in '0'..'9' || ch == '-') {
                    append(ch)
                } else {
                    append('_')
                    append(ch.code.toString(16))
                    append('_')
                }
            }
        }
        return "${trackId.platform.key}_$rawEncoded"
    }

    private fun trimDisk() {
        val files = allDirectories()
            .flatMap { dir -> dir.listFiles()?.toList().orEmpty() }
            .filter { it.isFile && !it.name.endsWith(".tmp") }
            .sortedBy { it.lastModified() }
        var size = files.sumOf { it.length() }
        for (file in files) {
            if (size <= maxDiskBytes) break
            val length = file.length()
            if (file.delete()) size -= length
        }
    }

    companion object {
        const val RAW_TTL_MS = 7L * 24 * 60 * 60 * 1000
        const val NEGATIVE_TTL_MS = 12L * 60 * 60 * 1000
        const val MAX_DISK_BYTES = 75L * 1024 * 1024
        private const val TRIM_INTERVAL = 10
    }
}

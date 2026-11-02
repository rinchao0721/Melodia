package com.lin0721.linmusic.desktop.data.local

import com.lin0721.linmusic.core.log.AppLogger
import com.lin0721.linmusic.desktop.data.CoverImage
import java.io.File
import java.io.IOException
import java.security.MessageDigest

private const val TAG = "LocalScanner"
private const val COVER_EDGE = 300
private const val MAX_WALK_DEPTH = 32

// 遍历文件夹、读标签、生成封面缩略图；均为阻塞调用，由调用方切到 IO 线程
// 缓存目录可在运行中迁移，封面目录每次现取
class LocalScanner(private val coverDirProvider: () -> File) {

    private val coverDir: File get() = coverDirProvider()

    fun collectFiles(folder: String, excluded: List<String>): List<String> {
        val root = File(folder)
        if (!root.isDirectory) return emptyList()
        return root.walkTopDown()
            .maxDepth(MAX_WALK_DEPTH)
            .onEnter { dir -> excluded.none { samePath(dir.path, it) || isUnder(dir.path, it) } }
            .onFail { dir, e -> AppLogger.w(TAG, "无法读取文件夹 ${dir.path}", e) }
            .filter { it.isFile && isAudioFile(it) }
            .map { normalizePath(it.path) }
            .toList()
    }

    // 文件不存在返回 null；大小与修改时间都没变时沿用旧记录
    fun refresh(path: String, previous: LocalTrack?): LocalTrack? {
        val file = File(path)
        if (!file.isFile) return null
        val size = file.length()
        val modified = file.lastModified()
        if (previous != null && previous.size == size && previous.lastModified == modified) {
            if (previous.hasCover && !coverFile(previous).isFile) {
                val restored = LocalTagIo.readArtwork(file)?.let { writeCover(path, modified, it) } ?: false
                return previous.copy(hasCover = restored)
            }
            return previous
        }
        val tags = LocalTagIo.read(file)
        val hasCover = tags.artwork?.let { writeCover(path, modified, it) } ?: false
        val identityChanged = previous != null && (previous.title != tags.title || previous.artist != tags.artist)
        val (songId, state) = when {
            previous == null -> null to LocalMatchState.PENDING
            identityChanged && previous.matchState in AUTO_STATES -> null to LocalMatchState.PENDING
            else -> previous.songId to previous.matchState
        }
        return LocalTrack(
            path = path,
            title = tags.title,
            artist = tags.artist,
            album = tags.album,
            year = tags.year,
            trackNumber = tags.trackNumber,
            durationMs = tags.durationMs,
            size = size,
            lastModified = modified,
            hasCover = hasCover,
            songId = songId,
            matchState = state
        )
    }

    fun coverFile(track: LocalTrack): File = coverFile(track.path, track.lastModified)

    // File.toURI 生成的 file:/C:/ 单斜杠形式 Coil 无法识别，需用 file:///
    fun coverUri(track: LocalTrack): String? =
        if (track.hasCover) coverFile(track).toPath().toUri().toString() else null

    // 删掉已不对应任何曲目的缩略图
    fun pruneCovers(tracks: Collection<LocalTrack>) {
        val keep = tracks.filter { it.hasCover }.mapTo(HashSet()) { coverFile(it).name }
        coverDir.listFiles()?.forEach { file ->
            if (file.isFile && file.name !in keep) file.delete()
        }
    }

    private fun coverFile(path: String, lastModified: Long): File = File(coverDir, "${pathHash(path)}_$lastModified.jpg")

    private fun writeCover(path: String, lastModified: Long, artwork: ByteArray): Boolean {
        val jpeg = CoverImage.thumbnailJpeg(artwork, COVER_EDGE).getOrNull() ?: return false
        return try {
            coverDir.mkdirs()
            coverFile(path, lastModified).writeBytes(jpeg)
            true
        } catch (e: IOException) {
            AppLogger.w(TAG, "写入封面缩略图失败 $path", e)
            false
        }
    }

    private fun pathHash(path: String): String {
        val digest = MessageDigest.getInstance("SHA-1").digest(path.lowercase().toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }
    }

    private companion object {
        val AUTO_STATES = setOf(LocalMatchState.PENDING, LocalMatchState.AUTO, LocalMatchState.NOT_FOUND)
    }
}

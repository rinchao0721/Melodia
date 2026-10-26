package com.lin0721.linmusic.feature.localmusic.data.scan

import android.content.Context
import android.content.Intent
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.OpenableColumns
import androidx.documentfile.provider.DocumentFile
import com.lin0721.linmusic.core.log.AppLogger
import com.lin0721.linmusic.feature.localmusic.data.db.LocalTrackEntity
import com.lin0721.linmusic.feature.localmusic.data.mergeImportedWavTags
import com.lin0721.linmusic.feature.localmusic.data.metadata.WavMetadata
import com.lin0721.linmusic.feature.localmusic.data.metadata.canSkipWavRefresh
import com.lin0721.linmusic.feature.localmusic.data.metadata.mergeWavMetadata
import com.lin0721.linmusic.feature.localmusic.data.metadata.readWavTags
import com.lin0721.linmusic.feature.localmusic.domain.LocalTrackSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

private const val TAG = "LocalMusicImporter"

private val AUDIO_EXTENSIONS = setOf(
    "mp3", "flac", "wav", "aac", "m4a", "ogg", "opus", "ape", "wma", "aiff"
)

data class ImportResult(
    val addedCount: Int,
    val skippedCount: Int,
    val totalFound: Int
)

// 持久化授权在解析时一并申请
class LocalMusicImporter(private val context: Context) {

    // knownUris 为库内已有条目，只申请权限不重复解析元数据
    suspend fun parseFiles(uris: List<Uri>, knownUris: Set<String>): List<LocalTrackEntity> = withContext(Dispatchers.IO) {
        uris.distinct().mapNotNull { uri ->
            ensureActive()
            runCatching {
                context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
            }.onFailure {
                runCatching {
                    context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }.onFailure { AppLogger.w(TAG, "获取持久化权限失败 uri=$uri", it) }
            }
            if (uri.toString() in knownUris) null else parseMetadata(uri)
        }
    }

    suspend fun collectFolder(treeUri: Uri): List<Uri> = withContext(Dispatchers.IO) {
        runCatching {
            context.contentResolver.takePersistableUriPermission(treeUri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
        }.onFailure {
            runCatching {
                context.contentResolver.takePersistableUriPermission(treeUri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }.onFailure { AppLogger.w(TAG, "获取文件夹持久化权限失败 uri=$treeUri", it) }
        }
        collectAudioFilesFromTree(treeUri)
    }

    private fun collectAudioFilesFromTree(treeUri: Uri): List<Uri> {
        val rootDoc = DocumentFile.fromTreeUri(context, treeUri) ?: return emptyList()
        val result = mutableListOf<Uri>()
        val queue = ArrayDeque<DocumentFile>()
        queue.add(rootDoc)

        while (queue.isNotEmpty() && result.size < 5000) {
            val currentDir = queue.removeFirst()
            val files = currentDir.listFiles()
            for (file in files) {
                if (file.isDirectory) {
                    queue.add(file)
                } else if (isAudioDocument(file)) {
                    result.add(file.uri)
                }
            }
        }
        return result
    }

    private fun isAudioDocument(doc: DocumentFile): Boolean {
        if (doc.isDirectory) return false
        val type = doc.type
        if (type != null && type.startsWith("audio/")) return true
        val name = doc.name ?: return false
        val ext = name.substringAfterLast('.', "").lowercase()
        return ext in AUDIO_EXTENSIONS
    }

    internal fun parseMetadata(uri: Uri): LocalTrackEntity? {
        var displayName: String? = null
        var fileSize: Long = 0L
        runCatching {
            context.contentResolver.query(
                uri,
                arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE),
                null,
                null,
                null
            )?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (nameIndex != -1) displayName = cursor.getString(nameIndex)
                    val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                    if (sizeIndex != -1) fileSize = cursor.getLong(sizeIndex)
                }
            }
        }

        val retriever = MediaMetadataRetriever()
        var title: String? = null
        var artist: String? = null
        var album: String? = null
        var durationMs: Long = 0L
        var year: Int? = null
        var albumArtist: String? = null
        var trackNumber: Int? = null

        try {
            retriever.setDataSource(context, uri)
            title = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE)
            artist = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST)
            album = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM)
            durationMs = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
            albumArtist = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUMARTIST)?.trim()?.takeIf { it.isNotEmpty() }
            year = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_YEAR)?.trim()?.toIntOrNull()?.takeIf { it > 0 }
            trackNumber = encodeTrackNumber(
                track = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_CD_TRACK_NUMBER),
                disc = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DISC_NUMBER)
            )
        } catch (e: Exception) {
            AppLogger.w(TAG, "解析音频元数据失败 uri=$uri", e)
        } finally {
            runCatching { retriever.release() }
        }

        val metadata = mergeWavMetadata(
            system = WavMetadata(title, artist, album, albumArtist, year, trackNumber),
            embedded = readWavTags(context, uri, displayName ?: Uri.decode(uri.lastPathSegment),
                runCatching { context.contentResolver.getType(uri) }
                    .onFailure { if (it is CancellationException) throw it }.getOrNull())
        )
        val finalTitle = metadata.title?.trim()?.takeIf { it.isNotBlank() }
            ?: displayName?.substringBeforeLast('.')?.trim()?.takeIf { it.isNotBlank() }
            ?: "未知曲目"
        val finalArtist = metadata.artist?.trim()?.takeIf { it.isNotBlank() } ?: "未知艺术家"

        val now = System.currentTimeMillis()
        return LocalTrackEntity(
            uri = uri.toString(),
            mediaStoreId = null,
            songId = null,
            title = finalTitle,
            artist = finalArtist,
            album = metadata.album?.trim()?.takeIf { it.isNotBlank() },
            durationMs = durationMs,
            sizeBytes = fileSize,
            path = null,
            dateAddedMs = now,
            dateModifiedMs = now,
            source = LocalTrackSource.IMPORTED.name,
            year = metadata.year,
            trackNumber = metadata.trackNumber,
            albumArtist = metadata.albumArtist
        )
    }

    // 已导入文件不会再次经过 MediaStore，升级后同步时只更新成功补读到的 WAV 标签。
    internal fun refreshWavTags(existing: LocalTrackEntity): LocalTrackEntity? {
        if (existing.source != LocalTrackSource.IMPORTED.name) return null
        val uri = Uri.parse(existing.uri)
        if (canSkipWavRefresh(Uri.decode(uri.lastPathSegment))) return null
        val tags = readWavTags(context, uri) ?: return null
        return mergeImportedWavTags(existing, tags).takeIf { it != existing }
    }
}

// 元数据形如 "3/12"，按 MediaStore 的规则编码为 碟号 * 1000 + 音轨号，便于统一排序
internal fun encodeTrackNumber(track: String?, disc: String?): Int? {
    val trackNo = track?.substringBefore('/')?.trim()?.toIntOrNull()?.takeIf { it in 1..999 } ?: return null
    val discNo = disc?.substringBefore('/')?.trim()?.toIntOrNull()?.takeIf { it > 0 } ?: 0
    return discNo * 1000 + trackNo
}

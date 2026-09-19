package com.lin0721.linmusic.core.localmusic

import android.content.Context
import android.content.Intent
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.OpenableColumns
import androidx.documentfile.provider.DocumentFile
import com.lin0721.linmusic.core.log.AppLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
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

class LocalMusicImporter(
    private val context: Context,
    private val importedMusicPreferences: ImportedMusicPreferences
) {

    suspend fun importFiles(uris: List<Uri>, existingUris: Set<String> = emptySet()): ImportResult =
        withContext(Dispatchers.IO) {
            val distinctUris = uris.distinct()
            if (distinctUris.isEmpty()) return@withContext ImportResult(0, 0, 0)

            val currentImported = importedMusicPreferences.records.first().map { it.uriString }.toSet()
            val allExisting = existingUris + currentImported

            val toProcess = mutableListOf<Uri>()
            var skippedCount = 0

            for (uri in distinctUris) {
                runCatching {
                    context.contentResolver.takePersistableUriPermission(
                        uri,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION
                    )
                }.onFailure { AppLogger.w(TAG, "获取持久化权限失败 uri=$uri", it) }

                if (uri.toString() in allExisting) {
                    skippedCount++
                } else {
                    toProcess.add(uri)
                }
            }

            val records = toProcess.mapNotNull { parseMetadata(it) }
            val added = importedMusicPreferences.addRecords(records)

            ImportResult(
                addedCount = added,
                skippedCount = skippedCount + (records.size - added),
                totalFound = distinctUris.size
            )
        }

    suspend fun importFolder(treeUri: Uri, existingUris: Set<String> = emptySet()): ImportResult =
        withContext(Dispatchers.IO) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    treeUri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            }.onFailure { AppLogger.w(TAG, "获取文件夹持久化权限失败 uri=$treeUri", it) }

            val audioUris = collectAudioFilesFromTree(treeUri)
            importFiles(audioUris, existingUris)
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

    private fun parseMetadata(uri: Uri): ImportedTrackRecord? {
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

        try {
            retriever.setDataSource(context, uri)
            title = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE)
            artist = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST)
            album = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM)
            durationMs = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
        } catch (e: Exception) {
            AppLogger.w(TAG, "解析音频元数据失败 uri=$uri", e)
        } finally {
            runCatching { retriever.release() }
        }

        val finalTitle = title?.trim()?.takeIf { it.isNotBlank() }
            ?: displayName?.substringBeforeLast('.')?.trim()?.takeIf { it.isNotBlank() }
            ?: "未知曲目"
        val finalArtist = artist?.trim()?.takeIf { it.isNotBlank() } ?: "未知艺术家"

        return ImportedTrackRecord(
            uriString = uri.toString(),
            title = finalTitle,
            artist = finalArtist,
            album = album?.trim()?.takeIf { it.isNotBlank() },
            durationMs = durationMs,
            sizeBytes = fileSize,
            dateAddedMs = System.currentTimeMillis()
        )
    }
}

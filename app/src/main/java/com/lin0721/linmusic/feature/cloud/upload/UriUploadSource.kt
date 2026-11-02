package com.lin0721.linmusic.feature.cloud.upload

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.OpenableColumns
import java.io.IOException
import java.io.InputStream

// SAF 选取的文件，content:// 没有真实路径，大小与文件名都要经 ContentResolver 查询
class UriUploadSource(
    private val context: Context,
    private val uri: Uri
) : UploadSource {

    override val key: String = uri.toString()

    override val fileName: String = queryFileName(context, uri)

    override val mimeType: String
        get() = context.contentResolver.getType(uri) ?: "audio/mpeg"

    override fun size(): Long {
        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
            if (sizeIndex != -1 && cursor.moveToFirst() && !cursor.isNull(sizeIndex)) {
                return cursor.getLong(sizeIndex)
            }
        }
        return 0L
    }

    override fun openStream(): InputStream =
        context.contentResolver.openInputStream(uri) ?: throw IOException("无法打开文件")

    override fun readMetadata(): LocalAudioMetadata {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(context, uri)
            LocalAudioMetadata(
                title = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE)?.trim()?.ifBlank { null },
                artist = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST)?.trim()?.ifBlank { null },
                album = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM)?.trim()?.ifBlank { null }
            )
        } catch (e: Exception) {
            LocalAudioMetadata(title = null, artist = null, album = null)
        } finally {
            retriever.release()
        }
    }
}

private fun queryFileName(context: Context, uri: Uri): String {
    context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
        val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
        if (nameIndex != -1 && cursor.moveToFirst()) {
            cursor.getString(nameIndex)?.let { return it }
        }
    }
    return uri.lastPathSegment.orEmpty()
}

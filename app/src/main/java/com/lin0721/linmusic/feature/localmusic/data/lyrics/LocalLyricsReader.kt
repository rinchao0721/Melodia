package com.lin0721.linmusic.feature.localmusic.data.lyrics

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import com.kyant.taglib.TagLib
import com.lin0721.linmusic.core.log.AppLogger
import com.lin0721.linmusic.core.localmusic.db.LocalTrackDao
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private const val TAG = "LocalLyricsReader"

// 本地音频歌词读取：仅从音频文件内嵌标签（LYRICS）读取
class LocalLyricsReader(
    private val context: Context,
    @Suppress("unused") private val dao: LocalTrackDao? = null
) {

    private val resolver: ContentResolver get() = context.contentResolver

    suspend fun read(sourceUri: String): String? = withContext(Dispatchers.IO) {
        val uri = runCatching { Uri.parse(sourceUri) }.getOrNull() ?: return@withContext null
        readEmbedded(uri)
    }

    private fun readEmbedded(uri: Uri): String? = runCatching {
        resolver.openFileDescriptor(uri, "r")?.use { pfd ->
            TagLib.getMetadata(pfd.dup().detachFd(), readPictures = false)
                ?.propertyMap
                ?.get("LYRICS")
                ?.firstOrNull { it.isNotBlank() }
        }
    }.onFailure { AppLogger.d(TAG, "内嵌歌词读取失败 uri=$uri", it) }.getOrNull()
}

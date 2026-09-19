package com.lin0721.linmusic.core.localmusic

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import androidx.core.content.FileProvider
import com.lin0721.linmusic.core.log.AppLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

private const val TAG = "LocalCoverArtCache"

// 提取本地音频内嵌封面并进行磁盘缓存
class LocalCoverArtCache(private val context: Context) {

    private val cacheDir = File(context.cacheDir, "local_covers").apply { mkdirs() }

    suspend fun coverUriFor(sourceUri: Uri): Uri? = withContext(Dispatchers.IO) {
        val cacheFile = File(cacheDir, "${sourceUri.toString().hashCode()}.jpg")
        if (cacheFile.exists()) {
            return@withContext fileProviderUri(cacheFile)
        }
        val bytes = extractEmbeddedPicture(sourceUri) ?: return@withContext null
        runCatching { cacheFile.writeBytes(bytes) }
            .onFailure {
                AppLogger.w(TAG, "封面缓存写入失败 uri=$sourceUri", it)
                return@withContext null
            }
        fileProviderUri(cacheFile)
    }

    private fun extractEmbeddedPicture(uri: Uri): ByteArray? {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(context, uri)
            retriever.embeddedPicture
        } catch (e: Exception) {
            AppLogger.w(TAG, "内嵌封面提取失败 uri=$uri", e)
            null
        } finally {
            runCatching { retriever.release() }
        }
    }

    private fun fileProviderUri(file: File): Uri =
        FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
}

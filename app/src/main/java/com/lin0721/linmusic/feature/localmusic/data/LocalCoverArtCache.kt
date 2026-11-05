package com.lin0721.linmusic.feature.localmusic.data

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import androidx.core.content.FileProvider
import com.lin0721.linmusic.core.log.AppLogger
import com.lin0721.linmusic.feature.localmusic.data.metadata.readWavPicture
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.ConcurrentHashMap

private const val TAG = "LocalCoverArtCache"

// 提取本地音频内嵌封面并进行磁盘缓存
class LocalCoverArtCache(private val context: Context) {

    private val cacheDir = File(context.cacheDir, "local_covers").apply { mkdirs() }
    // 改封面后换文件名，Coil 按 uri 缓存，同名文件会一直显示旧图
    private val versions = ConcurrentHashMap<String, Int>()

    suspend fun coverUriFor(sourceUri: Uri): Uri? = withContext(Dispatchers.IO) {
        val uriStr = sourceUri.toString()
        val version = versions[uriStr] ?: 0
        val cacheFile = File(cacheDir, "${uriStr.hashCode()}_v${version}.jpg")
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
        val systemPicture = try {
            retriever.setDataSource(context, uri)
            retriever.embeddedPicture
        } catch (e: Exception) {
            AppLogger.w(TAG, "内嵌封面提取失败 uri=$uri", e)
            null
        } finally {
            runCatching { retriever.release() }
        }
        return systemPicture ?: readWavPicture(context, uri)
    }

    private fun fileProviderUri(file: File): Uri =
        FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)

    fun invalidate(sourceUri: Uri) {
        val uriStr = sourceUri.toString()
        val prefix = "${uriStr.hashCode()}_v"
        cacheDir.listFiles { file -> file.name.startsWith(prefix) }?.forEach { runCatching { it.delete() } }
        versions[uriStr] = (versions[uriStr] ?: 0) + 1
    }
}

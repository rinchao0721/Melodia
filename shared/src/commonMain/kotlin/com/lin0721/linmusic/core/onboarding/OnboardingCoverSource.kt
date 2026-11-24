package com.lin0721.linmusic.core.onboarding

import com.lin0721.linmusic.core.log.AppLogger
import com.lin0721.linmusic.core.model.PlaylistDetail
import com.lin0721.linmusic.feature.playlist.data.PlaylistRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import java.io.File

private const val TAG = "OnboardingCoverSource"

// 首次启动页背景封面：取网易热歌榜专辑小图，首次拉取后缓存到本地文件，之后直接读缓存
class OnboardingCoverSource(
    private val cacheFile: File,
    private val fetchPlaylist: suspend (Long) -> PlaylistDetail?
) {
    private val json = Json { ignoreUnknownKeys = true }
    private val serializer = ListSerializer(String.serializer())

    // 有缓存直接返回；否则联网拉取并写缓存；都失败返回空列表，由界面退回渐变色块
    suspend fun load(): List<String> {
        readCache().takeIf { it.isNotEmpty() }?.let { return it }
        val detail = try {
            fetchPlaylist(HOT_SONGS_PLAYLIST_ID)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            AppLogger.w(TAG, "拉取热歌榜封面失败", e)
            null
        } ?: return emptyList()
        val urls = extractCoverUrls(detail)
        if (urls.isNotEmpty()) writeCache(urls)
        return urls
    }

    private suspend fun readCache(): List<String> = withContext(Dispatchers.IO) {
        try {
            if (!cacheFile.isFile) return@withContext emptyList()
            json.decodeFromString(serializer, cacheFile.readText()).filter { it.isNotBlank() }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            AppLogger.w(TAG, "读取封面缓存失败", e)
            emptyList()
        }
    }

    private suspend fun writeCache(urls: List<String>) = withContext(Dispatchers.IO) {
        try {
            cacheFile.parentFile?.mkdirs()
            val temp = File(cacheFile.parentFile, "${cacheFile.name}.tmp")
            temp.writeText(json.encodeToString(serializer, urls))
            if (!temp.renameTo(cacheFile)) {
                cacheFile.delete()
                if (!temp.renameTo(cacheFile)) temp.delete()
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            AppLogger.w(TAG, "写入封面缓存失败", e)
        }
    }

    companion object {
        const val HOT_SONGS_PLAYLIST_ID = 3778678L
        const val MAX_COVERS = 48
        private const val SIZE_PARAM = "param=150y150"

        fun fromRepository(repository: PlaylistRepository, cacheFile: File): OnboardingCoverSource =
            OnboardingCoverSource(cacheFile) { id -> repository.getPlaylistDetail(id).first().getOrNull() }

        // 去重后的专辑封面小图地址，按曲目顺序取前 MAX_COVERS 个
        fun extractCoverUrls(detail: PlaylistDetail, limit: Int = MAX_COVERS): List<String> =
            detail.tracks.asSequence()
                .map { it.al.picUrl.trim() }
                .filter { it.isNotEmpty() }
                .map { url -> url.substringBefore('?').replaceFirst("http://", "https://") }
                .distinct()
                .take(limit.coerceAtLeast(0))
                .map { "$it?$SIZE_PARAM" }
                .toList()
    }
}

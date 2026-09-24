package com.lin0721.linmusic.core.player

import android.net.Uri
import android.os.Bundle
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import kotlinx.serialization.Serializable

enum class PlayMode { LIST_LOOP, SINGLE_LOOP, SHUFFLE }

@Serializable
data class QueueItem(
    val songId: Long,
    val title: String,
    val artist: String,
    val coverUrl: String,
    // 本地外部音频 Uri，非空时直接本地播放
    val localUri: String? = null
) {
    fun toMediaItem(url: String, playContext: String? = null, artworkUri: String? = coverUrl): MediaItem {
        val bundle = Bundle().apply {
            putLong("songId", songId)
            if (playContext != null) putString("playContext", playContext)
        }
        val metadata = MediaMetadata.Builder()
            .setTitle(title)
            .setArtist(artist)
            .setArtworkUri(artworkUri?.takeIf { it.isNotBlank() }?.let { Uri.parse(it) })
            .setExtras(bundle)
            .build()
        return MediaItem.Builder()
            .setUri(url)
            .setMediaId(songId.toString())
            .setMediaMetadata(metadata)
            .setCustomCacheKey(streamCacheKey(url))
            .build()
    }

    // 网络链接带时效签名（路径时间戳 + 查询参数），默认以完整 URL 作缓存 key 会导致每次换新链接都缓存不命中；
    // 改用 songId + 文件名（音频文件摘要，按音质区分）作稳定 key，本地 Uri 仍沿用默认 key
    private fun streamCacheKey(url: String): String? {
        val uri = Uri.parse(url)
        if (uri.scheme != "http" && uri.scheme != "https") return null
        val fileName = uri.lastPathSegment?.takeIf { it.isNotBlank() } ?: return null
        return "$songId/$fileName"
    }
}

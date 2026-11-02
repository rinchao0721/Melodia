package com.lin0721.linmusic.core.player

import android.net.Uri
import android.os.Bundle
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata

private fun QueueItem.toMetadata(playContext: String?, artworkUri: String?): MediaMetadata {
    val bundle = Bundle().apply {
        putLong("songId", songId)
        if (playContext != null) putString("playContext", playContext)
    }
    return MediaMetadata.Builder()
        .setTitle(title)
        .setArtist(artist)
        .setArtworkUri(artworkUri?.takeIf { it.isNotBlank() }?.let { Uri.parse(it) })
        .setExtras(bundle)
        .build()
}

fun QueueItem.toMediaItem(url: String, playContext: String? = null, artworkUri: String? = coverUrl): MediaItem =
    MediaItem.Builder()
        .setUri(url)
        .setMediaId(songId.toString())
        .setMediaMetadata(toMetadata(playContext, artworkUri))
        .setCustomCacheKey(streamCacheKey(songId, url))
        .build()

// 离线缓存曲目的占位地址前缀，本身不可访问
internal const val OFFLINE_PLACEHOLDER_URI_PREFIX = "https://offline.invalid/"

// 离线播放已完整缓存的歌曲：URL 只用于推断容器格式，完整缓存命中时不会发起网络请求
fun QueueItem.toOfflineCachedMediaItem(cacheKey: String, playContext: String? = null, artworkUri: String? = coverUrl): MediaItem =
    MediaItem.Builder()
        .setUri(OFFLINE_PLACEHOLDER_URI_PREFIX + Uri.encode(cacheKey.substringAfter('/')))
        .setMediaId(songId.toString())
        .setMediaMetadata(toMetadata(playContext, artworkUri))
        .setCustomCacheKey(cacheKey)
        .build()

// 点击播放后、播放地址尚未返回时用于即时显示的曲目，只带元数据不带播放地址
fun QueueItem.toPendingMediaItem(playContext: String? = null, artworkUri: String? = coverUrl): MediaItem =
    MediaItem.Builder()
        .setMediaId(songId.toString())
        .setMediaMetadata(toMetadata(playContext, artworkUri))
        .build()

// 冷启动恢复的上次曲目，只带元数据不带播放地址
fun PlaybackState.toRestoredMediaItem(): MediaItem {
    val bundle = Bundle().apply { putLong("songId", songId) }
    val metadata = MediaMetadata.Builder()
        .setTitle(title)
        .setArtist(artist)
        .setArtworkUri(coverUrl.takeIf { it.isNotBlank() }?.let { Uri.parse(it) })
        .setExtras(bundle)
        .build()
    return MediaItem.Builder()
        .setMediaId(songId.toString())
        .setMediaMetadata(metadata)
        .build()
}

// 网络链接带时效签名（路径时间戳 + 查询参数），默认以完整 URL 作缓存 key 会导致每次换新链接都缓存不命中；
// 改用 songId + 文件名（音频文件摘要，按音质区分）作稳定 key，本地 Uri 仍沿用默认 key
private fun streamCacheKey(songId: Long, url: String): String? {
    val uri = Uri.parse(url)
    if (uri.scheme != "http" && uri.scheme != "https") return null
    val fileName = uri.lastPathSegment?.takeIf { it.isNotBlank() } ?: return null
    return "$songId/$fileName"
}

package com.lin0721.linmusic.feature.cloud.domain

data class CloudSong(
    val songId: Long,
    val name: String,
    val artist: String,
    val album: String,
    val bitrate: Int,
    val fileSizeBytes: Long,
    val fileSizeText: String,
    val addedAtText: String,
    // 未识别歌曲的封面字段是网易云通用占位图而非真实封面，coverUrl 已在映射时置空
    val coverUrl: String?,
    val isUnmatched: Boolean,
    val durationMs: Long = 0,
    val addedAtMs: Long = 0,
    // 播放、评论、歌词、喜欢用的 id：已识别时为正版歌曲 id；删除与纠正匹配仍用云盘 songId
    val playSongId: Long = songId
)

data class CloudQuota(
    val usedBytes: Long,
    val maxBytes: Long,
    val totalCount: Int
) {
    val usedRatio: Float get() = if (maxBytes <= 0) 0f else (usedBytes.toFloat() / maxBytes).coerceIn(0f, 1f)
}

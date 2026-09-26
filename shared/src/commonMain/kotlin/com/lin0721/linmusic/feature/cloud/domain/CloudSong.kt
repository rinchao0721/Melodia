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
    val isUnmatched: Boolean
)

data class CloudQuota(
    val usedBytes: Long,
    val maxBytes: Long,
    val totalCount: Int
) {
    val usedRatio: Float get() = if (maxBytes <= 0) 0f else (usedBytes.toFloat() / maxBytes).coerceIn(0f, 1f)
}

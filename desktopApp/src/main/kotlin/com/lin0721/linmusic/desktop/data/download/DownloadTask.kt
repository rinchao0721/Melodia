package com.lin0721.linmusic.desktop.data.download

import com.lin0721.linmusic.core.download.DownloadTrackInfo
import kotlinx.serialization.Serializable

@Serializable
enum class DownloadTaskStatus { QUEUED, DOWNLOADING, PAUSED, FAILED, SUCCEEDED }

@Serializable
data class DownloadTask(
    val id: String,
    val songId: Long,
    val songName: String,
    val artistName: String,
    val albumName: String = "",
    val coverUrl: String? = null,
    val albumYear: Int = 0,
    val durationMs: Long = 0,
    val level: String,
    // 批量下载所属歌单名，单曲为空
    val batchLabel: String? = null,
    val status: DownloadTaskStatus,
    val progress: Int = 0,
    val failureReason: String? = null,
    // 已有同等或更高音质而跳过
    val skipped: Boolean = false,
    val createdAt: Long,
    val finishedAt: Long = 0
) {
    val isActive: Boolean get() = status == DownloadTaskStatus.QUEUED || status == DownloadTaskStatus.DOWNLOADING

    // 未结束（含暂停）
    val isLive: Boolean get() = isActive || status == DownloadTaskStatus.PAUSED

    fun toTrackInfo() = DownloadTrackInfo(songId, songName, artistName, albumName, coverUrl, albumYear, durationMs)
}

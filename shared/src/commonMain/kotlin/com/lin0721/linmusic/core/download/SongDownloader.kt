package com.lin0721.linmusic.core.download

import com.lin0721.linmusic.core.model.Track
import java.util.UUID

// 待下载歌曲信息
data class DownloadTrackInfo(
    val songId: Long,
    val songName: String,
    val artistName: String,
    val albumName: String = "",
    val coverUrl: String? = null,
    val albumYear: Int = 0,
    // 0 表示未知，仅用于识别本地已有文件
    val durationMs: Long = 0
)

fun Track.toDownloadTrackInfo() = DownloadTrackInfo(
    songId = id,
    songName = name,
    artistName = ar.joinToString("/") { it.name },
    albumName = al.name,
    coverUrl = al.picUrl.takeIf { it.isNotBlank() },
    albumYear = yearFromEpochMillis(publishTime),
    durationMs = dt
)

// 批量入队结果：已下载过同等或更高音质、或已在下载队列中的歌曲不会重复入队
data class BatchEnqueueResult(
    val enqueuedCount: Int,
    val skippedCount: Int,
    val queuedCount: Int = 0
)

// 批量下载入队结果提示文案
fun BatchEnqueueResult.toToastMessage(): String {
    val notes = buildList {
        if (skippedCount > 0) add("跳过 $skippedCount 首已下载")
        if (queuedCount > 0) add("$queuedCount 首已在队列中")
    }.joinToString("，")
    return when {
        enqueuedCount == 0 && notes.isNotEmpty() -> "没有需要下载的歌曲：$notes"
        notes.isNotEmpty() -> "已将 $enqueuedCount 首歌曲加入下载队列，$notes"
        else -> "已将 $enqueuedCount 首歌曲加入下载队列"
    }
}

// 时间戳转年份
fun yearFromEpochMillis(epochMillis: Long): Int {
    if (epochMillis <= 0) return 0
    return runCatching {
        java.time.Instant.ofEpochMilli(epochMillis).atZone(java.time.ZoneId.systemDefault()).year
    }.getOrDefault(0)
}

// 歌曲下载入队的跨平台契约
interface SongDownloader {
    fun enqueueSingle(track: DownloadTrackInfo, level: String): UUID
    suspend fun enqueueBatch(tracks: List<DownloadTrackInfo>, level: String, batchTag: String, batchLabel: String): BatchEnqueueResult
}

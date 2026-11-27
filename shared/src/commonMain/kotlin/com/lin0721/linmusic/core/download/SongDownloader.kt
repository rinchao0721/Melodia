package com.lin0721.linmusic.core.download

import com.lin0721.linmusic.core.model.Track
import com.lin0721.linmusic.core.platform.KugouTrackMeta
import com.lin0721.linmusic.core.platform.TrackId
import com.lin0721.linmusic.core.platform.UnifiedTrack
import com.lin0721.linmusic.core.source.MusicPlatform
import java.util.UUID
import kotlin.math.absoluteValue

// 待下载歌曲信息
data class DownloadTrackInfo(
    val songId: Long,
    val songName: String,
    val artistName: String,
    val albumName: String = "",
    val coverUrl: String? = null,
    val albumYear: Int = 0,
    // 0 表示未知，仅用于识别本地已有文件
    val durationMs: Long = 0,
    val trackId: TrackId? = null,
    val kugouMeta: KugouTrackMeta? = null,
    val isPodcast: Boolean = false,
    val radioId: Long = 0L
) {
    val effectiveTrackId: TrackId
        get() = trackId ?: TrackId.netease(songId.coerceAtLeast(0L))

    val effectivePlatform: MusicPlatform
        get() = effectiveTrackId.platform
}

fun Track.toDownloadTrackInfo() = DownloadTrackInfo(
    songId = id,
    songName = name,
    artistName = ar.joinToString("/") { it.name },
    albumName = al.name,
    coverUrl = al.picUrl.takeIf { it.isNotBlank() },
    albumYear = yearFromEpochMillis(publishTime),
    durationMs = dt
)

fun UnifiedTrack.toDownloadTrackInfo(): DownloadTrackInfo {
    val legacySongId = when (platform) {
        MusicPlatform.NETEASE -> id.neteaseIdOrNull() ?: 0L
        MusicPlatform.LOCAL -> 0L
        else -> -1L - id.canonical.hashCode().toLong().absoluteValue
    }
    val resolvedArtist = artistNames.ifBlank { artists.joinToString("/") { it.name } }
    val resolvedCover = coverUrl.ifBlank { album?.coverUrl.orEmpty() }.takeIf { it.isNotBlank() }
    val publishMs = publishTimeMs.takeIf { it > 0L } ?: album?.publishTimeMs ?: 0L
    return DownloadTrackInfo(
        songId = legacySongId,
        songName = name,
        artistName = resolvedArtist,
        albumName = album?.name.orEmpty(),
        coverUrl = resolvedCover,
        albumYear = yearFromEpochMillis(publishMs),
        durationMs = durationMs,
        trackId = id,
        kugouMeta = kugouMeta
    )
}

// 批量入队结果：已下载过同等或更高音质、或已在下载队列中的歌曲不会重复入队
data class BatchEnqueueResult(
    val enqueuedCount: Int,
    val skippedCount: Int,
    val queuedCount: Int = 0
)

// 批量下载入队结果提示文案
fun BatchEnqueueResult.toToastMessage(unit: String = "首", noun: String = "歌曲"): String {
    val notes = buildList {
        if (skippedCount > 0) add("跳过 $skippedCount ${unit}已下载")
        if (queuedCount > 0) add("$queuedCount ${unit}已在队列中")
    }.joinToString("，")
    return when {
        enqueuedCount == 0 && notes.isNotEmpty() -> "没有需要下载的$noun：$notes"
        notes.isNotEmpty() -> "已将 $enqueuedCount $unit${noun}加入下载队列，$notes"
        else -> "已将 $enqueuedCount $unit${noun}加入下载队列"
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

package com.lin0721.linmusic.feature.localmusic.domain

import android.net.Uri
import com.lin0721.linmusic.core.localmusic.domain.LocalTrack as SharedLocalTrack
import com.lin0721.linmusic.core.localmusic.domain.LocalTrackSource as SharedLocalTrackSource

// 本地音乐来源类型
enum class LocalTrackSource { MELODIA_DOWNLOAD, EXTERNAL, IMPORTED, DOWNLOAD }

data class LocalTrack(
    val mediaStoreId: Long,
    val songId: Long?,
    val title: String,
    val artist: String,
    val album: String?,
    val durationMs: Long,
    val sizeBytes: Long,
    val uri: Uri,
    val path: String?,
    val dateAddedMs: Long,
    val source: LocalTrackSource,
    val albumArtist: String? = null,
    val year: Int? = null,
    val trackNumber: Int? = null,
    val qualityLevel: String? = null
)

// 播放队列里的歌曲 id：已匹配网易歌曲用真实 id，其余用 MediaStore id 的相反数占位
val LocalTrack.queueSongId: Long get() = songId ?: -mediaStoreId

fun LocalTrack.toShared(): SharedLocalTrack = SharedLocalTrack(
    location = uri.toString(),
    mediaStoreId = mediaStoreId,
    songId = songId,
    title = title,
    artist = artist,
    album = album,
    durationMs = durationMs,
    sizeBytes = sizeBytes,
    path = path,
    dateAddedMs = dateAddedMs,
    dateModifiedMs = dateAddedMs,
    source = when (source) {
        LocalTrackSource.MELODIA_DOWNLOAD -> SharedLocalTrackSource.MELODIA_DOWNLOAD
        LocalTrackSource.EXTERNAL -> SharedLocalTrackSource.EXTERNAL
        LocalTrackSource.IMPORTED -> SharedLocalTrackSource.IMPORTED
        LocalTrackSource.DOWNLOAD -> SharedLocalTrackSource.DOWNLOAD
    },
    albumArtist = albumArtist,
    year = year,
    trackNumber = trackNumber,
    qualityLevel = qualityLevel,
    folder = folderPathOf(path)
)

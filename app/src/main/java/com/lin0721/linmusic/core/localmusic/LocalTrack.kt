package com.lin0721.linmusic.core.localmusic

import android.net.Uri

// 本地音乐来源类型
enum class LocalTrackSource { MELODIA_DOWNLOAD, EXTERNAL, IMPORTED }

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
    val source: LocalTrackSource
)

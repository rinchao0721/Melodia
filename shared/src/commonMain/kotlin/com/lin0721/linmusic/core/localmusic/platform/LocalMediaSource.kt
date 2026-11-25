package com.lin0721.linmusic.core.localmusic.platform

import com.lin0721.linmusic.core.localmusic.domain.LocalTrackSource

data class LocalMediaEntry(
    val location: String,
    val mediaStoreId: Long?,
    val title: String,
    val artist: String,
    val album: String?,
    val albumArtist: String?,
    val year: Int?,
    val trackNumber: Int?,
    val durationMs: Long,
    val sizeBytes: Long,
    val path: String?,
    val dateAddedMs: Long,
    val dateModifiedMs: Long,
    val source: LocalTrackSource,
    val qualityLevel: String?,
    val hasEmbeddedCover: Boolean = false,
    val folder: String? = null
)

interface LocalMediaSource {
    suspend fun scanAll(): List<LocalMediaEntry>
}

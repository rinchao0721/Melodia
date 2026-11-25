package com.lin0721.linmusic.core.localmusic.domain

enum class LocalTrackSource {
    MELODIA_DOWNLOAD,
    EXTERNAL,
    IMPORTED,
    DOWNLOAD
}

// 只有 PENDING 会被自动匹配；用户手动匹配或解除后不再自动改动
enum class LocalMatchState {
    PENDING,
    AUTO,
    MANUAL,
    NOT_FOUND,
    DETACHED
}

data class LocalTrack(
    val location: String,
    val mediaStoreId: Long,
    val songId: Long?,
    val title: String,
    val artist: String,
    val album: String?,
    val durationMs: Long,
    val sizeBytes: Long,
    val path: String?,
    val dateAddedMs: Long,
    val dateModifiedMs: Long,
    val source: LocalTrackSource,
    val albumArtist: String? = null,
    val year: Int? = null,
    val trackNumber: Int? = null,
    val qualityLevel: String? = null,
    val matchState: LocalMatchState = LocalMatchState.PENDING,
    val matchedSongId: Long? = null,
    val hasEmbeddedCover: Boolean = false,
    val folder: String? = null
) {
    val queueSongId: Long get() = matchedSongId ?: songId ?: -mediaStoreId
}

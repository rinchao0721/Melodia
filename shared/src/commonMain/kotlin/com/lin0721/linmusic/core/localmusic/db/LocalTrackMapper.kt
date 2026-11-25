package com.lin0721.linmusic.core.localmusic.db

import com.lin0721.linmusic.core.localmusic.domain.LocalMatchState
import com.lin0721.linmusic.core.localmusic.domain.LocalTrack
import com.lin0721.linmusic.core.localmusic.domain.LocalTrackSource
import com.lin0721.linmusic.core.localmusic.domain.folderPathOf

private fun LocalTrackEntity.stableId(): Long =
    mediaStoreId ?: location.hashCode().toLong().let { if (it == 0L) 1L else it }

fun LocalTrackEntity.toDomain(): LocalTrack = LocalTrack(
    location = location,
    mediaStoreId = stableId(),
    songId = songId,
    title = title,
    artist = artist,
    album = album,
    durationMs = durationMs,
    sizeBytes = sizeBytes,
    path = path,
    dateAddedMs = dateAddedMs,
    dateModifiedMs = dateModifiedMs,
    source = LocalTrackSource.entries.firstOrNull { it.name == source } ?: LocalTrackSource.EXTERNAL,
    albumArtist = albumArtist,
    year = year,
    trackNumber = trackNumber,
    qualityLevel = qualityLevel,
    matchState = LocalMatchState.entries.firstOrNull { it.name == matchState } ?: LocalMatchState.PENDING,
    matchedSongId = matchedSongId,
    hasEmbeddedCover = hasEmbeddedCover,
    folder = folder ?: folderPathOf(path)
)

fun LocalTrack.toEntity(): LocalTrackEntity = LocalTrackEntity(
    location = location,
    mediaStoreId = mediaStoreId,
    songId = songId,
    title = title,
    artist = artist,
    album = album,
    durationMs = durationMs,
    sizeBytes = sizeBytes,
    path = path,
    dateAddedMs = dateAddedMs,
    dateModifiedMs = dateModifiedMs,
    source = source.name,
    albumArtist = albumArtist,
    year = year,
    trackNumber = trackNumber,
    qualityLevel = qualityLevel,
    matchState = matchState.name,
    matchedSongId = matchedSongId,
    hasEmbeddedCover = hasEmbeddedCover,
    folder = folder ?: folderPathOf(path)
)

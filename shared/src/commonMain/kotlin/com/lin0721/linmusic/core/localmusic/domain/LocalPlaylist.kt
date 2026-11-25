package com.lin0721.linmusic.core.localmusic.domain

data class LocalPlaylist(
    val id: Long,
    val name: String,
    val createdAt: Long,
    val updatedAt: Long,
    val isPinned: Boolean = false,
    val lastPlayedAt: Long = 0,
    val folderSources: List<String> = emptyList(),
    val tracks: List<LocalTrack> = emptyList()
)

fun <T> resolvePlaylistTracks(orderedLocations: List<String>, visibleTracksByLocation: Map<String, T>): List<T> =
    orderedLocations.mapNotNull { visibleTracksByLocation[it] }

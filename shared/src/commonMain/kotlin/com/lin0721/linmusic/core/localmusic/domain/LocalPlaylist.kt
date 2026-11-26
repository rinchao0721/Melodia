package com.lin0721.linmusic.core.localmusic.domain

data class LocalPlaylist(
    val id: Long,
    val name: String,
    val createdAt: Long,
    val updatedAt: Long,
    val isPinned: Boolean = false,
    val lastPlayedAt: Long = 0,
    val systemKind: String? = null,
    val folderSources: List<String> = emptyList(),
    val tracks: List<LocalTrack> = emptyList()
) {
    val isSystem: Boolean get() = systemKind != null
    val isLiked: Boolean get() = systemKind == SYSTEM_KIND_LIKED

    companion object {
        const val SYSTEM_KIND_LIKED = "liked"
        const val LIKED_PLAYLIST_NAME = "我喜欢的音乐"
    }
}

fun <T> resolvePlaylistTracks(orderedLocations: List<String>, visibleTracksByLocation: Map<String, T>): List<T> =
    orderedLocations.mapNotNull { visibleTracksByLocation[it] }

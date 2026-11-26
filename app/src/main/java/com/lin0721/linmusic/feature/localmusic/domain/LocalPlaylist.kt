package com.lin0721.linmusic.feature.localmusic.domain

import com.lin0721.linmusic.core.localmusic.domain.LocalPlaylist as SharedLocalPlaylist

data class LocalPlaylist(
    val id: Long,
    val name: String,
    val updatedAt: Long,
    // 曲库里不存在或被扫描设置隐藏的曲目不出现在这里
    val tracks: List<LocalTrack>,
    val folderSources: List<String> = emptyList(),
    val isPinned: Boolean = false,
    val systemKind: String? = null
) {
    val isSystem: Boolean get() = systemKind != null
    val isLiked: Boolean get() = systemKind == SharedLocalPlaylist.SYSTEM_KIND_LIKED
}

fun <T> resolvePlaylistTracks(orderedUris: List<String>, visibleTracksByUri: Map<String, T>): List<T> =
    orderedUris.mapNotNull { visibleTracksByUri[it] }

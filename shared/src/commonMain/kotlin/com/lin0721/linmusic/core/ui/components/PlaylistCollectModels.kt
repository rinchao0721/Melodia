package com.lin0721.linmusic.core.ui.components

// "添加到歌单"收藏面板的共享状态模型，被 playlist、artist 等多个域的收藏歌单 UI 复用。

data class PlaylistCollectItem(
    val playlistId: Long,
    val playlistName: String,
    val coverUrl: String,
    val isInitiallyContains: Boolean,
    var isContains: Boolean
)

data class PlaylistCollectState(
    val songId: Long = -1L,
    val collectItems: List<PlaylistCollectItem> = emptyList(),
    val isLoading: Boolean = false
)

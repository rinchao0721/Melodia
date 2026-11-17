package com.lin0721.linmusic.core.ui.components

// "添加到歌单"收藏面板的共享状态模型，被 playlist、artist 等多个域的收藏歌单 UI 复用。

data class PlaylistCollectItem(
    val playlistId: Long,
    val playlistName: String,
    val coverUrl: String,
    val isInitiallyContains: Boolean,
    var isContains: Boolean,
    // 歌单内歌曲数，来自网易歌单 trackCount
    val trackCount: Int = 0
)

data class PlaylistCollectState(
    val songId: Long = -1L,
    val collectItems: List<PlaylistCollectItem> = emptyList(),
    val isLoading: Boolean = false
)

// 一键收藏到“已点赞的歌曲”成功事件
data class CollectPromptEvent(
    val songId: Long
)

package com.lin0721.linmusic.feature.recent.ui

import com.lin0721.linmusic.core.source.MusicPlatform
import com.lin0721.linmusic.feature.recent.domain.RecentAlbum
import com.lin0721.linmusic.feature.recent.domain.RecentPlaylist
import com.lin0721.linmusic.feature.recent.domain.RecentSong

// 最近播放页的分类切换项
enum class RecentTab(val label: String) {
    SONG("歌曲"),
    PLAYLIST("歌单"),
    ALBUM("专辑")
}

// 最近播放 UI 状态
sealed interface RecentPlayUiState {
    data object Loading : RecentPlayUiState

    // 三类记录一次性拉齐，切 Tab 不再触发网络请求
    data class Success(
        val songs: List<RecentSong>,
        val playlists: List<RecentPlaylist>,
        val albums: List<RecentAlbum>,
        val platform: MusicPlatform = MusicPlatform.NETEASE
    ) : RecentPlayUiState

    data class Error(val message: String) : RecentPlayUiState
}

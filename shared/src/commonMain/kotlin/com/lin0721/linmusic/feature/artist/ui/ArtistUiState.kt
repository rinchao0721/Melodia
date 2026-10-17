package com.lin0721.linmusic.feature.artist.ui

import com.lin0721.linmusic.core.model.ArtistAlbum
import com.lin0721.linmusic.core.model.ArtistDetailInfo
import com.lin0721.linmusic.core.model.ArtistInfo
import com.lin0721.linmusic.core.model.Track

// 歌手详情页 UI 状态
sealed interface ArtistUiState {
    data object Loading : ArtistUiState

    // 加载成功，携带歌手详情与聚合的热门歌曲/专辑/相似歌手，以及各分页区块的增量数据
    data class Success(
        val artist: ArtistDetailInfo,
        val isFollowed: Boolean,
        val fansCount: Long,
        val topSongs: List<Track>,
        val albums: List<ArtistAlbum>,
        val albumsHasMore: Boolean = false,
        val albumsLoadingMore: Boolean = false,
        val similarArtists: List<ArtistInfo>,
        // 「全部歌曲」子 Tab 数据，首次切入才会触发加载
        val allSongs: List<Track> = emptyList(),
        val allSongsHasMore: Boolean = false,
        val allSongsLoadingMore: Boolean = false,
        val allSongsLoaded: Boolean = false,
        val albumsLoadError: String? = null
    ) : ArtistUiState

    data class Error(val message: String) : ArtistUiState
}

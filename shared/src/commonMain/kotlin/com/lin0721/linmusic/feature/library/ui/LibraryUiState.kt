package com.lin0721.linmusic.feature.library.ui

// 音乐库 UI 状态
sealed interface LibraryUiState {
    data object Loading : LibraryUiState

    // 加载成功，携带全量条目、过滤后条目与各分类计数
    data class Success(
        val allItems: List<LibraryItem>,
        val filteredItems: List<LibraryItem>,
        val artistCount: Int,
        val playlistCount: Int,
        val albumCount: Int,
        val kugouPlaylistCount: Int = 0,
        val kugouArtistCount: Int = 0
    ) : LibraryUiState

    data class Error(val message: String) : LibraryUiState
}

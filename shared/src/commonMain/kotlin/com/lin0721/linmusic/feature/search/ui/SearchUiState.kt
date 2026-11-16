package com.lin0721.linmusic.feature.search.ui

import com.lin0721.linmusic.core.deeplink.LinkPreviewState
import com.lin0721.linmusic.core.deeplink.SongLinkTarget
import com.lin0721.linmusic.core.model.Track
import com.lin0721.linmusic.feature.search.domain.HotSearch
import com.lin0721.linmusic.feature.search.domain.PlaylistTag
import com.lin0721.linmusic.feature.search.domain.SearchResultItem
import com.lin0721.linmusic.feature.search.domain.SearchSuggestion

// 发现页（默认词/热搜/精品歌单）状态
sealed interface DiscoveryUiState {
    data object Loading : DiscoveryUiState
    data class Success(
        val defaultKeyword: String,
        val hotSearches: List<HotSearch>,
        val playlistTags: List<PlaylistTag>
    ) : DiscoveryUiState
    data class Error(val message: String) : DiscoveryUiState
}

// 单个 SearchType Tab 的结果与分页状态
sealed interface SearchResultsUiState {
    data object Idle : SearchResultsUiState
    data object Loading : SearchResultsUiState
    data class Success(
        val items: List<SearchResultItem>,
        val totalCount: Int,
        val hasMore: Boolean,
        val isLoadingMore: Boolean = false
    ) : SearchResultsUiState
    data object Empty : SearchResultsUiState
    data class Error(val message: String) : SearchResultsUiState
}

// 外部第三方音源平台的搜索结果状态
sealed interface ExternalSearchUiState {
    data object Idle : ExternalSearchUiState
    data object Loading : ExternalSearchUiState
    data class Success(
        val tracks: List<com.lin0721.linmusic.core.source.ExternalTrack>,
        val hasMore: Boolean = true,
        val isLoadingMore: Boolean = false
    ) : ExternalSearchUiState
    data object Empty : ExternalSearchUiState
    data class Error(val message: String) : ExternalSearchUiState
}

// 平台 Tab 上的状态角标：未加载不显示，已加载显示条数，仍有下一页时加 +
fun ExternalSearchUiState.tabBadge(): String? = when (this) {
    ExternalSearchUiState.Idle -> null
    ExternalSearchUiState.Loading -> "加载中"
    ExternalSearchUiState.Empty -> "0"
    is ExternalSearchUiState.Error -> "失败"
    is ExternalSearchUiState.Success -> if (hasMore) "${tracks.size}+" else "${tracks.size}"
}

enum class SearchMode { Discovery, Typing, Results }

// 搜索框中识别出的网易云链接直达卡片数据
data class SearchLinkHit(
    val query: String,
    val target: SongLinkTarget,
    val previewState: LinkPreviewState
)

// suggestionQuery 为联想所属关键词，防止旧联想套在新输入上；songHits 同理按 songHitsQuery 校验
data class SearchInputState(
    val query: String = "",
    val suggestions: List<SearchSuggestion> = emptyList(),
    val suggestionQuery: String = "",
    val songHits: List<Track> = emptyList(),
    val songHitsQuery: String = "",
    val linkHit: SearchLinkHit? = null
) {
    val currentSuggestions: List<SearchSuggestion>
        get() = if (suggestionQuery == query) suggestions else emptyList()

    val currentSongHits: List<Track>
        get() = if (songHitsQuery == query) songHits else emptyList()

    val currentLinkHit: SearchLinkHit?
        get() = linkHit?.takeIf { it.query == query }
}

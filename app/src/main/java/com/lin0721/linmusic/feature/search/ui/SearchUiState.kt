package com.lin0721.linmusic.feature.search.ui

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

enum class SearchMode { Discovery, Typing, Results }

// suggestionQuery 为联想所属关键词，防止旧联想套在新输入上
data class SearchInputState(
    val query: String = "",
    val suggestions: List<SearchSuggestion> = emptyList(),
    val suggestionQuery: String = ""
) {
    val currentSuggestions: List<SearchSuggestion>
        get() = if (suggestionQuery == query) suggestions else emptyList()
}

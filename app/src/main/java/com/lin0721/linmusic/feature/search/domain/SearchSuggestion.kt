package com.lin0721.linmusic.feature.search.domain

// 联想条目：实体类直接跳详情，关键词类进结果页
sealed interface SearchSuggestion {
    val text: String

    data class Keyword(override val text: String) : SearchSuggestion

    data class ArtistMatch(val id: Long, override val text: String, val avatarUrl: String) : SearchSuggestion

    data class AlbumMatch(val id: Long, override val text: String, val artistName: String) : SearchSuggestion
}

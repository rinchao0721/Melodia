package com.lin0721.linmusic.feature.search.data.dto

import kotlinx.serialization.Serializable

@Serializable
data class HighQualityTagsResponse(
    val code: Int = 0,
    val tags: List<HighQualityTag> = emptyList()
) {
    val isSuccess: Boolean get() = code == 200
}

@Serializable
data class HighQualityTag(
    val id: Long = 0,
    val name: String = "",
    val category: Int = -1,
    val hot: Boolean = false
)

@Serializable
data class HighQualityPlaylistRequest(
    val cat: String = "全部",
    val limit: Int = 50,
    val lasttime: Long = 0,
    val total: Boolean = true
)

@Serializable
data class HighQualityPlaylistResponse(
    val code: Int = 0,
    val playlists: List<HighQualityPlaylist> = emptyList(),
    val more: Boolean = false,
    val lasttime: Long = 0,
    val total: Int = 0
) {
    val isSuccess: Boolean get() = code == 200
}

@Serializable
data class HighQualityPlaylist(
    val id: Long = 0,
    val name: String = "",
    val coverImgUrl: String = "",
    val tags: List<String> = emptyList(),
    val playCount: Long = 0
)

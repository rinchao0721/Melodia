package com.lin0721.linmusic.feature.search.domain

// 热搜领域模型
data class HotSearch(
    val keyword: String,
    val score: Int,
    val description: String = "",
    val iconUrl: String? = null
)

// 歌单标签领域模型
data class PlaylistTag(
    val name: String,
    val coverUrl: String = ""
)

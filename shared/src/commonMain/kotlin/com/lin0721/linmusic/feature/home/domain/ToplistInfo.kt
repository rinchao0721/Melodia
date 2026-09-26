package com.lin0721.linmusic.feature.home.domain

// 排行榜领域模型（UI 层直接使用，与 DTO 解耦）
data class ToplistInfo(
    val id: Long,
    val name: String,
    val coverUrl: String,
    val updateDesc: String,
    // 前三首格式："歌名 - 歌手"
    val topSongs: List<String>
)

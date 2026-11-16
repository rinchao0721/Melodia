package com.lin0721.linmusic.feature.home.domain

import kotlinx.serialization.Serializable

// 排行榜领域模型（UI 层直接使用，与 DTO 解耦）
@Serializable
data class ToplistInfo(
    val id: Long,
    val name: String,
    val coverUrl: String,
    val updateDesc: String,
    // 前三首格式："歌名 - 歌手"
    val topSongs: List<String>
)

// 首页「热歌榜」入口对应的榜单：名字带「热」的优先，没有则取第一个
fun List<ToplistInfo>.pickHotlist(): ToplistInfo? =
    firstOrNull { it.name.contains("热") } ?: firstOrNull()

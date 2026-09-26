package com.lin0721.linmusic.feature.player.domain

// 歌曲详情/百科信息领域模型
data class SongWikiData(
    val style: String = "",
    val album: String = "",
    val language: String = "",
    val publishTime: String = "",
    val bpm: String = "",
    val creators: String = "",
    val creatorRoles: List<SongWikiCreatorRole> = emptyList(),
    val entertainment: String = "",
    val awards: List<String> = emptyList(),
    // 可能多于 awards 实际列出的条数
    val awardTotal: Int = 0,
    val musicMemory: SongMusicMemory? = null
)

// 制作人员单个角色（如"作词"）及其对应的艺人名单
data class SongWikiCreatorRole(
    val roleName: String,
    val artistNames: List<String>
)

// 回忆坐标，缺失项为空串或 0
data class SongMusicMemory(
    val firstListenDate: String,
    val season: String,
    val period: String,
    val playCount: Int,
    val playCountText: String
)

package com.lin0721.linmusic.feature.home.domain

// 首页一个货架：一行标题 + 一组同类卡片
data class HomeShelf(
    val blockCode: String,
    val title: String,
    // 榜单类货架在封面角标上显示名次，名次即卡片在货架内的次序
    val showRank: Boolean = false,
    val cards: List<HomeCard>
)

// 货架里的一张卡片。只保留 Melodia 当前有落地页可跳的三类资源
sealed interface HomeCard {
    val id: Long
    val title: String
    val coverUrl: String

    // 卡片标题下方那行描述，无数据时为空串
    val caption: String

    data class Playlist(
        override val id: Long,
        override val title: String,
        override val coverUrl: String,
        override val caption: String
    ) : HomeCard

    data class Album(
        override val id: Long,
        override val title: String,
        override val coverUrl: String,
        override val caption: String
    ) : HomeCard

    data class Song(
        override val id: Long,
        override val title: String,
        override val coverUrl: String,
        override val caption: String
    ) : HomeCard

    // 播客单集。id 是节目 id 仅供 key 用，播放走 songId
    data class Voice(
        override val id: Long,
        val songId: Long,
        override val title: String,
        override val coverUrl: String,
        override val caption: String
    ) : HomeCard
}

// 一页区块数据聚合后的结果
data class HomeBlockPage(
    val shelves: List<HomeShelf>,
    // 翻下一页用的游标，为 null 表示已到底
    val nextCursor: String?,
    val hasMore: Boolean
)

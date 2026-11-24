package com.lin0721.linmusic.feature.home.domain

import com.lin0721.linmusic.core.source.MusicPlatform

// 首页区块类型：最近播放网格、日推大卡、货架
sealed interface HomeSection {
    val key: String

    data class Quick(
        val items: List<HomeSectionCard>,
        override val key: String = "quick"
    ) : HomeSection

    data class Daily(
        val items: List<HomeSectionCard>,
        val platform: MusicPlatform,
        override val key: String = "daily"
    ) : HomeSection

    data class Shelf(
        val blockCode: String,
        val title: String,
        val showRank: Boolean = false,
        val cards: List<HomeSectionCard>,
        override val key: String = "shelf_${blockCode}_$title"
    ) : HomeSection
}

// 首页统一卡片类型
enum class HomeCardKind {
    PLAYLIST,
    ALBUM,
    SONG,
    VOICE,
    RANK
}

// 平台无关的首页卡片模型
data class HomeSectionCard(
    val platform: MusicPlatform,
    val entityId: String,
    val kind: HomeCardKind,
    val title: String,
    val coverUrl: String,
    val caption: String = "",
    // 网易播客单集播放用 songId，entityId 仍存节目 id 供 key 使用
    val voiceSongId: Long? = null
)

// 卡片点击后的平台无关打开目标，供界面层导航
sealed interface HomeCardTarget {
    data class PlaylistEntity(
        val entityId: String,
        val title: String,
        val isAlbum: Boolean = false
    ) : HomeCardTarget

    data object Toplists : HomeCardTarget
    data object Cloud : HomeCardTarget
    data object None : HomeCardTarget
}

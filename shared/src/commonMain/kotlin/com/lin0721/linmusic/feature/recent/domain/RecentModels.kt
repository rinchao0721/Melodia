package com.lin0721.linmusic.feature.recent.domain

import com.lin0721.linmusic.core.model.Track
import com.lin0721.linmusic.core.platform.UnifiedTrack
import kotlinx.serialization.Serializable

// 最近播放的三类记录。playTime 用于按天分区块，playedAtText 是区块内的时钟时间（HH:mm）
data class RecentSong(
    val track: Track,
    val playTime: Long,
    val playedAtText: String,
    // 酷狗等非网易歌曲的统一模型，播放时据此入队
    val unified: UnifiedTrack? = null
)

@Serializable
data class RecentPlaylist(
    val id: Long,
    val name: String,
    val coverUrl: String,
    val creatorName: String,
    val playTime: Long,
    val playedAtText: String,
    // 非网易平台的规范实体 id，空表示网易歌单（用 id 跳转）
    val entityId: String? = null
)

data class RecentAlbum(
    val id: Long,
    val name: String,
    val coverUrl: String,
    val artistName: String,
    val playTime: Long,
    val playedAtText: String,
    val entityId: String? = null
)

package com.lin0721.linmusic.feature.recent.domain

import com.lin0721.linmusic.core.model.Track

// 最近播放的三类记录。playTime 用于按天分区块，playedAtText 是区块内的时钟时间（HH:mm）
data class RecentSong(
    val track: Track,
    val playTime: Long,
    val playedAtText: String
)

data class RecentPlaylist(
    val id: Long,
    val name: String,
    val coverUrl: String,
    val creatorName: String,
    val playTime: Long,
    val playedAtText: String
)

data class RecentAlbum(
    val id: Long,
    val name: String,
    val coverUrl: String,
    val artistName: String,
    val playTime: Long,
    val playedAtText: String
)

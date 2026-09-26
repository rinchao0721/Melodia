package com.lin0721.linmusic.feature.podcast.domain

// 电台分类
data class PodcastCategory(
    val id: Long,
    val name: String
)

// 电台：一个系列，本身不可播放，须进详情页听其中的节目
data class PodcastRadio(
    val id: Long,
    val name: String,
    val picUrl: String,
    val programCount: Int,
    val subCount: Long,
    val djName: String
)

// 节目：一期，可直接播放
data class PodcastProgram(
    val id: Long,
    // 播放用的歌曲 id，与节目自身 id 不是一回事
    val songId: Long,
    val name: String,
    val coverUrl: String,
    val durationMs: Long,
    val createTimeMs: Long,
    val listenerCount: Long,
    val serialNum: Int,
    val radioId: Long,
    val radioName: String,
    val djName: String
)

// 电台详情页头部
data class PodcastRadioDetail(
    val id: Long,
    val name: String,
    val picUrl: String,
    val desc: String,
    val category: String,
    val programCount: Int,
    val subCount: Long,
    val djName: String,
    val djAvatarUrl: String,
    val subscribed: Boolean
)

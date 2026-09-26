package com.lin0721.linmusic.feature.newworks.domain

data class NewWorksMv(
    val id: Long,
    val name: String,
    val coverUrl: String,
    val durationMs: Long,
    val playCount: Long,
    val artistName: String
)

data class NewWorksRelease(
    // 单曲：歌曲 id，点击直接播放；专辑：albumId，点击跳专辑详情页
    val id: Long,
    val title: String,
    val coverUrl: String,
    val artistName: String,
    val isAlbum: Boolean,
    // 单曲固定为 1，专辑取服务端下发的 albumSongCount
    val trackCount: Int
)

data class NewWorksReleasePage(
    val items: List<NewWorksRelease>,
    val hasMore: Boolean,
    val nextCursor: Long
)

package com.lin0721.linmusic.core.songlike

import kotlinx.serialization.Serializable
import retrofit2.http.Body
import retrofit2.http.POST

// 歌曲红心相关的网易云 Retrofit 接口定义。
interface SongLikeApi {

    // 喜欢/取消喜欢单曲（需登录）
    @POST("/eapi/song/like")
    suspend fun likeSong(
        @Body body: LikeSongRequest
    ): LikeSongResponse

    // 获取当前用户已红心的歌曲 ID 列表（需登录）
    @POST("/eapi/song/like/get")
    suspend fun getLikedSongIds(
        @Body body: LikeSongListRequest
    ): LikeSongListResponse
}

@Serializable
data class LikeSongRequest(
    val trackId: Long,
    val like: Boolean = true
)

@Serializable
data class LikeSongResponse(
    val code: Int = 0,
    val playlistId: Long = 0
) {
    val isSuccess: Boolean get() = code == 200
}

@Serializable
data class LikeSongListRequest(
    val uid: Long
)

@Serializable
data class LikeSongListResponse(
    val code: Int = 0,
    val ids: List<Long> = emptyList(),
    val checkPoint: Long = 0
) {
    val isSuccess: Boolean get() = code == 200
}

package com.lin0721.linmusic.core.userplaylist

import com.lin0721.linmusic.core.model.PlaylistCreator
import kotlinx.serialization.Serializable
import retrofit2.http.Body
import retrofit2.http.POST

// 当前用户歌单列表的网易云 Retrofit 接口定义。
interface UserPlaylistApi {

    // 获取用户歌单（需登录）
    @POST("/eapi/user/playlist")
    suspend fun getUserPlaylists(
        @Body body: UserPlaylistRequest
    ): UserPlaylistResponse

    // 更新用户歌单顺序（需登录）
    @POST("/weapi/playlist/order/update")
    suspend fun updatePlaylistOrder(
        @Body body: PlaylistOrderUpdateRequest
    ): PlaylistOrderUpdateResponse

    // 置顶自建歌单（需登录）
    @POST("/weapi/user/playlist/top")
    suspend fun topPlaylist(
        @Body body: PlaylistTopRequest
    ): PlaylistTopResponse

    // 取消置顶自建歌单（需登录）
    @POST("/weapi/user/playlist/top/cancel")
    suspend fun cancelTopPlaylist(
        @Body body: PlaylistTopRequest
    ): PlaylistTopResponse
}

@Serializable
data class PlaylistTopRequest(
    val id: Long
)

@Serializable
data class PlaylistTopResponse(
    val code: Int = 0,
    val message: String? = null
) {
    val isSuccess: Boolean get() = code == 200
}

@Serializable
data class PlaylistOrderUpdateRequest(
    val ids: String
)

@Serializable
data class PlaylistOrderUpdateResponse(
    val code: Int = 0,
    val message: String? = null
) {
    val isSuccess: Boolean get() = code == 200
}

@Serializable
data class UserPlaylistRequest(
    val uid: Long,
    val limit: Int = 1000,
    val offset: Int = 0,
    val includeTop: Boolean = true
)

@Serializable
data class UserPlaylistResponse(
    val code: Int = 0,
    val playlist: List<UserPlaylist> = emptyList()
) {
    val isSuccess: Boolean get() = code == 200
}

@Serializable
data class UserPlaylist(
    val id: Long = 0,
    val name: String = "",
    val coverImgUrl: String = "",
    val playCount: Long = 0,
    val trackCount: Int = 0,
    val userId: Long = 0,
    val creator: PlaylistCreator? = null,
    val updateTime: Long = 0,
    val top: Boolean = false,
    // 5 = 我喜欢的音乐；服务端对部分接口（如心动模式）只接受这类歌单
    val specialType: Int = 0
)

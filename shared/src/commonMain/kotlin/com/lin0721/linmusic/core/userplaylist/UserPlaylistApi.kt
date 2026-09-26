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
}

@Serializable
data class UserPlaylistRequest(
    val uid: Long,
    val limit: Int = 1000,
    val offset: Int = 0
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
    val updateTime: Long = 0
)

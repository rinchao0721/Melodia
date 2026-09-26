package com.lin0721.linmusic.feature.profile.data

import com.lin0721.linmusic.core.model.EmptyBody
import kotlinx.serialization.Serializable
import retrofit2.http.Body
import retrofit2.http.POST
import retrofit2.http.Path

interface ProfileApi {

    // 获取用户详情 (EAPI)
    @POST("/eapi/w/v1/user/detail/{uid}")
    suspend fun getUserDetailEapi(
        @Path("uid") uid: Long,
        @Body body: EmptyBody = EmptyBody()
    ): ProfileUserDetailResponse

    // 获取用户详情 (WEAPI)
    @POST("/weapi/v1/user/detail/{uid}")
    suspend fun getUserDetailWeapi(
        @Path("uid") uid: Long,
        @Body body: EmptyBody = EmptyBody()
    ): ProfileUserDetailResponse

    // 关注用户
    @POST("/weapi/user/follow/{id}")
    suspend fun followUser(
        @Path("id") uid: Long,
        @Body body: EmptyBody = EmptyBody()
    ): SimpleActionResponse

    // 取消关注用户
    @POST("/weapi/user/delfollow/{id}")
    suspend fun unfollowUser(
        @Path("id") uid: Long,
        @Body body: EmptyBody = EmptyBody()
    ): SimpleActionResponse

    // 获取用户歌单
    @POST("/weapi/user/playlist")
    suspend fun getUserPlaylists(
        @Body body: ProfileUserPlaylistsRequest
    ): ProfileUserPlaylistsResponse

    // 获取用户关注列表
    @POST("/weapi/user/getfollows/{uid}")
    suspend fun getUserFollows(
        @Path("uid") uid: Long,
        @Body body: ProfileUserFollowsRequest
    ): ProfileFollowListResponse

    // 获取用户粉丝列表
    @POST("/weapi/user/getfolloweds/{uid}")
    suspend fun getUserFolloweds(
        @Path("uid") uid: Long,
        @Body body: ProfileUserFollowedsRequest
    ): ProfileFollowListResponse

    // 获取用户动态列表
    @POST("/weapi/event/get/{uid}")
    suspend fun getUserEvents(
        @Path("uid") uid: Long,
        @Body body: ProfileUserEventsRequest
    ): ProfileEventResponse

    // 获取用户听歌排行
    @POST("/weapi/v1/play/record")
    suspend fun getUserListeningRank(
        @Body body: ProfileListeningRankRequest
    ): ProfileListeningRankResponse
}

// ======================= 用户详情 DTO =======================

@Serializable
data class ProfileUserDetailResponse(
    val level: Int = 0,
    val listenSongs: Int = 0,
    val profile: ProfileUserBasic = ProfileUserBasic()
) {
    val isSuccess: Boolean
        get() = profile.userId > 0L
}

@Serializable
data class ProfileUserBasic(
    val userId: Long = 0L,
    val nickname: String = "",
    val avatarUrl: String = "",
    val signature: String = "",
    val gender: Int = 0,
    val follows: Int = 0,
    val followeds: Int = 0,
    val playlistCount: Int = 0,
    val followed: Boolean = false // 是否已关注
)

// ======================= 简单动作 DTO =======================

@Serializable
data class SimpleActionResponse(
    val code: Int
) {
    val isSuccess: Boolean
        get() = code == 200
}

// ======================= 歌单列表 DTO =======================

@Serializable
data class ProfileUserPlaylistsRequest(
    val uid: Long,
    val limit: Int = 30,
    val offset: Int = 0,
    val includeVideo: Boolean = true
)

@Serializable
data class ProfileUserPlaylistsResponse(
    val code: Int,
    val playlist: List<ProfilePlaylist> = emptyList(),
    val more: Boolean = false
) {
    val isSuccess: Boolean
        get() = code == 200
}

@Serializable
data class ProfilePlaylist(
    val id: Long = 0L,
    val name: String = "",
    val coverImgUrl: String = "",
    val playCount: Long = 0L,
    val trackCount: Int = 0,
    val creator: ProfileUserBasic? = null
)

// ======================= 关注/粉丝列表 DTO =======================

@Serializable
data class ProfileUserFollowsRequest(
    val offset: Int = 0,
    val limit: Int = 30,
    val order: Boolean = true
)

@Serializable
data class ProfileUserFollowedsRequest(
    val userId: Long,
    val time: String = "0",
    val limit: Int = 20,
    val offset: Int = 0,
    val getcounts: String = "true"
)

@Serializable
data class ProfileFollowListResponse(
    val code: Int,
    val follow: List<ProfileUserBasic> = emptyList(),
    val followeds: List<ProfileUserBasic> = emptyList(), // 根据是关注列表还是粉丝列表，字段可能不同
    val more: Boolean = false
) {
    val isSuccess: Boolean
        get() = code == 200
}

// ======================= 动态列表 DTO =======================

@Serializable
data class ProfileUserEventsRequest(
    val getcounts: Boolean = true,
    val time: Long = -1L,
    val limit: Int = 30,
    val total: Boolean = false,
    val fromRN: String = "true"
)

@Serializable
data class ProfileEventResponse(
    val code: Int,
    val events: List<ProfileEvent> = emptyList(),
    val lasttime: Long = -1L,
    val more: Boolean = false
) {
    val isSuccess: Boolean
        get() = code == 200
}

@Serializable
data class ProfileEvent(
    val id: Long = 0L,
    // 动态内嵌套了 json string，里面包含具体内容
    val json: String = "",
    val showTime: Long = 0L,
    val user: ProfileUserBasic? = null
)

// ======================= 听歌排行 DTO =======================

@Serializable
data class ProfileListeningRankRequest(
    val uid: Long,
    val type: Int = 0
)

@Serializable
data class ProfileListeningRankResponse(
    val code: Int,
    val weekData: List<ProfileListenRecord> = emptyList(),
    val allData: List<ProfileListenRecord> = emptyList()
) {
    val isSuccess: Boolean
        get() = code == 200
}

@Serializable
data class ProfileListenRecord(
    val playCount: Int = 0,
    val score: Int = 0,
    val song: ProfileSong? = null
)

@Serializable
data class ProfileSong(
    val id: Long = 0L,
    val name: String = "",
    val ar: List<ProfileArtist> = emptyList(),
    val al: ProfileAlbum? = null
)

@Serializable
data class ProfileArtist(
    val id: Long = 0L,
    val name: String = ""
)

@Serializable
data class ProfileAlbum(
    val id: Long = 0L,
    val name: String = "",
    val picUrl: String = ""
)

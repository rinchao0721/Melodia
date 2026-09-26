package com.lin0721.linmusic.feature.library.data

import com.lin0721.linmusic.core.model.Artist
import com.lin0721.linmusic.core.model.EmptyBody
import com.lin0721.linmusic.core.model.Track
import kotlinx.serialization.Serializable
import retrofit2.http.Body
import retrofit2.http.POST

// 音乐库（收藏专辑/听歌排行/收藏数）相关的网易云 Retrofit 接口定义。
interface LibraryApi {

    @POST("/weapi/v1/play/record")
    suspend fun getUserRecord(
        @Body body: UserRecordRequest
    ): UserRecordResponse

    // 获取收藏专辑
    @POST("/eapi/album/sublist")
    suspend fun getAlbumSublist(
        @Body body: AlbumSublistRequest = AlbumSublistRequest()
    ): AlbumSublistResponse

    // 获取用户各分类收藏数
    @POST("/eapi/user/subcount")
    suspend fun getUserSubcount(
        @Body body: EmptyBody = EmptyBody()
    ): UserSubcountResponse
}

// ======================= 听歌排行 DTOs =======================

@Serializable
data class UserRecordRequest(
    val uid: Long,
    val type: Int
)

@Serializable
data class UserRecordResponse(
    val code: Int = 0,
    val weekData: List<UserRecordItem>? = null,
    val allData: List<UserRecordItem>? = null
) {
    val isSuccess: Boolean get() = code == 200
}

@Serializable
data class UserRecordItem(
    val playCount: Int = 0,
    val score: Int = 0,
    val song: Track
)

// ======================= 音乐库 (Library) DTOs =======================

@Serializable
data class AlbumSublistRequest(
    val limit: Int = 1000,
    val offset: Int = 0,
    val total: Boolean = true
)

@Serializable
data class AlbumSublistResponse(
    val code: Int = 0,
    val data: List<AlbumSubItem> = emptyList()
) {
    val isSuccess: Boolean get() = code == 200
}

@Serializable
data class AlbumSubItem(
    val id: Long = 0,
    val name: String = "",
    val picUrl: String = "",
    val artists: List<Artist> = emptyList(),
    val size: Int = 0,
    val subTime: Long = 0
)

@Serializable
data class UserSubcountResponse(
    val code: Int = 0,
    val artistCount: Int = 0,
    val playlistCount: Int = 0,
    val mvCount: Int = 0,
    val createPlaylistCount: Int = 0,
    val subPlaylistCount: Int = 0,
    val albumCount: Int = 0
) {
    val isSuccess: Boolean get() = code == 200
}

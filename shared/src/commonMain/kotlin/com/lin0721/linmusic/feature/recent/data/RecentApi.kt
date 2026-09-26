package com.lin0721.linmusic.feature.recent.data

import com.lin0721.linmusic.core.model.Artist
import com.lin0721.linmusic.core.model.PlaylistCreator
import com.lin0721.linmusic.core.model.Track
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import retrofit2.http.Body
import retrofit2.http.POST

// 最近播放（歌曲/歌单/专辑）相关的网易云 Retrofit 接口定义。
interface RecentApi {

    // 获取最近播放歌曲（需登录）
    @POST("/weapi/play-record/song/list")
    suspend fun getRecentSongs(
        @Body body: RecentRecordRequest = RecentRecordRequest()
    ): RecentSongResponse

    // 获取最近播放歌单（需登录）
    @POST("/eapi/play-record/playlist/list")
    suspend fun getRecentPlaylists(
        @Body body: RecentRecordRequest = RecentRecordRequest()
    ): RecentPlaylistResponse

    // 获取最近播放专辑（需登录）
    @POST("/weapi/play-record/album/list")
    suspend fun getRecentAlbums(
        @Body body: RecentRecordRequest = RecentRecordRequest()
    ): RecentAlbumResponse
}

// ======================= 请求体 =======================

// limit 服务端不生效，三个接口均一次性下发全量记录，截断放在 Repository 层
@Serializable
data class RecentRecordRequest(
    val limit: Int = 100
)

// ======================= 最近播放歌曲 =======================

@Serializable
data class RecentSongResponse(
    val code: Int = 0,
    val data: RecentSongData? = null
) {
    val isSuccess: Boolean get() = code == 200
}

@Serializable
data class RecentSongData(
    val list: List<RecentSongItem> = emptyList()
)

@Serializable
data class RecentSongItem(
    val playTime: Long = 0,
    val data: Track = Track()
)

// ======================= 最近播放歌单 =======================

@Serializable
data class RecentPlaylistResponse(
    val code: Int = 0,
    val data: RecentPlaylistData? = null
) {
    val isSuccess: Boolean get() = code == 200
}

@Serializable
data class RecentPlaylistData(
    val list: List<RecentPlaylistItem> = emptyList()
)

@Serializable
data class RecentPlaylistItem(
    val playTime: Long = 0,
    val data: RecentPlaylistInfo = RecentPlaylistInfo()
)

@Serializable
data class RecentPlaylistInfo(
    val id: Long = 0,
    val name: String = "",
    @SerialName("coverImgUrl")
    val picUrl: String = "",
    val creator: PlaylistCreator? = null
)

// ======================= 最近播放专辑 =======================

@Serializable
data class RecentAlbumResponse(
    val code: Int = 0,
    val data: RecentAlbumData? = null
) {
    val isSuccess: Boolean get() = code == 200
}

@Serializable
data class RecentAlbumData(
    val list: List<RecentAlbumItem> = emptyList()
)

@Serializable
data class RecentAlbumItem(
    val playTime: Long = 0,
    val data: RecentAlbumInfo = RecentAlbumInfo()
)

// 服务端在每张专辑里内联了完整曲目表（单次响应可达 600KB），songs 字段刻意不声明
@Serializable
data class RecentAlbumInfo(
    val id: Long = 0,
    val name: String = "",
    val picUrl: String = "",
    val artist: Artist? = null
)

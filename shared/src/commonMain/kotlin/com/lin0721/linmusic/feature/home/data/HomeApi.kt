package com.lin0721.linmusic.feature.home.data

import com.lin0721.linmusic.core.model.Album
import com.lin0721.linmusic.core.model.Artist
import com.lin0721.linmusic.core.model.EmptyBody
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import retrofit2.http.Body
import retrofit2.http.POST

// 首页/发现页相关的网易云 Retrofit 接口定义。
interface HomeApi {

    // 首页区块页：服务端编排好的货架序列，支持 cursor 翻页。
    // 未登录也返回内容，只是不带个性化。必须走 eapi —— weapi 版本不下发 cursor，翻不了页。
    @POST("/eapi/homepage/block/page")
    suspend fun getHomeBlockPage(
        @Body body: HomeBlockPageRequest = HomeBlockPageRequest()
    ): HomeBlockPageResponse

    // 获取个性化推荐歌单（公开接口，无需登录）
    @POST("/eapi/personalized/playlist")
    suspend fun getPersonalizedPlaylists(
        @Body body: PersonalizedRequest = PersonalizedRequest()
    ): PersonalizedResponse

    // 获取每日推荐歌曲（需登录，每天 06:00 更新）
    @POST("/eapi/v3/discovery/recommend/songs")
    suspend fun getDailyRecommendSongs(
        @Body body: EmptyBody = EmptyBody()
    ): DailyRecommendSongsResponse

    // 获取可用的历史日推日期列表（黑胶 VIP 功能）
    @POST("/weapi/discovery/recommend/songs/history/recent")
    suspend fun getHistoryRecommendDates(
        @Body body: EmptyBody = EmptyBody()
    ): HistoryDatesResponse

    // 获取指定日期的历史日推详情
    @POST("/weapi/discovery/recommend/songs/history/detail")
    suspend fun getHistoryRecommendDetail(
        @Body body: HistoryDetailRequest
    ): HistoryDetailResponse

    // 获取排行榜详情（含各榜单前三首歌曲）
    @POST("/eapi/toplist/detail")
    suspend fun getToplistDetail(
        @Body body: EmptyBody = EmptyBody()
    ): ToplistDetailResponse
}

// ==================== 个性化推荐 ====================

@Serializable
data class PersonalizedRequest(
    val limit: Int = 30
)

// 个性化推荐响应
@Serializable
data class PersonalizedResponse(
    val code: Int = 0,
    val result: List<PersonalizedPlaylist> = emptyList(),
) {
    val isSuccess: Boolean get() = code == 200
}

// 个性化推荐歌单数据
@Serializable
data class PersonalizedData(
    // 推荐歌单列表
    val playlists: List<PersonalizedPlaylist> = emptyList(),
)

@Serializable
data class PersonalizedPlaylist(
    val id: Long = 0,
    val name: String = "",
    // 歌单封面
    val picUrl: String = "",
)

// ======================= 每日推荐歌曲模型 =======================

@Serializable
data class DailyRecommendSongsResponse(
    val code: Int = 0,
    val data: DailyRecommendData? = null
) {
    val isSuccess: Boolean get() = code == 200
}

@Serializable
data class DailyRecommendData(
    val dailySongs: List<DailySong> = emptyList()
)

@Serializable
data class DailySong(
    val id: Long = 0,
    val name: String = "",
    val ar: List<Artist> = emptyList(),
    val al: Album = Album(),
    val fee: Int = 0,
    // 推荐理由（例如："根据你喜欢的 xxx 推荐"）
    val reason: String? = null
)

// ======================= 历史日推模型 =======================

@Serializable
data class HistoryDatesResponse(
    val code: Int = 0,
    val data: HistoryDatesData? = null
)

@Serializable
data class HistoryDatesData(
    @SerialName("dates")
    val list: List<String> = emptyList()
)

@Serializable
data class HistoryDetailRequest(
    val date: String
)

@Serializable
data class HistoryDetailResponse(
    val code: Int = 0,
    val data: HistoryDetailData? = null
)

@Serializable
data class HistoryDetailData(
    @SerialName("songs")
    val dailySongs: List<DailySong> = emptyList()
)

// ======================= 排行榜 DTO =======================

@Serializable
data class ToplistDetailResponse(
    val code: Int = 0,
    val list: List<ToplistDto> = emptyList()
)

@Serializable
data class ToplistDto(
    val id: Long = 0,
    val name: String = "",
    val coverImgUrl: String = "",
    val updateFrequency: String = "",
    // 部分榜单无歌曲预览数据
    val tracks: List<ToplistTrackDto>? = null
)

@Serializable
data class ToplistTrackDto(
    val first: String = "",  // 歌名
    val second: String = "" // 歌手
)

package com.lin0721.linmusic.feature.listendata.data

import com.lin0721.linmusic.core.model.EmptyBody
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonNames
import retrofit2.http.Body
import retrofit2.http.POST

// 听歌数据（听歌足迹）相关的网易云 Retrofit 接口定义。参考实现走裸 api，真机验证 weapi 前缀可用。
interface ListenDataApi {

    // 累计收听时长（需登录）
    @POST("/weapi/content/activity/listen/data/total")
    suspend fun getTotalDuration(
        @Body body: EmptyBody = EmptyBody()
    ): ListenTotalResponse

    // 今日播放排行（需登录）
    @POST("/weapi/content/activity/listen/data/today/song/play/rank")
    suspend fun getTodayRank(
        @Body body: EmptyBody = EmptyBody()
    ): TodayRankResponse

    // 周/月播放排行 Top20（需登录）
    @POST("/weapi/content/activity/listen/data/song/play/rank")
    suspend fun getSongPlayRank(
        @Body body: ListenPeriodRequest
    ): SongPlayRankResponse

    // 周/月收听报告（需登录）。type=year 服务端返回空块，年度数据走 getYearReport
    @POST("/weapi/content/activity/listen/data/report")
    suspend fun getReport(
        @Body body: ListenPeriodRequest
    ): ListenReportResponse

    // 历年收听汇总（需登录）
    @POST("/weapi/content/activity/listen/data/year/report")
    suspend fun getYearReport(
        @Body body: EmptyBody = EmptyBody()
    ): YearReportResponse
}

// ======================= 请求体 =======================

// endTime 不传即当前周期
@Serializable
data class ListenPeriodRequest(
    val type: String
)

// ======================= 累计时长 =======================

@Serializable
data class ListenTotalResponse(
    val code: Int = 0,
    val data: ListenTotalData? = null
) {
    val isSuccess: Boolean get() = code == 200
}

@Serializable
data class ListenTotalData(
    // 秒
    val totalDuration: Long = 0
)

// ======================= 今日排行 =======================

@Serializable
data class TodayRankResponse(
    val code: Int = 0,
    val data: TodayRankData? = null
) {
    val isSuccess: Boolean get() = code == 200
}

// 当天无收听时服务端返回空对象而非空数组
@Serializable
data class TodayRankData(
    val songDTOs: List<TodaySongDto> = emptyList()
)

@Serializable
data class TodaySongDto(
    val songId: Long = 0,
    val songName: String = "",
    val picUrl: String = "",
    val artists: List<ListenArtistDto> = emptyList()
)

// ======================= 周/月排行 =======================

@Serializable
data class SongPlayRankResponse(
    val code: Int = 0,
    val data: SongPlayRankData? = null
) {
    val isSuccess: Boolean get() = code == 200
}

@Serializable
data class SongPlayRankData(
    val songCount: Int = 0,
    val songItems: List<RankSongDto> = emptyList()
)

@Serializable
data class RankSongDto(
    val songId: Long = 0,
    val songName: String = "",
    val albumName: String = "",
    val picUrl: String = "",
    val playCount: Int = 0,
    val artists: List<ListenArtistDto> = emptyList()
)

@Serializable
data class ListenArtistDto(
    val artistId: Long = 0,
    val artistName: String = ""
)

// ======================= 历年汇总 =======================

@Serializable
data class YearReportResponse(
    val code: Int = 0,
    val data: YearReportData? = null
) {
    val isSuccess: Boolean get() = code == 200
}

@Serializable
data class YearReportData(
    val displayYear: Int = 0,
    val yearItems: List<YearItemDto> = emptyList()
)

@Serializable
data class YearItemDto(
    val year: Int = 0,
    val playNum: Int = 0,
    // 秒
    val playDuration: Long = 0
)

// ======================= 收听报告 =======================

@Serializable
data class ListenReportResponse(
    val code: Int = 0,
    val data: ListenReportData? = null
) {
    val isSuccess: Boolean get() = code == 200
}

// 服务端按块下发，各块可能整体为 null（如本账号的 topEmotionBlock）。
// 好友两块的字段名在周报与月报里不一致，且 KeyWord 大小写也不同，用 JsonNames 合并。
@OptIn(ExperimentalSerializationApi::class)
@Serializable
data class ListenReportData(
    val listenTimeBlock: ListenTimeBlockDto? = null,
    val listenTimeDistributionBlock: ListenDistributionBlockDto? = null,
    val wallpaperBlock: WallpaperBlockDto? = null,
    val topSongBlock: TopSongBlockDto? = null,
    val topArtistBlock: TopArtistBlockDto? = null,
    val topStyleBlock: TopStyleBlockDto? = null,
    val topAgeBlock: TopAgeBlockDto? = null,
    val topLanguageBlock: TopLanguageBlockDto? = null,
    val vipBlock: VipBlockDto? = null,
    val djListenDataBlock: DjListenBlockDto? = null,
    @JsonNames("friendsListenWeekBlock", "friendsListenMonthBlock")
    val friendsListenBlock: FriendsListenBlockDto? = null,
    @JsonNames("friendsKeywordWeekBlock", "friendsKeyWordMonthBlock")
    val friendsKeywordBlock: FriendsKeywordBlockDto? = null
)

@Serializable
data class ListenTimeBlockDto(
    // 分钟，不含播客
    val playDuration: Int = 0,
    // 运营文案，含网易云社区口径的自称，展示前需清洗
    val playDurationText: String = "",
    val circleTimePeriodDurations: List<TimePeriodDto> = emptyList()
)

@Serializable
data class TimePeriodDto(
    // early_morning / morning / noon / afternoon / night / deep_night
    val period: String = "",
    val duration: Int = 0
)

@Serializable
data class ListenDistributionBlockDto(
    val listenDays: Int = 0,
    val achievementTitle: AchievementTitleDto? = null,
    val durationDetails: List<DayDurationDto> = emptyList()
)

@Serializable
data class AchievementTitleDto(
    val mainTitle: String = "",
    val subTitle: String = ""
)

@Serializable
data class DayDurationDto(
    // yyyy-MM-dd
    val period: String = "",
    val duration: Int = 0
)

// 本周期听过的歌曲封面集合，服务端固定给 20 张
@Serializable
data class WallpaperBlockDto(
    val songCount: Int = 0,
    val picUrls: List<String> = emptyList()
)

// 服务端编排好的亮点：最多收听 / 本周首个收藏 / 年代最远，field 与 text 都是现成文案
@Serializable
data class TopSongBlockDto(
    val sections: List<TopSongSectionDto> = emptyList()
)

@Serializable
data class TopSongSectionDto(
    val songId: Long = 0,
    val songName: String = "",
    val picUrl: String = "",
    val field: String = "",
    val text: String = ""
)

@Serializable
data class TopArtistBlockDto(
    val sections: List<TopArtistSectionDto> = emptyList()
)

@Serializable
data class TopArtistSectionDto(
    val artistId: Long = 0,
    val artistName: String = "",
    val picUrl: String = "",
    // 形如「26次」
    val text: String = ""
)

@Serializable
data class TopStyleBlockDto(
    val genreName: String = ""
)

@Serializable
data class TopAgeBlockDto(
    val sections: List<TopAgeSectionDto> = emptyList()
)

@Serializable
data class TopAgeSectionDto(
    // 形如「2000」
    val age: String = "",
    val playSongNum: Int = 0
)

@Serializable
data class TopLanguageBlockDto(
    val sections: List<TopLanguageSectionDto> = emptyList()
)

@Serializable
data class TopLanguageSectionDto(
    val language: String = "",
    val playSongNum: Int = 0
)

@Serializable
data class VipBlockDto(
    val sections: List<VipSectionDto> = emptyList()
)

@Serializable
data class VipSectionDto(
    // vipSongCount / 累计享受 / 共计省下
    val type: String = "",
    val field: String = "",
    val mainText: String = "",
    val subText: String = ""
)

@Serializable
data class DjListenBlockDto(
    // 分钟
    val podcastPlayDuration: Int = 0,
    val podcastEpNum: Int = 0,
    val audiobookPlayDuration: Int = 0,
    val audiobookEpNum: Int = 0
)

@Serializable
data class FriendsListenBlockDto(
    val items: List<FriendListenItemDto> = emptyList()
)

@Serializable
data class FriendListenItemDto(
    val userId: Long = 0,
    val username: String = "",
    val userAvatar: String = "",
    val songId: Long = 0,
    val songName: String = "",
    val songPicUrl: String = "",
    val playCount: Int = 0
)

@Serializable
data class FriendsKeywordBlockDto(
    val items: List<FriendKeywordItemDto> = emptyList()
)

@Serializable
data class FriendKeywordItemDto(
    val title: String = "",
    val subTitle: String = ""
)

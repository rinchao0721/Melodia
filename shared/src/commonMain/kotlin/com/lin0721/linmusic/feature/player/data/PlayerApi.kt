package com.lin0721.linmusic.feature.player.data

import com.lin0721.linmusic.core.model.Track
import kotlinx.serialization.Serializable
import retrofit2.http.Body
import retrofit2.http.POST

// 播放器详情页（歌曲详情/百科/创作者）的网易云 Retrofit 接口定义。
interface PlayerApi {

    @POST("/eapi/v3/song/detail")
    suspend fun getSongDetail(
        @Body body: SongDetailRequest
    ): SongDetailResponse

    // 获取歌曲百科简要信息
    @POST("/weapi/song/play/about/block/page")
    suspend fun getSongWikiSummary(
        @Body body: SongWikiSummaryRequest
    ): SongWikiSummaryResponse

    // 获取歌曲创作者信息
    @POST("/weapi/song/creators")
    suspend fun getSongCreators(
        @Body body: SongCreatorsRequest
    ): SongCreatorsResponse

    // 获取副歌时间段（公开接口）
    @POST("/eapi/song/chorus")
    suspend fun getSongChorus(
        @Body body: SongChorusRequest
    ): SongChorusResponse
}

// ======================= 歌曲详情 DTO =======================

@Serializable
data class SongDetailRequest(
    val c: String
)

@Serializable
data class SongDetailResponse(
    val code: Int = 0,
    val songs: List<Track> = emptyList()
) {
    val isSuccess: Boolean get() = code == 200
}

// ======================= 歌曲百科简要信息 DTO =======================

@Serializable
data class SongWikiSummaryRequest(
    val songId: Long
)

@Serializable
data class SongWikiSummaryResponse(
    val code: Int = 0,
    val data: SongWikiSummaryData? = null
) {
    val isSuccess: Boolean get() = code == 200
}

@Serializable
data class SongWikiSummaryData(
    val blocks: List<SongWikiBlock> = emptyList()
)

@Serializable
data class SongWikiBlock(
    val code: String = "",
    val showType: String = "",
    val creatives: List<SongWikiCreative> = emptyList()
)

@Serializable
data class SongWikiCreative(
    val creativeType: String = "",
    val resources: List<SongWikiResource> = emptyList(),
    val uiElement: SongWikiUiElement? = null
)

@Serializable
data class SongWikiResource(
    // 回忆坐标里区分 FIRST_LISTEN / TOTAL_PLAY
    val resourceType: String = "",
    val resourceExt: SongWikiResourceExt? = null,
    val uiElement: SongWikiUiElement? = null
)

@Serializable
data class SongWikiUiElement(
    val mainTitle: SongWikiMainTitle? = null,
    val textLinks: List<SongWikiTextLink> = emptyList(),
    // 获奖成就的按钮文案是总数，如「3项」
    val buttons: List<SongWikiButton> = emptyList()
)

@Serializable
data class SongWikiButton(
    val text: String = ""
)

@Serializable
data class SongWikiResourceExt(
    val musicFirstListenDto: MusicFirstListenDto? = null,
    val musicTotalPlayDto: MusicTotalPlayDto? = null
)

// date 形如「2026.08.06 22:39」，season/period 如「夏末」「深夜」
@Serializable
data class MusicFirstListenDto(
    val date: String = "",
    val season: String = "",
    val period: String = ""
)

// text 是服务端的类比文案，如「如同看了3600字的诗」
@Serializable
data class MusicTotalPlayDto(
    val playCount: Int = 0,
    val text: String = ""
)

@Serializable
data class SongWikiMainTitle(
    val title: String = ""
)

@Serializable
data class SongWikiTextLink(
    val text: String = ""
)

// ======================= 歌曲创作者 DTO =======================

@Serializable
data class SongCreatorsRequest(
    val songId: Long
)

@Serializable
data class SongCreatorsResponse(
    val code: Int = 0,
    val data: SongCreatorsData? = null
) {
    val isSuccess: Boolean get() = code == 200
}

@Serializable
data class SongCreatorsData(
    val songCreatorsRoleVos: List<SongCreatorRole> = emptyList()
)

@Serializable
data class SongCreatorRole(
    val roleName: String = "",
    val creatorMetaVOS: List<CreatorMeta> = emptyList()
)

@Serializable
data class CreatorMeta(
    val artistName: String = ""
)

// ======================= 副歌时间 DTO =======================

// ids 是 JSON 数组的字符串形式，如 "[186016]"
@Serializable
data class SongChorusRequest(
    val ids: String
)

@Serializable
data class SongChorusResponse(
    val code: Int = 0,
    val chorus: List<SongChorusItem> = emptyList()
) {
    val isSuccess: Boolean get() = code == 200
}

// startTime/endTime 单位毫秒
@Serializable
data class SongChorusItem(
    val id: Long = 0L,
    val startTime: Long = 0L,
    val endTime: Long = 0L
)

package com.lin0721.linmusic.core.player.data

import com.lin0721.linmusic.core.model.Track
import kotlinx.serialization.Serializable
import retrofit2.http.Body
import retrofit2.http.POST

// 播放引擎所需（播放链接/歌词/相似与智能推荐）的网易云 Retrofit 接口定义。
interface PlaybackApi {

    // 获取歌曲播放链接
    @POST("/eapi/song/enhance/player/url/v1")
    suspend fun getSongUrl(
        @Body body: SongUrlRequest
    ): SongUrlResponse

    @POST("/eapi/v1/discovery/simiSong")
    suspend fun getSimiSongs(
        @Body body: SimiSongRequest
    ): SimiSongResponse

    @POST("/eapi/song/lyric/v1")
    suspend fun getLyrics(
        @Body body: LyricRequest
    ): LyricResponse

    // ================== 智能推荐歌曲 ==================
    @POST("/eapi/playmode/intelligence/list")
    suspend fun getIntelligenceSongs(
        @Body body: IntelligenceSongsRequest
    ): IntelligenceSongsResponse

    // ================== 播放行为上报（打卡） ==================
    // 走 /eapi/feedback/weblog，需分别上报 startplay/play 两条日志
    @POST("/eapi/feedback/weblog")
    suspend fun reportWeblog(
        @Body body: WeblogRequest
    ): WeblogResponse
}

// ======================= 播放链接 DTO =======================

@Serializable
data class SongUrlRequest(
    val ids: String,
    val level: String = "standard",
    val encodeType: String = "flac"
)

@Serializable
data class SongUrlResponse(
    val code: Int = 0,
    val data: List<SongUrlItem> = emptyList(),
) {
    val isSuccess: Boolean get() = code == 200
}

@Serializable
data class SongUrlItem(
    val id: Long = 0,
    val url: String? = null,
    val br: Long = 0,
    val size: Long = 0,
    val md5: String? = null,
    val type: String? = null,
    // VIP歌曲或者无版权时收费标识，freeTrialInfo 不为空表示可能只能试听
    val freeTrialInfo: FreeTrialInfo? = null,
)

@Serializable
data class FreeTrialInfo(
    val start: Long = 0,
    val end: Long = 0,
)

// ======================= 歌词 DTO =======================

@Serializable
data class LyricRequest(
    val id: Long,
    val cp: Boolean = false,
    val tv: Int = -1,
    val lv: Int = -1,
    val rv: Int = -1,
    val kv: Int = -1,
    val yv: Int = 99, // 默认设为 99 以请求 YRC 歌词
    val ytv: Int = -1,
    val yrv: Int = -1
)

@Serializable
data class LyricResponse(
    val code: Int = 0,
    val lrc: LyricContent? = null,
    val tlyric: LyricContent? = null,
    val ytlrc: LyricContent? = null,    // 新增：YRC 对应的翻译
    val romalrc: LyricContent? = null,
    val yrc: LyricContent? = null, // 新增：存放逐字歌词的原始文本
    val nolyric: Boolean = false, // 标识是否有歌词，若为 true 则通常无歌词
    val uncollected: Boolean = false // 标识歌曲是否未收录歌词
) {
    val isSuccess: Boolean get() = code == 200
}

@Serializable
data class LyricContent(
    val version: Int = 0,
    val lyric: String? = null
)

// ======================= 相似歌曲 DTO =======================

@Serializable
data class SimiSongRequest(
    val songid: String
)

@Serializable
data class SimiSongResponse(
    val code: Int = 0,
    val songs: List<Track> = emptyList()
) {
    val isSuccess: Boolean get() = code == 200
}

// ======================= 智能推荐歌曲 DTO =======================

@Serializable
data class IntelligenceSongsRequest(
    val songId: String,
    val playlistId: String,
    val type: String = "fromPlayOne",
    val startMusicId: String,
    val count: Int = 20
)

@Serializable
data class IntelligenceSongsResponse(
    val code: Int = 0,
    val data: List<IntelligenceItem> = emptyList()
) {
    val isSuccess: Boolean get() = code == 200
}

@Serializable
data class IntelligenceItem(
    val id: Long = 0,
    val recommended: Boolean = false,
    val songInfo: Track? = null
)

// ======================= 播放打卡上报 DTO =======================

// logs 是数组序列化后的 JSON 字符串（网易服务端约定），不是嵌套对象，需在 Repository 层手动编码
@Serializable
data class WeblogRequest(
    val logs: String
)

@Serializable
data class WeblogResponse(
    val code: Int = 0,
    val data: String? = null
) {
    val isSuccess: Boolean get() = code == 200
}

@Serializable
data class StartPlayLogEntry(
    val action: String = "startplay",
    val json: StartPlayLogJson
)

@Serializable
data class StartPlayLogJson(
    val id: Long,
    val type: String = "song",
    val mainsite: String = "1",
    val mainsiteWeb: String = "1",
    val content: String
)

@Serializable
data class PlayLogEntry(
    val action: String = "play",
    val json: PlayLogJson
)

@Serializable
data class PlayLogJson(
    val download: Int = 0,
    val end: String = "playend",
    val id: Long,
    val sourceId: Long,
    val time: Long,
    val type: String = "song",
    val wifi: Int = 0,
    val source: String = "list",
    val mainsite: String = "1",
    val mainsiteWeb: String = "1",
    val content: String
)

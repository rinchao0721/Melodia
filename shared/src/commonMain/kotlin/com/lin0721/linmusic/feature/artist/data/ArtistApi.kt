package com.lin0721.linmusic.feature.artist.data

import com.lin0721.linmusic.core.model.Artist
import com.lin0721.linmusic.core.model.ArtistAlbum
import com.lin0721.linmusic.core.model.ArtistDetailInfo
import com.lin0721.linmusic.core.model.ArtistMv
import com.lin0721.linmusic.core.model.Track
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonNames
import retrofit2.http.Body
import retrofit2.http.POST
import retrofit2.http.Path

// 歌手/艺人相关的网易云 Retrofit 接口定义。
interface ArtistApi {

    @POST("/eapi/discovery/simiArtist")
    suspend fun getSimiArtists(
        @Body body: SimiArtistRequest
    ): SimiArtistResponse

    @POST("/eapi/artist/head/info/get")
    suspend fun getArtistDetail(
        @Body body: ArtistDetailRequest
    ): ArtistDetailResponse

    // 获取歌手粉丝数量与关注状态
    @POST("/weapi/artist/follow/count/get")
    suspend fun getArtistFollowCount(
        @Body body: ArtistFollowCountRequest
    ): ArtistFollowCountGetResponse

    @POST("/weapi/artist/albums/{id}")
    suspend fun getArtistAlbums(
        @Path("id") id: Long,
        @Body body: ArtistAlbumRequest = ArtistAlbumRequest()
    ): ArtistAlbumResponse

    @POST("/weapi/artist/top/song")
    suspend fun getArtistTopSongs(
        @Body body: ArtistTopSongsRequest
    ): ArtistTopSongsResponse

    // ================== 收藏与取消收藏歌手 ==================
    @POST("/weapi/artist/{op}")
    suspend fun subscribeArtist(
        @Path("op") op: String, // sub 表示关注, unsub 表示取消关注
        @Body body: ArtistSubscriptionRequest
    ): ArtistSubscriptionResponse

    // ================== 歌手 MV ==================
    @POST("/weapi/artist/mvs")
    suspend fun getArtistMvs(
        @Body body: ArtistMvsRequest
    ): ArtistMvsResponse

    // 获取 MV 播放地址
    @POST("/weapi/song/enhance/play/mv/url")
    suspend fun getMvUrl(
        @Body body: MvUrlRequest
    ): MvUrlResponse

    // ================== 歌手全部歌曲（裸 api 无加密，前缀未经真机验证，eapi/weapi 两版均实现由调用方择优）==================
    @POST("/eapi/v1/artist/songs")
    suspend fun getArtistAllSongsEapi(
        @Body body: ArtistAllSongsRequest
    ): ArtistAllSongsResponse

    @POST("/weapi/v1/artist/songs")
    suspend fun getArtistAllSongsWeapi(
        @Body body: ArtistAllSongsRequest
    ): ArtistAllSongsResponse

    // ================== MV 详情/收藏/点赞（观看页信息面板用）==================
    @POST("/weapi/v1/mv/detail")
    suspend fun getMvDetail(
        @Body body: MvDetailRequest
    ): MvDetailResponse

    @POST("/weapi/mv/{op}")
    suspend fun subscribeMv(
        @Path("op") op: String, // sub 表示收藏, unsub 表示取消收藏
        @Body body: MvSubscriptionRequest
    ): MvSubscriptionResponse

    // 通用资源点赞/取消点赞（threadId 形如 R_MV_5_{mvId}）
    @POST("/weapi/resource/{op}")
    suspend fun likeResource(
        @Path("op") op: String, // like 表示点赞, unlike 表示取消点赞
        @Body body: ResourceLikeRequest
    ): ResourceLikeResponse
}

// ======================= 相似歌手 DTO =======================

@Serializable
data class SimiArtistRequest(
    val artistid: Long
)

@Serializable
data class SimiArtistResponse(
    val code: Int = 0,
    val artists: List<Artist> = emptyList()
) {
    val isSuccess: Boolean get() = code == 200
}

// ======================= 艺人详情 DTO =======================

@Serializable
data class ArtistDetailRequest(val id: Long)

@Serializable
data class ArtistDetailResponse(
    val code: Int = 0,
    val data: ArtistDetailData? = null
) {
    val isSuccess: Boolean get() = code == 200
}

@Serializable
data class ArtistDetailData(
    val artist: ArtistDetailInfo? = null
)

// ======================= 艺人粉丝数量 DTO =======================

@Serializable
data class ArtistFollowCountRequest(val id: Long)

// 歌手关注与粉丝数详细数据
@Serializable
data class ArtistFollowCountData(
    @SerialName("fans") val fans: Long? = null,             // 粉丝数
    @SerialName("fansCnt") val fansCnt: Long? = null,       // 粉丝计数 (EAPI返回)
    @SerialName("fansCount") val fansCount: Long? = null,   // 粉丝总数
    @SerialName("followCount") val followCount: Long? = null, // 关注数
    @SerialName("isFollow") val isFollow: Boolean = false    // 当前登录用户是否已关注
)

// 歌手关注数获取响应体 ( concrete 实体类，避免泛型反序列化问题 )
@Serializable
data class ArtistFollowCountGetResponse(
    @SerialName("code") val code: Int = 0,              // 响应状态码
    @SerialName("message") val message: String? = null, // 响应消息
    @SerialName("data") val data: ArtistFollowCountData? = null // 核心业务数据
) {
    // 请求是否成功
    val isSuccess: Boolean get() = code == 200
}

// ======================= 艺人专辑 DTO =======================

@Serializable
data class ArtistAlbumRequest(
    val limit: Int = 10,
    val offset: Int = 0,
    val total: Boolean = true
)

@Serializable
data class ArtistAlbumResponse(
    val code: Int = 0,
    val hotAlbums: List<ArtistAlbum> = emptyList(),
    val more: Boolean = false
) {
    val isSuccess: Boolean get() = code == 200
}

// ======================= 艺人热门单曲 DTO =======================

@Serializable
data class ArtistTopSongsRequest(val id: Long)

@Serializable
data class ArtistTopSongsResponse(
    val code: Int = 0,
    val songs: List<Track> = emptyList()
) {
    val isSuccess: Boolean get() = code == 200
}

// ======================= 收藏与取消收藏歌手 DTO =======================

@Serializable
data class ArtistSubscriptionRequest(
    val artistId: Long,
    val artistIds: String
)

@Serializable
data class ArtistSubscriptionResponse(
    val code: Int = 0,
    val message: String? = null
) {
    val isSuccess: Boolean get() = code == 200
}

// ======================= 歌手 MV 列表 DTO =======================

@Serializable
data class ArtistMvsRequest(
    val artistId: Long,
    val limit: Int = 20,
    val offset: Int = 0,
    val total: Boolean = true
)

@Serializable
data class ArtistMvsResponse(
    val code: Int = 0,
    val mvs: List<ArtistMv> = emptyList(),
    @OptIn(ExperimentalSerializationApi::class)
    @JsonNames("hasMore", "more")
    val hasMore: Boolean = false
) {
    val isSuccess: Boolean get() = code == 200
}

// ======================= MV 播放地址 DTO =======================

@Serializable
data class MvUrlRequest(
    val id: Long,
    val r: Int = 1080
)

@Serializable
data class MvUrlResponse(
    val code: Int = 0,
    val data: MvUrlData? = null
) {
    val isSuccess: Boolean get() = code == 200
}

@Serializable
data class MvUrlData(
    val id: Long = 0,
    val url: String? = null,
    val r: Int = 0,
    val size: Long = 0
)

// ======================= 歌手全部歌曲 DTO =======================

@Serializable
data class ArtistAllSongsRequest(
    val id: Long,
    @SerialName("private_cloud") val privateCloud: String = "true",
    @SerialName("work_type") val workType: Int = 1,
    val order: String = "hot", // hot=热门, time=按时间
    val offset: Int = 0,
    val limit: Int = 100
)

@Serializable
data class ArtistAllSongsResponse(
    val code: Int = 0,
    val songs: List<Track> = emptyList(),
    val more: Boolean = false,
    val total: Int = 0
) {
    val isSuccess: Boolean get() = code == 200
}

// ======================= MV 详情 DTO =======================
// 字段名参照网易云通用惯例编写，未经真机数据验证；isSubed/isLiked 拿不到时默认 false，
// 不影响点赞/收藏动作本身，只是初始态展示可能不准确

@Serializable
data class MvDetailRequest(val id: Long)

@Serializable
data class MvDetailResponse(
    val code: Int = 0,
    val data: MvDetailData? = null
) {
    val isSuccess: Boolean get() = code == 200
}

@Serializable
data class MvDetailData(
    val id: Long = 0,
    val name: String = "",
    val artistId: Long = 0,
    val artistName: String = "",
    @OptIn(ExperimentalSerializationApi::class)
    @JsonNames("imgurl16v9", "imgurl", "cover")
    val cover: String = "",
    val duration: Long = 0,
    @OptIn(ExperimentalSerializationApi::class)
    @JsonNames("playCount", "playcount")
    val playCount: Long = 0,
    @OptIn(ExperimentalSerializationApi::class)
    @JsonNames("subCount", "subCnt")
    val subCount: Long = 0,
    @OptIn(ExperimentalSerializationApi::class)
    @JsonNames("shareCount", "shareCnt")
    val shareCount: Long = 0,
    @OptIn(ExperimentalSerializationApi::class)
    @JsonNames("commentCount", "commentCnt")
    val commentCount: Long = 0,
    @OptIn(ExperimentalSerializationApi::class)
    @JsonNames("likedCount", "likedCnt")
    val likedCount: Long = 0,
    @OptIn(ExperimentalSerializationApi::class)
    @JsonNames("subed", "hasSubed")
    val subed: Boolean = false,
    @OptIn(ExperimentalSerializationApi::class)
    @JsonNames("liked", "hasLiked")
    val liked: Boolean = false,
    val publishTime: String = "",
    val briefDesc: String? = null
)

// ======================= 收藏/取消收藏 MV DTO =======================

@Serializable
data class MvSubscriptionRequest(
    val mvId: Long,
    val mvIds: String
)

@Serializable
data class MvSubscriptionResponse(
    val code: Int = 0,
    val message: String? = null
) {
    val isSuccess: Boolean get() = code == 200
}

// ======================= 通用资源点赞 DTO =======================

@Serializable
data class ResourceLikeRequest(
    val threadId: String
)

@Serializable
data class ResourceLikeResponse(
    val code: Int = 0,
    val message: String? = null
) {
    val isSuccess: Boolean get() = code == 200
}

package com.lin0721.linmusic.core.userartist

import com.lin0721.linmusic.core.model.Artist
import kotlinx.serialization.Serializable
import retrofit2.http.Body
import retrofit2.http.POST

// 关注歌手列表（含未登录时的热门歌手兜底）的网易云 Retrofit 接口定义。
interface UserArtistApi {

    // 获取已关注歌手 (需登录)
    @POST("/eapi/artist/sublist")
    suspend fun getArtistSublist(
        @Body body: ArtistSublistRequest = ArtistSublistRequest()
    ): ArtistSublistResponse

    // 获取热门歌手
    @POST("/eapi/artist/top")
    suspend fun getTopArtists(
        @Body body: TopArtistsRequest = TopArtistsRequest()
    ): TopArtistsResponse
}

@Serializable
data class TopArtistsRequest(
    val offset: Int = 0,
    val limit: Int = 30,
    val total: Boolean = true
)

@Serializable
data class TopArtistsResponse(
    val code: Int = 0,
    val artists: List<Artist> = emptyList(),
) {
    val isSuccess: Boolean get() = code == 200
}

@Serializable
data class ArtistSublistRequest(
    val limit: Int = 25,
    val offset: Int = 0,
    val total: Boolean = true
)

@Serializable
data class ArtistSublistResponse(
    val code: Int = 0,
    // 实际返回结构：{"data":[...], "code":200}，data 字段直接就是歌手数组
    val data: List<Artist> = emptyList()
)

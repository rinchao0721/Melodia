package com.lin0721.linmusic.feature.search.data

import com.lin0721.linmusic.core.model.EmptyBody
import com.lin0721.linmusic.feature.search.data.dto.CloudSearchRequest
import com.lin0721.linmusic.feature.search.data.dto.CloudSearchResponse
import com.lin0721.linmusic.feature.search.data.dto.HighQualityPlaylistRequest
import com.lin0721.linmusic.feature.search.data.dto.HighQualityPlaylistResponse
import com.lin0721.linmusic.feature.search.data.dto.HighQualityTagsResponse
import com.lin0721.linmusic.feature.search.data.dto.HotSearchDetailResponse
import com.lin0721.linmusic.feature.search.data.dto.SearchDefaultResponse
import com.lin0721.linmusic.feature.search.data.dto.SearchSuggestRequest
import com.lin0721.linmusic.feature.search.data.dto.SearchSuggestResponse
import com.lin0721.linmusic.feature.search.data.dto.SearchSuggestWebResponse
import retrofit2.http.Body
import retrofit2.http.POST

// 搜索相关的网易云 Retrofit 接口定义，DTO 见 data/dto 包。
interface SearchApi {

    // 获取默认搜索词
    @POST("/eapi/search/defaultkeyword/get")
    suspend fun getSearchDefaultKeyword(
        @Body body: EmptyBody = EmptyBody()
    ): SearchDefaultResponse

    // 热搜详情
    @POST("/eapi/hotsearchlist/get")
    suspend fun getHotSearchDetail(
        @Body body: EmptyBody = EmptyBody()
    ): HotSearchDetailResponse

    // 云搜索（按 type 区分单曲/专辑/歌手/歌单）
    @POST("/eapi/cloudsearch/pc")
    suspend fun cloudSearch(
        @Body body: CloudSearchRequest
    ): CloudSearchResponse

    // 获取精品歌单标签列表
    @POST("/eapi/playlist/highquality/tags")
    suspend fun getHighQualityTags(
        @Body body: EmptyBody = EmptyBody()
    ): HighQualityTagsResponse

    // 获取精品歌单列表（用于获取标签封面图）
    @POST("/eapi/playlist/highquality/list")
    suspend fun getHighQualityPlaylists(
        @Body body: HighQualityPlaylistRequest
    ): HighQualityPlaylistResponse

    // 搜索联想词（真机核实：weapi，非本文件其余接口使用的 eapi）
    @POST("/weapi/search/suggest/keyword")
    suspend fun getSearchSuggest(
        @Body body: SearchSuggestRequest
    ): SearchSuggestResponse

    // 搜索联想实体：歌手/专辑等（真机核实：weapi）
    @POST("/weapi/search/suggest/web")
    suspend fun getSearchSuggestWeb(
        @Body body: SearchSuggestRequest
    ): SearchSuggestWebResponse
}

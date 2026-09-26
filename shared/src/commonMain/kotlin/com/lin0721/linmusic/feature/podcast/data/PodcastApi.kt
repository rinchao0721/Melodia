package com.lin0721.linmusic.feature.podcast.data

import com.lin0721.linmusic.core.model.EmptyBody
import retrofit2.http.Body
import retrofit2.http.POST

// 「播客」tab 的电台接口。各接口 eapi/weapi 均可用，统一取 eapi 与项目既有主接口保持一致。
interface PodcastApi {

    // 电台分类，19 个（公开接口）
    @POST("/eapi/djradio/category/get")
    suspend fun getCategories(
        @Body body: EmptyBody = EmptyBody()
    ): PodcastCategoryResponse

    // 推荐节目。节目自带所属电台与主播，可直接播放
    @POST("/eapi/program/recommend/v1")
    suspend fun getRecommendPrograms(
        @Body body: PodcastProgramRecommendRequest
    ): PodcastProgramRecommendResponse

    // 猜你喜欢的电台（需登录，未登录返回空）
    @POST("/eapi/djradio/personalize/rcmd")
    suspend fun getPersonalizedRadios(
        @Body body: PodcastPersonalizeRequest = PodcastPersonalizeRequest()
    ): PodcastPersonalizeResponse

    // 精选电台（公开接口）
    @POST("/eapi/djradio/recommend/v1")
    suspend fun getRecommendRadios(
        @Body body: EmptyBody = EmptyBody()
    ): PodcastRecommendResponse

    // 电台榜
    @POST("/eapi/djradio/toplist")
    suspend fun getToplistRadios(
        @Body body: PodcastToplistRequest = PodcastToplistRequest()
    ): PodcastToplistResponse

    // 电台详情
    @POST("/eapi/djradio/v2/get")
    suspend fun getRadioDetail(
        @Body body: PodcastRadioDetailRequest
    ): PodcastRadioDetailResponse

    // 订阅电台（需登录）
    @POST("/eapi/djradio/sub")
    suspend fun subscribeRadio(
        @Body body: PodcastSubscribeRequest
    ): PodcastSubscribeResponse

    // 取消订阅（需登录）
    @POST("/eapi/djradio/unsub")
    suspend fun unsubscribeRadio(
        @Body body: PodcastSubscribeRequest
    ): PodcastSubscribeResponse

    // 电台下的节目列表，一次 30 条
    @POST("/eapi/dj/program/byradio")
    suspend fun getRadioPrograms(
        @Body body: PodcastProgramListRequest
    ): PodcastProgramListResponse
}

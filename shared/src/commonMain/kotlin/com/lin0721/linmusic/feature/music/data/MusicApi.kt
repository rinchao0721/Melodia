package com.lin0721.linmusic.feature.music.data

import com.lin0721.linmusic.core.model.EmptyBody
import retrofit2.http.Body
import retrofit2.http.POST

// 「音乐」tab 的曲风体系接口。六个接口 eapi/weapi 均可用，统一取 eapi 与项目既有主接口保持一致。
interface MusicApi {

    // 曲风列表：28 个一级曲风，各自带二级子标签（公开接口）
    @POST("/eapi/tag/list/get")
    suspend fun getStyleList(
        @Body body: EmptyBody = EmptyBody()
    ): StyleListResponse

    // 我的曲风偏好，带占比（需登录，未登录返回空数组）
    @POST("/eapi/tag/my/preference/get")
    suspend fun getStylePreference(
        @Body body: EmptyBody = EmptyBody()
    ): StylePreferenceResponse

    // 曲风详情：封面、简介、数量统计、曲风画像、该曲风下最爱的歌
    @POST("/eapi/style-tag/home/head")
    suspend fun getStyleHead(
        @Body body: StyleHeadRequest
    ): StyleHeadResponse

    // 曲风歌单
    @POST("/eapi/style-tag/home/playlist")
    suspend fun getStylePlaylists(
        @Body body: StyleContentRequest
    ): StylePlaylistResponse

    // 曲风歌曲
    @POST("/eapi/style-tag/home/song")
    suspend fun getStyleSongs(
        @Body body: StyleContentRequest
    ): StyleSongResponse

    // 曲风歌手
    @POST("/eapi/style-tag/home/artist")
    suspend fun getStyleArtists(
        @Body body: StyleContentRequest
    ): StyleArtistResponse
}

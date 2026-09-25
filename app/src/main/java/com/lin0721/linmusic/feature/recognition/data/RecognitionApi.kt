package com.lin0721.linmusic.feature.recognition.data

import com.lin0721.linmusic.core.network.NeteaseEndpoints
import com.lin0721.linmusic.feature.recognition.data.dto.AudioMatchResponse
import retrofit2.http.GET
import retrofit2.http.Query

private const val AUDIO_MATCH_URL = "https://${NeteaseEndpoints.EAPI_HOST}/api/music/audio/match"

// 听歌识曲网易云 Retrofit 接口定义，DTO 见 data/dto 包。
interface RecognitionApi {

    // 听歌识曲指纹匹配（无需登录）。参考 api-enhanced audio_match.js：裸 GET 无请求体，CryptoInterceptor 直接放行
    @GET(AUDIO_MATCH_URL)
    suspend fun matchAudio(
        @Query("sessionId") sessionId: String,
        @Query("duration") durationSeconds: Int,
        @Query("rawdata") fingerprint: String,
        @Query("algorithmCode") algorithmCode: String = "shazam_v2",
        @Query("times") times: Int = 1,
        @Query("decrypt") decrypt: Int = 1
    ): AudioMatchResponse
}

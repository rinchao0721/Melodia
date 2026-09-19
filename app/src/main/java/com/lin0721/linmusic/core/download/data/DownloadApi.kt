package com.lin0721.linmusic.core.download.data

import com.lin0721.linmusic.core.player.data.FreeTrialInfo
import kotlinx.serialization.Serializable
import retrofit2.http.Body
import retrofit2.http.POST

interface DownloadApi {

    // 获取客户端下载直链
    @POST("/eapi/song/enhance/download/url/v1")
    suspend fun getSongDownloadUrl(
        @Body body: SongDownloadUrlRequest
    ): SongDownloadUrlResponse
}

@Serializable
data class SongDownloadUrlRequest(
    val id: Long,
    val level: String,
    val immerseType: String = "c51"
)

@Serializable
data class SongDownloadUrlResponse(
    val code: Int = 0,
    val data: SongDownloadUrlItem? = null
) {
    val isSuccess: Boolean get() = code == 200
}

@Serializable
data class SongDownloadUrlItem(
    val id: Long = 0,
    val url: String? = null,
    val level: String? = null,
    val encodeType: String? = null,
    val br: Long = 0,
    val size: Long = 0,
    val md5: String? = null,
    val type: String? = null,
    // 试听限制信息
    val freeTrialInfo: FreeTrialInfo? = null,
)

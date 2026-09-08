package com.lin0721.linmusic.feature.cloud.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import retrofit2.http.Body
import retrofit2.http.POST

// 云盘上传相关的网易云 Retrofit 接口定义。
// 真机核实 eapi/weapi 均放行，统一走 weapi。
interface CloudUploadApi {

    // 检查文件是否已存在于服务器（按 md5 查重），needUpload=false 时可跳过实际字节上传
    @POST("/weapi/cloud/upload/check")
    suspend fun checkUpload(
        @Body body: CloudUploadCheckRequest
    ): CloudUploadCheckResponse

    // 申请上传凭证。真机核实：bucket 传什么值服务端都返回同一个真实存储桶，
    @POST("/weapi/nos/token/alloc")
    suspend fun allocNosToken(
        @Body body: NosTokenRequest
    ): NosTokenResponse

    // 登记云盘歌曲信息（文件字节已上传完成后调用）
    @POST("/weapi/upload/cloud/info/v2")
    suspend fun registerCloudInfo(
        @Body body: CloudInfoRequest
    ): CloudInfoResponse

    // 发布，之后才会出现在 /v1/cloud/get 列表里
    @POST("/weapi/cloud/pub/v2")
    suspend fun publishCloud(
        @Body body: CloudPublishRequest
    ): CloudActionResponse
}

// ======================= Stage 1：查重 =======================

@Serializable
data class CloudUploadCheckRequest(
    val bitrate: String,
    val ext: String = "",
    val length: Long,
    val md5: String,
    val songId: String = "0",
    val version: Int = 1
)

@Serializable
data class CloudUploadCheckResponse(
    val code: Int = 0,
    // 未识别文件时是一长串十六进制字符串，不是数值型 id，不能用 Long 承接
    val songId: String = "",
    val needUpload: Boolean = true
) {
    val isSuccess: Boolean get() = code == 200
}

// ======================= Stage 2：申请上传凭证 =======================

@Serializable
data class NosTokenRequest(
    val bucket: String = "",
    val ext: String,
    val filename: String,
    val local: Boolean = false,
    @SerialName("nos_product")
    val nosProduct: Int = 3,
    val type: String = "audio",
    // 音频场景查重必需；图片场景（type="other"）参考实现整个不带该字段，
    // 传 null 时配合 explicitNulls=false 会被整条跳过，传空串则会发出 "md5":""，两者不等价
    val md5: String? = null,
    // 参考实现里图片场景会传，音频场景没有该字段，二者均已验证可正常工作，因此设为可选
    @SerialName("return_body")
    val returnBody: String? = null
)

@Serializable
data class NosTokenResponse(
    val code: Int = 0,
    val result: NosTokenResult? = null
) {
    val isSuccess: Boolean get() = code == 200
}

@Serializable
data class NosTokenResult(
    val bucket: String = "",
    val token: String = "",
    val objectKey: String = "",
    val resourceId: Long = 0,
    val docId: Long = 0
)

// ======================= Stage 4：登记 =======================

@Serializable
data class CloudInfoRequest(
    val md5: String,
    val songid: String,
    val filename: String,
    val song: String,
    val album: String,
    val artist: String,
    val bitrate: String,
    val resourceId: Long
)

@Serializable
data class CloudInfoResponse(
    val code: Int = 0,
    val songId: String = ""
) {
    val isSuccess: Boolean get() = code == 200
}

// ======================= Stage 5：发布 =======================

@Serializable
data class CloudPublishRequest(
    val songid: String
)

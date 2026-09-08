package com.lin0721.linmusic.core.api

import com.lin0721.linmusic.core.model.EmptyBody
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.POST

// 账号鉴权相关的网易云 Retrofit 接口定义（跨业务域共用，由 core/auth 消费）。
interface NeteaseApiService {

    // 获取当前登录账号信息
    @POST("/eapi/nuser/account/get")
    suspend fun getAccountInfo(
        @Body body: EmptyBody = EmptyBody()
    ): AccountInfoResponse

    // 退出登录
    @POST("/eapi/logout")
    suspend fun logoutApi(
        @Body body: EmptyBody = EmptyBody()
    ): LogoutApiResponse

    // 获取二维码登录 key
    @POST("/eapi/login/qrcode/unikey")
    suspend fun getQrKey(
        @Body body: QrKeyRequest = QrKeyRequest()
    ): QrKeyResponse

    // 轮询二维码扫码状态；登录成功时 Cookie 经由 Set-Cookie 响应头下发，故需拿到原始 Response 读取头部
    @POST("/eapi/login/qrcode/client/login")
    suspend fun checkQrStatus(
        @Body body: QrCheckRequest
    ): Response<QrCheckResponse>
}

// ======================= 用户账户信息 =======================

@Serializable
data class AccountInfoResponse(
    val code: Int = 0,
    val account: Account? = null,
    val profile: UserProfile? = null
)

@Serializable
data class Account(
    val id: Long = 0,
    val userName: String = "",
    val type: Int = 0,
    val status: Int = 0,
)

@Serializable
data class UserProfile(
    val userId: Long = 0,
    val nickname: String = "",
    val avatarUrl: String = "",
    @SerialName("backgroundUrl")
    val backgroundUrl: String = "",
    val signature: String = "",
)

@Serializable
data class LogoutApiResponse(
    val code: Int = 0
) {
    val isSuccess: Boolean get() = code == 200
}

// ======================= 二维码登录 =======================

// type 固定为 3，网易云二维码登录约定值
@Serializable
data class QrKeyRequest(val type: Int = 3)

@Serializable
data class QrKeyResponse(
    val code: Int = 0,
    val unikey: String = ""
)

@Serializable
data class QrCheckRequest(
    val key: String,
    val type: Int = 3
)

// code: 800 二维码过期 / 801 等待扫码 / 802 待确认 / 803 授权成功
// cookies 不是服务端 JSON 字段，由 AuthRepositoryImpl 从 Set-Cookie 响应头解析后回填
@Serializable
data class QrCheckResponse(
    val code: Int = 0,
    val message: String? = null,
    @Transient
    val cookies: String? = null
)

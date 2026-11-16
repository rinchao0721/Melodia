package com.lin0721.linmusic.core.api

import com.lin0721.linmusic.core.model.EmptyBody
import com.lin0721.linmusic.core.network.HeaderInterceptor
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.Headers
import retrofit2.http.POST

// 账号鉴权相关的网易云 Retrofit 接口定义（跨业务域共用，由 core/auth 消费）。
interface NeteaseApiService {

    // 获取当前登录账号信息；传入 overrideCookie 时使用指定凭据校验（添加账户时不污染原会话）
    @POST("/eapi/nuser/account/get")
    suspend fun getAccountInfo(
        @Body body: EmptyBody = EmptyBody(),
        @Header(HeaderInterceptor.HEADER_OVERRIDE_SESSION_COOKIE) overrideCookie: String? = null
    ): AccountInfoResponse

    // 退出登录
    @POST("/eapi/logout")
    suspend fun logoutApi(
        @Body body: EmptyBody = EmptyBody()
    ): LogoutApiResponse

    // 获取二维码登录 key（声明不附带已登录会话 Cookie，避免旧会话干扰）
    @Headers("${HeaderInterceptor.HEADER_NO_SESSION_COOKIE}: true")
    @POST("/weapi/login/qrcode/unikey")
    suspend fun getQrKey(
        @Body body: QrKeyRequest = QrKeyRequest()
    ): QrKeyResponse

    // 轮询二维码扫码状态；登录成功时 Cookie 经由 Set-Cookie 响应头下发，故需拿到原始 Response 读取头部
    @Headers("${HeaderInterceptor.HEADER_NO_SESSION_COOKIE}: true")
    @POST("/weapi/login/qrcode/client/login")
    suspend fun checkQrStatus(
        @Body body: QrCheckRequest
    ): Response<QrCheckResponse>

    // 刷新登录态，参考 api-enhanced login_refresh；新 Cookie 在 Set-Cookie 里
    @POST("/weapi/login/token/refresh")
    suspend fun refreshLogin(
        @Body body: EmptyBody = EmptyBody()
    ): Response<LoginRefreshResponse>
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

// type 固定为 1，网易云 Web/PC 二维码登录标准参数
@Serializable
data class QrKeyRequest(val type: Int = 1)

@Serializable
data class QrKeyResponse(
    val code: Int = 0,
    val unikey: String = ""
)

@Serializable
data class QrCheckRequest(
    val key: String,
    val type: Int = 1
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

// ======================= 登录续期 =======================

@Serializable
data class LoginRefreshResponse(
    val code: Int = 0,
    val message: String? = null
)

package com.lin0721.linmusic.feature.settings.data

import com.lin0721.linmusic.core.model.EmptyBody
import kotlinx.serialization.Serializable
import retrofit2.http.Body
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.Part
import okhttp3.MultipartBody

// 个人信息、等级、签到与隐私设置相关的网易云 Retrofit 接口定义。
interface SettingsApi {

    // 获取用户等级信息
    @POST("/eapi/user/level")
    suspend fun getUserLevel(
        @Body body: EmptyBody = EmptyBody()
    ): UserLevelResponse

    // 获取 VIP 状态信息
    @POST("/eapi/vip/info")
    suspend fun getVipInfo(
        @Body body: EmptyBody = EmptyBody()
    ): VipInfoResponse

    // 获取账号绑定信息
    @POST("/eapi/user/binding")
    suspend fun getUserBindings(
        @Body body: UserBindingRequest
    ): UserBindingResponse

    // 修改用户个人资料
    @POST("/eapi/user/update")
    suspend fun updateUserProfile(
        @Body body: UserProfileUpdateRequest
    ): UserProfileUpdateResponse

    // 检查昵称可用性
    @POST("/eapi/nickname/check")
    suspend fun checkNickname(
        @Body body: NicknameCheckRequest
    ): NicknameCheckResponse

    // 每日签到
    @POST("/eapi/point/dailyTask")
    suspend fun dailySignin(
        @Body body: DailySigninRequest
    ): DailySigninResponse

    // 更换头像 (支持 MultipartBody 上传)
    @Multipart
    @POST("/weapi/user/avatar/upload/v1")
    suspend fun uploadAvatar(
        @Part imgFile: MultipartBody.Part
    ): AvatarUploadResponse
}

// ======================= 设置和隐私 DTO =======================

@Serializable
data class UserLevelResponse(
    val code: Int = 0,
    val data: UserLevelData? = null
) {
    val isSuccess: Boolean get() = code == 200
}

@Serializable
data class UserLevelData(
    val level: Int = 0,
    val nextPlayCount: Long = 0,
    val nextLoginCount: Int = 0,
    val nowPlayCount: Long = 0,
    val nowLoginCount: Int = 0,
    val progress: Double = 0.0
)

@Serializable
data class VipInfoResponse(
    val code: Int = 0,
    val data: VipInfoData? = null
) {
    val isSuccess: Boolean get() = code == 200
}

@Serializable
data class VipInfoData(
    val redVipLevel: Int = 0,
    val vipType: Int = 0, // 0-非VIP, 10-音乐包, 11-黑胶VIP
    val expireTime: Long = 0,
    val isVip: Boolean = false
)

@Serializable
data class UserBindingRequest(
    val uid: Long
)

@Serializable
data class UserBindingResponse(
    val code: Int = 0,
    val bindings: List<UserBindingItem> = emptyList()
) {
    val isSuccess: Boolean get() = code == 200
}

@Serializable
data class UserBindingItem(
    val type: Int = 0, // 1-手机, 2-新浪微博, 5-腾讯微博, 10-微信, 20-QQ, 1000-网易邮箱
    val typeName: String = "",
    val userId: Long = 0,
    val expired: Boolean = false
)

@Serializable
data class UserProfileUpdateRequest(
    val nickname: String,
    val gender: Int,
    val birthday: Long,
    val province: Int,
    val city: Int,
    val signature: String
)

@Serializable
data class UserProfileUpdateResponse(
    val code: Int = 0
) {
    val isSuccess: Boolean get() = code == 200
}

@Serializable
data class NicknameCheckRequest(
    val nickname: String
)

@Serializable
data class NicknameCheckResponse(
    val code: Int = 0,
    val duplicated: Boolean = false,
    val candidateNicknames: List<String> = emptyList()
) {
    val isSuccess: Boolean get() = code == 200
}

@Serializable
data class DailySigninRequest(
    val type: Int = 0
)

@Serializable
data class DailySigninResponse(
    val code: Int = 0,
    val point: Int = 0,
    val msg: String? = null
) {
    val isSuccess: Boolean get() = code == 200 || code == -2
}

@Serializable
data class AvatarUploadResponse(
    val code: Int = 0,
    val url: String? = null
) {
    val isSuccess: Boolean get() = code == 200
}

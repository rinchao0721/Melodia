package com.lin0721.linmusic.feature.settings.data

import com.lin0721.linmusic.core.network.apiFlow
import kotlinx.coroutines.flow.Flow
import okhttp3.MediaType.Companion.toMediaType

class SettingsRepositoryImpl(
    private val apiService: SettingsApi
) : SettingsRepository {

    override fun getUserLevel(): Flow<Result<UserLevelData>> = apiFlow(
        request = { apiService.getUserLevel() },
        isSuccess = { it.isSuccess && it.data != null },
        code = { it.code },
        transform = { it.data!! }
    )

    override fun getVipInfo(): Flow<Result<VipInfoData>> = apiFlow(
        request = { apiService.getVipInfo() },
        isSuccess = { it.isSuccess && it.data != null },
        code = { it.code },
        transform = { it.data!! }
    )

    override fun getUserBindings(uid: Long): Flow<Result<List<UserBindingItem>>> = apiFlow(
        request = { apiService.getUserBindings(UserBindingRequest(uid = uid)) },
        isSuccess = { it.isSuccess },
        code = { it.code },
        transform = { it.bindings }
    )

    override fun updateUserProfile(
        nickname: String,
        gender: Int,
        birthday: Long,
        province: Int,
        city: Int,
        signature: String
    ): Flow<Result<Unit>> = apiFlow(
        request = {
            apiService.updateUserProfile(
                UserProfileUpdateRequest(
                    nickname = nickname,
                    gender = gender,
                    birthday = birthday,
                    province = province,
                    city = city,
                    signature = signature
                )
            )
        },
        isSuccess = { it.isSuccess },
        code = { it.code },
        transform = { Unit }
    )

    override fun checkNickname(nickname: String): Flow<Result<Boolean>> = apiFlow(
        request = { apiService.checkNickname(NicknameCheckRequest(nickname = nickname)) },
        isSuccess = { it.isSuccess },
        code = { it.code },
        transform = { it.duplicated }
    )

    override fun dailySignin(type: Int): Flow<Result<Int>> = apiFlow(
        request = { apiService.dailySignin(DailySigninRequest(type = type)) },
        // code == -2 代表重复签到，视为成功但积分为 0
        isSuccess = { it.isSuccess || it.code == -2 },
        code = { it.code },
        msg = { it.msg },
        transform = { if (it.isSuccess) it.point else 0 }
    )

    override fun uploadAvatar(file: java.io.File): Flow<Result<String>> = apiFlow(
        request = {
            val mediaType = "image/*".toMediaType()
            val requestFile = okhttp3.RequestBody.create(mediaType, file)
            val body = okhttp3.MultipartBody.Part.createFormData("imgFile", file.name, requestFile)
            apiService.uploadAvatar(body)
        },
        isSuccess = { it.isSuccess },
        code = { it.code },
        transform = { it.url ?: "" }
    )
}

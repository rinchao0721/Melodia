package com.lin0721.linmusic.core.auth

import kotlinx.coroutines.flow.firstOrNull

// 登录成功后的账号同步，供 home/library/artist/playlist 等域共用
class SyncProfileAfterLoginUseCase(
    private val userPreferences: UserPreferences,
    private val authRepository: AuthRepository
) {

    // 保存 Cookie 并拉取账号信息落库，返回同步到的资料；未取到资料时返回 null，调用方据此跳过后续刷新
    suspend operator fun invoke(cookies: String): UserProfile? {
        userPreferences.saveCookies(cookies)
        val response = authRepository.getAccountInfo().firstOrNull()?.getOrNull() ?: return null
        val remoteProfile = response.profile ?: return null
        val profile = UserProfile(
            uid = remoteProfile.userId,
            nickname = remoteProfile.nickname,
            avatarUrl = remoteProfile.avatarUrl
        )
        userPreferences.saveUserProfile(profile)
        return profile
    }
}

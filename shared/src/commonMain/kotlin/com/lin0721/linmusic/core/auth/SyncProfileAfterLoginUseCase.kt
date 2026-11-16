package com.lin0721.linmusic.core.auth

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull

// 登录后同步新凭据的结果：区分成功、达到账户数量上限与校验失败（后两者均保留原活跃账户）
sealed interface LoginSyncResult {
    data class Success(val profile: UserProfile, val accountChanged: Boolean) : LoginSyncResult
    data object LimitReached : LoginSyncResult
    data object Failed : LoginSyncResult
}

// 登录成功后的账号同步，供 home/library/artist/playlist 等域共用
class SyncProfileAfterLoginUseCase(
    private val userPreferences: UserPreferences,
    private val authRepository: AuthRepository,
    private val accountSwitcherProvider: () -> AccountSwitcher? = { null }
) {

    // 先以新 Cookie 单独校验账号资料，不修改当前活跃会话；校验失败、取消或超 5 个账户上限时保留原活跃账户不动
    suspend fun syncAccount(cookies: String): LoginSyncResult {
        val previousUid = userPreferences.activeUid.first()
        val remoteProfile = authRepository.getAccountInfo(cookies).firstOrNull()?.getOrNull()?.profile
            ?: return LoginSyncResult.Failed

        val profile = UserProfile(
            uid = remoteProfile.userId,
            nickname = remoteProfile.nickname,
            avatarUrl = remoteProfile.avatarUrl
        )
        val result = userPreferences.upsertAccount(
            account = StoredAccount(
                uid = profile.uid,
                nickname = profile.nickname,
                avatarUrl = profile.avatarUrl,
                cookies = cookies
            ),
            activate = true
        )
        return when (result) {
            UpsertAccountResult.LimitReached -> LoginSyncResult.LimitReached
            is UpsertAccountResult.Added,
            is UpsertAccountResult.Updated -> {
                accountSwitcherProvider()?.onAccountActivatedAfterLogin(profile = profile, previousUid = previousUid)
                LoginSyncResult.Success(profile = profile, accountChanged = previousUid != profile.uid)
            }
        }
    }

    // 兼容旧调用方：成功返回资料，失败或超限时返回 null
    suspend operator fun invoke(cookies: String): UserProfile? =
        (syncAccount(cookies) as? LoginSyncResult.Success)?.profile
}

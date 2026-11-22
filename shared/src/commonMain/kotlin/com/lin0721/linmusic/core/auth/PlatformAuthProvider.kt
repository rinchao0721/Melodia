package com.lin0721.linmusic.core.auth

import com.lin0721.linmusic.core.log.AppLogger
import com.lin0721.linmusic.core.network.AppError
import com.lin0721.linmusic.core.platform.AccountId
import com.lin0721.linmusic.core.source.MusicPlatform
import kotlinx.coroutines.flow.first

private const val TAG = "PlatformAuthProvider"
private const val NETEASE_SESSION_COOKIE = "MUSIC_U"

// 跨平台二维码登录凭证
data class PlatformQrKey(
    val platform: MusicPlatform,
    val key: String,
    val qrUrl: String = "",
    val qrImageBase64: String? = null
)

// 跨平台二维码轮询状态
sealed interface PlatformQrPollResult {
    data object Waiting : PlatformQrPollResult
    data class Confirming(val nickname: String? = null, val avatarUrl: String? = null) : PlatformQrPollResult
    data class Authorized(val auth: PlatformAuthResult) : PlatformQrPollResult
    data object Expired : PlatformQrPollResult
    data class Failed(val message: String, val errorCode: Int = 0) : PlatformQrPollResult
}

// 平台登录/请求风控挑战信息（如酷狗 SSA 验证码、短信二次验证）
data class PlatformRiskChallenge(
    val platform: MusicPlatform,
    val ssaCode: String = "",
    val eventId: String = "",
    val message: String = "",
    val sid: String? = null,
    val edt: String? = null
)

// 平台鉴权成功结果（供各平台 Provider 统一写入 UserPreferences）
data class PlatformAuthResult(
    val platform: MusicPlatform,
    val accountId: AccountId,
    val nickname: String = "",
    val avatarUrl: String = "",
    val cookies: String = "",
    val credentials: Map<String, String> = emptyMap(),
    val vipLabel: String = "",
    val account: StoredAccount? = null
)

// 跨平台用户资料详情
data class PlatformUserDetail(
    val accountId: AccountId,
    val nickname: String = "",
    val avatarUrl: String = "",
    val vipLabel: String = "",
    val isVip: Boolean = false,
    val extra: Map<String, String> = emptyMap()
)

// 平台无关鉴权契约
interface PlatformAuthProvider {
    val platform: MusicPlatform

    suspend fun createQrLogin(): Result<PlatformQrKey>

    suspend fun pollQrLogin(key: String): Result<PlatformQrPollResult>

    suspend fun sendPhoneCaptcha(mobile: String): Result<Unit> =
        Result.failure(UnsupportedOperationException("${platform.displayName} 暂不支持手机验证码"))

    suspend fun loginByPhone(mobile: String, code: String, userid: String? = null): Result<PlatformAuthResult> =
        Result.failure(UnsupportedOperationException("${platform.displayName} 暂不支持手机验证码登录"))

    suspend fun refreshSession(account: StoredAccount): Result<PlatformAuthResult>

    suspend fun fetchUserDetail(account: StoredAccount): Result<PlatformUserDetail>

    suspend fun logout(account: StoredAccount): Result<Unit> = Result.success(Unit)
}

// 网易云音乐鉴权提供方（薄封装现有 AuthRepository，保持原有登录态流程）
class NeteaseAuthProvider(
    private val authRepository: AuthRepository,
    private val userPreferences: UserPreferences,
    private val nowMs: () -> Long = { System.currentTimeMillis() }
) : PlatformAuthProvider {

    override val platform: MusicPlatform = MusicPlatform.NETEASE

    override suspend fun createQrLogin(): Result<PlatformQrKey> =
        authRepository.getQrKey().first().mapCatching { resp ->
            val key = resp.unikey
            require(key.isNotBlank()) { "网易云二维码 key 为空" }
            PlatformQrKey(
                platform = MusicPlatform.NETEASE,
                key = key,
                qrUrl = "https://music.163.com/login?codekey=$key"
            )
        }

    override suspend fun pollQrLogin(key: String): Result<PlatformQrPollResult> =
        authRepository.checkQrStatus(key).first().map { resp ->
            when (resp.code) {
                800 -> PlatformQrPollResult.Expired
                801 -> PlatformQrPollResult.Waiting
                802 -> PlatformQrPollResult.Confirming()
                803 -> {
                    val cookies = resp.cookies.orEmpty()
                    val profile = authRepository.getAccountInfo(cookies).first().getOrNull()?.profile
                    if (profile == null) {
                        PlatformQrPollResult.Failed("获取网易云账号信息失败", resp.code)
                    } else {
                        val accountId = AccountId.netease(profile.userId)
                        val account = StoredAccount(
                            uid = profile.userId,
                            nickname = profile.nickname,
                            avatarUrl = profile.avatarUrl,
                            cookies = cookies,
                            lastLoginRefreshAt = nowMs(),
                            lastUsedAt = nowMs(),
                            platform = MusicPlatform.NETEASE,
                            platformUid = profile.userId.toString()
                        )
                        userPreferences.upsertPlatformAccount(account, activate = true)
                        PlatformQrPollResult.Authorized(
                            PlatformAuthResult(
                                platform = MusicPlatform.NETEASE,
                                accountId = accountId,
                                nickname = profile.nickname,
                                avatarUrl = profile.avatarUrl,
                                cookies = cookies,
                                account = account
                            )
                        )
                    }
                }
                else -> PlatformQrPollResult.Failed(resp.message ?: "网易云二维码状态异常", resp.code)
            }
        }

    override suspend fun refreshSession(account: StoredAccount): Result<PlatformAuthResult> {
        val headers = authRepository.refreshLogin().first().getOrElse { return Result.failure(it) }
        val merged = mergeCookies(account.cookies, headers)
        if (!merged.contains("$NETEASE_SESSION_COOKIE=")) {
            return Result.failure(AppError.Unauthorized)
        }
        val refreshedAt = nowMs()
        val updated = account.copy(cookies = merged, lastLoginRefreshAt = refreshedAt)
        userPreferences.upsertPlatformAccount(updated, activate = false)
        return Result.success(
            PlatformAuthResult(
                platform = MusicPlatform.NETEASE,
                accountId = updated.accountId,
                nickname = updated.nickname,
                avatarUrl = updated.avatarUrl,
                cookies = merged,
                account = updated
            )
        )
    }

    override suspend fun fetchUserDetail(account: StoredAccount): Result<PlatformUserDetail> =
        authRepository.getAccountInfo(account.cookies).first().mapCatching { resp ->
            val profile = resp.profile ?: throw AppError.Unauthorized
            PlatformUserDetail(
                accountId = AccountId.netease(profile.userId),
                nickname = profile.nickname,
                avatarUrl = profile.avatarUrl,
                vipLabel = account.vipLabel,
                isVip = account.vipLabel.isNotBlank()
            )
        }

    override suspend fun logout(account: StoredAccount): Result<Unit> =
        authRepository.logout().first()
            .onFailure { AppLogger.w(TAG, "网易云退出登录失败 uid=${account.uid}", it) }
}

// 平台鉴权注册表：按 MusicPlatform 分发对应 PlatformAuthProvider
class PlatformSessionRegistry(
    providers: List<PlatformAuthProvider>
) {
    private val providerMap: Map<MusicPlatform, PlatformAuthProvider> =
        providers.associateBy { it.platform }

    fun providerOf(platform: MusicPlatform): PlatformAuthProvider? = providerMap[platform]

    fun requireProvider(platform: MusicPlatform): PlatformAuthProvider =
        providerMap[platform] ?: error("未注册平台鉴权提供方: ${platform.key}")

    fun supportedPlatforms(): Set<MusicPlatform> = providerMap.keys
}

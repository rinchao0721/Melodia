package com.lin0721.linmusic.core.auth

import com.lin0721.linmusic.core.cache.MetadataCache
import com.lin0721.linmusic.core.kugou.KugouRiskException
import com.lin0721.linmusic.core.log.AppLogger
import com.lin0721.linmusic.core.network.AppError
import com.lin0721.linmusic.core.platform.AccountId
import com.lin0721.linmusic.core.player.PlaybackController
import com.lin0721.linmusic.core.source.MusicPlatform
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

private const val TAG = "SessionMonitor"
private const val CONFIRM_DELAY_MS = 1_500L
private const val REFRESH_INTERVAL_MS = 6L * 60 * 60 * 1000
private const val SESSION_COOKIE = "MUSIC_U"

// 酷狗明确标识 token 失效/需重新登录的业务错误码
private val KUGOU_EXPLICIT_INVALID_CODES = setOf(20018, 20019, 30013)

// 会话过期后的本地状态通知，供壳层区分“还有剩余账户已自动切换”与“已无登录账户”
data class ExpiredAccountNotice(
    val expiredProfile: UserProfile,
    val nextProfile: UserProfile?
)

// 跨平台账号过期通知（如酷狗会话失效回退/清理通知）
data class PlatformExpiredAccountNotice(
    val platform: MusicPlatform,
    val expiredAccount: StoredAccount,
    val nextAccount: StoredAccount?
)

// 登录会话的守护：启动与切号时校验并按间隔续期，运行中遇到 301 再核对一次，确认失效时仅移除当前失效账户并回退到最近账户
class SessionMonitor(
    private val userPreferences: UserPreferences,
    private val authRepository: AuthRepository,
    private val playbackControllerProvider: () -> PlaybackController? = { null },
    private val metadataCache: MetadataCache? = null,
    private val nowMs: () -> Long = { System.currentTimeMillis() },
    private val platformSessionRegistry: PlatformSessionRegistry? = null
) {
    constructor(
        userPreferences: UserPreferences,
        authRepository: AuthRepository,
        nowMs: () -> Long
    ) : this(
        userPreferences = userPreferences,
        authRepository = authRepository,
        playbackControllerProvider = { null },
        metadataCache = null,
        nowMs = nowMs,
        platformSessionRegistry = null
    )

    private val checkMutex = Mutex()
    private val kugouCheckMutex = Mutex()

    private val _expiredNotices = MutableSharedFlow<ExpiredAccountNotice>(
        extraBufferCapacity = 4,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    val expiredNotices: SharedFlow<ExpiredAccountNotice> = _expiredNotices.asSharedFlow()

    private val _platformExpiredNotices = MutableSharedFlow<PlatformExpiredAccountNotice>(
        extraBufferCapacity = 4,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    val platformExpiredNotices: SharedFlow<PlatformExpiredAccountNotice> = _platformExpiredNotices.asSharedFlow()

    @Volatile
    private var registeredOnExpired: (suspend () -> Unit)? = null

    // 常驻收集 301 事件；onExpired 在本地登录态清理/切换之后回调
    suspend fun watch(onExpired: suspend () -> Unit): Unit = coroutineScope {
        registeredOnExpired = onExpired
        launch { verifyAtStartup(onExpired) }
        AuthEvents.unauthorized.collect { handleUnauthorized(onExpired) }
    }

    suspend fun verifyAtStartup(onExpired: suspend () -> Unit) = coroutineScope {
        launch {
            checkMutex.withLock {
                verifyActiveLocked(onExpired, checkFallbackChain = true)
            }
        }
        launch {
            kugouCheckMutex.withLock {
                verifyKugouAtStartupLocked()
            }
        }
    }

    // 切到新账户后主动校验当前活跃会话，复用 watch 时注册的过期回调
    suspend fun verifyActiveAccount() {
        val onExpired = registeredOnExpired ?: suspend {}
        checkMutex.withLock {
            verifyActiveLocked(onExpired, checkFallbackChain = false)
        }
    }

    // 未登录时的 301 只是功能需要登录，不是过期
    suspend fun handleUnauthorized(onExpired: suspend () -> Unit) {
        checkMutex.withLock {
            if (!isLoggedInLocally()) return@withLock
            if (isSessionInvalid()) {
                expireLocked(onExpired)
            }
        }
    }

    private suspend fun verifyActiveLocked(
        onExpired: suspend () -> Unit,
        checkFallbackChain: Boolean
    ) {
        val visited = HashSet<Long>()
        repeat(UserPreferences.MAX_ACCOUNTS) {
            val activeUid = userPreferences.activeUid.first() ?: return
            if (!visited.add(activeUid)) return
            if (isSessionInvalid()) {
                val nextProfile = expireLocked(onExpired)
                if (!checkFallbackChain || nextProfile == null) return
            } else {
                refreshIfDue()
                return
            }
        }
    }

    // 启动时校验酷狗活跃账号：超 6 小时或首次校验刷新 token，并拉取最新资料/VIP；仅在明确 token 失效时移除酷狗账号，绝不影响网易云
    private suspend fun verifyKugouAtStartupLocked() {
        val provider = platformSessionRegistry?.providerOf(MusicPlatform.KUGOU) ?: return
        val visited = HashSet<String>()
        repeat(UserPreferences.MAX_ACCOUNTS) {
            val activeKugou = userPreferences.activeAccountOf(MusicPlatform.KUGOU).first() ?: return
            if (!visited.add(activeKugou.accountId.canonical)) return

            var currentAccount = activeKugou
            val refreshDue = currentAccount.lastLoginRefreshAt <= 0L ||
                (nowMs() - currentAccount.lastLoginRefreshAt) >= REFRESH_INTERVAL_MS

            if (refreshDue) {
                val refreshResult = provider.refreshSession(currentAccount)
                val refreshErr = refreshResult.exceptionOrNull()
                if (refreshErr != null) {
                    if (isExplicitKugouTokenInvalid(refreshErr)) {
                        expireKugouLocked(currentAccount, provider)
                        return@repeat
                    }
                    AppLogger.w(TAG, "酷狗会话续期遇到非致命异常，保留当前账号 accountId=${currentAccount.accountId.canonical}", refreshErr)
                    return
                }
                currentAccount = refreshResult.getOrNull()?.account
                    ?: userPreferences.activeAccountOf(MusicPlatform.KUGOU).first()
                    ?: currentAccount
            }

            val detailResult = provider.fetchUserDetail(currentAccount)
            val detailErr = detailResult.exceptionOrNull()
            if (detailErr != null) {
                if (isExplicitKugouTokenInvalid(detailErr)) {
                    expireKugouLocked(currentAccount, provider)
                    return@repeat
                }
                AppLogger.w(TAG, "酷狗拉取用户资料失败，保留当前账号 accountId=${currentAccount.accountId.canonical}", detailErr)
                return
            }

            val detail = detailResult.getOrNull() ?: return
            val updated = currentAccount.copy(
                nickname = detail.nickname.ifBlank { currentAccount.nickname },
                avatarUrl = detail.avatarUrl.ifBlank { currentAccount.avatarUrl },
                vipLabel = detail.vipLabel,
                lastLoginRefreshAt = if (currentAccount.lastLoginRefreshAt <= 0L) nowMs() else currentAccount.lastLoginRefreshAt
            )
            if (updated != currentAccount) {
                userPreferences.upsertPlatformAccount(updated, activate = false)
            }
            return
        }
    }

    private suspend fun expireKugouLocked(
        expiredAccount: StoredAccount,
        provider: PlatformAuthProvider
    ) {
        AppLogger.w(TAG, "酷狗会话已明确失效，移除失效酷狗账号 accountId=${expiredAccount.accountId.canonical}")
        runCatching { provider.logout(expiredAccount) }
        val nextId = userPreferences.removePlatformAccount(expiredAccount.accountId)
        metadataCache?.clearScope(MusicPlatform.KUGOU.key)
        metadataCache?.clearScope(expiredAccount.accountId.canonical)
        val nextAccount = if (nextId != null) {
            userPreferences.accountsOf(MusicPlatform.KUGOU).first().firstOrNull { it.accountId == nextId }
        } else {
            null
        }
        AccountEvents.notifyPlatformSwitched(
            platform = MusicPlatform.KUGOU,
            previousAccountId = expiredAccount.accountId,
            currentAccountId = nextAccount?.accountId,
            reason = AccountSwitchReason.EXPIRED
        )
        _platformExpiredNotices.tryEmit(
            PlatformExpiredAccountNotice(
                platform = MusicPlatform.KUGOU,
                expiredAccount = expiredAccount,
                nextAccount = nextAccount
            )
        )
    }

    private suspend fun isLoggedInLocally(): Boolean = userPreferences.userProfile.first() != null

    // 服务端明确答复未登录才算失效，并隔一小段时间复核一次防止偶发返回；网络错误等无法判断时按仍有效处理
    private suspend fun isSessionInvalid(): Boolean {
        if (!queryInvalid()) return false
        delay(CONFIRM_DELAY_MS)
        return queryInvalid()
    }

    private suspend fun queryInvalid(): Boolean = authRepository.getAccountInfo().first().fold(
        onSuccess = { it.profile == null },
        onFailure = { it is AppError.Unauthorized }
    )

    private suspend fun expireLocked(onExpired: suspend () -> Unit): UserProfile? {
        val activeUid = userPreferences.activeUid.first() ?: return null
        val expiredAccount = userPreferences.storedAccounts.first()
            .firstOrNull { it.uid == activeUid }
        val expiredProfile = expiredAccount?.toUserProfile()
            ?: userPreferences.userProfile.first()
            ?: UserProfile(uid = activeUid, nickname = "", avatarUrl = "")

        AppLogger.w(TAG, "账户会话已失效，移除失效账户 uid=$activeUid")
        playbackControllerProvider()?.stopAndClearQueue()
        // 必须在 removeAccount 前调用 logout，保证请求仍携带即将失效的账户凭据且不误注销回落账户
        runCatching { authRepository.logout().first() }
        val nextUid = userPreferences.removeAccount(activeUid)
        metadataCache?.clearAll()

        val nextAccount = if (nextUid != null) {
            userPreferences.storedAccounts.first().firstOrNull { it.uid == nextUid }
        } else {
            null
        }
        val nextProfile = nextAccount?.toUserProfile()
        AccountEvents.notifySwitched(
            previousUid = activeUid,
            currentUid = nextProfile?.uid,
            reason = AccountSwitchReason.EXPIRED
        )
        AccountEvents.notifyPlatformSwitched(
            platform = MusicPlatform.NETEASE,
            previousAccountId = AccountId.netease(activeUid),
            currentAccountId = nextProfile?.uid?.let { AccountId.netease(it) },
            reason = AccountSwitchReason.EXPIRED
        )
        _expiredNotices.tryEmit(
            ExpiredAccountNotice(
                expiredProfile = expiredProfile,
                nextProfile = nextProfile
            )
        )
        if (expiredAccount != null) {
            _platformExpiredNotices.tryEmit(
                PlatformExpiredAccountNotice(
                    platform = MusicPlatform.NETEASE,
                    expiredAccount = expiredAccount,
                    nextAccount = nextAccount
                )
            )
        }
        onExpired()
        return nextProfile
    }

    private suspend fun refreshIfDue() {
        if (nowMs() - userPreferences.lastLoginRefreshAt.first() < REFRESH_INTERVAL_MS) return
        authRepository.refreshLogin().first()
            .onSuccess { headers ->
                val merged = mergeCookies(userPreferences.cookies.first(), headers)
                // 刷新响应异常时不覆盖现有会话
                if (merged.contains("$SESSION_COOKIE=")) {
                    userPreferences.saveCookies(merged)
                    userPreferences.saveLastLoginRefreshAt(nowMs())
                }
            }
            .onFailure { AppLogger.w(TAG, "登录续期失败", it) }
    }
}

// 判定酷狗异常是否为“服务端明确拒绝 token / 登录态失效”；网络波动与风控挑战不在此列，避免误踢账号
internal fun isExplicitKugouTokenInvalid(error: Throwable?): Boolean {
    if (error == null) return false
    if (error is AppError.Unauthorized) return true
    if (error is KugouRiskException || error is AppError.NetworkError || error is AppError.RiskControl) return false
    if (error is AppError.BizError) {
        if (error.code in KUGOU_EXPLICIT_INVALID_CODES) return true
        val msg = error.rawMsg.orEmpty().lowercase()
        val hasTokenInvalidWord = msg.contains("token") &&
            (msg.contains("无效") || msg.contains("过期") || msg.contains("失效") ||
                msg.contains("invalid") || msg.contains("expired"))
        if (hasTokenInvalidWord) return true
        if (msg.contains("登录已失效") || msg.contains("登录过期") ||
            msg.contains("请重新登录") || msg.contains("请先登录")
        ) {
            return true
        }
    }
    return false
}

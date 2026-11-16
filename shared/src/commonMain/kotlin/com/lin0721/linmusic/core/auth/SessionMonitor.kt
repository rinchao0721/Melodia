package com.lin0721.linmusic.core.auth

import com.lin0721.linmusic.core.cache.MetadataCache
import com.lin0721.linmusic.core.log.AppLogger
import com.lin0721.linmusic.core.network.AppError
import com.lin0721.linmusic.core.player.PlaybackController
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

// 会话过期后的本地状态通知，供壳层区分“还有剩余账户已自动切换”与“已无登录账户”
data class ExpiredAccountNotice(
    val expiredProfile: UserProfile,
    val nextProfile: UserProfile?
)

// 登录会话的守护：启动与切号时校验并按间隔续期，运行中遇到 301 再核对一次，确认失效时仅移除当前失效账户并回退到最近账户
class SessionMonitor(
    private val userPreferences: UserPreferences,
    private val authRepository: AuthRepository,
    private val playbackControllerProvider: () -> PlaybackController? = { null },
    private val metadataCache: MetadataCache? = null,
    private val nowMs: () -> Long = { System.currentTimeMillis() }
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
        nowMs = nowMs
    )

    private val checkMutex = Mutex()
    private val _expiredNotices = MutableSharedFlow<ExpiredAccountNotice>(
        extraBufferCapacity = 4,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    val expiredNotices: SharedFlow<ExpiredAccountNotice> = _expiredNotices.asSharedFlow()

    @Volatile
    private var registeredOnExpired: (suspend () -> Unit)? = null

    // 常驻收集 301 事件；onExpired 在本地登录态清理/切换之后回调
    suspend fun watch(onExpired: suspend () -> Unit): Unit = coroutineScope {
        registeredOnExpired = onExpired
        launch { verifyAtStartup(onExpired) }
        AuthEvents.unauthorized.collect { handleUnauthorized(onExpired) }
    }

    suspend fun verifyAtStartup(onExpired: suspend () -> Unit) {
        checkMutex.withLock {
            verifyActiveLocked(onExpired, checkFallbackChain = true)
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
        val expiredProfile = userPreferences.storedAccounts.first()
            .firstOrNull { it.uid == activeUid }
            ?.toUserProfile()
            ?: userPreferences.userProfile.first()
            ?: UserProfile(uid = activeUid, nickname = "", avatarUrl = "")

        AppLogger.w(TAG, "账户会话已失效，移除失效账户 uid=$activeUid")
        playbackControllerProvider()?.stopAndClearQueue()
        // 必须在 removeAccount 前调用 logout，保证请求仍携带即将失效的账户凭据且不误注销回落账户
        runCatching { authRepository.logout().first() }
        val nextUid = userPreferences.removeAccount(activeUid)
        metadataCache?.clearAll()

        val nextProfile = if (nextUid != null) {
            userPreferences.storedAccounts.first().firstOrNull { it.uid == nextUid }?.toUserProfile()
        } else {
            null
        }
        AccountEvents.notifySwitched(
            previousUid = activeUid,
            currentUid = nextProfile?.uid,
            reason = AccountSwitchReason.EXPIRED
        )
        _expiredNotices.tryEmit(
            ExpiredAccountNotice(
                expiredProfile = expiredProfile,
                nextProfile = nextProfile
            )
        )
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

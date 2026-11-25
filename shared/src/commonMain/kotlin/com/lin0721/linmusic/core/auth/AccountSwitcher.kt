package com.lin0721.linmusic.core.auth

import com.lin0721.linmusic.core.cache.MetadataCache
import com.lin0721.linmusic.core.kugou.runCatchingCancellable
import com.lin0721.linmusic.core.log.AppLogger
import com.lin0721.linmusic.core.platform.AccountId
import com.lin0721.linmusic.core.player.PlaybackController
import com.lin0721.linmusic.core.source.MusicPlatform
import com.lin0721.linmusic.feature.playlist.domain.SongCollectDelegate
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

private const val TAG = "AccountSwitcher"

// 账户切换、退出与新登录切入协调器：统一收口停止播放清队列、切换活跃账户、清缓存、广播事件与会话校验
class AccountSwitcher(
    private val userPreferences: UserPreferences,
    private val authRepository: AuthRepository,
    private val metadataCache: MetadataCache? = null,
    private val playbackControllerProvider: () -> PlaybackController? = { null },
    private val sessionMonitorProvider: () -> SessionMonitor? = { null },
    private val platformSessionRegistry: PlatformSessionRegistry? = null
) {
    private val switchMutex = Mutex()

    // 统一切换入口：平台或账号有变化时停播清队列 -> 切活跃账号 -> 写当前平台 -> 清缓存 -> 广播事件 -> 校验网易会话
    suspend fun switchToAccount(accountId: AccountId): Boolean {
        val switched = switchMutex.withLock {
            val target = userPreferences.allAccounts.first().firstOrNull { it.accountId == accountId }
                ?: return false
            val platform = target.platform
            val previousPlatform = userPreferences.currentPlatform.first()
            val previousActive = userPreferences.activeAccountOf(platform).first()?.accountId
            val accountChanged = previousActive != accountId
            if (!accountChanged && previousPlatform == platform) return false
            playbackControllerProvider()?.stopAndClearForAccountChange()
            if (!userPreferences.switchPlatformAccount(accountId)) return false
            userPreferences.setCurrentPlatform(platform)
            if (accountChanged) {
                clearCacheScopesOf(platform, previousActive, accountId)
            }
            if (platform == MusicPlatform.NETEASE && accountChanged) {
                AccountEvents.notifySwitched(
                    previousUid = previousActive?.neteaseIdOrNull(),
                    currentUid = target.uid,
                    reason = AccountSwitchReason.SWITCHED
                )
            }
            AccountEvents.notifyPlatformSwitched(
                platform = platform,
                previousAccountId = previousActive,
                currentAccountId = accountId,
                reason = AccountSwitchReason.SWITCHED
            )
            true
        }
        if (!switched) return false
        AppLogger.i(TAG, "已切换账户 accountId=${accountId.canonical}")
        if (accountId.platform == MusicPlatform.NETEASE) {
            sessionMonitorProvider()?.verifyActiveAccount()
        }
        return true
    }

    // 切到网易游客：清队列、置空网易活跃账号（不删除已存账号）、当前平台改为网易
    suspend fun browseAsGuest(): Boolean = switchMutex.withLock {
        val previousPlatform = userPreferences.currentPlatform.first()
        val previousNetease = userPreferences.activeAccountOf(MusicPlatform.NETEASE).first()?.accountId
        if (previousPlatform == MusicPlatform.NETEASE && previousNetease == null) return@withLock false
        playbackControllerProvider()?.stopAndClearForAccountChange()
        if (previousNetease != null) {
            userPreferences.deactivatePlatform(MusicPlatform.NETEASE)
            clearNeteaseCacheScopes(previousNetease.neteaseIdOrNull())
        }
        userPreferences.setCurrentPlatform(MusicPlatform.NETEASE)
        if (previousNetease != null) {
            AccountEvents.notifySwitched(
                previousUid = previousNetease.neteaseIdOrNull(),
                currentUid = null,
                reason = AccountSwitchReason.SWITCHED
            )
        }
        AccountEvents.notifyPlatformSwitched(
            platform = MusicPlatform.NETEASE,
            previousAccountId = previousNetease,
            currentAccountId = null,
            reason = AccountSwitchReason.SWITCHED
        )
        AppLogger.i(TAG, "已切换为网易游客")
        true
    }

    // 进入本地模式：停播清队列、当前平台改为 LOCAL，保留所有已登录账号与各平台活跃账号
    suspend fun enterLocalMode(): Boolean = switchMutex.withLock {
        val previousPlatform = userPreferences.currentPlatform.first()
        if (previousPlatform == MusicPlatform.LOCAL) return@withLock false
        val previousAccountId = userPreferences.currentAccountId.first()
        playbackControllerProvider()?.stopAndClearForAccountChange()
        userPreferences.setCurrentPlatform(MusicPlatform.LOCAL)
        AccountEvents.notifyPlatformSwitched(
            platform = MusicPlatform.LOCAL,
            previousAccountId = previousAccountId,
            currentAccountId = null,
            reason = AccountSwitchReason.SWITCHED
        )
        AppLogger.i(TAG, "已进入本地模式")
        true
    }

    // 退出指定账号；若为当前账号按 同平台最近账号 -> 另一平台账号 -> 网易游客 回落，返回退出后的当前账号
    suspend fun logout(accountId: AccountId): AccountId? = switchMutex.withLock {
        val target = userPreferences.allAccounts.first().firstOrNull { it.accountId == accountId }
            ?: return@withLock userPreferences.currentAccountId.first()
        val platform = target.platform
        val isCurrent = userPreferences.currentAccountId.first() == accountId
        val wasActive = userPreferences.activeAccountOf(platform).first()?.accountId == accountId
        if (isCurrent) {
            playbackControllerProvider()?.stopAndClearForAccountChange()
        }
        if (platform == MusicPlatform.NETEASE && wasActive) {
            // 须在移除前调用，保证请求携带即将退出账号的凭据
            runCatchingCancellable { authRepository.logout().first() }
                .onFailure { AppLogger.w(TAG, "服务端退出登录失败，继续移除本地账户 accountId=${accountId.canonical}", it) }
        } else {
            runCatchingCancellable { platformSessionRegistry?.providerOf(platform)?.logout(target) }
                .onFailure { AppLogger.w(TAG, "平台服务端退出失败，继续移除本地账户 accountId=${accountId.canonical}", it) }
        }
        val nextSame = userPreferences.removePlatformAccount(accountId)
        clearCacheScopesOf(platform, accountId, nextSame)
        if (platform == MusicPlatform.NETEASE && wasActive) {
            AccountEvents.notifySwitched(
                previousUid = target.uid,
                currentUid = nextSame?.neteaseIdOrNull(),
                reason = AccountSwitchReason.LOGGED_OUT
            )
        }
        AccountEvents.notifyPlatformSwitched(
            platform = platform,
            previousAccountId = accountId,
            currentAccountId = nextSame,
            reason = AccountSwitchReason.LOGGED_OUT
        )
        if (!isCurrent || nextSame != null) {
            AppLogger.i(TAG, "已退出账户 accountId=${accountId.canonical}, nextAccountId=${nextSame?.canonical}")
            return@withLock userPreferences.currentAccountId.first()
        }
        val fallback = fallbackAccountOutside(platform)
        if (fallback == null) {
            userPreferences.setCurrentPlatform(MusicPlatform.NETEASE)
            if (platform != MusicPlatform.NETEASE) {
                AccountEvents.notifyPlatformSwitched(
                    platform = MusicPlatform.NETEASE,
                    previousAccountId = null,
                    currentAccountId = null,
                    reason = AccountSwitchReason.SWITCHED
                )
            }
            AppLogger.i(TAG, "已退出账户 accountId=${accountId.canonical}, 回落为网易游客")
            return@withLock null
        }
        val fallbackPlatform = fallback.platform
        val previousActive = userPreferences.activeAccountOf(fallbackPlatform).first()?.accountId
        if (previousActive != fallback.accountId) {
            userPreferences.switchPlatformAccount(fallback.accountId)
            clearCacheScopesOf(fallbackPlatform, previousActive, fallback.accountId)
            if (fallbackPlatform == MusicPlatform.NETEASE) {
                AccountEvents.notifySwitched(
                    previousUid = previousActive?.neteaseIdOrNull(),
                    currentUid = fallback.uid,
                    reason = AccountSwitchReason.SWITCHED
                )
            }
        }
        userPreferences.setCurrentPlatform(fallbackPlatform)
        AccountEvents.notifyPlatformSwitched(
            platform = fallbackPlatform,
            previousAccountId = previousActive,
            currentAccountId = fallback.accountId,
            reason = AccountSwitchReason.SWITCHED
        )
        AppLogger.i(TAG, "已退出账户 accountId=${accountId.canonical}, 回落至 ${fallback.accountId.canonical}")
        fallback.accountId
    }

    // 其他平台的回落账号：优先该平台活跃账号，否则取最近使用的账号
    private suspend fun fallbackAccountOutside(platform: MusicPlatform): StoredAccount? {
        val others = userPreferences.allAccounts.first().filter { it.platform != platform }
        if (others.isEmpty()) return null
        val actives = userPreferences.activeAccountsByPlatform.first()
        return others.firstOrNull { actives[it.platform]?.accountId == it.accountId } ?: others.first()
    }

    // 兼容旧入口：切换到指定网易账号
    suspend fun switchTo(uid: Long): Boolean = switchToAccount(AccountId.netease(uid))

    // 兼容旧入口：退出当前活跃网易账号，返回退出后的活跃网易账号资料
    suspend fun logoutCurrent(): UserProfile? {
        val currentUid = userPreferences.activeUid.first()
        if (currentUid == null) {
            switchMutex.withLock {
                playbackControllerProvider()?.stopAndClearForAccountChange()
                userPreferences.clearUserProfile()
                clearNeteaseCacheScopes()
                AccountEvents.notifySwitched(
                    previousUid = null,
                    currentUid = null,
                    reason = AccountSwitchReason.LOGGED_OUT
                )
                AccountEvents.notifyPlatformSwitched(
                    platform = MusicPlatform.NETEASE,
                    previousAccountId = null,
                    currentAccountId = null,
                    reason = AccountSwitchReason.LOGGED_OUT
                )
            }
            return null
        }
        logout(AccountId.netease(currentUid))
        return userPreferences.activeAccountOf(MusicPlatform.NETEASE).first()?.toUserProfile()
    }

    // 网易登录写库后的收尾：账号或平台有变化时停播清队列，当前平台切到网易，广播 ADDED 事件
    suspend fun onAccountActivatedAfterLogin(profile: UserProfile, previousUid: Long?) {
        switchMutex.withLock {
            val previousPlatform = userPreferences.currentPlatform.first()
            val switchingFromOther = previousUid != null && previousUid != profile.uid
            if (switchingFromOther || previousPlatform != MusicPlatform.NETEASE) {
                playbackControllerProvider()?.stopAndClearForAccountChange()
            }
            if (switchingFromOther) {
                clearNeteaseCacheScopes(previousUid, profile.uid)
            }
            userPreferences.setCurrentPlatform(MusicPlatform.NETEASE)
            AccountEvents.notifySwitched(
                previousUid = previousUid,
                currentUid = profile.uid,
                reason = AccountSwitchReason.ADDED
            )
            AccountEvents.notifyPlatformSwitched(
                platform = MusicPlatform.NETEASE,
                previousAccountId = previousUid?.let { AccountId.netease(it) },
                currentAccountId = AccountId.netease(profile.uid),
                reason = AccountSwitchReason.ADDED
            )
        }
    }

    // 切换指定平台活跃账号：网易云走完整切号链路；其他平台仅切换对应平台并清理该平台缓存命名空间，不影响其他平台
    suspend fun switchPlatformAccount(accountId: AccountId): Boolean {
        if (accountId.platform == MusicPlatform.NETEASE) {
            val uid = accountId.rawId.toLongOrNull() ?: return false
            return switchTo(uid)
        }
        val switched = switchMutex.withLock {
            val current = userPreferences.activeAccountOf(accountId.platform).first()
            if (current?.accountId == accountId) return false
            val target = userPreferences.accountsOf(accountId.platform).first()
                .firstOrNull { it.accountId == accountId }
                ?: return false
            if (!userPreferences.switchPlatformAccount(accountId)) return false
            clearPlatformCacheScopes(accountId.platform, current?.accountId, accountId)
            AccountEvents.notifyPlatformSwitched(
                platform = accountId.platform,
                previousAccountId = current?.accountId,
                currentAccountId = target.accountId,
                reason = AccountSwitchReason.SWITCHED
            )
            true
        }
        if (switched) {
            AppLogger.i(TAG, "已切换平台账户 accountId=${accountId.canonical}")
        }
        return switched
    }

    // 独立退出指定平台账号：调用对应平台 Provider 的 logout 后移除本地凭据并广播事件，绝不影响其他平台登录态
    suspend fun logoutPlatformAccount(accountId: AccountId): AccountId? {
        if (accountId.platform == MusicPlatform.NETEASE) {
            val uid = accountId.rawId.toLongOrNull()
            val activeNeteaseUid = userPreferences.activeUid.first()
            if (uid != null && uid == activeNeteaseUid) {
                return logoutCurrent()?.uid?.let { AccountId.netease(it) }
            }
            return switchMutex.withLock {
                val target = userPreferences.accountsOf(MusicPlatform.NETEASE).first()
                    .firstOrNull { it.accountId == accountId }
                    ?: return@withLock null
                runCatching { platformSessionRegistry?.providerOf(MusicPlatform.NETEASE)?.logout(target) }
                    .onFailure { AppLogger.w(TAG, "服务端退出非活跃网易账号失败 accountId=${accountId.canonical}", it) }
                val nextId = userPreferences.removePlatformAccount(accountId)
                clearPlatformCacheScopes(MusicPlatform.NETEASE, accountId, nextId)
                AccountEvents.notifyPlatformSwitched(
                    platform = MusicPlatform.NETEASE,
                    previousAccountId = accountId,
                    currentAccountId = nextId,
                    reason = AccountSwitchReason.LOGGED_OUT
                )
                nextId
            }
        }

        return switchMutex.withLock {
            val target = userPreferences.accountsOf(accountId.platform).first()
                .firstOrNull { it.accountId == accountId }
                ?: return@withLock null
            runCatching { platformSessionRegistry?.providerOf(accountId.platform)?.logout(target) }
                .onFailure { AppLogger.w(TAG, "平台服务端退出失败，继续移除本地账户 accountId=${accountId.canonical}", it) }
            val nextId = userPreferences.removePlatformAccount(accountId)
            clearPlatformCacheScopes(accountId.platform, accountId, nextId)
            AccountEvents.notifyPlatformSwitched(
                platform = accountId.platform,
                previousAccountId = accountId,
                currentAccountId = nextId,
                reason = AccountSwitchReason.LOGGED_OUT
            )
            AppLogger.i(TAG, "已退出平台账户 accountId=${accountId.canonical}, nextAccountId=${nextId?.canonical}")
            nextId
        }
    }

    // 非网易平台登录成功收尾：广播 ADDED 事件并在已注册 Provider 时同步最新用户资料/VIP 标签
    suspend fun onPlatformAccountLoggedIn(account: StoredAccount, previousAccountId: AccountId?) {
        if (account.platform == MusicPlatform.NETEASE) {
            onAccountActivatedAfterLogin(account.toUserProfile(), previousAccountId?.rawId?.toLongOrNull())
            return
        }
        switchMutex.withLock {
            val previousPlatform = userPreferences.currentPlatform.first()
            val accountChanged = previousAccountId != null && previousAccountId != account.accountId
            if (accountChanged || previousPlatform != account.platform) {
                playbackControllerProvider()?.stopAndClearForAccountChange()
            }
            if (accountChanged) {
                clearPlatformCacheScopes(account.platform, previousAccountId, account.accountId)
            }
            userPreferences.setCurrentPlatform(account.platform)
            AccountEvents.notifyPlatformSwitched(
                platform = account.platform,
                previousAccountId = previousAccountId,
                currentAccountId = account.accountId,
                reason = AccountSwitchReason.ADDED
            )
        }
        val provider = platformSessionRegistry?.providerOf(account.platform) ?: return
        runCatching { provider.fetchUserDetail(account).getOrNull() }
            .onSuccess { detail ->
                if (detail != null) {
                    val enriched = account.copy(
                        nickname = detail.nickname.ifBlank { account.nickname },
                        avatarUrl = detail.avatarUrl.ifBlank { account.avatarUrl },
                        vipLabel = detail.vipLabel.ifBlank { account.vipLabel }
                    )
                    if (enriched != account) {
                        userPreferences.upsertPlatformAccount(enriched, activate = false)
                    }
                }
            }
            .onFailure { AppLogger.w(TAG, "登录后同步平台资料失败 accountId=${account.accountId.canonical}", it) }
    }

    private suspend fun clearCacheScopesOf(platform: MusicPlatform, vararg accountIds: AccountId?) {
        if (platform == MusicPlatform.NETEASE) {
            clearNeteaseCacheScopes(*accountIds.map { it?.neteaseIdOrNull() }.toTypedArray())
        } else {
            clearPlatformCacheScopes(platform, *accountIds)
        }
    }

    private suspend fun clearNeteaseCacheScopes(vararg uids: Long?) {
        SongCollectDelegate.clearNeteaseCache()
        val cache = metadataCache ?: return
        cache.clearScope(MusicPlatform.NETEASE.key)
        cache.clearScope("0")
        uids.filterNotNull().distinct().forEach { uid ->
            cache.clearScope(uid.toString())
            cache.clearScope(AccountId.netease(uid).canonical)
        }
    }

    private suspend fun clearPlatformCacheScopes(
        platform: MusicPlatform,
        vararg accountIds: AccountId?
    ) {
        when (platform) {
            MusicPlatform.KUGOU -> SongCollectDelegate.clearKugouCache()
            MusicPlatform.NETEASE -> SongCollectDelegate.clearNeteaseCache()
            else -> Unit
        }
        val cache = metadataCache ?: return
        cache.clearScope(platform.key)
        if (platform == MusicPlatform.NETEASE) {
            cache.clearScope("0")
        }
        accountIds.filterNotNull().distinctBy { it.canonical }.forEach { id ->
            cache.clearScope(id.canonical)
            if (platform == MusicPlatform.NETEASE) {
                cache.clearScope(id.rawId)
            }
        }
    }
}

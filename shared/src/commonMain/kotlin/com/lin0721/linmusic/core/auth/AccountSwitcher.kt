package com.lin0721.linmusic.core.auth

import com.lin0721.linmusic.core.cache.MetadataCache
import com.lin0721.linmusic.core.log.AppLogger
import com.lin0721.linmusic.core.platform.AccountId
import com.lin0721.linmusic.core.player.PlaybackController
import com.lin0721.linmusic.core.source.MusicPlatform
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

    // 切换到指定已登录账户：停止播放并清空队列 -> switchTo(uid) 并刷新 lastUsedAt -> 广播切换事件 -> 校验新账户会话
    suspend fun switchTo(uid: Long): Boolean {
        val switchedProfile = switchMutex.withLock {
            val currentUid = userPreferences.activeUid.first()
            if (currentUid == uid) return false
            val targetAccount = userPreferences.storedAccounts.first().firstOrNull { it.uid == uid }
                ?: return false
            playbackControllerProvider()?.stopAndClearQueue()
            if (!userPreferences.switchTo(uid)) return false
            clearNeteaseCacheScopes(currentUid, uid)
            val profile = targetAccount.toUserProfile()
            AccountEvents.notifySwitched(
                previousUid = currentUid,
                currentUid = profile.uid,
                reason = AccountSwitchReason.SWITCHED
            )
            AccountEvents.notifyPlatformSwitched(
                platform = MusicPlatform.NETEASE,
                previousAccountId = currentUid?.let { AccountId.netease(it) },
                currentAccountId = AccountId.netease(profile.uid),
                reason = AccountSwitchReason.SWITCHED
            )
            profile
        }
        AppLogger.i(TAG, "已切换账户 uid=${switchedProfile.uid}")
        sessionMonitorProvider()?.verifyActiveAccount()
        return true
    }

    // 退出当前账户：停播清队列 -> 带着当前账户凭据调用服务端 logout -> removeAccount 并切到最近剩余账户 -> 广播事件
    suspend fun logoutCurrent(): UserProfile? = switchMutex.withLock {
        val currentUid = userPreferences.activeUid.first()
        playbackControllerProvider()?.stopAndClearQueue()
        if (currentUid == null) {
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
            return@withLock null
        }
        runCatching { authRepository.logout().first() }
            .onFailure { AppLogger.w(TAG, "服务端退出登录失败，继续移除本地账户 uid=$currentUid", it) }
        val nextUid = userPreferences.removeAccount(currentUid)
        clearNeteaseCacheScopes(currentUid, nextUid)
        val nextProfile = if (nextUid != null) {
            userPreferences.storedAccounts.first().firstOrNull { it.uid == nextUid }?.toUserProfile()
        } else {
            null
        }
        AccountEvents.notifySwitched(
            previousUid = currentUid,
            currentUid = nextProfile?.uid,
            reason = AccountSwitchReason.LOGGED_OUT
        )
        AccountEvents.notifyPlatformSwitched(
            platform = MusicPlatform.NETEASE,
            previousAccountId = AccountId.netease(currentUid),
            currentAccountId = nextProfile?.uid?.let { AccountId.netease(it) },
            reason = AccountSwitchReason.LOGGED_OUT
        )
        AppLogger.i(TAG, "已退出账户 uid=$currentUid, 切换至 nextUid=$nextUid")
        nextProfile
    }

    // 登录成功写库后的切换收尾：若此前活跃着另一账户则先停播清队列并清缓存，随后广播 ADDED 事件
    suspend fun onAccountActivatedAfterLogin(profile: UserProfile, previousUid: Long?) {
        switchMutex.withLock {
            val switchingFromOther = previousUid != null && previousUid != profile.uid
            if (switchingFromOther) {
                playbackControllerProvider()?.stopAndClearQueue()
                clearNeteaseCacheScopes(previousUid, profile.uid)
            }
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
            if (previousAccountId != null && previousAccountId != account.accountId) {
                clearPlatformCacheScopes(account.platform, previousAccountId, account.accountId)
            }
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

    private suspend fun clearNeteaseCacheScopes(vararg uids: Long?) {
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

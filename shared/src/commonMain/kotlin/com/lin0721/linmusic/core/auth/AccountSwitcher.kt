package com.lin0721.linmusic.core.auth

import com.lin0721.linmusic.core.cache.MetadataCache
import com.lin0721.linmusic.core.log.AppLogger
import com.lin0721.linmusic.core.player.PlaybackController
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
    private val sessionMonitorProvider: () -> SessionMonitor? = { null }
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
            metadataCache?.clearAll()
            val profile = targetAccount.toUserProfile()
            AccountEvents.notifySwitched(
                previousUid = currentUid,
                currentUid = profile.uid,
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
            metadataCache?.clearAll()
            AccountEvents.notifySwitched(
                previousUid = null,
                currentUid = null,
                reason = AccountSwitchReason.LOGGED_OUT
            )
            return@withLock null
        }
        runCatching { authRepository.logout().first() }
            .onFailure { AppLogger.w(TAG, "服务端退出登录失败，继续移除本地账户 uid=$currentUid", it) }
        val nextUid = userPreferences.removeAccount(currentUid)
        metadataCache?.clearAll()
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
        AppLogger.i(TAG, "已退出账户 uid=$currentUid, 切换至 nextUid=$nextUid")
        nextProfile
    }

    // 登录成功写库后的切换收尾：若此前活跃着另一账户则先停播清队列并清缓存，随后广播 ADDED 事件
    suspend fun onAccountActivatedAfterLogin(profile: UserProfile, previousUid: Long?) {
        switchMutex.withLock {
            val switchingFromOther = previousUid != null && previousUid != profile.uid
            if (switchingFromOther) {
                playbackControllerProvider()?.stopAndClearQueue()
                metadataCache?.clearAll()
            }
            AccountEvents.notifySwitched(
                previousUid = previousUid,
                currentUid = profile.uid,
                reason = AccountSwitchReason.ADDED
            )
        }
    }
}

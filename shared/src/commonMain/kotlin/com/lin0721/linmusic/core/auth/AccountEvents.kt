package com.lin0721.linmusic.core.auth

import com.lin0721.linmusic.core.platform.AccountId
import com.lin0721.linmusic.core.source.MusicPlatform
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

// 账户切换触发原因
enum class AccountSwitchReason {
    SWITCHED,
    ADDED,
    LOGGED_OUT,
    EXPIRED
}

// 活跃账户变化事件，供未直接订阅 uid 的页面/管理器联动刷新（网易向后兼容流）
data class AccountSwitchEvent(
    val previousUid: Long?,
    val currentUid: Long?,
    val reason: AccountSwitchReason
)

// 跨平台活跃账户变化事件
data class PlatformAccountChangeEvent(
    val platform: MusicPlatform,
    val previousAccountId: AccountId?,
    val currentAccountId: AccountId?,
    val reason: AccountSwitchReason
)

// 全局账户切换事件总线；无订阅者时直接丢弃
object AccountEvents {
    private val _switched = MutableSharedFlow<AccountSwitchEvent>(
        extraBufferCapacity = 4,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    val switched: SharedFlow<AccountSwitchEvent> = _switched.asSharedFlow()

    private val _platformSwitched = MutableSharedFlow<PlatformAccountChangeEvent>(
        extraBufferCapacity = 8,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    val platformSwitched: SharedFlow<PlatformAccountChangeEvent> = _platformSwitched.asSharedFlow()

    fun notifySwitched(previousUid: Long?, currentUid: Long?, reason: AccountSwitchReason) {
        _switched.tryEmit(AccountSwitchEvent(previousUid, currentUid, reason))
    }

    fun notifyPlatformSwitched(
        platform: MusicPlatform,
        previousAccountId: AccountId?,
        currentAccountId: AccountId?,
        reason: AccountSwitchReason
    ) {
        _platformSwitched.tryEmit(
            PlatformAccountChangeEvent(
                platform = platform,
                previousAccountId = previousAccountId,
                currentAccountId = currentAccountId,
                reason = reason
            )
        )
    }
}

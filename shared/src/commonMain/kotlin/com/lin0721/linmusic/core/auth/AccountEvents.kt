package com.lin0721.linmusic.core.auth

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

// 活跃账户变化事件，供未直接订阅 uid 的页面/管理器联动刷新
data class AccountSwitchEvent(
    val previousUid: Long?,
    val currentUid: Long?,
    val reason: AccountSwitchReason
)

// 全局账户切换事件总线；无订阅者时直接丢弃
object AccountEvents {
    private val _switched = MutableSharedFlow<AccountSwitchEvent>(
        extraBufferCapacity = 4,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    val switched: SharedFlow<AccountSwitchEvent> = _switched.asSharedFlow()

    fun notifySwitched(previousUid: Long?, currentUid: Long?, reason: AccountSwitchReason) {
        _switched.tryEmit(AccountSwitchEvent(previousUid, currentUid, reason))
    }
}

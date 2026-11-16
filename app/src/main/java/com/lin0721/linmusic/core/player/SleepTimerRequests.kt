package com.lin0721.linmusic.core.player

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.getAndUpdate

const val ACTION_OPEN_SLEEP_TIMER_FROM_NOTIFICATION = "com.lin0721.linmusic.action.OPEN_SLEEP_TIMER"

// 通知栏定时关闭按钮拉起 App 后，由播放页消费一次并打开定时关闭面板
class SleepTimerRequests {

    private val _pendingOpenTimer = MutableStateFlow(false)
    val pendingOpenTimer: StateFlow<Boolean> = _pendingOpenTimer.asStateFlow()

    fun requestOpenTimer() {
        _pendingOpenTimer.value = true
    }

    fun consumeOpenTimer(): Boolean = _pendingOpenTimer.getAndUpdate { false }
}

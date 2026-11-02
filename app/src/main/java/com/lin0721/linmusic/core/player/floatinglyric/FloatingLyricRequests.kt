package com.lin0721.linmusic.core.player.floatinglyric

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.getAndUpdate

const val ACTION_OPEN_LYRIC_SETTINGS = "com.lin0721.linmusic.action.OPEN_LYRIC_SETTINGS"

// 悬浮歌词设置按钮拉起 App 后，由设置页消费一次并打开歌词子菜单
class FloatingLyricRequests {

    private val _pendingOpenSettings = MutableStateFlow(false)
    val pendingOpenSettings: StateFlow<Boolean> = _pendingOpenSettings.asStateFlow()

    fun requestOpenSettings() {
        _pendingOpenSettings.value = true
    }

    fun consumeOpenSettings(): Boolean = _pendingOpenSettings.getAndUpdate { false }
}

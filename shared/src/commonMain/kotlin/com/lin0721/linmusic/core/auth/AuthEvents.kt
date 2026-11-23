package com.lin0721.linmusic.core.auth

import com.lin0721.linmusic.core.source.MusicPlatform
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

// 接口返回 301 或显式未授权码时发出，由 SessionMonitor 核对会话是否真的失效；没有订阅者时直接丢弃
object AuthEvents {

    private val _unauthorized = MutableSharedFlow<Unit>(extraBufferCapacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    val unauthorized: SharedFlow<Unit> = _unauthorized.asSharedFlow()

    private val _platformUnauthorized = MutableSharedFlow<MusicPlatform>(extraBufferCapacity = 4, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    val platformUnauthorized: SharedFlow<MusicPlatform> = _platformUnauthorized.asSharedFlow()

    fun notifyUnauthorized() {
        _unauthorized.tryEmit(Unit)
    }

    fun notifyPlatformUnauthorized(platform: MusicPlatform) {
        _platformUnauthorized.tryEmit(platform)
    }
}

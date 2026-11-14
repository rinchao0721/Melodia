package com.lin0721.linmusic.core.deeplink

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.getAndUpdate

// Activity 收到外链后把待打开的歌曲 id 放进来，界面层消费一次就清空，
// 避免旋转屏幕或重组时同一首歌被反复起播
object SongLinkRequests {

    private val _pendingTarget = MutableStateFlow<SongLinkTarget?>(null)
    val pendingTarget: StateFlow<SongLinkTarget?> = _pendingTarget.asStateFlow()

    // 剪贴板里认出链接时不直接跳，先挂在这里等用户确认
    private val _pendingClipboard = MutableStateFlow<ClipboardLink?>(null)
    val pendingClipboard: StateFlow<ClipboardLink?> = _pendingClipboard.asStateFlow()

    fun request(target: SongLinkTarget) {
        _pendingTarget.value = target
    }

    fun consume(): SongLinkTarget? = _pendingTarget.getAndUpdate { null }

    fun offerClipboard(link: ClipboardLink) {
        _pendingClipboard.value = link
    }

    fun takeClipboard(): ClipboardLink? = _pendingClipboard.getAndUpdate { null }

    fun dismissClipboard() {
        _pendingClipboard.value = null
    }
}

// 剪贴板里认出的链接，连同原文一起给弹窗展示
data class ClipboardLink(val target: SongLinkTarget, val text: String)

package com.lin0721.linmusic.core.deeplink

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.getAndUpdate

// 剪贴板里认出的链接，连同原文一起给提示浮条展示
data class ClipboardLink(val target: SongLinkTarget, val text: String)

// 外部链接与剪贴板识别的跨平台请求通道，由 Koin 持有单例
class SongLinkRequests {

    private val _pendingTarget = MutableStateFlow<SongLinkTarget?>(null)
    val pendingTarget: StateFlow<SongLinkTarget?> = _pendingTarget.asStateFlow()

    // 剪贴板里认出链接时不直接跳，先挂在这里等用户通过浮条确认
    private val _pendingClipboard = MutableStateFlow<ClipboardLink?>(null)
    val pendingClipboard: StateFlow<ClipboardLink?> = _pendingClipboard.asStateFlow()

    // 进程内最近一次已处理或应用内主动复制的链接原文，避免与 DataStore 异步写入竞态导致反问自己
    @Volatile
    private var lastHandledInMemory: String? = null

    fun request(target: SongLinkTarget) {
        _pendingTarget.value = target
    }

    fun consume(): SongLinkTarget? = _pendingTarget.getAndUpdate { null }

    fun offerClipboard(link: ClipboardLink) {
        lastHandledInMemory = link.text
        _pendingClipboard.value = link
    }

    fun takeClipboard(): ClipboardLink? = _pendingClipboard.getAndUpdate { null }

    fun dismissClipboard() {
        _pendingClipboard.value = null
    }

    fun markSelfHandled(text: String) {
        lastHandledInMemory = text
    }

    fun isRecentlyHandled(text: String): Boolean = lastHandledInMemory == text
}

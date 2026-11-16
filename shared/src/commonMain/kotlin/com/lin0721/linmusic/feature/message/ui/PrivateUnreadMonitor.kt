package com.lin0721.linmusic.feature.message.ui

import com.lin0721.linmusic.core.auth.UserPreferences
import com.lin0721.linmusic.core.log.AppLogger
import com.lin0721.linmusic.feature.message.data.MessageRepository
import com.lin0721.linmusic.feature.message.data.PrivateReadPreferences
import com.lin0721.linmusic.feature.message.data.withLocalRead
import com.lin0721.linmusic.feature.message.domain.PrivateConversation
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

private const val TAG = "PrivateUnreadMonitor"
private const val DEFAULT_POLL_INTERVAL_MS = 60_000L
// 取够一页会话，才能把本地已读的会话从服务端总未读里扣掉
private const val POLL_LIMIT = 50

// 登录且在前台时按 60s 轮询会话接口取未读数；退到后台停止，登出归零
class PrivateUnreadMonitor(
    private val messageRepository: MessageRepository,
    private val userPreferences: UserPreferences,
    private val readPreferences: PrivateReadPreferences,
    private val scope: CoroutineScope,
    private val pollIntervalMs: Long = DEFAULT_POLL_INTERVAL_MS
) {
    private val _unreadCount = MutableStateFlow(0)
    val unreadCount: StateFlow<Int> = _unreadCount.asStateFlow()

    // 会话摘要或未读可能变化时发出，会话列表据此静默重拉
    private val _conversationsChanged = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val conversationsChanged: SharedFlow<Unit> = _conversationsChanged.asSharedFlow()

    // 从分享页等聊天页之外的入口发出私信后发出对方 uid，打开着的聊天页据此重拉
    private val _messageSent = MutableSharedFlow<Long>(extraBufferCapacity = 4)
    val messageSent: SharedFlow<Long> = _messageSent.asSharedFlow()

    private val foreground = MutableStateFlow(true)
    private var currentUid: Long? = null
    private var pollJob: Job? = null

    init {
        scope.launch {
            combine(
                userPreferences.userProfile
                    .map { it?.uid?.takeIf { uid -> uid > 0L } }
                    .distinctUntilChanged(),
                foreground
            ) { uid, isForeground -> uid to isForeground }
                .collect { (uid, isForeground) ->
                    currentUid = uid
                    pollJob?.cancel()
                    pollJob = null
                    when {
                        uid == null -> _unreadCount.value = 0
                        isForeground -> startPolling(uid)
                    }
                }
        }
    }

    // 回到前台会立即补查一次再恢复轮询
    fun setForeground(isForeground: Boolean) {
        foreground.value = isForeground
    }

    fun notifyConversationsChanged() {
        _conversationsChanged.tryEmit(Unit)
    }

    fun notifyMessageSent(peerUid: Long) {
        if (peerUid <= 0L) return
        _messageSent.tryEmit(peerUid)
        _conversationsChanged.tryEmit(Unit)
    }

    fun refresh() {
        scope.launch {
            val uid = currentUid ?: userPreferences.userProfile.first()?.uid?.takeIf { it > 0L }
            currentUid = uid
            pollJob?.cancel()
            pollJob = null
            when {
                uid == null -> _unreadCount.value = 0
                foreground.value -> startPolling(uid)
                else -> fetchOnce(uid)
            }
        }
    }

    // 拖掉会话角标：本地记为已读到该会话最后一条，总数先行扣减
    fun markConversationRead(conversation: PrivateConversation) {
        if (conversation.unreadCount <= 0) return
        scope.launch {
            val uid = currentUid ?: return@launch
            readPreferences.markRead(uid, conversation.user.uid, conversation.lastTime)
            _unreadCount.value = (_unreadCount.value - conversation.unreadCount).coerceAtLeast(0)
            _conversationsChanged.tryEmit(Unit)
        }
    }

    // 拖掉总未读角标：重拉一页会话全部记为本地已读；超出这一页的会话下次轮询仍会计入
    fun markAllRead() {
        scope.launch {
            val uid = currentUid ?: return@launch
            _unreadCount.value = 0
            messageRepository.getPrivateConversations(selfUid = uid, cursor = 0L, limit = POLL_LIMIT).first()
                .onSuccess { page ->
                    page.items.filter { it.unreadCount > 0 }.forEach {
                        readPreferences.markRead(uid, it.user.uid, it.lastTime)
                    }
                }
                .onFailure { error -> AppLogger.w(TAG, "私信全部已读拉取会话失败", error) }
            _conversationsChanged.tryEmit(Unit)
        }
    }

    private fun startPolling(uid: Long) {
        pollJob = scope.launch {
            while (isActive) {
                fetchOnce(uid)
                delay(pollIntervalMs)
            }
        }
    }

    private suspend fun fetchOnce(uid: Long) {
        messageRepository.getPrivateConversations(selfUid = uid, cursor = 0L, limit = POLL_LIMIT).first()
            .onSuccess { page ->
                if (currentUid == uid) {
                    val readTimes = readPreferences.snapshot(uid)
                    val locallyRead = page.items.sumOf { conversation ->
                        conversation.unreadCount - conversation.withLocalRead(readTimes).unreadCount
                    }
                    val count = (page.newMsgCount - locallyRead).coerceAtLeast(0)
                    if (count != _unreadCount.value) {
                        _unreadCount.value = count
                        _conversationsChanged.tryEmit(Unit)
                    }
                }
            }
            .onFailure { error ->
                AppLogger.w(TAG, "私信未读轮询失败", error)
            }
    }
}

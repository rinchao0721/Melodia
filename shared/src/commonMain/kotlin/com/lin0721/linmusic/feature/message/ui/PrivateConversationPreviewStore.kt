package com.lin0721.linmusic.feature.message.ui

import com.lin0721.linmusic.core.auth.AccountEvents
import com.lin0721.linmusic.core.auth.AccountSwitchEvent
import com.lin0721.linmusic.core.auth.UserPreferences
import com.lin0721.linmusic.core.log.AppLogger
import com.lin0721.linmusic.feature.message.data.MessageRepository
import com.lin0721.linmusic.feature.message.data.PrivateReadPreferences
import com.lin0721.linmusic.feature.message.data.withLocalRead
import com.lin0721.linmusic.feature.message.domain.PrivateConversation
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

private const val TAG = "ConversationPreview"
const val CONVERSATION_PREVIEW_LIMIT = 3

// 侧边栏私信预览；loaded 为 false 表示当前账户还没拿到过结果
data class ConversationPreviewState(
    val items: List<PrivateConversation> = emptyList(),
    val loaded: Boolean = false,
    val failed: Boolean = false
)

// 取最近几条会话并套上本地已读
fun buildConversationPreview(
    items: List<PrivateConversation>,
    readTimes: Map<Long, Long>,
    limit: Int = CONVERSATION_PREVIEW_LIMIT
): List<PrivateConversation> =
    if (limit <= 0) emptyList() else items.take(limit).map { it.withLocalRead(readTimes) }

// 最近私信的内存缓存：按账户存，切换账户清空；打开时先给缓存再后台刷新
class PrivateConversationPreviewStore(
    private val messageRepository: MessageRepository,
    private val userPreferences: UserPreferences,
    private val readPreferences: PrivateReadPreferences,
    private val unreadMonitor: PrivateUnreadMonitor,
    private val scope: CoroutineScope,
    private val limit: Int = CONVERSATION_PREVIEW_LIMIT,
    accountEvents: Flow<AccountSwitchEvent> = AccountEvents.switched
) {
    private data class Entry(val items: List<PrivateConversation>, val failed: Boolean)

    private val entries = MutableStateFlow<Map<Long, Entry>>(emptyMap())

    val state: StateFlow<ConversationPreviewState> = combine(userPreferences.activeUid, entries) { uid, map ->
        val entry = uid?.let { map[it] }
        if (entry == null) {
            ConversationPreviewState()
        } else {
            ConversationPreviewState(items = entry.items, loaded = true, failed = entry.failed)
        }
    }.stateIn(scope, SharingStarted.Eagerly, ConversationPreviewState())

    init {
        scope.launch {
            accountEvents.collect { entries.value = emptyMap() }
        }
        // 只在已有缓存时跟随未读变化刷新，没打开过侧边栏不额外请求
        scope.launch {
            unreadMonitor.conversationsChanged.collect {
                val uid = activeUid() ?: return@collect
                if (entries.value.containsKey(uid)) refresh()
            }
        }
    }

    fun refresh() {
        scope.launch {
            val uid = activeUid() ?: return@launch
            val readTimes = readPreferences.snapshot(uid)
            val result = try {
                messageRepository.getPrivateConversations(selfUid = uid, cursor = 0L, limit = limit).first()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Result.failure(e)
            }
            if (activeUid() != uid) return@launch
            result
                .onSuccess { page ->
                    val items = buildConversationPreview(page.items, readTimes, limit)
                    entries.update { it + (uid to Entry(items, failed = false)) }
                }
                .onFailure { error ->
                    AppLogger.w(TAG, "侧边栏私信预览拉取失败", error)
                    entries.update { map ->
                        map + (uid to Entry(map[uid]?.items.orEmpty(), failed = true))
                    }
                }
        }
    }

    // 拖掉单条会话角标：本地先清零，已读记录交给未读监视器写入
    fun markRead(conversation: PrivateConversation) {
        if (conversation.unreadCount <= 0) return
        scope.launch {
            val uid = activeUid() ?: return@launch
            entries.update { map ->
                val entry = map[uid] ?: return@update map
                val items = entry.items.map {
                    if (it.user.uid == conversation.user.uid) it.copy(unreadCount = 0) else it
                }
                map + (uid to entry.copy(items = items))
            }
        }
        unreadMonitor.markConversationRead(conversation)
    }

    private suspend fun activeUid(): Long? = userPreferences.activeUid.first()?.takeIf { it > 0L }
}

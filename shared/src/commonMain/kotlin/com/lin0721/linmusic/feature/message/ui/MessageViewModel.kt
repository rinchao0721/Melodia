package com.lin0721.linmusic.feature.message.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lin0721.linmusic.core.auth.UserPreferences
import com.lin0721.linmusic.core.network.AppError
import com.lin0721.linmusic.core.network.ResourceProvider
import com.lin0721.linmusic.core.network.toUserMessage
import com.lin0721.linmusic.core.player.data.PlaybackRepository
import com.lin0721.linmusic.feature.message.data.MessageRepository
import com.lin0721.linmusic.feature.message.data.PrivateReadPreferences
import com.lin0721.linmusic.feature.message.data.withLocalRead
import com.lin0721.linmusic.feature.message.domain.CommentMessage
import com.lin0721.linmusic.feature.message.domain.ForwardMessage
import com.lin0721.linmusic.feature.message.domain.MessagePage
import com.lin0721.linmusic.feature.message.domain.NoticeItem
import com.lin0721.linmusic.feature.message.domain.PrivateConversation
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

private const val PAGE_SIZE = 30

// 通知与评论首页游标为 -1，@我 与私信会话按 offset 翻页从 0 起
private const val TIME_CURSOR_START = -1L
private const val OFFSET_CURSOR_START = 0L

// 通知里评论的歌名逐首补查，限制并发以免触发风控
private const val SONG_LOOKUP_CONCURRENCY = 4

enum class MessageTab(val title: String) {
    PRIVATE("私信"),
    NOTICE("通知"),
    FORWARD("@我"),
    COMMENT("评论")
}

class MessageViewModel(
    private val messageRepository: MessageRepository,
    private val playbackRepository: PlaybackRepository,
    private val userPreferences: UserPreferences,
    private val resourceProvider: ResourceProvider,
    private val unreadMonitor: PrivateUnreadMonitor,
    private val readPreferences: PrivateReadPreferences
) : ViewModel() {

    // null 表示偏好尚未读出，避免已登录用户看到一闪而过的"未登录"
    val isLoggedIn: StateFlow<Boolean?> = userPreferences.userProfile
        .map<_, Boolean?> { it != null }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    private val _selectedTab = MutableStateFlow(MessageTab.PRIVATE)
    val selectedTab: StateFlow<MessageTab> = _selectedTab.asStateFlow()

    private val _toastEvent = MutableSharedFlow<String>()
    val toastEvent: SharedFlow<String> = _toastEvent.asSharedFlow()

    private val _songNames = MutableStateFlow<Map<Long, String>>(emptyMap())
    val songNames: StateFlow<Map<Long, String>> = _songNames.asStateFlow()

    private val requestedSongIds = mutableSetOf<Long>()
    private val songLookupPermits = Semaphore(SONG_LOOKUP_CONCURRENCY)

    val privatePager = MessagePager<PrivateConversation>(
        scope = viewModelScope,
        initialCursor = OFFSET_CURSOR_START,
        fetch = { cursor -> fetchPrivateConversations(cursor) },
        keyOf = { it.user.uid },
        errorMessage = ::toErrorMessage,
        onToast = { _toastEvent.emit(it) }
    )

    val noticePager = MessagePager<NoticeItem>(
        scope = viewModelScope,
        initialCursor = TIME_CURSOR_START,
        fetch = { cursor -> messageRepository.getNotices(cursor, PAGE_SIZE) },
        keyOf = { it.id },
        errorMessage = ::toErrorMessage,
        onToast = { _toastEvent.emit(it) },
        onPageLoaded = ::resolveSongNames
    )

    val forwardPager = MessagePager<ForwardMessage>(
        scope = viewModelScope,
        initialCursor = OFFSET_CURSOR_START,
        fetch = { cursor -> messageRepository.getForwards(cursor, PAGE_SIZE) },
        keyOf = { it.key },
        errorMessage = ::toErrorMessage,
        onToast = { _toastEvent.emit(it) }
    )

    val commentPager = MessagePager<CommentMessage>(
        scope = viewModelScope,
        initialCursor = TIME_CURSOR_START,
        fetch = { cursor -> fetchReceivedComments(cursor) },
        keyOf = { it.commentId },
        errorMessage = ::toErrorMessage,
        onToast = { _toastEvent.emit(it) }
    )

    init {
        viewModelScope.launch {
            unreadMonitor.conversationsChanged.collect { privatePager.refreshSilently() }
        }
    }

    fun selectTab(tab: MessageTab) {
        _selectedTab.value = tab
    }

    // 页面可见且已登录时，由界面按当前 Tab 触发首次加载
    fun ensureLoaded(tab: MessageTab) {
        when (tab) {
            MessageTab.PRIVATE -> privatePager.loadIfIdle()
            MessageTab.NOTICE -> noticePager.loadIfIdle()
            MessageTab.FORWARD -> forwardPager.loadIfIdle()
            MessageTab.COMMENT -> commentPager.loadIfIdle()
        }
    }

    private fun fetchPrivateConversations(cursor: Long) = flow {
        val uid = userPreferences.userProfile.first()?.uid
        if (uid == null) {
            emit(Result.failure<MessagePage<PrivateConversation>>(AppError.Unauthorized))
        } else {
            val readTimes = readPreferences.snapshot(uid)
            emitAll(
                messageRepository.getPrivateConversations(uid, cursor, PAGE_SIZE).map { result ->
                    result.map { page -> page.copy(items = page.items.map { it.withLocalRead(readTimes) }) }
                }
            )
        }
    }

    private fun fetchReceivedComments(cursor: Long) = flow {
        val uid = userPreferences.userProfile.first()?.uid
        if (uid == null) {
            emit(Result.failure<MessagePage<CommentMessage>>(AppError.Unauthorized))
        } else {
            emitAll(messageRepository.getReceivedComments(uid, cursor, PAGE_SIZE))
        }
    }

    private fun toErrorMessage(error: Throwable): String = error.toUserMessage(resourceProvider)

    // 通知里的评论只带 threadId，歌名逐首补查；失败不重试，界面退化为不显示歌名
    private fun resolveSongNames(items: List<NoticeItem>) {
        val pending = items.filterIsInstance<NoticeItem.CommentLike>()
            .mapNotNull { it.songId }
            .distinct()
            .filter { requestedSongIds.add(it) }
        pending.forEach { songId ->
            viewModelScope.launch {
                songLookupPermits.withPermit {
                    playbackRepository.getSongDetail(songId).first()
                        .onSuccess { track ->
                            if (track.name.isNotBlank()) _songNames.update { it + (songId to track.name) }
                        }
                }
            }
        }
    }
}

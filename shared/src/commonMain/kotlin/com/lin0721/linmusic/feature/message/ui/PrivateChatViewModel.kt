package com.lin0721.linmusic.feature.message.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lin0721.linmusic.core.auth.UserPreferences
import com.lin0721.linmusic.core.network.ResourceProvider
import com.lin0721.linmusic.core.network.toUserMessage
import com.lin0721.linmusic.core.player.NowPlaying
import com.lin0721.linmusic.core.player.PlaybackController
import com.lin0721.linmusic.core.player.QueueItem
import com.lin0721.linmusic.feature.message.data.MessageRepository
import com.lin0721.linmusic.feature.message.data.PrivateReadPreferences
import com.lin0721.linmusic.feature.message.domain.MessageUser
import com.lin0721.linmusic.feature.message.domain.PrivateMessage
import com.lin0721.linmusic.feature.player.data.PlayerRepository
import com.lin0721.linmusic.feature.profile.data.ProfileRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

private const val CHAT_PAGE_SIZE = 30
internal const val PRIVATE_CHAT_TIME_GAP_MS = 5 * 60 * 1000L

sealed interface ChatState {
    data object Loading : ChatState
    data object Ready : ChatState
    data class Error(val message: String) : ChatState
}

// 聊天列表渲染项：携带收发方向、5 分钟时间分隔与同一发送者连续分组标记
data class PrivateChatItem(
    val message: PrivateMessage,
    val isMine: Boolean,
    val showTimeDivider: Boolean,
    val isGroupStart: Boolean,
    val isGroupEnd: Boolean
)

internal fun groupPrivateMessages(
    messages: List<PrivateMessage>,
    isMine: (PrivateMessage) -> Boolean,
    timeGapMs: Long = PRIVATE_CHAT_TIME_GAP_MS
): List<PrivateChatItem> {
    if (messages.isEmpty()) return emptyList()
    return messages.mapIndexed { index, current ->
        val prev = messages.getOrNull(index - 1)
        val next = messages.getOrNull(index + 1)
        val currentMine = isMine(current)
        val showTimeDivider = prev == null || (current.time - prev.time) >= timeGapMs
        val sameAsPrev = prev != null && !showTimeDivider && isSameSender(prev, current, isMine(prev), currentMine)
        val nextShowDivider = next == null || (next.time - current.time) >= timeGapMs
        val sameAsNext = next != null && !nextShowDivider && isSameSender(current, next, currentMine, isMine(next))
        PrivateChatItem(
            message = current,
            isMine = currentMine,
            showTimeDivider = showTimeDivider,
            isGroupStart = !sameAsPrev,
            isGroupEnd = !sameAsNext
        )
    }
}

private fun isSameSender(
    first: PrivateMessage,
    second: PrivateMessage,
    firstMine: Boolean,
    secondMine: Boolean
): Boolean {
    if (firstMine != secondMine) return false
    if (first.fromUserId > 0L && second.fromUserId > 0L) {
        return first.fromUserId == second.fromUserId
    }
    return true
}

class PrivateChatViewModel(
    private val messageRepository: MessageRepository,
    private val userPreferences: UserPreferences,
    private val resourceProvider: ResourceProvider,
    private val playerRepository: PlayerRepository,
    private val playbackController: PlaybackController,
    private val profileRepository: ProfileRepository,
    private val unreadMonitor: PrivateUnreadMonitor,
    private val readPreferences: PrivateReadPreferences
) : ViewModel() {

    private val _state = MutableStateFlow<ChatState>(ChatState.Loading)
    val state: StateFlow<ChatState> = _state.asStateFlow()

    private val _messages = MutableStateFlow<List<PrivateMessage>>(emptyList())
    val messages: StateFlow<List<PrivateMessage>> = _messages.asStateFlow()

    private val _chatItems = MutableStateFlow<List<PrivateChatItem>>(emptyList())
    val chatItems: StateFlow<List<PrivateChatItem>> = _chatItems.asStateFlow()

    private val _peerUser = MutableStateFlow<MessageUser?>(null)
    val peerUser: StateFlow<MessageUser?> = _peerUser.asStateFlow()

    private val _draft = MutableStateFlow("")
    val draft: StateFlow<String> = _draft.asStateFlow()

    private val _hasMore = MutableStateFlow(true)
    val hasMore: StateFlow<Boolean> = _hasMore.asStateFlow()

    private val _isLoadingMore = MutableStateFlow(false)
    val isLoadingMore: StateFlow<Boolean> = _isLoadingMore.asStateFlow()

    private val _sending = MutableStateFlow(false)
    val sending: StateFlow<Boolean> = _sending.asStateFlow()

    val nowPlaying: StateFlow<NowPlaying?> = playbackController.nowPlaying
    val isPlaying: StateFlow<Boolean> = playbackController.isPlaying

    // 仅允许分享可识别正数 songId 的网易云音乐曲目（排除本地/外部平台/播客）
    val shareableNowPlaying: StateFlow<NowPlaying?> = combine(
        playbackController.nowPlaying,
        playbackController.currentQueueItem
    ) { _, _ -> currentShareableSong() }
        .stateIn(viewModelScope, SharingStarted.Eagerly, currentShareableSong())

    private val _toastEvent = MutableSharedFlow<String>()
    val toastEvent: SharedFlow<String> = _toastEvent.asSharedFlow()

    private val _selfUid = MutableStateFlow(0L)

    init {
        viewModelScope.launch {
            unreadMonitor.messageSent.collect { uid ->
                if (uid == peerUid) loadPage(time = 0L, isLoadMore = false, showLoading = false)
            }
        }
    }
    private val byId = LinkedHashMap<Long, PrivateMessage>()
    private val draftsByPeer = mutableMapOf<Long, String>()
    private var oldestTime = 0L
    private var peerUid = 0L
    private var opened = false
    private var loadJob: Job? = null
    private var profileJob: Job? = null

    // 收发方向优先按 toUser 与聊天对象比对，不强依赖偏好读盘时机
    fun isMine(message: PrivateMessage): Boolean = when {
        peerUid > 0L && message.toUserId > 0L -> message.toUserId == peerUid
        _selfUid.value > 0L && message.fromUserId > 0L -> message.fromUserId == _selfUid.value
        else -> false
    }

    fun open(userId: Long, initialUser: MessageUser? = null) {
        if (userId <= 0L) return
        if (opened && userId == peerUid) {
            if (initialUser != null && (_peerUser.value == null || _peerUser.value?.nickname.isNullOrBlank())) {
                _peerUser.value = initialUser
            }
            return
        }
        if (peerUid > 0L) {
            draftsByPeer[peerUid] = _draft.value
        }
        loadJob?.cancel()
        profileJob?.cancel()
        profileJob = null
        opened = true
        peerUid = userId
        byId.clear()
        oldestTime = 0L
        _hasMore.value = true
        _isLoadingMore.value = false
        _peerUser.value = initialUser
        _draft.value = draftsByPeer[userId].orEmpty()
        publish()
        loadPage(time = 0L, isLoadMore = false, showLoading = true)
    }

    fun loadMore() {
        if (peerUid <= 0L || !_hasMore.value || _isLoadingMore.value || _state.value is ChatState.Loading) return
        loadPage(time = oldestTime, isLoadMore = true, showLoading = false)
    }

    fun retry() {
        if (peerUid <= 0L) return
        if (byId.isEmpty()) {
            loadPage(time = 0L, isLoadMore = false, showLoading = true)
        } else {
            loadMore()
        }
    }

    fun refresh() {
        if (peerUid <= 0L) return
        loadPage(time = 0L, isLoadMore = false, showLoading = byId.isEmpty())
    }

    fun updateDraft(text: String) {
        _draft.value = text
        if (peerUid > 0L) {
            draftsByPeer[peerUid] = text
        }
    }

    fun sendDraft() {
        send(_draft.value)
    }

    fun send(text: String) {
        val content = text.trim()
        val targetUid = peerUid
        if (content.isEmpty() || targetUid <= 0L || _sending.value) return
        _sending.value = true
        viewModelScope.launch {
            messageRepository.sendPrivateText(targetUid, content).first()
                .onSuccess {
                    if (_draft.value == text) {
                        _draft.value = ""
                        draftsByPeer.remove(targetUid)
                    }
                    // 发送成功后重拉最新一页，以服务端真实记录为准
                    loadPage(time = 0L, isLoadMore = false, showLoading = false)
                    unreadMonitor.notifyConversationsChanged()
                }
                .onFailure { failure -> _toastEvent.emit(failure.toUserMessage(resourceProvider)) }
            _sending.value = false
        }
    }

    fun sendCurrentSong() {
        val now = currentShareableSong()
        val songId = now?.songId ?: 0L
        val targetUid = peerUid
        if (now == null || songId <= 0L || targetUid <= 0L || _sending.value) {
            viewModelScope.launch { _toastEvent.emit("当前没有可分享的网易云歌曲") }
            return
        }
        _sending.value = true
        viewModelScope.launch {
            messageRepository.sendPrivateSong(targetUid, songId, "分享单曲：${now.title}").first()
                .onSuccess {
                    loadPage(time = 0L, isLoadMore = false, showLoading = false)
                    unreadMonitor.notifyConversationsChanged()
                }
                .onFailure { failure -> _toastEvent.emit(failure.toUserMessage(resourceProvider)) }
            _sending.value = false
        }
    }

    fun playSong(songId: Long) {
        if (songId <= 0L) return
        viewModelScope.launch {
            val track = playerRepository.getSongDetail(songId).first().getOrNull()
            if (track == null) {
                _toastEvent.emit("这首歌取不到详情，暂时播不了")
                return@launch
            }
            playbackController.playQueue(
                items = listOf(QueueItem(track.id, track.name, track.ar.joinToString("/") { it.name }, track.al.picUrl)),
                startIndex = 0,
                playContext = "私信"
            )
        }
    }

    private fun currentShareableSong(): NowPlaying? {
        val now = playbackController.nowPlaying.value ?: return null
        val songId = now.songId ?: return null
        if (songId <= 0L) return null
        val queueItem = playbackController.currentQueueItem.value
        if (queueItem != null && queueItem.songId == songId && (queueItem.external != null || queueItem.radioId != 0L)) {
            return null
        }
        return now
    }

    private fun loadPage(time: Long, isLoadMore: Boolean, showLoading: Boolean) {
        val targetUid = peerUid
        if (targetUid <= 0L) return
        // 新请求会取消进行中的翻页，翻页标记要同步复位，否则上滑加载永久失效
        loadJob?.cancel()
        _isLoadingMore.value = isLoadMore
        if (!isLoadMore && showLoading) {
            _state.value = ChatState.Loading
        }
        loadJob = viewModelScope.launch {
            val uid = _selfUid.value.takeIf { it > 0L }
                ?: userPreferences.userProfile.first()?.uid?.also { _selfUid.value = it }
                ?: 0L
            if (uid <= 0L) {
                _isLoadingMore.value = false
                if (byId.isEmpty()) {
                    _state.value = ChatState.Error("请先登录")
                } else {
                    _toastEvent.emit("请先登录")
                }
                return@launch
            }
            messageRepository.getPrivateHistory(targetUid, time, CHAT_PAGE_SIZE).first()
                .onSuccess { page ->
                    if (peerUid != targetUid) return@onSuccess
                    page.items.forEach { byId[it.id] = it }
                    _hasMore.value = page.hasMore
                    oldestTime = byId.values.mapNotNull { it.time.takeIf { t -> t > 0L } }.minOrNull() ?: oldestTime
                    resolvePeerProfile(targetUid, page.items)
                    publish()
                    _isLoadingMore.value = false
                    _state.value = ChatState.Ready
                    if (time == 0L) {
                        markReadUpToLatest(uid, targetUid)
                    }
                }
                .onFailure { failure ->
                    if (peerUid != targetUid) return@onFailure
                    _isLoadingMore.value = false
                    val message = failure.toUserMessage(resourceProvider)
                    if (byId.isEmpty()) {
                        _state.value = ChatState.Error(message)
                    } else {
                        _toastEvent.emit(message)
                        _state.value = ChatState.Ready
                    }
                }
        }
    }

    private fun markReadUpToLatest(selfUid: Long, targetUid: Long) {
        val latest = byId.values.maxOfOrNull { it.time } ?: return
        viewModelScope.launch {
            readPreferences.markRead(selfUid, targetUid, latest)
            unreadMonitor.refresh()
            unreadMonitor.notifyConversationsChanged()
        }
    }

    // 历史里对方资料可能缺头像或昵称（如官方号、音乐人账号），逐项合并，仍缺时查一次用户详情补齐
    private fun resolvePeerProfile(targetUid: Long, items: List<PrivateMessage>) {
        val candidates = items.asSequence()
            .mapNotNull { msg ->
                msg.fromUser?.takeIf { it.uid == targetUid }
                    ?: msg.toUser?.takeIf { it.uid == targetUid }
            }
            .toList()
        val merged = candidates.fold(_peerUser.value ?: MessageUser(targetUid, "", "")) { acc, user -> acc.fillBlanks(user) }
        _peerUser.value = merged.takeIf { it.nickname.isNotBlank() || it.avatarUrl.isNotBlank() } ?: _peerUser.value
        if ((merged.nickname.isBlank() || merged.avatarUrl.isBlank()) && profileJob == null) {
            profileJob = viewModelScope.launch {
                profileRepository.getUserDetail(targetUid).first()
                    .onSuccess { info ->
                        if (peerUid != targetUid) return@onSuccess
                        val current = _peerUser.value ?: MessageUser(targetUid, "", "")
                        _peerUser.value = current.fillBlanks(MessageUser(info.uid, info.nickname, info.avatarUrl))
                    }
            }
        }
    }

    private fun MessageUser.fillBlanks(other: MessageUser): MessageUser = copy(
        nickname = nickname.ifBlank { other.nickname },
        avatarUrl = avatarUrl.ifBlank { other.avatarUrl }
    )

    private fun publish() {
        val sorted = byId.values.sortedWith(compareBy<PrivateMessage> { it.time }.thenBy { it.id })
        _messages.value = sorted
        _chatItems.value = groupPrivateMessages(sorted, ::isMine)
    }
}

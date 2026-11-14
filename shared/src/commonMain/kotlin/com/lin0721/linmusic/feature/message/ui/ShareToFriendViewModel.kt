package com.lin0721.linmusic.feature.message.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lin0721.linmusic.core.auth.UserPreferences
import com.lin0721.linmusic.core.network.ResourceProvider
import com.lin0721.linmusic.core.network.toUserMessage
import com.lin0721.linmusic.feature.message.data.MessageRepository
import com.lin0721.linmusic.feature.message.domain.MessageUser
import com.lin0721.linmusic.feature.profile.data.ProfileRepository
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

private const val SHARE_PAGE_SIZE = 30

sealed interface ShareToFriendUiState {
    data object Idle : ShareToFriendUiState
    data object Loading : ShareToFriendUiState
    data class Ready(
        val recentUsers: List<MessageUser>,
        val followUsers: List<MessageUser>,
        val query: String = "",
        val hasMoreFollows: Boolean = false,
        val isLoadingMoreFollows: Boolean = false,
        val sendingToUid: Long? = null
    ) : ShareToFriendUiState {
        val filteredRecentUsers: List<MessageUser>
            get() = filterByQuery(recentUsers, query)

        val filteredFollowUsers: List<MessageUser>
            get() = filterByQuery(followUsers, query)

        private fun filterByQuery(users: List<MessageUser>, raw: String): List<MessageUser> {
            val trimmed = raw.trim()
            if (trimmed.isEmpty()) return users
            return users.filter { it.nickname.contains(trimmed, ignoreCase = true) }
        }
    }

    data class Error(val message: String) : ShareToFriendUiState
}

// 分享单曲给好友的选人状态机：合并最近私信会话与关注列表
class ShareToFriendViewModel(
    private val messageRepository: MessageRepository,
    private val profileRepository: ProfileRepository,
    private val userPreferences: UserPreferences,
    private val resourceProvider: ResourceProvider,
    private val unreadMonitor: PrivateUnreadMonitor
) : ViewModel() {

    private val _uiState = MutableStateFlow<ShareToFriendUiState>(ShareToFriendUiState.Idle)
    val uiState: StateFlow<ShareToFriendUiState> = _uiState.asStateFlow()

    private val _toastEvent = MutableSharedFlow<String>()
    val toastEvent: SharedFlow<String> = _toastEvent.asSharedFlow()

    // 成功后发出收件人，界面据此显示「已发送给某人」提示；成功不再走 toastEvent
    private val _shareCompleted = MutableSharedFlow<MessageUser>()
    val shareCompleted: SharedFlow<MessageUser> = _shareCompleted.asSharedFlow()

    private var selfUid = 0L
    private var followOffset = 0

    fun loadIfNeeded() {
        val state = _uiState.value
        if (state is ShareToFriendUiState.Idle || state is ShareToFriendUiState.Error) {
            refresh()
        }
    }

    fun refresh() {
        _uiState.value = ShareToFriendUiState.Loading
        followOffset = 0
        viewModelScope.launch {
            val uid = userPreferences.userProfile.first()?.uid ?: 0L
            if (uid <= 0L) {
                _uiState.value = ShareToFriendUiState.Error("请先登录")
                return@launch
            }
            selfUid = uid
            val conversationsDeferred = async {
                messageRepository.getPrivateConversations(selfUid = uid, cursor = 0L, limit = SHARE_PAGE_SIZE).first()
            }
            val followsDeferred = async {
                profileRepository.getUserFollows(uid = uid, offset = 0, limit = SHARE_PAGE_SIZE).first()
            }
            val conversationsResult = conversationsDeferred.await()
            val followsResult = followsDeferred.await()
            if (conversationsResult.isFailure && followsResult.isFailure) {
                val error = conversationsResult.exceptionOrNull() ?: followsResult.exceptionOrNull()!!
                _uiState.value = ShareToFriendUiState.Error(error.toUserMessage(resourceProvider))
                return@launch
            }
            val recent = conversationsResult.getOrNull()?.items.orEmpty()
                .map { it.user }
                .distinctBy { it.uid }
            val followPage = followsResult.getOrNull()
            val follows = followPage?.users.orEmpty()
                .map { MessageUser(it.uid, it.nickname, it.avatarUrl) }
                .distinctBy { it.uid }
            followOffset = follows.size
            _uiState.value = ShareToFriendUiState.Ready(
                recentUsers = recent,
                followUsers = follows,
                hasMoreFollows = followPage?.hasMore == true && follows.isNotEmpty()
            )
        }
    }

    fun loadMoreFollows() {
        val current = _uiState.value as? ShareToFriendUiState.Ready ?: return
        if (!current.hasMoreFollows || current.isLoadingMoreFollows || selfUid <= 0L) return
        _uiState.value = current.copy(isLoadingMoreFollows = true)
        viewModelScope.launch {
            profileRepository.getUserFollows(uid = selfUid, offset = followOffset, limit = SHARE_PAGE_SIZE).first()
                .onSuccess { page ->
                    val latest = _uiState.value as? ShareToFriendUiState.Ready ?: return@onSuccess
                    val added = page.users.map { MessageUser(it.uid, it.nickname, it.avatarUrl) }
                    followOffset += added.size
                    _uiState.value = latest.copy(
                        followUsers = (latest.followUsers + added).distinctBy { it.uid },
                        hasMoreFollows = page.hasMore && added.isNotEmpty(),
                        isLoadingMoreFollows = false
                    )
                }
                .onFailure { failure ->
                    val latest = _uiState.value as? ShareToFriendUiState.Ready ?: return@onFailure
                    _uiState.value = latest.copy(isLoadingMoreFollows = false)
                    _toastEvent.emit(failure.toUserMessage(resourceProvider))
                }
        }
    }

    fun updateQuery(query: String) {
        val current = _uiState.value as? ShareToFriendUiState.Ready ?: return
        _uiState.value = current.copy(query = query)
    }

    fun shareSong(userId: Long, songId: Long, songName: String, note: String = "") {
        if (userId <= 0L) return
        if (songId <= 0L) {
            viewModelScope.launch { _toastEvent.emit("仅支持分享网易云歌曲") }
            return
        }
        val current = _uiState.value as? ShareToFriendUiState.Ready ?: return
        if (current.sendingToUid != null) return
        val content = note.trim().ifEmpty {
            if (songName.isNotBlank()) "分享单曲：$songName" else "分享单曲"
        }
        val recipient = (current.recentUsers + current.followUsers).firstOrNull { it.uid == userId }
            ?: MessageUser(userId, "", "")
        _uiState.value = current.copy(sendingToUid = userId)
        viewModelScope.launch {
            messageRepository.sendPrivateSong(userId = userId, songId = songId, text = content).first()
                .onSuccess {
                    val latest = _uiState.value as? ShareToFriendUiState.Ready
                    if (latest != null) _uiState.value = latest.copy(sendingToUid = null)
                    _shareCompleted.emit(recipient)
                    unreadMonitor.notifyMessageSent(userId)
                }
                .onFailure { failure ->
                    val latest = _uiState.value as? ShareToFriendUiState.Ready
                    if (latest != null) _uiState.value = latest.copy(sendingToUid = null)
                    _toastEvent.emit(failure.toUserMessage(resourceProvider))
                }
        }
    }
}

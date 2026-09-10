package com.lin0721.linmusic.feature.profile.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lin0721.linmusic.core.auth.UserPreferences
import com.lin0721.linmusic.feature.profile.data.ProfileRepository
import com.lin0721.linmusic.feature.profile.domain.ProfileEventInfo
import com.lin0721.linmusic.feature.profile.domain.ProfileListenRankItem
import com.lin0721.linmusic.feature.profile.domain.ProfilePlaylistInfo
import com.lin0721.linmusic.feature.profile.domain.ProfileUserInfo
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch

private const val PLAYLIST_PAGE_SIZE = 20
private const val EVENT_PAGE_SIZE = 20

sealed interface ProfileUiState {
    data object Loading : ProfileUiState

    data class Success(
        val userInfo: ProfileUserInfo,
        val isSelf: Boolean,
        val selectedTab: Int = 0,
        val playlists: List<ProfilePlaylistInfo> = emptyList(),
        val playlistsHasMore: Boolean = false,
        val playlistsLoadingMore: Boolean = false,
        val playlistsLoaded: Boolean = false,
        val events: List<ProfileEventInfo> = emptyList(),
        val eventsHasMore: Boolean = false,
        val eventsLoadingMore: Boolean = false,
        val eventsLastTime: Long = -1L,
        val eventsLoaded: Boolean = false,
        val rankSubTab: Int = 0,
        val rankItems: List<ProfileListenRankItem> = emptyList(),
        val rankLoading: Boolean = false,
        val rankLoaded: Boolean = false
    ) : ProfileUiState

    data class Error(val message: String) : ProfileUiState
}

class ProfileViewModel(
    private val profileRepository: ProfileRepository,
    private val userPreferences: UserPreferences
) : ViewModel() {

    // Koin 的 koinViewModel() 按类型缓存实例，同一个 uid 的 ViewModel 会被复用；
    // uid 不放进构造参数，改由外部 LaunchedEffect(uid) 驱动加载，避免切换用户时读到上一个人的缓存数据
    private var uid: Long = 0L

    private val _uiState = MutableStateFlow<ProfileUiState>(ProfileUiState.Loading)
    val uiState: StateFlow<ProfileUiState> = _uiState.asStateFlow()

    private val _toastEvent = MutableSharedFlow<String>()
    val toastEvent: SharedFlow<String> = _toastEvent.asSharedFlow()

    private var playlistOffset = 0

    // 由 ProfileScreen 的 LaunchedEffect(uid) 调用，uid 变化时会重新触发
    fun load(uid: Long) {
        this.uid = uid
        loadUserProfile()
    }

    fun loadUserProfile() {
        _uiState.value = ProfileUiState.Loading
        viewModelScope.launch {
            val primaryResult = profileRepository.getUserDetail(uid).first()
            val finalResult = if (primaryResult.isSuccess) {
                primaryResult
            } else {
                // EAPI 请求失败时走 WEAPI 兜底
                profileRepository.getUserDetailFallback(uid).first()
            }

            finalResult.onSuccess { userInfo ->
                val currentUserProfile = userPreferences.userProfile.firstOrNull()
                val isSelf = currentUserProfile?.uid == uid
                _uiState.value = ProfileUiState.Success(
                    userInfo = userInfo,
                    isSelf = isSelf
                )
                loadPlaylistsIfNeeded()
            }.onFailure { error ->
                _uiState.value = ProfileUiState.Error(
                    error.message ?: "获取用户信息失败"
                )
            }
        }
    }

    fun retry() {
        loadUserProfile()
    }

    fun selectTab(tabIndex: Int) {
        val state = _uiState.value as? ProfileUiState.Success ?: return
        if (state.selectedTab == tabIndex) return
        _uiState.value = state.copy(selectedTab = tabIndex)
        when (tabIndex) {
            0 -> loadPlaylistsIfNeeded()
            1 -> loadEventsIfNeeded()
            2 -> loadListeningRankIfNeeded()
        }
    }

    fun loadPlaylistsIfNeeded() {
        val state = _uiState.value as? ProfileUiState.Success ?: return
        if (state.playlistsLoaded) return
        playlistOffset = 0
        // 首次拉取也要标 loading
        _uiState.value = state.copy(playlistsLoadingMore = true)
        viewModelScope.launch {
            profileRepository.getUserPlaylists(uid, offset = 0, limit = PLAYLIST_PAGE_SIZE)
                .first()
                .onSuccess { page ->
                    playlistOffset = page.playlists.size
                    val latest = _uiState.value as? ProfileUiState.Success ?: return@onSuccess
                    _uiState.value = latest.copy(
                        playlists = page.playlists,
                        playlistsHasMore = page.hasMore,
                        playlistsLoaded = true,
                        playlistsLoadingMore = false
                    )
                }
                .onFailure { error ->
                    _toastEvent.emit(error.message ?: "加载歌单失败")
                    val latest = _uiState.value as? ProfileUiState.Success ?: return@onFailure
                    _uiState.value = latest.copy(playlistsLoaded = true, playlistsLoadingMore = false)
                }
        }
    }

    fun loadMorePlaylists() {
        val state = _uiState.value as? ProfileUiState.Success ?: return
        if (!state.playlistsHasMore || state.playlistsLoadingMore) return
        _uiState.value = state.copy(playlistsLoadingMore = true)
        viewModelScope.launch {
            profileRepository.getUserPlaylists(uid, offset = playlistOffset, limit = PLAYLIST_PAGE_SIZE)
                .first()
                .onSuccess { page ->
                    playlistOffset += page.playlists.size
                    val latest = _uiState.value as? ProfileUiState.Success ?: return@onSuccess
                    _uiState.value = latest.copy(
                        playlists = latest.playlists + page.playlists,
                        playlistsHasMore = page.hasMore,
                        playlistsLoadingMore = false
                    )
                }
                .onFailure { error ->
                    _toastEvent.emit(error.message ?: "加载更多歌单失败")
                    val latest = _uiState.value as? ProfileUiState.Success ?: return@onFailure
                    _uiState.value = latest.copy(playlistsLoadingMore = false)
                }
        }
    }

    fun loadEventsIfNeeded() {
        val state = _uiState.value as? ProfileUiState.Success ?: return
        if (state.eventsLoaded) return
        // 首次拉取也要标 loading，理由同 loadPlaylistsIfNeeded
        _uiState.value = state.copy(eventsLoadingMore = true)
        viewModelScope.launch {
            // 首次拉取动态 time 传 -1L 获取最新一页
            profileRepository.getUserEvents(uid, time = -1L, limit = EVENT_PAGE_SIZE)
                .first()
                .onSuccess { page ->
                    val latest = _uiState.value as? ProfileUiState.Success ?: return@onSuccess
                    _uiState.value = latest.copy(
                        events = page.events,
                        eventsHasMore = page.hasMore,
                        eventsLastTime = page.lasttime,
                        eventsLoaded = true,
                        eventsLoadingMore = false
                    )
                }
                .onFailure { error ->
                    _toastEvent.emit(error.message ?: "加载动态失败")
                    val latest = _uiState.value as? ProfileUiState.Success ?: return@onFailure
                    _uiState.value = latest.copy(eventsLoaded = true, eventsLoadingMore = false)
                }
        }
    }

    fun loadMoreEvents() {
        val state = _uiState.value as? ProfileUiState.Success ?: return
        if (!state.eventsHasMore || state.eventsLoadingMore) return
        _uiState.value = state.copy(eventsLoadingMore = true)
        viewModelScope.launch {
            profileRepository.getUserEvents(uid, time = state.eventsLastTime, limit = EVENT_PAGE_SIZE)
                .first()
                .onSuccess { page ->
                    val latest = _uiState.value as? ProfileUiState.Success ?: return@onSuccess
                    _uiState.value = latest.copy(
                        events = latest.events + page.events,
                        eventsHasMore = page.hasMore,
                        eventsLastTime = page.lasttime,
                        eventsLoadingMore = false
                    )
                }
                .onFailure { error ->
                    _toastEvent.emit(error.message ?: "加载更多动态失败")
                    val latest = _uiState.value as? ProfileUiState.Success ?: return@onFailure
                    _uiState.value = latest.copy(eventsLoadingMore = false)
                }
        }
    }

    fun selectRankSubTab(subTab: Int) {
        val state = _uiState.value as? ProfileUiState.Success ?: return
        if (state.rankSubTab == subTab && state.rankLoaded) return
        _uiState.value = state.copy(rankSubTab = subTab)
        loadListeningRank(subTab)
    }

    fun loadListeningRankIfNeeded() {
        val state = _uiState.value as? ProfileUiState.Success ?: return
        if (state.rankLoaded) return
        loadListeningRank(state.rankSubTab)
    }

    private fun loadListeningRank(type: Int) {
        val state = _uiState.value as? ProfileUiState.Success ?: return
        _uiState.value = state.copy(rankLoading = true)
        viewModelScope.launch {
            profileRepository.getUserListeningRank(uid, type = type)
                .first()
                .onSuccess { items ->
                    val latest = _uiState.value as? ProfileUiState.Success ?: return@onSuccess
                    _uiState.value = latest.copy(
                        rankItems = items,
                        rankLoading = false,
                        rankLoaded = true
                    )
                }
                .onFailure { error ->
                    _toastEvent.emit(error.message ?: "加载听歌排行失败")
                    val latest = _uiState.value as? ProfileUiState.Success ?: return@onFailure
                    _uiState.value = latest.copy(
                        rankLoading = false,
                        rankLoaded = true
                    )
                }
        }
    }

    fun toggleFollow() {
        val state = _uiState.value as? ProfileUiState.Success ?: return
        val currentFollowed = state.userInfo.isFollowedByMe
        val targetFollowed = !currentFollowed
        viewModelScope.launch {
            val request = if (targetFollowed) {
                profileRepository.followUser(uid)
            } else {
                profileRepository.unfollowUser(uid)
            }

            request.first()
                .onSuccess {
                    val latest = _uiState.value as? ProfileUiState.Success ?: return@onSuccess
                    val newFollowedsCount = if (targetFollowed) {
                        latest.userInfo.followedsCount + 1
                    } else {
                        (latest.userInfo.followedsCount - 1).coerceAtLeast(0)
                    }
                    val updatedUserInfo = latest.userInfo.copy(
                        isFollowedByMe = targetFollowed,
                        followedsCount = newFollowedsCount
                    )
                    _uiState.value = latest.copy(userInfo = updatedUserInfo)
                    val msg = if (targetFollowed) "关注成功" else "已取消关注"
                    _toastEvent.emit(msg)
                }
                .onFailure { error ->
                    val defaultMsg = if (targetFollowed) "关注失败" else "取消关注失败"
                    _toastEvent.emit(error.message ?: defaultMsg)
                }
        }
    }
}

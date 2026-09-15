package com.lin0721.linmusic.feature.artist.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lin0721.linmusic.core.auth.UserPreferences
import com.lin0721.linmusic.core.comment.data.CommentRepository
import com.lin0721.linmusic.core.comment.data.CommentSortType
import com.lin0721.linmusic.core.comment.domain.CommentsSectionController
import com.lin0721.linmusic.core.comment.domain.CommentComposerState
import com.lin0721.linmusic.core.comment.domain.CommentFloorState
import com.lin0721.linmusic.core.comment.ui.CommentsState
import com.lin0721.linmusic.core.log.AppLogger
import com.lin0721.linmusic.core.model.ArtistMv
import com.lin0721.linmusic.core.model.CommentItem
import com.lin0721.linmusic.core.model.CommentUser
import com.lin0721.linmusic.core.network.ResourceProvider
import com.lin0721.linmusic.core.network.toUserMessage
import com.lin0721.linmusic.core.player.PlayerManager
import com.lin0721.linmusic.feature.artist.data.ArtistRepository
import com.lin0721.linmusic.feature.artist.domain.MvDetail
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

private const val TAG = "ArtistMvPlayerViewModel"

// MV 播放页 UI 状态：只负责拿播放地址，实际渲染/进度/播放控制交给 Composable 里的 ExoPlayer 实例
sealed interface MvPlayerUiState {
    data object Loading : MvPlayerUiState
    data class Success(val videoUrl: String) : MvPlayerUiState
    data class Error(val message: String) : MvPlayerUiState
    // 移动网络下开启了"仅 Wi-Fi 播放"，起播被拦截
    data object Blocked : MvPlayerUiState
}

class ArtistMvPlayerViewModel(
    private val artistRepository: ArtistRepository,
    private val commentRepository: CommentRepository,
    private val playerManager: PlayerManager,
    private val userPreferences: UserPreferences,
    private val resourceProvider: ResourceProvider
) : ViewModel() {

    private val _uiState = MutableStateFlow<MvPlayerUiState>(MvPlayerUiState.Loading)
    val uiState: StateFlow<MvPlayerUiState> = _uiState.asStateFlow()

    private val _mvDetail = MutableStateFlow<MvDetail?>(null)
    val mvDetail: StateFlow<MvDetail?> = _mvDetail.asStateFlow()

    private val commentsController = CommentsSectionController(
        scope = viewModelScope,
        repository = commentRepository,
        onToast = { message -> _toastEvent.emit(message) },
        resourceProvider = resourceProvider
    )
    val commentsState: StateFlow<CommentsState> = commentsController.commentsState
    val composerState: StateFlow<CommentComposerState> = commentsController.composerState
    val floorState: StateFlow<CommentFloorState> = commentsController.floorState

    private val _relatedMvs = MutableStateFlow<List<ArtistMv>>(emptyList())
    val relatedMvs: StateFlow<List<ArtistMv>> = _relatedMvs.asStateFlow()

    // 频道行用：歌手头像/粉丝数/是否已关注（复用歌手详情页的同一套接口，非本页专属数据）
    private val _artistAvatar = MutableStateFlow("")
    val artistAvatar: StateFlow<String> = _artistAvatar.asStateFlow()

    private val _fansCount = MutableStateFlow(0L)
    val fansCount: StateFlow<Long> = _fansCount.asStateFlow()

    private val _isArtistFollowed = MutableStateFlow(false)
    val isArtistFollowed: StateFlow<Boolean> = _isArtistFollowed.asStateFlow()

    private val _toastEvent = MutableSharedFlow<String>()
    val toastEvent: SharedFlow<String> = _toastEvent.asSharedFlow()

    val userProfile = userPreferences.userProfile.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = null
    )

    // forcePlayOnMobile: 用户在 Blocked 提示下点了"仍然播放"，跳过本次拦截检查
    fun loadMvUrl(mvId: Long, resolution: Int = 1080, forcePlayOnMobile: Boolean = false) {
        viewModelScope.launch {
            if (!forcePlayOnMobile && playerManager.shouldBlockPlaybackOnMobile()) {
                _uiState.value = MvPlayerUiState.Blocked
                return@launch
            }
            // 进入 MV 播放页时暂停背景音乐，避免和 MV 自带音轨重叠
            if (playerManager.isPlaying.value) playerManager.pause()
            _uiState.value = MvPlayerUiState.Loading
            artistRepository.getMvUrl(mvId, resolution).collect { result ->
                result.onSuccess { url ->
                    _uiState.value = MvPlayerUiState.Success(url)
                }.onFailure { e ->
                    AppLogger.e(TAG, "获取 MV 播放地址失败 mvId=$mvId resolution=$resolution", e)
                    _uiState.value = MvPlayerUiState.Error(e.toUserMessage(resourceProvider))
                }
            }
        }
    }

    fun loadMvDetail(mvId: Long) {
        viewModelScope.launch {
            artistRepository.getMvDetail(mvId).first()
                .onSuccess { detail ->
                    _mvDetail.value = detail
                    loadRelatedMvs(detail.artistId, excludeMvId = mvId)
                    loadArtistInfo(detail.artistId)
                }
                .onFailure { e ->
                    AppLogger.w(TAG, "获取 MV 详情失败 mvId=$mvId", e)
                }
        }
    }

    private fun loadRelatedMvs(artistId: Long, excludeMvId: Long) {
        if (artistId <= 0) return
        viewModelScope.launch {
            artistRepository.getArtistMvs(artistId, limit = 10, offset = 0).first()
                .onSuccess { page ->
                    _relatedMvs.value = page.mvs.filter { it.id != excludeMvId }
                }
        }
    }

    // 频道行数据：头像/粉丝数/关注态，全部复用歌手详情页已有接口
    private fun loadArtistInfo(artistId: Long) {
        if (artistId <= 0) return
        viewModelScope.launch {
            val detailDeferred = async { artistRepository.getArtistDetail(artistId).first() }
            val fansDeferred = async { artistRepository.getArtistFansCount(artistId).first() }
            val followDeferred = async { artistRepository.checkArtistFollowed(artistId).first() }
            detailDeferred.await().onSuccess { _artistAvatar.value = it.avatar.ifBlank { it.cover } }
            fansDeferred.await().onSuccess { _fansCount.value = it }
            followDeferred.await().onSuccess { _isArtistFollowed.value = it }
        }
    }

    fun toggleArtistFollow() {
        viewModelScope.launch {
            if (userProfile.value == null) {
                _toastEvent.emit("请先登录账号")
                return@launch
            }
            val artistId = _mvDetail.value?.artistId ?: return@launch
            val target = !_isArtistFollowed.value
            _isArtistFollowed.value = target
            artistRepository.subscribeArtist(artistId, target).collect { result ->
                result.onSuccess {
                    _toastEvent.emit(if (target) "已关注歌手" else "已取消关注歌手")
                }.onFailure { e ->
                    _isArtistFollowed.value = !target
                    _toastEvent.emit(e.toUserMessage(resourceProvider))
                }
            }
        }
    }

    fun loadComments(mvId: Long) {
        commentsController.load("R_MV_5_$mvId")
    }

    fun likeComment(comment: CommentItem) {
        viewModelScope.launch {
            if (userProfile.value == null) {
                _toastEvent.emit("请先登录账号")
                return@launch
            }
            commentsController.like(comment)
        }
    }

    fun likeComment(mvId: Long, comment: CommentItem) {
        likeComment(comment)
    }

    fun changeCommentSort(sortType: CommentSortType) = commentsController.changeSort(sortType)
    fun loadMoreComments() = commentsController.loadMore()
    fun submitComment(content: String) {
        val profile = userProfile.value ?: return
        commentsController.submitComment(content, CommentUser(userId = profile.uid, nickname = profile.nickname, avatarUrl = profile.avatarUrl))
    }

    fun submitCommentReply(parentCommentId: Long, content: String) {
        val profile = userProfile.value ?: return
        commentsController.submitReply(parentCommentId, content, CommentUser(userId = profile.uid, nickname = profile.nickname, avatarUrl = profile.avatarUrl))
    }
    fun deleteCommentItem(comment: CommentItem) = commentsController.deleteComment(comment)
    fun openCommentFloor(comment: CommentItem) = commentsController.openFloor(comment)
    fun loadMoreCommentFloor() = commentsController.loadMoreFloor()
    fun closeCommentFloor() = commentsController.closeFloor()

    fun toggleLike(mvId: Long) {
        viewModelScope.launch {
            if (userProfile.value == null) {
                _toastEvent.emit("请先登录账号")
                return@launch
            }
            val detail = _mvDetail.value ?: return@launch
            val target = !detail.isLiked
            _mvDetail.value = detail.copy(isLiked = target)
            artistRepository.likeMv(mvId, target).collect { result ->
                result.onFailure { e ->
                    _mvDetail.value = detail
                    _toastEvent.emit(e.toUserMessage(resourceProvider))
                }
            }
        }
    }

    fun toggleSubscribe(mvId: Long) {
        viewModelScope.launch {
            if (userProfile.value == null) {
                _toastEvent.emit("请先登录账号")
                return@launch
            }
            val detail = _mvDetail.value ?: return@launch
            val target = !detail.isSubscribed
            _mvDetail.value = detail.copy(isSubscribed = target)
            artistRepository.subscribeMv(mvId, target).collect { result ->
                result.onSuccess {
                    _toastEvent.emit(if (target) "已收藏 MV" else "已取消收藏")
                }.onFailure { e ->
                    _mvDetail.value = detail
                    _toastEvent.emit(e.toUserMessage(resourceProvider))
                }
            }
        }
    }
}

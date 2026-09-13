package com.lin0721.linmusic.core.comment.domain

import com.lin0721.linmusic.core.comment.data.CommentRepository
import com.lin0721.linmusic.core.comment.data.CommentSortType
import com.lin0721.linmusic.core.comment.ui.CommentsState
import com.lin0721.linmusic.core.model.CommentItem
import com.lin0721.linmusic.core.network.ResourceProvider
import com.lin0721.linmusic.core.network.toUserMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

private const val PAGE_SIZE = 20

sealed interface CommentComposerState {
    data object Idle : CommentComposerState
    data object Submitting : CommentComposerState
}

sealed interface CommentFloorState {
    data object Idle : CommentFloorState
    data object Loading : CommentFloorState
    data class Success(
        val ownerComment: CommentItem,
        val replies: List<CommentItem>,
        val hasMore: Boolean,
        val isLoadingMore: Boolean = false
    ) : CommentFloorState
    data class Error(val message: String) : CommentFloorState
}

// 评论区共享逻辑：Player/Playlist/ArtistMvPlayer 三个 ViewModel 各自持有一个实例并转发状态，
// 避免加载/分页/排序/点赞/发表/回复/删除/楼层这些逻辑在三处重复实现
class CommentsSectionController(
    private val scope: CoroutineScope,
    private val repository: CommentRepository,
    private val onToast: suspend (String) -> Unit,
    private val resourceProvider: ResourceProvider
) {

    private val _commentsState = MutableStateFlow<CommentsState>(CommentsState.Loading)
    val commentsState: StateFlow<CommentsState> = _commentsState.asStateFlow()

    private val _composerState = MutableStateFlow<CommentComposerState>(CommentComposerState.Idle)
    val composerState: StateFlow<CommentComposerState> = _composerState.asStateFlow()

    private val _floorState = MutableStateFlow<CommentFloorState>(CommentFloorState.Idle)
    val floorState: StateFlow<CommentFloorState> = _floorState.asStateFlow()

    private var threadId: String = ""
    private var currentSortType: CommentSortType = CommentSortType.LATEST
    private var currentPageNo: Int = 1

    fun load(threadId: String) {
        this.threadId = threadId
        this.currentPageNo = 1
        fetchPage(pageNo = 1, sortType = currentSortType, previousCursor = null, append = false)
    }

    fun retry() {
        if (threadId.isNotEmpty()) {
            fetchPage(pageNo = currentPageNo, sortType = currentSortType, previousCursor = null, append = false)
        }
    }

    fun loadMore() {
        val state = _commentsState.value as? CommentsState.Success ?: return
        if (!state.hasMore || state.isLoadingMore) return
        _commentsState.value = state.copy(isLoadingMore = true)
        fetchPage(pageNo = currentPageNo + 1, sortType = currentSortType, previousCursor = state.cursor, append = true)
    }

    fun changeSort(sortType: CommentSortType) {
        if (sortType == currentSortType) return
        currentSortType = sortType
        currentPageNo = 1
        fetchPage(pageNo = 1, sortType = sortType, previousCursor = null, append = false)
    }

    private fun fetchPage(pageNo: Int, sortType: CommentSortType, previousCursor: String?, append: Boolean) {
        if (!append) _commentsState.value = CommentsState.Loading
        val cursor = sortType.cursorFor(pageNo, PAGE_SIZE, previousCursor)
        scope.launch {
            repository.getCommentsV2(threadId, pageNo, PAGE_SIZE, cursor, sortType).collect { result ->
                result.onSuccess { data ->
                    currentPageNo = pageNo
                    val previous = _commentsState.value as? CommentsState.Success
                    val mergedComments = if (append && previous != null) previous.comments + data.comments else data.comments
                    _commentsState.value = CommentsState.Success(
                        hotComments = if (append) previous?.hotComments.orEmpty() else emptyList(),
                        comments = mergedComments,
                        total = data.totalCount,
                        sortType = sortType,
                        cursor = data.cursor,
                        hasMore = data.hasMore,
                        isLoadingMore = false
                    )
                }.onFailure { error ->
                    if (append) {
                        val previous = _commentsState.value as? CommentsState.Success
                        if (previous != null) _commentsState.value = previous.copy(isLoadingMore = false)
                        scope.launch { onToast(error.toUserMessage(resourceProvider)) }
                    } else {
                        _commentsState.value = CommentsState.Error(error.toUserMessage(resourceProvider))
                    }
                }
            }
        }
    }

    fun like(comment: CommentItem) {
        val currentState = _commentsState.value as? CommentsState.Success ?: return
        val targetLike = !comment.liked

        fun bump(item: CommentItem) = if (item.commentId == comment.commentId) {
            item.copy(liked = targetLike, likedCount = item.likedCount + if (targetLike) 1 else -1)
        } else item

        _commentsState.value = currentState.copy(
            comments = currentState.comments.map(::bump),
            hotComments = currentState.hotComments.map(::bump)
        )

        scope.launch {
            repository.likeComment(threadId, comment.commentId, targetLike).collect { result ->
                result.onFailure { error ->
                    _commentsState.value = currentState
                    onToast(error.toUserMessage(resourceProvider))
                }
            }
        }
    }

    fun submitComment(content: String) {
        if (content.isBlank()) return
        _composerState.value = CommentComposerState.Submitting
        scope.launch {
            repository.addComment(threadId, content).collect { result ->
                _composerState.value = CommentComposerState.Idle
                result.onSuccess { newComment ->
                    val state = _commentsState.value as? CommentsState.Success ?: return@onSuccess
                    _commentsState.value = if (newComment != null) {
                        state.copy(comments = listOf(newComment) + state.comments, total = state.total + 1)
                    } else {
                        state.copy(total = state.total + 1)
                    }
                }.onFailure { error ->
                    onToast(error.toUserMessage(resourceProvider))
                }
            }
        }
    }

    fun submitReply(parentCommentId: Long, content: String) {
        if (content.isBlank()) return
        _composerState.value = CommentComposerState.Submitting
        scope.launch {
            repository.replyComment(threadId, parentCommentId, content).collect { result ->
                _composerState.value = CommentComposerState.Idle
                result.onSuccess {
                    scope.launch { onToast("回复成功") }
                }.onFailure { error ->
                    onToast(error.toUserMessage(resourceProvider))
                }
            }
        }
    }

    fun deleteComment(comment: CommentItem) {
        val currentState = _commentsState.value as? CommentsState.Success ?: return
        val updated = currentState.copy(
            comments = currentState.comments.filterNot { it.commentId == comment.commentId },
            total = (currentState.total - 1).coerceAtLeast(0)
        )
        _commentsState.value = updated
        scope.launch {
            repository.deleteComment(threadId, comment.commentId).collect { result ->
                result.onFailure { error ->
                    _commentsState.value = currentState
                    onToast(error.toUserMessage(resourceProvider))
                }
            }
        }
    }

    fun openFloor(comment: CommentItem) {
        _floorState.value = CommentFloorState.Loading
        fetchFloor(comment, time = -1, append = false)
    }

    fun loadMoreFloor() {
        val state = _floorState.value as? CommentFloorState.Success ?: return
        if (!state.hasMore || state.isLoadingMore) return
        val lastTime = state.replies.lastOrNull()?.time ?: -1
        _floorState.value = state.copy(isLoadingMore = true)
        fetchFloor(state.ownerComment, time = lastTime, append = true)
    }

    fun closeFloor() {
        _floorState.value = CommentFloorState.Idle
    }

    private fun fetchFloor(ownerComment: CommentItem, time: Long, append: Boolean) {
        scope.launch {
            repository.getFloorComments(threadId, ownerComment.commentId, time, PAGE_SIZE).collect { result ->
                result.onSuccess { data ->
                    val previous = _floorState.value as? CommentFloorState.Success
                    val merged = if (append && previous != null) previous.replies + data.comments else data.comments
                    _floorState.value = CommentFloorState.Success(
                        ownerComment = ownerComment,
                        replies = merged,
                        hasMore = data.hasMore,
                        isLoadingMore = false
                    )
                }.onFailure { error ->
                    if (append) {
                        val previous = _floorState.value as? CommentFloorState.Success
                        if (previous != null) _floorState.value = previous.copy(isLoadingMore = false)
                        onToast(error.toUserMessage(resourceProvider))
                    } else {
                        _floorState.value = CommentFloorState.Error(error.toUserMessage(resourceProvider))
                    }
                }
            }
        }
    }
}


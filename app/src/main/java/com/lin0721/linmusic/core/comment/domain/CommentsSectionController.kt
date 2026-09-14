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
    // 提交失败，未清空输入框内容，UI 侧据此保留用户已输入的草稿而不是清空重打
    data object Failed : CommentComposerState
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
private data class TabCacheEntry(
    val state: CommentsState.Success,
    val pageNo: Int
)

// 评论区共享逻辑：Player/Playlist/ArtistMvPlayer 三个 ViewModel 各自持有一个实例并转发状态，
// 避免加载/分页/排序/点赞/发表/回复/删除/楼层这些逻辑在三处重复实现
class CommentsSectionController(
    private val scope: CoroutineScope,
    private val repository: CommentRepository,
    private val onToast: suspend (String) -> Unit,
    private val resourceProvider: ResourceProvider
) {

    private val _commentsState = MutableStateFlow<CommentsState>(CommentsState.Loading())
    val commentsState: StateFlow<CommentsState> = _commentsState.asStateFlow()

    private val _composerState = MutableStateFlow<CommentComposerState>(CommentComposerState.Idle)
    val composerState: StateFlow<CommentComposerState> = _composerState.asStateFlow()

    private val _floorState = MutableStateFlow<CommentFloorState>(CommentFloorState.Idle)
    val floorState: StateFlow<CommentFloorState> = _floorState.asStateFlow()

    private var threadId: String = ""
    private var currentSortType: CommentSortType = CommentSortType.RECOMMEND
    private var currentPageNo: Int = 1
    private val sortCache = mutableMapOf<CommentSortType, TabCacheEntry>()

    fun load(threadId: String) {
        if (this.threadId == threadId && _commentsState.value is CommentsState.Success) {
            return
        }
        this.threadId = threadId
        sortCache.clear()
        this.currentPageNo = 1
        _commentsState.value = CommentsState.Loading(sortType = currentSortType)
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
        val cached = sortCache[sortType]
        if (cached != null) {
            currentPageNo = cached.pageNo
            _commentsState.value = cached.state
        } else {
            val lastTotal = (_commentsState.value as? CommentsState.Success)?.total
                ?: _commentsState.value.totalCount
            _commentsState.value = CommentsState.Loading(sortType = sortType, totalCount = lastTotal)
            currentPageNo = 1
            fetchPage(pageNo = 1, sortType = sortType, previousCursor = null, append = false)
        }
    }

    private fun fetchPage(pageNo: Int, sortType: CommentSortType, previousCursor: String?, append: Boolean) {
        if (!append && _commentsState.value !is CommentsState.Loading) {
            val lastTotal = (_commentsState.value as? CommentsState.Success)?.total
                ?: _commentsState.value.totalCount
            _commentsState.value = CommentsState.Loading(sortType = sortType, totalCount = lastTotal)
        }
        val cursor = sortType.cursorFor(pageNo, PAGE_SIZE, previousCursor)
        val requestThreadId = threadId
        scope.launch {
            repository.getCommentsV2(requestThreadId, pageNo, PAGE_SIZE, cursor, sortType).collect { result ->
                // 请求发出后 threadId 变了（切歌）或排序又被切换过，说明这次响应已经过期，丢弃不覆盖当前状态
                if (requestThreadId != threadId || sortType != currentSortType) return@collect
                result.onSuccess { data ->
                    currentPageNo = pageNo
                    val previous = if (append) _commentsState.value as? CommentsState.Success else null
                    val mergedComments = if (append && previous != null) previous.comments + data.comments else data.comments
                    val newState = CommentsState.Success(
                        hotComments = if (append) previous?.hotComments.orEmpty() else emptyList(),
                        comments = mergedComments,
                        total = data.totalCount,
                        sortType = sortType,
                        cursor = data.cursor,
                        hasMore = data.hasMore,
                        isLoadingMore = false
                    )
                    _commentsState.value = newState
                    sortCache[sortType] = TabCacheEntry(state = newState, pageNo = pageNo)
                }.onFailure { error ->
                    if (append) {
                        val previous = _commentsState.value as? CommentsState.Success
                        if (previous != null) _commentsState.value = previous.copy(isLoadingMore = false)
                        scope.launch { onToast(error.toUserMessage(resourceProvider)) }
                    } else {
                        val lastTotal = _commentsState.value.totalCount
                        _commentsState.value = CommentsState.Error(
                            message = error.toUserMessage(resourceProvider),
                            sortType = sortType,
                            totalCount = lastTotal
                        )
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

        val updatedCurrent = currentState.copy(
            comments = currentState.comments.map(::bump),
            hotComments = currentState.hotComments.map(::bump)
        )
        _commentsState.value = updatedCurrent
        sortCache[currentState.sortType] = TabCacheEntry(state = updatedCurrent, pageNo = currentPageNo)

        // 跨 Tab 同步缓存中的点赞状态
        sortCache.forEach { (type, entry) ->
            if (type != currentState.sortType) {
                val updatedState = entry.state.copy(
                    comments = entry.state.comments.map(::bump),
                    hotComments = entry.state.hotComments.map(::bump)
                )
                sortCache[type] = entry.copy(state = updatedState)
            }
        }

        val requestThreadId = threadId
        scope.launch {
            repository.likeComment(requestThreadId, comment.commentId, targetLike).collect { result ->
                if (requestThreadId != threadId) return@collect
                result.onFailure { error ->
                    _commentsState.value = currentState
                    sortCache[currentState.sortType] = TabCacheEntry(state = currentState, pageNo = currentPageNo)
                    onToast(error.toUserMessage(resourceProvider))
                }
            }
        }
    }

    fun submitComment(content: String) {
        if (content.isBlank()) return
        _composerState.value = CommentComposerState.Submitting
        val requestThreadId = threadId
        scope.launch {
            repository.addComment(requestThreadId, content).collect { result ->
                if (requestThreadId != threadId) return@collect
                result.onSuccess { newComment ->
                    _composerState.value = CommentComposerState.Idle
                    val state = _commentsState.value as? CommentsState.Success ?: return@onSuccess
                    val newTotal = state.total + 1
                    val updatedState = if (newComment != null) {
                        state.copy(comments = listOf(newComment) + state.comments, total = newTotal)
                    } else {
                        state.copy(total = newTotal)
                    }
                    _commentsState.value = updatedState
                    sortCache[state.sortType] = TabCacheEntry(state = updatedState, pageNo = currentPageNo)

                    // 跨 Tab 同步更新总评论数
                    sortCache.forEach { (type, entry) ->
                        if (type != state.sortType) {
                            sortCache[type] = entry.copy(state = entry.state.copy(total = newTotal))
                        }
                    }
                }.onFailure { error ->
                    // 失败态不清空输入框内容，交由 UI 保留用户已输入的草稿
                    _composerState.value = CommentComposerState.Failed
                    onToast(error.toUserMessage(resourceProvider))
                }
            }
        }
    }

    fun submitReply(parentCommentId: Long, content: String) {
        if (content.isBlank()) return
        _composerState.value = CommentComposerState.Submitting
        val requestThreadId = threadId
        scope.launch {
            repository.replyComment(requestThreadId, parentCommentId, content).collect { result ->
                if (requestThreadId != threadId) return@collect
                result.onSuccess {
                    _composerState.value = CommentComposerState.Idle
                    onToast("回复成功")
                    // 回复的响应体是否带新评论对象未经真机验证，统一重新拉一次楼层首页保证列表里能看到刚发的这条
                    val floor = _floorState.value as? CommentFloorState.Success
                    if (floor != null) {
                        fetchFloor(floor.ownerComment, time = -1, append = false)
                    }
                }.onFailure { error ->
                    _composerState.value = CommentComposerState.Failed
                    onToast(error.toUserMessage(resourceProvider))
                }
            }
        }
    }

    fun deleteComment(comment: CommentItem) {
        val currentState = _commentsState.value as? CommentsState.Success ?: return
        val newTotal = (currentState.total - 1).coerceAtLeast(0)
        val updated = currentState.copy(
            comments = currentState.comments.filterNot { it.commentId == comment.commentId },
            total = newTotal
        )
        _commentsState.value = updated
        sortCache[currentState.sortType] = TabCacheEntry(state = updated, pageNo = currentPageNo)

        // 跨 Tab 同步删除
        sortCache.forEach { (type, entry) ->
            if (type != currentState.sortType) {
                val updatedState = entry.state.copy(
                    comments = entry.state.comments.filterNot { it.commentId == comment.commentId },
                    total = (entry.state.total - 1).coerceAtLeast(0)
                )
                sortCache[type] = entry.copy(state = updatedState)
            }
        }

        val requestThreadId = threadId
        scope.launch {
            repository.deleteComment(requestThreadId, comment.commentId).collect { result ->
                if (requestThreadId != threadId) return@collect
                result.onFailure { error ->
                    _commentsState.value = currentState
                    sortCache[currentState.sortType] = TabCacheEntry(state = currentState, pageNo = currentPageNo)
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


package com.lin0721.linmusic.core.comment.domain

import com.lin0721.linmusic.core.comment.data.CommentRepository
import com.lin0721.linmusic.core.comment.data.CommentSortType
import com.lin0721.linmusic.core.comment.data.isQQThread
import com.lin0721.linmusic.core.comment.ui.CommentsState
import com.lin0721.linmusic.core.model.BeRepliedComment
import com.lin0721.linmusic.core.model.CommentItem
import com.lin0721.linmusic.core.model.CommentUser
import com.lin0721.linmusic.core.network.ResourceProvider
import com.lin0721.linmusic.core.network.toUserMessage
import com.lin0721.linmusic.core.platform.UnifiedComment
import com.lin0721.linmusic.core.platform.repository.CommentTarget
import com.lin0721.linmusic.core.platform.repository.PlatformCommentRouter
import com.lin0721.linmusic.core.source.MusicPlatform
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.absoluteValue

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

private data class TabCacheEntry(
    val state: CommentsState.Success,
    val pageNo: Int
)

// 评论区共享逻辑：Player/Playlist 两个 ViewModel 各自持有一个实例并转发状态，
// 避免加载/分页/排序/点赞/发表/回复/删除/楼层这些逻辑在两处重复实现
class CommentsSectionController(
    private val scope: CoroutineScope,
    private val repository: CommentRepository,
    private val onToast: suspend (String) -> Unit,
    private val resourceProvider: ResourceProvider,
    private val platformCommentRouter: PlatformCommentRouter? = null
) {

    private val _commentsState = MutableStateFlow<CommentsState>(CommentsState.Loading())
    val commentsState: StateFlow<CommentsState> = _commentsState.asStateFlow()

    private val _composerState = MutableStateFlow<CommentComposerState>(CommentComposerState.Idle)
    val composerState: StateFlow<CommentComposerState> = _composerState.asStateFlow()

    private val _floorState = MutableStateFlow<CommentFloorState>(CommentFloorState.Idle)
    val floorState: StateFlow<CommentFloorState> = _floorState.asStateFlow()

    private var threadId: String = ""
    private var currentTarget: CommentTarget? = null
    private var currentKugouSpecialId: String? = null
    private var currentSortType: CommentSortType = CommentSortType.RECOMMEND
    private var currentPageNo: Int = 1
    private var currentFloorPageNo: Int = 1
    private val sortCache = mutableMapOf<CommentSortType, TabCacheEntry>()

    val activePlatform: MusicPlatform
        get() = currentTarget?.platform ?: if (isQQThread(threadId)) MusicPlatform.QQ else MusicPlatform.NETEASE

    fun load(threadId: String) {
        if (this.currentTarget == null && this.threadId == threadId && _commentsState.value is CommentsState.Success) {
            return
        }
        this.currentTarget = null
        this.currentKugouSpecialId = null
        this.threadId = threadId
        sortCache.clear()
        this.currentPageNo = 1
        _commentsState.value = CommentsState.Loading(sortType = currentSortType)
        fetchPage(pageNo = 1, sortType = currentSortType, previousCursor = null, append = false)
    }

    fun loadTarget(target: CommentTarget) {
        if (platformCommentRouter == null && target.platform == MusicPlatform.NETEASE) {
            legacyNeteaseThreadId(target)?.let {
                load(it)
                return
            }
        }
        if (this.currentTarget == target && _commentsState.value is CommentsState.Success) {
            return
        }
        this.currentTarget = target
        this.currentKugouSpecialId = null
        this.threadId = ""
        sortCache.clear()
        this.currentPageNo = 1
        _commentsState.value = CommentsState.Loading(sortType = currentSortType)
        fetchPage(pageNo = 1, sortType = currentSortType, previousCursor = null, append = false)
    }

    fun retry() {
        if (currentTarget != null || threadId.isNotEmpty()) {
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
        val requestTarget = currentTarget
        val router = platformCommentRouter

        if (requestTarget != null && router != null) {
            scope.launch {
                router.getComments(requestTarget, pageNo, PAGE_SIZE, cursor, sortType).collect { result ->
                    if (requestTarget != currentTarget || sortType != currentSortType) return@collect
                    result.onSuccess { data ->
                        currentPageNo = pageNo
                        if (!data.kugouSpecialId.isNullOrBlank()) {
                            currentKugouSpecialId = data.kugouSpecialId
                        }
                        val previous = if (append) _commentsState.value as? CommentsState.Success else null
                        val mappedNormal = data.comments.map { it.toCommentItem() }
                        val mappedHot = if (append) previous?.hotComments.orEmpty() else data.hotComments.map { it.toCommentItem() }
                        val mergedComments = if (append && previous != null) previous.comments + mappedNormal else mappedNormal
                        val platform = requestTarget.platform
                        val readOnly = platform != MusicPlatform.NETEASE && platform != MusicPlatform.KUGOU
                        val newState = CommentsState.Success(
                            hotComments = mappedHot,
                            comments = mergedComments,
                            total = data.totalCount,
                            sortType = sortType,
                            cursor = data.cursor,
                            hasMore = data.hasMore,
                            isLoadingMore = false,
                            readOnly = readOnly,
                            platform = platform,
                            canLike = platform == MusicPlatform.NETEASE,
                            canReply = platform == MusicPlatform.NETEASE || platform == MusicPlatform.KUGOU,
                            canDelete = platform == MusicPlatform.NETEASE || platform == MusicPlatform.KUGOU,
                            supportsFloor = platform == MusicPlatform.NETEASE || platform == MusicPlatform.KUGOU,
                            supportsSort = platform == MusicPlatform.NETEASE
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
            return
        }

        scope.launch {
            repository.getCommentsV2(requestThreadId, pageNo, PAGE_SIZE, cursor, sortType).collect { result ->
                // 请求发出后 threadId 变了（切歌）或排序又被切换过，说明这次响应已经过期，丢弃不覆盖当前状态
                if (requestTarget != currentTarget || requestThreadId != threadId || sortType != currentSortType) return@collect
                result.onSuccess { data ->
                    currentPageNo = pageNo
                    val previous = if (append) _commentsState.value as? CommentsState.Success else null
                    val mergedComments = if (append && previous != null) previous.comments + data.comments else data.comments
                    val readOnly = isQQThread(requestThreadId)
                    val newState = CommentsState.Success(
                        hotComments = if (append) previous?.hotComments.orEmpty() else emptyList(),
                        comments = mergedComments,
                        total = data.totalCount,
                        sortType = sortType,
                        cursor = data.cursor,
                        hasMore = data.hasMore,
                        isLoadingMore = false,
                        readOnly = readOnly,
                        platform = if (readOnly) MusicPlatform.QQ else MusicPlatform.NETEASE
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

    // 楼层里的回复不在主列表中，需同时改楼层状态，否则点赞后界面不变
    fun like(comment: CommentItem) {
        val requestTarget = currentTarget
        val requestThreadId = threadId
        if ((requestTarget == null && requestThreadId.isEmpty()) || comment.commentId == 0L) return
        if (requestTarget != null && requestTarget.platform == MusicPlatform.KUGOU) {
            scope.launch { onToast("酷狗暂未开放评论点赞") }
            return
        }
        val targetLike = !comment.liked
        applyLikeState(comment.commentId, targetLike)

        scope.launch {
            val flow = if (requestTarget != null && platformCommentRouter != null) {
                val commentKey = comment.commentKey.ifBlank { comment.commentId.toString() }
                platformCommentRouter.likeComment(requestTarget, commentKey, targetLike)
            } else {
                repository.likeComment(requestThreadId, comment.commentId, targetLike)
            }
            flow.collect { result ->
                if (requestTarget != currentTarget || requestThreadId != threadId) return@collect
                result.onFailure { error ->
                    applyLikeState(comment.commentId, !targetLike)
                    onToast(error.toUserMessage(resourceProvider))
                }
            }
        }
    }

    // 按目标状态改写，已是目标状态的条目不动，回滚时不会把计数改错
    private fun applyLikeState(commentId: Long, liked: Boolean) {
        fun CommentItem.withLike(): CommentItem =
            if (this.commentId != commentId || this.liked == liked) this
            else copy(liked = liked, likedCount = (likedCount + if (liked) 1 else -1).coerceAtLeast(0))

        fun CommentsState.Success.withLike(): CommentsState.Success = copy(
            comments = comments.map { it.withLike() },
            hotComments = hotComments.map { it.withLike() }
        )

        (_commentsState.value as? CommentsState.Success)?.let { state ->
            _commentsState.value = state.withLike()
        }
        sortCache.keys.toList().forEach { type ->
            sortCache[type]?.let { entry -> sortCache[type] = entry.copy(state = entry.state.withLike()) }
        }
        (_floorState.value as? CommentFloorState.Success)?.let { floor ->
            _floorState.value = floor.copy(
                ownerComment = floor.ownerComment.withLike(),
                replies = floor.replies.map { it.withLike() }
            )
        }
    }

    // author 用于服务端未按预期返回新评论对象时，本地兜底构造一条乐观展示的评论——
    fun submitComment(content: String, author: CommentUser) {
        if (content.isBlank()) return
        _composerState.value = CommentComposerState.Submitting
        val requestTarget = currentTarget
        val requestThreadId = threadId
        val optimisticComment = buildOptimisticComment(author, content)
        scope.launch {
            val resultFlow = if (requestTarget != null && platformCommentRouter != null) {
                platformCommentRouter.addComment(requestTarget, content).let { flow ->
                    kotlinx.coroutines.flow.flow {
                        flow.collect { res -> emit(res.map { it?.toCommentItem() }) }
                    }
                }
            } else {
                repository.addComment(requestThreadId, content)
            }
            resultFlow.collect { result ->
                if (requestTarget != currentTarget || requestThreadId != threadId) return@collect
                result.onSuccess { newComment ->
                    _composerState.value = CommentComposerState.Idle
                    onToast("发送成功")
                    val state = _commentsState.value as? CommentsState.Success ?: return@onSuccess
                    val commentToInsert = newComment ?: optimisticComment
                    val newTotal = state.total + 1
                    val updatedState = state.copy(comments = listOf(commentToInsert) + state.comments, total = newTotal)
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

    fun submitReply(parentCommentId: Long, content: String, author: CommentUser) {
        if (content.isBlank()) return
        _composerState.value = CommentComposerState.Submitting
        val requestTarget = currentTarget
        val requestThreadId = threadId
        val optimisticReply = buildOptimisticComment(author, content, parentCommentId = parentCommentId)
        val floor = _floorState.value as? CommentFloorState.Success
        val targetComment = findCommentById(parentCommentId)
        scope.launch {
            val resultFlow = if (requestTarget != null && platformCommentRouter != null) {
                val floorOwnerKey = floor?.ownerComment?.let { it.commentKey.ifBlank { it.commentId.toString() } }
                val targetKey = targetComment?.let { it.commentKey.ifBlank { it.commentId.toString() } }
                    ?: parentCommentId.toString()
                val isSubReply = floor != null && floor.ownerComment.commentId != parentCommentId
                val tid = floorOwnerKey ?: targetKey
                val pid = if (isSubReply) targetKey else "0"
                platformCommentRouter.replyComment(
                    target = requestTarget,
                    commentId = tid,
                    content = content,
                    pid = pid,
                    replyUserName = if (isSubReply) targetComment?.user?.nickname else null,
                    replyContent = if (isSubReply) targetComment?.content else null,
                    kugouSpecialId = currentKugouSpecialId
                ).let { flow ->
                    kotlinx.coroutines.flow.flow {
                        flow.collect { res -> emit(res.map { it?.toCommentItem() }) }
                    }
                }
            } else {
                repository.replyComment(requestThreadId, parentCommentId, content)
            }
            resultFlow.collect { result ->
                if (requestTarget != currentTarget || requestThreadId != threadId) return@collect
                result.onSuccess { newComment ->
                    _composerState.value = CommentComposerState.Idle
                    onToast("发送成功")

                    val currentFloor = _floorState.value as? CommentFloorState.Success
                    val commentToInsert = newComment ?: optimisticReply
                    if (currentFloor != null) {
                        _floorState.value = currentFloor.copy(replies = listOf(commentToInsert) + currentFloor.replies)
                    }

                    // 楼主评论的"展开 N 条回复"计数同步 +1，不管当前有没有打开楼层页都要更新
                    val topLevelCommentId = currentFloor?.ownerComment?.commentId ?: parentCommentId
                    val listState = _commentsState.value as? CommentsState.Success
                    if (listState != null) {
                        fun bumpReplyCount(item: CommentItem) =
                            if (item.commentId == topLevelCommentId) item.copy(replyCount = item.replyCount + 1) else item
                        val updated = listState.copy(
                            comments = listState.comments.map(::bumpReplyCount),
                            hotComments = listState.hotComments.map(::bumpReplyCount)
                        )
                        _commentsState.value = updated
                        sortCache[listState.sortType] = TabCacheEntry(state = updated, pageNo = currentPageNo)
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
        fun isSameComment(item: CommentItem): Boolean =
            if (comment.commentKey.isNotBlank() && item.commentKey.isNotBlank()) {
                item.commentKey == comment.commentKey
            } else {
                item.commentId == comment.commentId
            }

        val updated = currentState.copy(
            comments = currentState.comments.filterNot(::isSameComment),
            hotComments = currentState.hotComments.filterNot(::isSameComment),
            total = newTotal
        )
        _commentsState.value = updated
        sortCache[currentState.sortType] = TabCacheEntry(state = updated, pageNo = currentPageNo)

        // 跨 Tab 同步删除
        sortCache.forEach { (type, entry) ->
            if (type != currentState.sortType) {
                val updatedState = entry.state.copy(
                    comments = entry.state.comments.filterNot(::isSameComment),
                    hotComments = entry.state.hotComments.filterNot(::isSameComment),
                    total = (entry.state.total - 1).coerceAtLeast(0)
                )
                sortCache[type] = entry.copy(state = updatedState)
            }
        }

        val requestTarget = currentTarget
        val requestThreadId = threadId
        val floorOwnerKey = (_floorState.value as? CommentFloorState.Success)?.ownerComment?.commentKey
            ?.takeIf { it.isNotBlank() }
        scope.launch {
            val flow = if (requestTarget != null && platformCommentRouter != null) {
                val cid = comment.commentKey.ifBlank { comment.commentId.toString() }
                platformCommentRouter.deleteComment(
                    target = requestTarget,
                    commentId = cid,
                    tid = floorOwnerKey,
                    kugouSpecialId = currentKugouSpecialId
                )
            } else {
                repository.deleteComment(requestThreadId, comment.commentId)
            }
            flow.collect { result ->
                if (requestTarget != currentTarget || requestThreadId != threadId) return@collect
                result.onFailure { error ->
                    _commentsState.value = currentState
                    sortCache[currentState.sortType] = TabCacheEntry(state = currentState, pageNo = currentPageNo)
                    onToast(error.toUserMessage(resourceProvider))
                }
            }
        }
    }

    fun openFloor(comment: CommentItem) {
        currentFloorPageNo = 1
        _floorState.value = CommentFloorState.Loading
        fetchFloor(comment, time = -1, pageNo = 1, append = false)
    }

    fun loadMoreFloor() {
        val state = _floorState.value as? CommentFloorState.Success ?: return
        if (!state.hasMore || state.isLoadingMore) return
        val lastTime = state.replies.lastOrNull()?.time ?: -1
        _floorState.value = state.copy(isLoadingMore = true)
        fetchFloor(state.ownerComment, time = lastTime, pageNo = currentFloorPageNo + 1, append = true)
    }

    fun closeFloor() {
        _floorState.value = CommentFloorState.Idle
    }

    private fun fetchFloor(ownerComment: CommentItem, time: Long, pageNo: Int, append: Boolean) {
        val requestTarget = currentTarget
        val requestThreadId = threadId
        val router = platformCommentRouter
        scope.launch {
            if (requestTarget != null && router != null) {
                val parentId = ownerComment.commentKey.ifBlank { ownerComment.commentId.toString() }
                router.getFloorComments(
                    target = requestTarget,
                    parentCommentId = parentId,
                    cursorOrTime = time,
                    pageNo = pageNo,
                    limit = PAGE_SIZE,
                    kugouSpecialId = currentKugouSpecialId
                ).collect { result ->
                    if (requestTarget != currentTarget || requestThreadId != threadId) return@collect
                    result.onSuccess { data ->
                        currentFloorPageNo = pageNo
                        val previous = _floorState.value as? CommentFloorState.Success
                        val mapped = data.comments.map { it.toCommentItem() }
                        val merged = if (append && previous != null) previous.replies + mapped else mapped
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
            } else {
                repository.getFloorComments(requestThreadId, ownerComment.commentId, time, PAGE_SIZE).collect { result ->
                    if (requestTarget != currentTarget || requestThreadId != threadId) return@collect
                    result.onSuccess { data ->
                        currentFloorPageNo = pageNo
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

    private fun findCommentById(commentId: Long): CommentItem? {
        (_floorState.value as? CommentFloorState.Success)?.let { floor ->
            if (floor.ownerComment.commentId == commentId) return floor.ownerComment
            floor.replies.firstOrNull { it.commentId == commentId }?.let { return it }
        }
        val listState = _commentsState.value as? CommentsState.Success ?: return null
        return listState.hotComments.firstOrNull { it.commentId == commentId }
            ?: listState.comments.firstOrNull { it.commentId == commentId }
    }

    private fun legacyNeteaseThreadId(target: CommentTarget): String? = when (target) {
        is CommentTarget.TrackTarget -> target.trackId.neteaseIdOrNull()?.let { "R_SO_4_$it" }
        is CommentTarget.PlaylistTarget -> target.playlistId.neteaseIdOrNull()?.let { "A_PL_0_$it" }
        is CommentTarget.AlbumTarget -> target.albumId.neteaseIdOrNull()?.let { "R_AL_3_$it" }
        is CommentTarget.RawNeteaseThread -> target.threadId
    }

    private fun UnifiedComment.toCommentItem(): CommentItem {
        val numericId = id.toLongOrNull()?.takeIf { it != 0L }
            ?: -(id.hashCode().toLong().absoluteValue + 1L)
        return CommentItem(
            commentId = numericId,
            user = CommentUser(
                userId = author.id?.rawId?.toLongOrNull() ?: 0L,
                nickname = author.nickname,
                avatarUrl = author.avatarUrl,
                platform = author.id?.platform ?: platform
            ),
            content = content,
            time = timeMs,
            timeStr = timeText,
            likedCount = likedCount,
            liked = liked,
            beReplied = repliedTo.map { replied ->
                BeRepliedComment(
                    user = replied.author?.let { a ->
                        CommentUser(
                            userId = a.id?.rawId?.toLongOrNull() ?: 0L,
                            nickname = a.nickname,
                            avatarUrl = a.avatarUrl,
                            platform = a.id?.platform ?: platform
                        )
                    },
                    content = replied.content,
                    beRepliedCommentId = replied.commentId?.toLongOrNull()
                )
            }.takeIf { it.isNotEmpty() },
            replyCount = replyCount,
            parentCommentId = parentCommentId?.toLongOrNull() ?: 0L,
            commentKey = id,
            parentCommentKey = parentCommentId.orEmpty()
        )
    }

    // 服务端未按预期返回新评论对象时的本地兜底：commentId 用负数时间戳标记"未与服务端 ID 对齐"，
    private fun buildOptimisticComment(author: CommentUser, content: String, parentCommentId: Long = 0): CommentItem {
        val now = System.currentTimeMillis()
        return CommentItem(
            commentId = -now,
            user = author,
            content = content,
            time = now,
            timeStr = "刚刚",
            likedCount = 0,
            liked = false,
            parentCommentId = parentCommentId
        )
    }
}


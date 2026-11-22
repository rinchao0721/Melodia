package com.lin0721.linmusic.desktop.ui.comments

import com.lin0721.linmusic.core.auth.StoredAccount
import com.lin0721.linmusic.core.auth.UserProfile
import com.lin0721.linmusic.core.comment.data.CommentSortType
import com.lin0721.linmusic.core.comment.domain.CommentComposerState
import com.lin0721.linmusic.core.comment.domain.CommentFloorState
import com.lin0721.linmusic.core.comment.ui.CommentsState
import com.lin0721.linmusic.core.model.CommentItem
import com.lin0721.linmusic.feature.player.ui.PlayerViewModel
import com.lin0721.linmusic.feature.playlist.ui.PlaylistViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

// 评论面板的数据来源：歌曲与歌单各有自己的评论线程，面板只依赖这一层
interface CommentsHost {
    val commentsState: StateFlow<CommentsState>
    val composerState: StateFlow<CommentComposerState>
    val floorState: StateFlow<CommentFloorState>
    val userProfile: StateFlow<UserProfile?>
    val kugouAccount: StateFlow<StoredAccount?> get() = MutableStateFlow(null)

    fun retry()
    fun like(comment: CommentItem)
    fun changeSort(sortType: CommentSortType)
    fun loadMore()
    fun submit(content: String)
    fun reply(parentCommentId: Long, content: String)
    fun delete(comment: CommentItem)
    fun openFloor(comment: CommentItem)
    fun loadMoreFloor()
    fun closeFloor()
}

fun PlayerViewModel.asCommentsHost(): CommentsHost = object : CommentsHost {
    override val commentsState = this@asCommentsHost.commentsState
    override val composerState = this@asCommentsHost.composerState
    override val floorState = this@asCommentsHost.floorState
    override val userProfile = this@asCommentsHost.userProfile
    override val kugouAccount = this@asCommentsHost.kugouAccount

    override fun retry() = retryComments()
    override fun like(comment: CommentItem) = likeComment(comment)
    override fun changeSort(sortType: CommentSortType) = changeCommentSort(sortType)
    override fun loadMore() = loadMoreComments()
    override fun submit(content: String) = submitComment(content)
    override fun reply(parentCommentId: Long, content: String) = submitCommentReply(parentCommentId, content)
    override fun delete(comment: CommentItem) = deleteCommentItem(comment)
    override fun openFloor(comment: CommentItem) = openCommentFloor(comment)
    override fun loadMoreFloor() = loadMoreCommentFloor()
    override fun closeFloor() = closeCommentFloor()
}

fun PlaylistViewModel.asCommentsHost(): CommentsHost = object : CommentsHost {
    override val commentsState = this@asCommentsHost.commentsState
    override val composerState = this@asCommentsHost.composerState
    override val floorState = this@asCommentsHost.floorState
    override val userProfile = this@asCommentsHost.userProfile
    override val kugouAccount = this@asCommentsHost.kugouAccount

    override fun retry() = retryComments()
    override fun like(comment: CommentItem) = likeComment(comment)
    override fun changeSort(sortType: CommentSortType) = changeCommentSort(sortType)
    override fun loadMore() = loadMoreComments()
    override fun submit(content: String) = submitComment(content)
    override fun reply(parentCommentId: Long, content: String) = submitCommentReply(parentCommentId, content)
    override fun delete(comment: CommentItem) = deleteCommentItem(comment)
    override fun openFloor(comment: CommentItem) = openCommentFloor(comment)
    override fun loadMoreFloor() = loadMoreCommentFloor()
    override fun closeFloor() = closeCommentFloor()
}

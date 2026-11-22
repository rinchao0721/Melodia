package com.lin0721.linmusic.core.comment.ui

import com.lin0721.linmusic.core.comment.data.CommentSortType
import com.lin0721.linmusic.core.model.CommentItem
import com.lin0721.linmusic.core.source.MusicPlatform

sealed interface CommentsState {
    val sortType: CommentSortType
    val totalCount: Int?

    data class Loading(
        override val sortType: CommentSortType = CommentSortType.RECOMMEND,
        override val totalCount: Int? = null
    ) : CommentsState

    data class Success(
        val hotComments: List<CommentItem>,
        val comments: List<CommentItem>,
        val total: Int,
        override val sortType: CommentSortType = CommentSortType.RECOMMEND,
        val cursor: String = "0",
        val hasMore: Boolean = false,
        val isLoadingMore: Boolean = false,
        // QQ 音乐评论只能浏览，界面据此隐藏输入栏、点赞与回复
        val readOnly: Boolean = false,
        val platform: MusicPlatform = MusicPlatform.NETEASE,
        val canLike: Boolean = !readOnly,
        val canReply: Boolean = !readOnly,
        val canDelete: Boolean = !readOnly,
        val supportsFloor: Boolean = !readOnly,
        val supportsSort: Boolean = !readOnly
    ) : CommentsState {
        override val totalCount: Int get() = total
    }

    data class Error(
        val message: String,
        override val sortType: CommentSortType = CommentSortType.RECOMMEND,
        override val totalCount: Int? = null
    ) : CommentsState
}

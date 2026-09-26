package com.lin0721.linmusic.core.comment.ui

import com.lin0721.linmusic.core.comment.data.CommentSortType
import com.lin0721.linmusic.core.model.CommentItem

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
        val isLoadingMore: Boolean = false
    ) : CommentsState {
        override val totalCount: Int get() = total
    }

    data class Error(
        val message: String,
        override val sortType: CommentSortType = CommentSortType.RECOMMEND,
        override val totalCount: Int? = null
    ) : CommentsState
}

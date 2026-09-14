package com.lin0721.linmusic.core.comment.data

import com.lin0721.linmusic.core.model.CommentItem
import kotlinx.coroutines.flow.Flow

// 评论数据仓储（core 共享能力，被 playlist/player 等多个域的评论 Tab 复用）
interface CommentRepository {

    fun getComments(songId: Long, limit: Int = 20, offset: Int = 0): Flow<Result<CommentsResponse>>

    // 获取通用资源的评论 (例如歌单 A_PL_0_ID)
    fun getComments(threadId: String, limit: Int = 20, offset: Int = 0): Flow<Result<CommentsResponse>>

    // 评论点赞/取消点赞
    fun likeComment(threadId: String, commentId: Long, like: Boolean): Flow<Result<Unit>>

    // 分页 + 排序切换的评论列表
    fun getCommentsV2(
        threadId: String,
        pageNo: Int,
        pageSize: Int,
        cursor: String,
        sortType: CommentSortType
    ): Flow<Result<CommentsV2Data>>

    // 楼层子回复列表；time 传上一页最后一条子回复的 time，首页传 -1
    fun getFloorComments(
        threadId: String,
        parentCommentId: Long,
        time: Long,
        limit: Int = 20
    ): Flow<Result<FloorCommentsData>>

    // 发表评论
    fun addComment(threadId: String, content: String): Flow<Result<CommentItem?>>

    // 回复评论
    fun replyComment(threadId: String, commentId: Long, content: String): Flow<Result<CommentItem?>>

    // 删除自己发表的评论
    fun deleteComment(threadId: String, commentId: Long): Flow<Result<Unit>>
}

package com.lin0721.linmusic.core.comment.domain

import com.lin0721.linmusic.core.comment.data.CommentRepository
import com.lin0721.linmusic.core.comment.data.CommentSortType
import com.lin0721.linmusic.core.comment.data.CommentsResponse
import com.lin0721.linmusic.core.comment.data.CommentsV2Data
import com.lin0721.linmusic.core.comment.data.FloorCommentsData
import com.lin0721.linmusic.core.model.CommentItem
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

// 测试替身：每个方法的返回值都可以从测试用例外部直接赋值，不需要 mocking 库
class FakeCommentRepository : CommentRepository {

    var commentsV2Result: Result<CommentsV2Data> = Result.success(CommentsV2Data())
    var floorResult: Result<FloorCommentsData> = Result.success(FloorCommentsData())
    var likeResult: Result<Unit> = Result.success(Unit)
    var addCommentResult: Result<CommentItem?> = Result.success(null)
    var replyCommentResult: Result<CommentItem?> = Result.success(null)
    var deleteCommentResult: Result<Unit> = Result.success(Unit)

    var lastGetCommentsV2Call: Quintuple? = null
    data class Quintuple(val threadId: String, val pageNo: Int, val pageSize: Int, val cursor: String, val sortType: CommentSortType)

    override fun getComments(songId: Long, limit: Int, offset: Int): Flow<Result<CommentsResponse>> =
        flowOf(Result.success(CommentsResponse()))

    override fun getComments(threadId: String, limit: Int, offset: Int): Flow<Result<CommentsResponse>> =
        flowOf(Result.success(CommentsResponse()))

    override fun likeComment(threadId: String, commentId: Long, like: Boolean): Flow<Result<Unit>> =
        flowOf(likeResult)

    override fun getCommentsV2(
        threadId: String,
        pageNo: Int,
        pageSize: Int,
        cursor: String,
        sortType: CommentSortType
    ): Flow<Result<CommentsV2Data>> {
        lastGetCommentsV2Call = Quintuple(threadId, pageNo, pageSize, cursor, sortType)
        return flowOf(commentsV2Result)
    }

    override fun getFloorComments(
        threadId: String,
        parentCommentId: Long,
        time: Long,
        limit: Int
    ): Flow<Result<FloorCommentsData>> = flowOf(floorResult)

    override fun addComment(threadId: String, content: String): Flow<Result<CommentItem?>> =
        flowOf(addCommentResult)

    override fun replyComment(threadId: String, commentId: Long, content: String): Flow<Result<CommentItem?>> =
        flowOf(replyCommentResult)

    override fun deleteComment(threadId: String, commentId: Long): Flow<Result<Unit>> =
        flowOf(deleteCommentResult)
}

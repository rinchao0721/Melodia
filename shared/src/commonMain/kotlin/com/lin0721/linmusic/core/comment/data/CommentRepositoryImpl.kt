package com.lin0721.linmusic.core.comment.data

import com.lin0721.linmusic.core.model.CommentItem
import com.lin0721.linmusic.core.network.apiFlow
import kotlinx.coroutines.flow.Flow

class CommentRepositoryImpl(
    private val apiService: CommentApi
) : CommentRepository {

    override fun getComments(songId: Long, limit: Int, offset: Int): Flow<Result<CommentsResponse>> =
        getComments(threadId = "R_SO_4_$songId", limit = limit, offset = offset)

    override fun getComments(threadId: String, limit: Int, offset: Int): Flow<Result<CommentsResponse>> = apiFlow(
        request = {
            apiService.getComments(
                threadId = threadId,
                body = CommentsRequest(
                    threadId = threadId,
                    rid = threadId.substringAfterLast("_"),
                    limit = limit,
                    offset = offset
                )
            )
        },
        isSuccess = { it.isSuccess },
        code = { it.code },
        transform = { it }
    )

    override fun likeComment(threadId: String, commentId: Long, like: Boolean): Flow<Result<Unit>> = apiFlow(
        request = {
            apiService.likeComment(
                op = if (like) "like" else "unlike",
                body = LikeCommentRequest(
                    threadId = threadId,
                    commentId = commentId
                )
            )
        },
        isSuccess = { it.isSuccess },
        code = { it.code },
        msg = { it.message },
        transform = { Unit }
    )

    override fun getCommentsV2(
        threadId: String,
        pageNo: Int,
        pageSize: Int,
        cursor: String,
        sortType: CommentSortType
    ): Flow<Result<CommentsV2Data>> = apiFlow(
        request = {
            apiService.getCommentsV2(
                CommentsV2Request(
                    threadId = threadId,
                    pageNo = pageNo,
                    pageSize = pageSize,
                    cursor = cursor,
                    sortType = sortType.wireValue
                )
            )
        },
        isSuccess = { it.isSuccess },
        code = { it.code },
        transform = { it.data ?: CommentsV2Data() }
    )

    override fun getFloorComments(
        threadId: String,
        parentCommentId: Long,
        time: Long,
        limit: Int
    ): Flow<Result<FloorCommentsData>> = apiFlow(
        request = {
            apiService.getFloorComments(
                FloorCommentsRequest(
                    parentCommentId = parentCommentId,
                    threadId = threadId,
                    time = time,
                    limit = limit
                )
            )
        },
        isSuccess = { it.isSuccess },
        code = { it.code },
        transform = { it.data ?: FloorCommentsData() }
    )

    override fun addComment(threadId: String, content: String): Flow<Result<CommentItem?>> = apiFlow(
        request = { apiService.addComment(AddCommentRequest(threadId = threadId, content = content)) },
        isSuccess = { it.isSuccess },
        code = { it.code },
        msg = { it.message },
        transform = { it.comment }
    )

    override fun replyComment(threadId: String, commentId: Long, content: String): Flow<Result<CommentItem?>> = apiFlow(
        request = {
            apiService.replyComment(
                ReplyCommentRequest(threadId = threadId, commentId = commentId, content = content)
            )
        },
        isSuccess = { it.isSuccess },
        code = { it.code },
        msg = { it.message },
        transform = { it.comment }
    )

    override fun deleteComment(threadId: String, commentId: Long): Flow<Result<Unit>> = apiFlow(
        request = { apiService.deleteComment(DeleteCommentRequest(threadId = threadId, commentId = commentId)) },
        isSuccess = { it.isSuccess },
        code = { it.code },
        msg = { it.message },
        transform = { Unit }
    )
}

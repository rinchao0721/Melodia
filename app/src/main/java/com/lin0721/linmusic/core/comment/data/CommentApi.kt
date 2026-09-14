package com.lin0721.linmusic.core.comment.data

import com.lin0721.linmusic.core.model.CommentItem
import kotlinx.serialization.Serializable
import retrofit2.http.Body
import retrofit2.http.POST
import retrofit2.http.Path

// 评论相关的网易云 Retrofit 接口定义。
interface CommentApi {

    @POST("/eapi/v1/resource/comments/{threadId}")
    suspend fun getComments(
        @Path("threadId") threadId: String,
        @Body body: CommentsRequest
    ): CommentsResponse

    // 评论点赞与取消点赞
    @POST("/weapi/v1/comment/{op}")
    suspend fun likeComment(
        @Path("op") op: String, // "like" 或 "unlike"
        @Body body: LikeCommentRequest
    ): LikeCommentResponse

    // 分页 + 排序切换的评论列表（推荐/最热/最新）
    @POST("/weapi/v2/resource/comments")
    suspend fun getCommentsV2(@Body body: CommentsV2Request): CommentsV2Response

    // 某条评论下的楼层子回复列表
    @POST("/weapi/resource/comment/floor/get")
    suspend fun getFloorComments(@Body body: FloorCommentsRequest): FloorCommentsResponse

    // 发表评论（需要登录，走 xeapi 加密）
    @POST("/xeapi/resource/comments/add")
    suspend fun addComment(@Body body: AddCommentRequest): CommentActionResponse

    // 回复评论（需要登录，走 xeapi 加密）
    @POST("/xeapi/v1/resource/comments/reply")
    suspend fun replyComment(@Body body: ReplyCommentRequest): CommentActionResponse

    // 删除自己发表的评论（需要登录，走 xeapi 加密）
    @POST("/xeapi/resource/comments/delete")
    suspend fun deleteComment(@Body body: DeleteCommentRequest): CommentActionResponse
}

// ======================= 评论 DTO =======================

// 排序方式：wireValue 推荐=99，最热=2，最新=3）
enum class CommentSortType(val wireValue: Int) {
    RECOMMEND(99), HOT(2), LATEST(3);

    // 按排序方式计算下一页请求要带的 cursor；推荐/最热用数字页码换算，最新沿用上一页返回的 cursor
    fun cursorFor(pageNo: Int, pageSize: Int, previousCursor: String?): String = when (this) {
        RECOMMEND -> ((pageNo - 1) * pageSize).toString()
        HOT -> "normalHot#${(pageNo - 1) * pageSize}"
        LATEST -> if (pageNo <= 1) "0" else (previousCursor ?: "0")
    }
}

@Serializable
data class CommentsV2Request(
    val threadId: String,
    val pageNo: Int = 1,
    val pageSize: Int = 20,
    val cursor: String = "0",
    val sortType: Int = CommentSortType.LATEST.wireValue,
    val showInner: Boolean = true
)

@Serializable
data class CommentsV2Response(
    val code: Int = 0,
    val data: CommentsV2Data? = null
) {
    val isSuccess: Boolean get() = code == 200
}

@Serializable
data class CommentsV2Data(
    val comments: List<CommentItem> = emptyList(),
    val totalCount: Int = 0,
    val hasMore: Boolean = false,
    val cursor: String = "0",
    val sortType: Int = CommentSortType.LATEST.wireValue
)

@Serializable
data class CommentsRequest(
    val threadId: String,
    val rid: String,
    val limit: Int = 20,
    val offset: Int = 0,
    val beforeTime: Long = 0
)

@Serializable
data class CommentsResponse(
    val code: Int = 0,
    val total: Int = 0,
    val more: Boolean = false,
    val comments: List<CommentItem> = emptyList(),
    val hotComments: List<CommentItem> = emptyList()
) {
    val isSuccess: Boolean get() = code == 200
}

@Serializable
data class LikeCommentRequest(
    val threadId: String,
    val commentId: Long
)

@Serializable
data class LikeCommentResponse(
    val code: Int = 0,
    val message: String? = null
) {
    val isSuccess: Boolean get() = code == 200
}

@Serializable
data class FloorCommentsRequest(
    val parentCommentId: Long,
    val threadId: String,
    val time: Long = -1,
    val limit: Int = 20
)

@Serializable
data class FloorCommentsResponse(
    val code: Int = 0,
    val data: FloorCommentsData? = null
) {
    val isSuccess: Boolean get() = code == 200
}

@Serializable
data class FloorCommentsData(
    val comments: List<CommentItem> = emptyList(),
    val hasMore: Boolean = false,
    val totalCount: Int = 0
)

@Serializable
data class AddCommentRequest(
    val threadId: String,
    val content: String,
    val resourceType: String = "0",
    val expressionPicId: String = "-1",
    val bubbleId: String = "-1"
)

@Serializable
data class ReplyCommentRequest(
    val threadId: String,
    val commentId: Long,
    val content: String,
    val resourceType: String = "0"
)

@Serializable
data class DeleteCommentRequest(
    val threadId: String,
    val commentId: Long
)

// comment 字段名未经真机验证，可空；拿不到就走"发表成功但刷新列表才能看到"的兜底分支
@Serializable
data class CommentActionResponse(
    val code: Int = 0,
    val comment: CommentItem? = null,
    val message: String? = null
) {
    val isSuccess: Boolean get() = code == 200
}




package com.lin0721.linmusic.feature.message.data

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import retrofit2.http.Body
import retrofit2.http.POST
import retrofit2.http.Path

// 消息相关的网易云 Retrofit 接口定义，均需要登录。
interface MessageApi {

    // 回复我的评论
    @POST("/weapi/v1/user/comments/{uid}")
    suspend fun getReceivedComments(
        @Path("uid") uid: Long,
        @Body body: MessageCommentsRequest
    ): MessageCommentsResponse

    // @我
    @POST("/weapi/forwards/get")
    suspend fun getForwards(
        @Body body: MessageForwardsRequest
    ): MessageForwardsResponse

    // 通知
    @POST("/weapi/msg/notices")
    suspend fun getNotices(
        @Body body: MessageNoticesRequest
    ): MessageNoticesResponse

    // 私信会话列表
    @POST("/eapi/msg/private/users")
    suspend fun getPrivateConversations(
        @Body body: PrivateConversationsRequest
    ): PrivateConversationsResponse

    // 私信聊天历史
    @POST("/eapi/msg/private/history")
    suspend fun getPrivateHistory(
        @Body body: PrivateHistoryRequest
    ): PrivateHistoryResponse

    // 发送私信：必须走 eapi，userIds 需传字符串化数组（如 "[123]"），走 weapi 会报 400
    @POST("/eapi/msg/private/send")
    suspend fun sendPrivateMessage(
        @Body body: PrivateSendRequest
    ): PrivateSendResponse
}

// ======================= 回复我的评论 DTO =======================

// beforeTime 首页传 "-1"，翻页传上一页末条评论的 time
@Serializable
data class MessageCommentsRequest(
    val uid: Long,
    val beforeTime: String = "-1",
    val limit: Int = 30,
    val total: String = "true"
)

@Serializable
data class MessageCommentsResponse(
    val code: Int = 0,
    val more: Boolean = false,
    val comments: List<MessageCommentDto> = emptyList()
) {
    val isSuccess: Boolean get() = code == 200
}

// resourceJson 是字符串形式的 JSON，含被评论资源的 id/name/creator，由 MessageParsers 二次解析
@Serializable
data class MessageCommentDto(
    val commentId: Long = 0,
    val content: String = "",
    val time: Long = 0,
    val user: MessageUserDto = MessageUserDto(),
    val beRepliedUser: MessageUserDto? = null,
    val beRepliedContent: String? = null,
    val threadId: String = "",
    val resourceType: Int = 0,
    val resourceJson: String? = null
)

@Serializable
data class MessageUserDto(
    val userId: Long = 0,
    val nickname: String = "",
    val avatarUrl: String = ""
)

// ======================= @我 DTO =======================

@Serializable
data class MessageForwardsRequest(
    val offset: Int = 0,
    val limit: Int = 30,
    val total: String = "true"
)

// forwards 元素结构尚无真实样本（样本账号无 @ 数据），以 JsonObject 宽松接收，由 MessageParsers 提取通用字段
@Serializable
data class MessageForwardsResponse(
    val code: Int = 0,
    val more: Boolean = false,
    val forwards: List<JsonObject> = emptyList()
) {
    val isSuccess: Boolean get() = code == 200
}

// ======================= 通知 DTO =======================

// time 首页传 -1，翻页传上一页响应的 lastTime
@Serializable
data class MessageNoticesRequest(
    val time: Long = -1,
    val limit: Int = 30
)

// lastTime 比本页末条 time 更早，翻页必须用它而不是末条的 time
@Serializable
data class MessageNoticesResponse(
    val code: Int = 0,
    val more: Boolean = false,
    val lastTime: Long = 0,
    val notices: List<MessageNoticeDto> = emptyList()
) {
    val isSuccess: Boolean get() = code == 200
}

// notice 是字符串形式的 JSON，由 MessageParsers 按其内部 type 解析
@Serializable
data class MessageNoticeDto(
    val id: Long = 0,
    val time: Long = 0,
    val notice: String? = null
)

// ======================= 私信 DTO =======================

@Serializable
data class PrivateConversationsRequest(
    val offset: Int = 0,
    val limit: Int = 30,
    val total: String = "true"
)

// lastMsg 为双层 JSON 字符串，会话节点结构随版本存在差异，以 JsonObject 宽松接收
@Serializable
data class PrivateConversationsResponse(
    val code: Int = 0,
    val more: Boolean = false,
    val newMsgCount: Int = 0,
    val msgs: List<JsonObject> = emptyList()
) {
    val isSuccess: Boolean get() = code == 200
}

// time 首页传 0，翻页传上一页末条消息的 time
@Serializable
data class PrivateHistoryRequest(
    val userId: Long,
    val limit: Int = 30,
    val time: Long = 0,
    val total: String = "true"
)

@Serializable
data class PrivateHistoryResponse(
    val code: Int = 0,
    val more: Boolean = false,
    val hint: String? = null,
    val isArtist: Boolean = false,
    val isSubed: Boolean = false,
    val msgs: List<JsonObject> = emptyList()
) {
    val isSuccess: Boolean get() = code == 200
}

// userIds 必须是字符串化数组（如 "[123]"）；分享单曲时 type="song" 且 id 必须为数字，传字符串会被服务端存成「请升级 app」
@Serializable
data class PrivateSendRequest(
    val type: String = "text",
    val msg: String,
    val userIds: String,
    val id: Long? = null
)

@Serializable
data class PrivateSendResponse(
    val code: Int = 0,
    val msg: String? = null
) {
    val isSuccess: Boolean get() = code == 200
}

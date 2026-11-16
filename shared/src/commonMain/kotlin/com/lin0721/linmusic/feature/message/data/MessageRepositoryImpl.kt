package com.lin0721.linmusic.feature.message.data

import com.lin0721.linmusic.core.network.apiFlow
import com.lin0721.linmusic.feature.message.domain.CommentMessage
import com.lin0721.linmusic.feature.message.domain.ForwardMessage
import com.lin0721.linmusic.feature.message.domain.MessagePage
import com.lin0721.linmusic.feature.message.domain.NoticeItem
import com.lin0721.linmusic.feature.message.domain.PrivateConversation
import com.lin0721.linmusic.feature.message.domain.PrivateMessage
import com.lin0721.linmusic.feature.message.domain.parseCommentMessage
import com.lin0721.linmusic.feature.message.domain.parseForward
import com.lin0721.linmusic.feature.message.domain.parseNotice
import com.lin0721.linmusic.feature.message.domain.parsePrivateConversation
import com.lin0721.linmusic.feature.message.domain.parsePrivateMessage
import kotlinx.coroutines.flow.Flow

class MessageRepositoryImpl(
    private val messageApi: MessageApi
) : MessageRepository {

    override fun getReceivedComments(
        uid: Long,
        cursor: Long,
        limit: Int
    ): Flow<Result<MessagePage<CommentMessage>>> = apiFlow(
        request = {
            messageApi.getReceivedComments(
                uid,
                MessageCommentsRequest(uid = uid, beforeTime = cursor.toString(), limit = limit)
            )
        },
        isSuccess = { it.isSuccess },
        code = { it.code },
        transform = { response ->
            val items = response.comments.map(::parseCommentMessage)
            MessagePage(
                items = items,
                hasMore = response.more && items.isNotEmpty(),
                nextCursor = items.lastOrNull()?.time ?: cursor
            )
        }
    )

    override fun getForwards(cursor: Long, limit: Int): Flow<Result<MessagePage<ForwardMessage>>> = apiFlow(
        request = {
            messageApi.getForwards(MessageForwardsRequest(offset = cursor.toInt(), limit = limit))
        },
        isSuccess = { it.isSuccess },
        code = { it.code },
        transform = { response ->
            val items = response.forwards.mapIndexed { index, obj -> parseForward(cursor + index, obj) }
            MessagePage(
                items = items,
                hasMore = response.more && items.isNotEmpty(),
                nextCursor = cursor + items.size
            )
        }
    )

    override fun getNotices(cursor: Long, limit: Int): Flow<Result<MessagePage<NoticeItem>>> = apiFlow(
        request = { messageApi.getNotices(MessageNoticesRequest(time = cursor, limit = limit)) },
        isSuccess = { it.isSuccess },
        code = { it.code },
        transform = { response ->
            val items = response.notices.map(::parseNotice)
            MessagePage(
                items = items,
                hasMore = response.more && items.isNotEmpty() && response.lastTime > 0,
                nextCursor = response.lastTime
            )
        }
    )

    override fun getPrivateConversations(
        selfUid: Long,
        cursor: Long,
        limit: Int
    ): Flow<Result<MessagePage<PrivateConversation>>> = apiFlow(
        request = {
            messageApi.getPrivateConversations(
                PrivateConversationsRequest(offset = cursor.toInt(), limit = limit)
            )
        },
        isSuccess = { it.isSuccess },
        code = { it.code },
        transform = { response ->
            val items = response.msgs.mapNotNull { parsePrivateConversation(it, selfUid) }
            MessagePage(
                items = items,
                hasMore = response.more && items.isNotEmpty(),
                nextCursor = cursor + items.size,
                newMsgCount = response.newMsgCount.coerceAtLeast(0)
            )
        }
    )

    override fun getPrivateHistory(
        userId: Long,
        cursor: Long,
        limit: Int
    ): Flow<Result<MessagePage<PrivateMessage>>> = apiFlow(
        request = {
            messageApi.getPrivateHistory(
                PrivateHistoryRequest(userId = userId, time = cursor, limit = limit)
            )
        },
        isSuccess = { it.isSuccess },
        code = { it.code },
        transform = { response ->
            val items = response.msgs.mapNotNull(::parsePrivateMessage)
            MessagePage(
                items = items,
                hasMore = response.more && items.isNotEmpty(),
                nextCursor = items.mapNotNull { it.time.takeIf { t -> t > 0L } }.minOrNull() ?: cursor
            )
        }
    )

    override fun sendPrivateText(userId: Long, text: String): Flow<Result<Unit>> = sendPrivate(
        PrivateSendRequest(type = "text", msg = text, userIds = "[$userId]")
    )

    override fun sendPrivateSong(userId: Long, songId: Long, text: String): Flow<Result<Unit>> = sendPrivate(
        PrivateSendRequest(type = "song", msg = text, userIds = "[$userId]", id = songId)
    )

    private fun sendPrivate(request: PrivateSendRequest): Flow<Result<Unit>> = apiFlow(
        request = { messageApi.sendPrivateMessage(request) },
        isSuccess = { it.isSuccess },
        code = { it.code },
        msg = { it.msg },
        transform = { }
    )
}

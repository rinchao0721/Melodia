package com.lin0721.linmusic.feature.message.data

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MessageRepositoryImplTest {

    private class FakeMessageApi : MessageApi {
        var commentsResponse = MessageCommentsResponse(code = 200)
        var forwardsResponse = MessageForwardsResponse(code = 200)
        var noticesResponse = MessageNoticesResponse(code = 200)
        var privateConversationsResponse = PrivateConversationsResponse(code = 200)
        var privateHistoryResponse = PrivateHistoryResponse(code = 200)
        var privateSendResponse = PrivateSendResponse(code = 200)

        var lastCommentsUid = 0L
        var lastCommentsRequest: MessageCommentsRequest? = null
        var lastForwardsRequest: MessageForwardsRequest? = null
        var lastNoticesRequest: MessageNoticesRequest? = null

        override suspend fun getReceivedComments(uid: Long, body: MessageCommentsRequest): MessageCommentsResponse {
            lastCommentsUid = uid
            lastCommentsRequest = body
            return commentsResponse
        }

        override suspend fun getForwards(body: MessageForwardsRequest): MessageForwardsResponse {
            lastForwardsRequest = body
            return forwardsResponse
        }

        override suspend fun getNotices(body: MessageNoticesRequest): MessageNoticesResponse {
            lastNoticesRequest = body
            return noticesResponse
        }

        override suspend fun getPrivateConversations(body: PrivateConversationsRequest): PrivateConversationsResponse =
            privateConversationsResponse

        override suspend fun getPrivateHistory(body: PrivateHistoryRequest): PrivateHistoryResponse =
            privateHistoryResponse

        override suspend fun sendPrivateMessage(body: PrivateSendRequest): PrivateSendResponse =
            privateSendResponse
    }

    private val api = FakeMessageApi()
    private val repository = MessageRepositoryImpl(api)

    @Test
    fun `评论翻页游标为末条time，请求带uid与beforeTime`() = runTest {
        api.commentsResponse = MessageCommentsResponse(
            code = 200,
            more = true,
            comments = listOf(MessageCommentDto(commentId = 1, time = 900), MessageCommentDto(commentId = 2, time = 700))
        )

        val page = repository.getReceivedComments(uid = 42, cursor = -1, limit = 30).first().getOrThrow()

        assertEquals(42L, api.lastCommentsUid)
        assertEquals("-1", api.lastCommentsRequest?.beforeTime)
        assertEquals(42L, api.lastCommentsRequest?.uid)
        assertEquals(700L, page.nextCursor)
        assertTrue(page.hasMore)
    }

    @Test
    fun `评论页为空时即使服务端称有更多也视为结束，游标保持不变`() = runTest {
        api.commentsResponse = MessageCommentsResponse(code = 200, more = true, comments = emptyList())

        val page = repository.getReceivedComments(uid = 1, cursor = 123, limit = 30).first().getOrThrow()

        assertFalse(page.hasMore)
        assertEquals(123L, page.nextCursor)
    }

    @Test
    fun `通知翻页游标取响应的lastTime而不是末条time`() = runTest {
        api.noticesResponse = MessageNoticesResponse(
            code = 200,
            more = true,
            lastTime = 500,
            notices = listOf(
                MessageNoticeDto(id = 1, time = 900, notice = """{"type":99,"user":{"userId":1}}"""),
                MessageNoticeDto(id = 2, time = 800, notice = null)
            )
        )

        val page = repository.getNotices(cursor = -1, limit = 30).first().getOrThrow()

        assertEquals(-1L, api.lastNoticesRequest?.time)
        assertEquals(500L, page.nextCursor)
        assertTrue(page.hasMore)
        assertEquals(2, page.items.size)
    }

    @Test
    fun `通知lastTime为0时不再继续翻页`() = runTest {
        api.noticesResponse = MessageNoticesResponse(
            code = 200,
            more = true,
            lastTime = 0,
            notices = listOf(MessageNoticeDto(id = 1, time = 900))
        )

        val page = repository.getNotices(cursor = -1, limit = 30).first().getOrThrow()

        assertFalse(page.hasMore)
    }

    @Test
    fun `at我以偏移量翻页，条目key由偏移量派生`() = runTest {
        val element = Json.parseToJsonElement("""{"eventTime":10}""") as JsonObject
        api.forwardsResponse = MessageForwardsResponse(code = 200, more = true, forwards = listOf(element, element))

        val page = repository.getForwards(cursor = 60, limit = 30).first().getOrThrow()

        assertEquals(60, api.lastForwardsRequest?.offset)
        assertEquals(listOf(60L, 61L), page.items.map { it.key })
        assertEquals(62L, page.nextCursor)
        assertTrue(page.hasMore)
    }

    @Test
    fun `业务码非200时返回失败`() = runTest {
        api.noticesResponse = MessageNoticesResponse(code = 500)

        val result = repository.getNotices(cursor = -1, limit = 30).first()

        assertTrue(result.isFailure)
    }
}

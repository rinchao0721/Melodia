package com.lin0721.linmusic.feature.message.data

import com.lin0721.linmusic.core.network.AppError
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.longOrNull
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PrivateMessageRepositoryTest {

    private class FakePrivateMessageApi : MessageApi {
        var conversationsResponse = PrivateConversationsResponse(code = 200)
        var historyResponse = PrivateHistoryResponse(code = 200)
        var sendResponse = PrivateSendResponse(code = 200)

        var lastConversationsRequest: PrivateConversationsRequest? = null
        var lastHistoryRequest: PrivateHistoryRequest? = null
        val sentRequests = mutableListOf<PrivateSendRequest>()

        override suspend fun getReceivedComments(uid: Long, body: MessageCommentsRequest) =
            MessageCommentsResponse(code = 200)

        override suspend fun getForwards(body: MessageForwardsRequest) =
            MessageForwardsResponse(code = 200)

        override suspend fun getNotices(body: MessageNoticesRequest) =
            MessageNoticesResponse(code = 200)

        override suspend fun getPrivateConversations(body: PrivateConversationsRequest): PrivateConversationsResponse {
            lastConversationsRequest = body
            return conversationsResponse
        }

        override suspend fun getPrivateHistory(body: PrivateHistoryRequest): PrivateHistoryResponse {
            lastHistoryRequest = body
            return historyResponse
        }

        override suspend fun sendPrivateMessage(body: PrivateSendRequest): PrivateSendResponse {
            sentRequests += body
            return sendResponse
        }
    }

    private fun jsonObj(raw: String): JsonObject = Json.parseToJsonElement(raw) as JsonObject

    private val api = FakePrivateMessageApi()
    private val repository = MessageRepositoryImpl(api)

    @Test
    fun `会话列表按offset翻页并保留顶层newMsgCount，跳过损坏项`() = runTest {
        val valid = jsonObj(
            """
            {
              "fromUser": {"userId": 200, "nickname": "好友", "avatarUrl": ""},
              "toUser": {"userId": 100, "nickname": "我", "avatarUrl": ""},
              "user": {"fromUserId": 200, "toUserId": 100},
              "lastMsgTime": 900,
              "newMsgCount": 1,
              "lastMsg": "{\"msg\":\"在吗\",\"type\":6}"
            }
            """.trimIndent()
        )
        val broken = jsonObj("""{"fromUser":{"userId":1}}""")
        api.conversationsResponse = PrivateConversationsResponse(
            code = 200,
            more = true,
            newMsgCount = 4,
            msgs = listOf(valid, broken)
        )

        val page = repository.getPrivateConversations(selfUid = 100L, cursor = 30L, limit = 20).first().getOrThrow()

        assertEquals(30, api.lastConversationsRequest?.offset)
        assertEquals(20, api.lastConversationsRequest?.limit)
        assertEquals(1, page.items.size)
        assertEquals(31L, page.nextCursor)
        assertEquals(4, page.newMsgCount)
        assertTrue(page.hasMore)
    }

    @Test
    fun `聊天历史按最小正时间戳计算nextCursor，空结果结束翻页`() = runTest {
        val msg1 = jsonObj("""{"id":11,"time":500,"msg":"{\"type\":6,\"msg\":\"较新\"}"}""")
        val msg2 = jsonObj("""{"id":10,"time":200,"msg":"{\"type\":6,\"msg\":\"较早\"}"}""")
        api.historyResponse = PrivateHistoryResponse(
            code = 200,
            more = true,
            msgs = listOf(msg1, msg2)
        )

        val page = repository.getPrivateHistory(userId = 200L, cursor = 0L, limit = 30).first().getOrThrow()

        assertEquals(200L, api.lastHistoryRequest?.userId)
        assertEquals(0L, api.lastHistoryRequest?.time)
        assertEquals(200L, page.nextCursor)
        assertTrue(page.hasMore)

        api.historyResponse = PrivateHistoryResponse(code = 200, more = true, msgs = emptyList())
        val emptyPage = repository.getPrivateHistory(userId = 200L, cursor = 200L, limit = 30).first().getOrThrow()
        assertFalse(emptyPage.hasMore)
        assertEquals(200L, emptyPage.nextCursor)
    }

    @Test
    fun `发送文本与歌曲时userIds为字符串化数组，歌曲id序列化为数字Long`() = runTest {
        repository.sendPrivateText(userId = 123L, text = "你好").first().getOrThrow()
        repository.sendPrivateSong(userId = 123L, songId = 456789L, text = "分享单曲：晴天").first().getOrThrow()

        assertEquals(2, api.sentRequests.size)
        val textReq = api.sentRequests[0]
        assertEquals("text", textReq.type)
        assertEquals("[123]", textReq.userIds)
        assertNull(textReq.id)

        val songReq = api.sentRequests[1]
        assertEquals("song", songReq.type)
        assertEquals("[123]", songReq.userIds)
        assertEquals(456789L, songReq.id)

        val encodedSong = Json.parseToJsonElement(Json.encodeToString(songReq)) as JsonObject
        val idPrimitive = encodedSong["id"] as JsonPrimitive
        assertFalse(idPrimitive.isString)
        assertEquals(456789L, idPrimitive.longOrNull)
        val userIdsPrimitive = encodedSong["userIds"] as JsonPrimitive
        assertTrue(userIdsPrimitive.isString)
        assertEquals("[123]", userIdsPrimitive.content)
    }

    @Test
    fun `发送失败返回业务错误码`() = runTest {
        api.sendResponse = PrivateSendResponse(code = 400, msg = "参数错误")

        val result = repository.sendPrivateText(123L, "hi").first()

        assertTrue(result.isFailure)
        val error = result.exceptionOrNull() as AppError.BizError
        assertEquals(400, error.code)
        assertEquals("参数错误", error.rawMsg)
    }
}

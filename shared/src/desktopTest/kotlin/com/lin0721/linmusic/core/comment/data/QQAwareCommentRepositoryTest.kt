package com.lin0721.linmusic.core.comment.data

import com.lin0721.linmusic.core.model.CommentItem
import com.lin0721.linmusic.core.source.QQCommentApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneId

class QQAwareCommentRepositoryTest {

    private class FakeNetease : CommentRepository {
        var v2Calls = 0
        override fun getComments(songId: Long, limit: Int, offset: Int): Flow<Result<CommentsResponse>> = flowOf(Result.success(CommentsResponse()))
        override fun getComments(threadId: String, limit: Int, offset: Int): Flow<Result<CommentsResponse>> = flowOf(Result.success(CommentsResponse()))
        override fun likeComment(threadId: String, commentId: Long, like: Boolean): Flow<Result<Unit>> = flowOf(Result.success(Unit))
        override fun getCommentsV2(threadId: String, pageNo: Int, pageSize: Int, cursor: String, sortType: CommentSortType): Flow<Result<CommentsV2Data>> {
            v2Calls++
            return flowOf(Result.success(CommentsV2Data(totalCount = 7)))
        }
        override fun getFloorComments(threadId: String, parentCommentId: Long, time: Long, limit: Int): Flow<Result<FloorCommentsData>> = flowOf(Result.success(FloorCommentsData()))
        override fun addComment(threadId: String, content: String): Flow<Result<CommentItem?>> = flowOf(Result.success(null))
        override fun replyComment(threadId: String, commentId: Long, content: String): Flow<Result<CommentItem?>> = flowOf(Result.success(null))
        override fun deleteComment(threadId: String, commentId: Long): Flow<Result<Unit>> = flowOf(Result.success(Unit))
    }

    private val body = """
        {"code":0,
         "comment":{"commenttotal":45,"commentlist":[
           {"commentid":"c1","rootcommentid":"c1","nick":"甲","avatarurl":"http://a","rootcommentcontent":"好听[em]e400822[/em]","praisenum":3,"time":1700000000}
         ]},
         "hot_comment":{"commenttotal":2,"commentlist":[
           {"commentid":"h1","rootcommentid":"h1","nick":"乙","rootcommentcontent":"热评","praisenum":99,"time":1600000000},
           {"commentid":"h2","rootcommentid":"h2","nick":"丙","rootcommentcontent":"热评二","praisenum":9,"time":1600000001}
         ]}}
    """.trimIndent()

    @Test
    fun latestCommentsArePagedAndEmojiCodesStripped() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setBody(body))
            val repo = QQAwareCommentRepository(FakeNetease(), QQCommentApi(server.url("/c").toString()), ZoneId.of("Asia/Shanghai"))

            val data = repo.getCommentsV2(qqSongThreadId(97773), 1, 20, "0", CommentSortType.LATEST).first().getOrThrow()

            assertEquals(1, data.comments.size)
            assertEquals("好听", data.comments[0].content)
            assertEquals(1_700_000_000_000L, data.comments[0].time)
            assertEquals(45, data.totalCount)
            assertTrue(data.hasMore)
            val url = server.takeRequest().requestUrl!!
            assertEquals("97773", url.queryParameter("topid"))
            assertEquals("0", url.queryParameter("pagenum"))
            assertEquals("utf-8", url.queryParameter("outCharset"))
        }
    }

    @Test
    fun hotSortReturnsHotListWithoutPaging() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setBody(body))
            val repo = QQAwareCommentRepository(FakeNetease(), QQCommentApi(server.url("/c").toString()))

            val data = repo.getCommentsV2(qqSongThreadId(1), 1, 20, "0", CommentSortType.HOT).first().getOrThrow()

            assertEquals(listOf("热评", "热评二"), data.comments.map { it.content })
            assertFalse(data.hasMore)
        }
    }

    @Test
    fun unmatchedSongAndWriteOperationsFailWithoutNetwork() = runBlocking {
        MockWebServer().use { server ->
            val netease = FakeNetease()
            val repo = QQAwareCommentRepository(netease, QQCommentApi(server.url("/c").toString()))

            val unmatched = repo.getCommentsV2(qqSongThreadId(0), 1, 20, "0", CommentSortType.LATEST).first()
            assertEquals("未在 QQ 音乐找到这首歌的评论", unmatched.exceptionOrNull()?.message)
            assertTrue(repo.addComment(qqSongThreadId(1), "hi").first().isFailure)
            assertTrue(repo.likeComment(qqSongThreadId(1), 1, true).first().isFailure)
            assertEquals(0, server.requestCount)
            assertEquals(0, netease.v2Calls)
        }
    }

    @Test
    fun neteaseThreadsAreDelegated() = runBlocking {
        val netease = FakeNetease()
        val repo = QQAwareCommentRepository(netease, QQCommentApi("http://127.0.0.1:1/unused"))

        val data = repo.getCommentsV2("R_SO_4_1", 1, 20, "0", CommentSortType.RECOMMEND).first().getOrThrow()

        assertEquals(7, data.totalCount)
        assertEquals(1, netease.v2Calls)
    }

    @Test
    fun nonZeroCodeIsFailure() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setBody("""{"code":-1}"""))
            val repo = QQAwareCommentRepository(FakeNetease(), QQCommentApi(server.url("/c").toString()))

            assertTrue(repo.getCommentsV2(qqSongThreadId(1), 1, 20, "0", CommentSortType.LATEST).first().isFailure)
        }
    }
}

package com.lin0721.linmusic.core.comment.data

import com.lin0721.linmusic.core.model.CommentItem
import com.lin0721.linmusic.core.model.CommentUser
import com.lin0721.linmusic.core.source.QQComment
import com.lin0721.linmusic.core.source.QQCommentApi
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

private const val QQ_THREAD_PREFIX = "QQ_SO_"
private val QQ_EMOJI_CODE = Regex("""\[em]e\d+\[/em]""")

// 外部平台歌曲的评论线程，topId 为 0 表示没在 QQ 音乐匹配到
fun qqSongThreadId(qqSongId: Long): String = "$QQ_THREAD_PREFIX$qqSongId"

fun isQQThread(threadId: String): Boolean = threadId.startsWith(QQ_THREAD_PREFIX)

// 外部平台歌曲的评论取自 QQ 音乐，只读；其余线程照常交给网易云
class QQAwareCommentRepository(
    private val netease: CommentRepository,
    private val qqApi: QQCommentApi,
    private val zone: ZoneId = ZoneId.systemDefault()
) : CommentRepository {

    override fun getComments(songId: Long, limit: Int, offset: Int): Flow<Result<CommentsResponse>> =
        netease.getComments(songId, limit, offset)

    override fun getComments(threadId: String, limit: Int, offset: Int): Flow<Result<CommentsResponse>> =
        if (isQQThread(threadId)) unsupported() else netease.getComments(threadId, limit, offset)

    override fun likeComment(threadId: String, commentId: Long, like: Boolean): Flow<Result<Unit>> =
        if (isQQThread(threadId)) unsupported() else netease.likeComment(threadId, commentId, like)

    override fun getCommentsV2(
        threadId: String,
        pageNo: Int,
        pageSize: Int,
        cursor: String,
        sortType: CommentSortType
    ): Flow<Result<CommentsV2Data>> {
        if (!isQQThread(threadId)) return netease.getCommentsV2(threadId, pageNo, pageSize, cursor, sortType)
        val topId = threadId.removePrefix(QQ_THREAD_PREFIX).toLongOrNull() ?: 0L
        if (topId <= 0L) return flowOf(Result.failure(IllegalStateException("未在 QQ 音乐找到这首歌的评论")))
        return flow {
            val result = try {
                Result.success(fetchQQ(topId, pageNo, pageSize, sortType))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Result.failure(IllegalStateException("QQ 音乐评论加载失败", e))
            }
            emit(result)
        }
    }

    // 热评只在首页下发且不分页；推荐与最新都按最新评论分页
    private suspend fun fetchQQ(topId: Long, pageNo: Int, pageSize: Int, sortType: CommentSortType): CommentsV2Data {
        val page = (pageNo - 1).coerceAtLeast(0)
        val response = qqApi.fetch(topId, page, pageSize, lastCommentId = "")
        val today = LocalDate.now(zone)
        if (sortType == CommentSortType.HOT) {
            val hot = response.hotComment
            return CommentsV2Data(
                comments = hot?.commentlist.orEmpty().map { it.toCommentItem(today) },
                totalCount = hot?.commenttotal ?: 0,
                hasMore = false,
                sortType = sortType.wireValue
            )
        }
        val latest = response.comment
        val comments = latest?.commentlist.orEmpty()
        val total = latest?.commenttotal ?: 0
        return CommentsV2Data(
            comments = comments.map { it.toCommentItem(today) },
            totalCount = total,
            hasMore = comments.isNotEmpty() && pageNo * pageSize < total,
            cursor = pageNo.toString(),
            sortType = sortType.wireValue
        )
    }

    private fun QQComment.toCommentItem(today: LocalDate): CommentItem = CommentItem(
        // QQ 评论 id 是字符串，界面只用它做列表 key
        commentId = commentid.hashCode().toLong() and 0xFFFFFFFFL,
        user = CommentUser(nickname = nick, avatarUrl = avatarurl),
        content = rootcommentcontent.replace(QQ_EMOJI_CODE, "").trim(),
        time = time * 1000,
        timeStr = formatDate(time, today),
        likedCount = praisenum
    )

    private fun formatDate(epochSeconds: Long, today: LocalDate): String {
        if (epochSeconds <= 0L) return ""
        val date = Instant.ofEpochSecond(epochSeconds).atZone(zone).toLocalDate()
        return when {
            date == today -> "今天"
            date.year == today.year -> "${date.monthValue}月${date.dayOfMonth}日"
            else -> "${date.year}年${date.monthValue}月${date.dayOfMonth}日"
        }
    }

    override fun getFloorComments(
        threadId: String,
        parentCommentId: Long,
        time: Long,
        limit: Int
    ): Flow<Result<FloorCommentsData>> =
        if (isQQThread(threadId)) unsupported() else netease.getFloorComments(threadId, parentCommentId, time, limit)

    override fun addComment(threadId: String, content: String): Flow<Result<CommentItem?>> =
        if (isQQThread(threadId)) unsupported() else netease.addComment(threadId, content)

    override fun replyComment(threadId: String, commentId: Long, content: String): Flow<Result<CommentItem?>> =
        if (isQQThread(threadId)) unsupported() else netease.replyComment(threadId, commentId, content)

    override fun deleteComment(threadId: String, commentId: Long): Flow<Result<Unit>> =
        if (isQQThread(threadId)) unsupported() else netease.deleteComment(threadId, commentId)

    private fun <T> unsupported(): Flow<Result<T>> =
        flowOf(Result.failure(IllegalStateException("QQ 音乐评论只支持浏览")))
}

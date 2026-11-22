package com.lin0721.linmusic.core.platform.repository

import com.lin0721.linmusic.core.comment.data.CommentRepository
import com.lin0721.linmusic.core.comment.data.CommentSortType
import com.lin0721.linmusic.core.kugou.KugouCommentResourceType
import com.lin0721.linmusic.core.kugou.KugouCommentService
import com.lin0721.linmusic.core.kugou.KugouPlaybackService
import com.lin0721.linmusic.core.platform.AlbumId
import com.lin0721.linmusic.core.platform.PlaylistId
import com.lin0721.linmusic.core.platform.TrackId
import com.lin0721.linmusic.core.platform.UnifiedComment
import com.lin0721.linmusic.core.platform.UnifiedCreator
import com.lin0721.linmusic.core.platform.toUnified
import com.lin0721.linmusic.core.source.MusicPlatform
import com.lin0721.linmusic.core.source.QQComment
import com.lin0721.linmusic.core.source.QQCommentApi
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

sealed interface CommentTarget {
    val platform: MusicPlatform

    data class TrackTarget(
        val trackId: TrackId,
        val kugouMixSongId: Long? = null,
        val qqSongId: Long? = null,
        val title: String = "",
        val artists: String = "",
        val specialId: String? = null
    ) : CommentTarget {
        override val platform: MusicPlatform get() = trackId.platform
    }

    data class PlaylistTarget(
        val playlistId: PlaylistId,
        val name: String = ""
    ) : CommentTarget {
        override val platform: MusicPlatform get() = playlistId.platform
    }

    data class AlbumTarget(
        val albumId: AlbumId,
        val name: String = ""
    ) : CommentTarget {
        override val platform: MusicPlatform get() = albumId.platform
    }

    data class RawNeteaseThread(val threadId: String) : CommentTarget {
        override val platform: MusicPlatform get() = MusicPlatform.NETEASE
    }
}

data class UnifiedCommentsPage(
    val comments: List<UnifiedComment>,
    val hotComments: List<UnifiedComment> = emptyList(),
    val totalCount: Int = comments.size,
    val hasMore: Boolean = false,
    val cursor: String = "0",
    val kugouSpecialId: String? = null,
    val kugouSpecialName: String? = null
)

data class UnifiedFloorCommentsPage(
    val comments: List<UnifiedComment>,
    val totalCount: Int = comments.size,
    val hasMore: Boolean = false
)

interface PlatformCommentRepository {
    fun getComments(
        target: CommentTarget,
        pageNo: Int = 1,
        pageSize: Int = 30,
        cursor: String = "0",
        sortType: CommentSortType = CommentSortType.LATEST
    ): Flow<Result<UnifiedCommentsPage>>

    fun getFloorComments(
        target: CommentTarget,
        parentCommentId: String,
        cursorOrTime: Long = -1L,
        pageNo: Int = 1,
        limit: Int = 20,
        kugouSpecialId: String? = null
    ): Flow<Result<UnifiedFloorCommentsPage>>

    fun addComment(target: CommentTarget, content: String): Flow<Result<UnifiedComment?>>

    fun replyComment(
        target: CommentTarget,
        commentId: String,
        content: String,
        pid: String = "0",
        replyUserName: String? = null,
        replyContent: String? = null,
        kugouSpecialId: String? = null
    ): Flow<Result<UnifiedComment?>>

    fun deleteComment(
        target: CommentTarget,
        commentId: String,
        tid: String? = null,
        kugouSpecialId: String? = null
    ): Flow<Result<Unit>>

    fun likeComment(
        target: CommentTarget,
        commentId: String,
        like: Boolean
    ): Flow<Result<Unit>>
}

class NeteasePlatformCommentRepository(
    private val commentRepository: CommentRepository
) : PlatformCommentRepository {

    override fun getComments(
        target: CommentTarget,
        pageNo: Int,
        pageSize: Int,
        cursor: String,
        sortType: CommentSortType
    ): Flow<Result<UnifiedCommentsPage>> {
        val threadId = resolveNeteaseThreadId(target)
            ?: return flowOf(Result.failure(IllegalArgumentException("无法解析网易云评论线程 ID")))
        return commentRepository.getCommentsV2(threadId, pageNo, pageSize, cursor, sortType).map { result ->
            result.map { data ->
                UnifiedCommentsPage(
                    comments = data.comments.map { it.toUnified(MusicPlatform.NETEASE) },
                    totalCount = data.totalCount,
                    hasMore = data.hasMore,
                    cursor = data.cursor
                )
            }
        }
    }

    override fun getFloorComments(
        target: CommentTarget,
        parentCommentId: String,
        cursorOrTime: Long,
        pageNo: Int,
        limit: Int,
        kugouSpecialId: String?
    ): Flow<Result<UnifiedFloorCommentsPage>> {
        val threadId = resolveNeteaseThreadId(target)
            ?: return flowOf(Result.failure(IllegalArgumentException("无法解析网易云评论线程 ID")))
        val numericParent = parentCommentId.toLongOrNull()
            ?: return flowOf(Result.failure(IllegalArgumentException("无效网易云父评论 ID: $parentCommentId")))
        return commentRepository.getFloorComments(threadId, numericParent, cursorOrTime, limit).map { result ->
            result.map { data ->
                UnifiedFloorCommentsPage(
                    comments = data.comments.map { it.toUnified(MusicPlatform.NETEASE) },
                    totalCount = data.totalCount,
                    hasMore = data.hasMore
                )
            }
        }
    }

    override fun addComment(target: CommentTarget, content: String): Flow<Result<UnifiedComment?>> {
        val threadId = resolveNeteaseThreadId(target)
            ?: return flowOf(Result.failure(IllegalArgumentException("无法解析网易云评论线程 ID")))
        return commentRepository.addComment(threadId, content).map { result ->
            result.map { it?.toUnified(MusicPlatform.NETEASE) }
        }
    }

    override fun replyComment(
        target: CommentTarget,
        commentId: String,
        content: String,
        pid: String,
        replyUserName: String?,
        replyContent: String?,
        kugouSpecialId: String?
    ): Flow<Result<UnifiedComment?>> {
        val threadId = resolveNeteaseThreadId(target)
            ?: return flowOf(Result.failure(IllegalArgumentException("无法解析网易云评论线程 ID")))
        val numericId = commentId.toLongOrNull()
            ?: return flowOf(Result.failure(IllegalArgumentException("无效网易云评论 ID: $commentId")))
        return commentRepository.replyComment(threadId, numericId, content).map { result ->
            result.map { it?.toUnified(MusicPlatform.NETEASE) }
        }
    }

    override fun deleteComment(
        target: CommentTarget,
        commentId: String,
        tid: String?,
        kugouSpecialId: String?
    ): Flow<Result<Unit>> {
        val threadId = resolveNeteaseThreadId(target)
            ?: return flowOf(Result.failure(IllegalArgumentException("无法解析网易云评论线程 ID")))
        val numericId = commentId.toLongOrNull()
            ?: return flowOf(Result.failure(IllegalArgumentException("无效网易云评论 ID: $commentId")))
        return commentRepository.deleteComment(threadId, numericId)
    }

    override fun likeComment(
        target: CommentTarget,
        commentId: String,
        like: Boolean
    ): Flow<Result<Unit>> {
        val threadId = resolveNeteaseThreadId(target)
            ?: return flowOf(Result.failure(IllegalArgumentException("无法解析网易云评论线程 ID")))
        val numericId = commentId.toLongOrNull()
            ?: return flowOf(Result.failure(IllegalArgumentException("无效网易云评论 ID: $commentId")))
        return commentRepository.likeComment(threadId, numericId, like)
    }

    private fun resolveNeteaseThreadId(target: CommentTarget): String? = when (target) {
        is CommentTarget.TrackTarget -> target.trackId.neteaseIdOrNull()?.let { "R_SO_4_$it" }
        is CommentTarget.PlaylistTarget -> target.playlistId.neteaseIdOrNull()?.let { "A_PL_0_$it" }
        is CommentTarget.AlbumTarget -> target.albumId.neteaseIdOrNull()?.let { "R_AL_3_$it" }
        is CommentTarget.RawNeteaseThread -> target.threadId
    }
}

class KugouPlatformCommentRepository(
    private val commentService: KugouCommentService,
    private val playbackService: KugouPlaybackService? = null,
    private val isEnabled: suspend () -> Boolean = { true }
) : PlatformCommentRepository {

    override fun getComments(
        target: CommentTarget,
        pageNo: Int,
        pageSize: Int,
        cursor: String,
        sortType: CommentSortType
    ): Flow<Result<UnifiedCommentsPage>> = flow {
        if (!isEnabled()) {
            emit(Result.failure(IllegalStateException("酷狗音乐平台已关闭")))
            return@flow
        }
        val res = when (target) {
            is CommentTarget.TrackTarget -> {
                val mixSongId = resolveMixSongId(target)
                if (mixSongId <= 0L) {
                    Result.failure(IllegalArgumentException("缺少酷狗歌曲 mixsongid，无法加载评论"))
                } else {
                    commentService.getMusicComments(mixSongId, pageNo, pageSize)
                }
            }
            is CommentTarget.PlaylistTarget -> commentService.getPlaylistComments(target.playlistId.rawId, pageNo, pageSize)
            is CommentTarget.AlbumTarget -> commentService.getAlbumComments(target.albumId.rawId, pageNo, pageSize)
            is CommentTarget.RawNeteaseThread -> Result.failure(IllegalArgumentException("酷狗不支持网易云线程 ID"))
        }
        emit(
            res.map { page ->
                UnifiedCommentsPage(
                    comments = page.comments.map { it.toUnifiedComment() },
                    hotComments = page.weightComments.map { it.toUnifiedComment() },
                    totalCount = page.totalCount,
                    hasMore = page.hasMore,
                    cursor = pageNo.toString(),
                    kugouSpecialId = page.specialId.takeIf { it.isNotBlank() },
                    kugouSpecialName = page.specialName.takeIf { it.isNotBlank() }
                )
            }
        )
    }

    override fun getFloorComments(
        target: CommentTarget,
        parentCommentId: String,
        cursorOrTime: Long,
        pageNo: Int,
        limit: Int,
        kugouSpecialId: String?
    ): Flow<Result<UnifiedFloorCommentsPage>> = flow {
        if (!isEnabled()) {
            emit(Result.failure(IllegalStateException("酷狗音乐平台已关闭")))
            return@flow
        }
        val (type, specialId, mixSongId) = resolveCommentMeta(target, kugouSpecialId)
        val effectiveSpecialId = specialId.ifBlank {
            if (mixSongId != null && mixSongId > 0L) {
                commentService.getMusicComments(mixSongId, page = 1, pageSize = 1).getOrNull()?.specialId.orEmpty()
            } else {
                ""
            }
        }
        if (effectiveSpecialId.isBlank()) {
            emit(Result.failure(IllegalArgumentException("缺少酷狗评论资源 special_id")))
            return@flow
        }
        val res = commentService.getFloorComments(
            resourceType = type,
            specialId = effectiveSpecialId,
            tid = parentCommentId,
            mixSongId = mixSongId,
            page = pageNo,
            pageSize = limit
        ).map { page ->
            UnifiedFloorCommentsPage(
                comments = page.comments.map { it.toUnifiedComment() },
                totalCount = page.totalCount,
                hasMore = page.hasMore
            )
        }
        emit(res)
    }

    override fun addComment(target: CommentTarget, content: String): Flow<Result<UnifiedComment?>> = flow {
        if (!isEnabled()) {
            emit(Result.failure(IllegalStateException("酷狗音乐平台已关闭")))
            return@flow
        }
        val (type, specialId, mixSongId, name) = resolveSendMeta(target)
        val res = commentService.sendComment(
            resourceType = type,
            content = content,
            specialId = specialId.takeIf { it.isNotBlank() },
            mixSongId = mixSongId,
            resourceName = name.takeIf { it.isNotBlank() }
        ).map { it?.toUnifiedComment() }
        emit(res)
    }

    override fun replyComment(
        target: CommentTarget,
        commentId: String,
        content: String,
        pid: String,
        replyUserName: String?,
        replyContent: String?,
        kugouSpecialId: String?
    ): Flow<Result<UnifiedComment?>> = flow {
        if (!isEnabled()) {
            emit(Result.failure(IllegalStateException("酷狗音乐平台已关闭")))
            return@flow
        }
        val (type, specialId, mixSongId, name) = resolveSendMeta(target, kugouSpecialId)
        val effectiveSpecialId = specialId.ifBlank {
            if (mixSongId != null && mixSongId > 0L) {
                commentService.getMusicComments(mixSongId, page = 1, pageSize = 1).getOrNull()?.specialId.orEmpty()
            } else {
                ""
            }
        }
        if (effectiveSpecialId.isBlank()) {
            emit(Result.failure(IllegalArgumentException("缺少酷狗评论资源 special_id")))
            return@flow
        }
        val res = commentService.replyComment(
            resourceType = type,
            specialId = effectiveSpecialId,
            tid = commentId,
            content = content,
            pid = pid,
            replyUserName = replyUserName,
            replyContent = replyContent,
            resourceName = name.takeIf { it.isNotBlank() }
        ).map { it?.toUnifiedComment() }
        emit(res)
    }

    override fun deleteComment(
        target: CommentTarget,
        commentId: String,
        tid: String?,
        kugouSpecialId: String?
    ): Flow<Result<Unit>> = flow {
        if (!isEnabled()) {
            emit(Result.failure(IllegalStateException("酷狗音乐平台已关闭")))
            return@flow
        }
        val (type, specialId, mixSongId) = resolveCommentMeta(target, kugouSpecialId)
        val res = commentService.deleteComment(
            resourceType = type,
            cid = commentId,
            specialId = specialId.takeIf { it.isNotBlank() },
            tid = tid,
            mixSongId = mixSongId
        )
        emit(res)
    }

    override fun likeComment(
        target: CommentTarget,
        commentId: String,
        like: Boolean
    ): Flow<Result<Unit>> =
        flowOf(Result.failure(UnsupportedOperationException("酷狗暂未开放评论点赞接口")))

    private suspend fun resolveMixSongId(target: CommentTarget.TrackTarget): Long {
        target.kugouMixSongId?.takeIf { it > 0L }?.let { return it }
        val service = playbackService ?: return 0L
        return service.getPrivilegeLite(listOf(target.trackId.rawId))
            .getOrNull()
            ?.firstOrNull()
            ?.mixSongId
            ?: 0L
    }

    private suspend fun resolveCommentMeta(
        target: CommentTarget,
        overrideSpecialId: String?
    ): Triple<KugouCommentResourceType, String, Long?> = when (target) {
        is CommentTarget.TrackTarget -> Triple(
            KugouCommentResourceType.SONG,
            overrideSpecialId?.takeIf { it.isNotBlank() } ?: target.specialId.orEmpty(),
            resolveMixSongId(target).takeIf { it > 0L }
        )
        is CommentTarget.PlaylistTarget -> Triple(
            KugouCommentResourceType.PLAYLIST,
            overrideSpecialId?.takeIf { it.isNotBlank() } ?: target.playlistId.rawId,
            null
        )
        is CommentTarget.AlbumTarget -> Triple(
            KugouCommentResourceType.ALBUM,
            overrideSpecialId?.takeIf { it.isNotBlank() } ?: target.albumId.rawId,
            null
        )
        is CommentTarget.RawNeteaseThread -> Triple(KugouCommentResourceType.SONG, "", null)
    }

    private suspend fun resolveSendMeta(
        target: CommentTarget,
        overrideSpecialId: String? = null
    ): SendMeta = when (target) {
        is CommentTarget.TrackTarget -> SendMeta(
            type = KugouCommentResourceType.SONG,
            specialId = overrideSpecialId?.takeIf { it.isNotBlank() } ?: target.specialId.orEmpty(),
            mixSongId = resolveMixSongId(target).takeIf { it > 0L },
            name = target.title
        )
        is CommentTarget.PlaylistTarget -> SendMeta(
            type = KugouCommentResourceType.PLAYLIST,
            specialId = overrideSpecialId?.takeIf { it.isNotBlank() } ?: target.playlistId.rawId,
            mixSongId = null,
            name = target.name
        )
        is CommentTarget.AlbumTarget -> SendMeta(
            type = KugouCommentResourceType.ALBUM,
            specialId = overrideSpecialId?.takeIf { it.isNotBlank() } ?: target.albumId.rawId,
            mixSongId = null,
            name = target.name
        )
        is CommentTarget.RawNeteaseThread -> SendMeta(KugouCommentResourceType.SONG, "", null, "")
    }

    private data class SendMeta(
        val type: KugouCommentResourceType,
        val specialId: String,
        val mixSongId: Long?,
        val name: String
    )
}

class QQPlatformCommentRepository(
    private val qqApi: QQCommentApi,
    private val zone: ZoneId = ZoneId.systemDefault()
) : PlatformCommentRepository {

    override fun getComments(
        target: CommentTarget,
        pageNo: Int,
        pageSize: Int,
        cursor: String,
        sortType: CommentSortType
    ): Flow<Result<UnifiedCommentsPage>> {
        val topId = (target as? CommentTarget.TrackTarget)?.qqSongId ?: 0L
        if (topId <= 0L) {
            return flowOf(Result.failure(IllegalStateException("未在 QQ 音乐找到这首歌的评论")))
        }
        return flow {
            val result = try {
                val page = (pageNo - 1).coerceAtLeast(0)
                val response = qqApi.fetch(topId, page, pageSize, lastCommentId = "")
                val today = LocalDate.now(zone)
                if (sortType == CommentSortType.HOT) {
                    val hot = response.hotComment
                    val items = hot?.commentlist.orEmpty().map { it.toUnifiedComment(today) }
                    Result.success(
                        UnifiedCommentsPage(
                            comments = items,
                            hotComments = items,
                            totalCount = hot?.commenttotal ?: 0,
                            hasMore = false
                        )
                    )
                } else {
                    val latest = response.comment
                    val rawComments = latest?.commentlist.orEmpty()
                    val total = latest?.commenttotal ?: 0
                    Result.success(
                        UnifiedCommentsPage(
                            comments = rawComments.map { it.toUnifiedComment(today) },
                            hotComments = response.hotComment?.commentlist.orEmpty().map { it.toUnifiedComment(today) },
                            totalCount = total,
                            hasMore = rawComments.isNotEmpty() && pageNo * pageSize < total,
                            cursor = pageNo.toString()
                        )
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Result.failure(IllegalStateException("QQ 音乐评论加载失败", e))
            }
            emit(result)
        }
    }

    override fun getFloorComments(
        target: CommentTarget,
        parentCommentId: String,
        cursorOrTime: Long,
        pageNo: Int,
        limit: Int,
        kugouSpecialId: String?
    ): Flow<Result<UnifiedFloorCommentsPage>> = unsupported()

    override fun addComment(target: CommentTarget, content: String): Flow<Result<UnifiedComment?>> = unsupported()

    override fun replyComment(
        target: CommentTarget,
        commentId: String,
        content: String,
        pid: String,
        replyUserName: String?,
        replyContent: String?,
        kugouSpecialId: String?
    ): Flow<Result<UnifiedComment?>> = unsupported()

    override fun deleteComment(
        target: CommentTarget,
        commentId: String,
        tid: String?,
        kugouSpecialId: String?
    ): Flow<Result<Unit>> = unsupported()

    override fun likeComment(
        target: CommentTarget,
        commentId: String,
        like: Boolean
    ): Flow<Result<Unit>> = unsupported()

    private fun QQComment.toUnifiedComment(today: LocalDate): UnifiedComment = UnifiedComment(
        id = commentid.ifBlank { hashCode().toString() },
        platform = MusicPlatform.QQ,
        author = UnifiedCreator(nickname = nick, avatarUrl = avatarurl),
        content = rootcommentcontent.replace(QQ_EMOJI_CODE, "").trim(),
        timeMs = time * 1000L,
        timeText = formatDate(time, today),
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

    private fun <T> unsupported(): Flow<Result<T>> =
        flowOf(Result.failure(UnsupportedOperationException("QQ 音乐评论只支持浏览")))

    private companion object {
        private val QQ_EMOJI_CODE = Regex("""\[em]e\d+\[/em]""")
    }
}

// 跨平台评论路由：网易走原生评论、酷狗原生通道、QQ 与外部匹配曲目保留 QQ 只读兜底
class PlatformCommentRouter(
    private val netease: PlatformCommentRepository,
    private val kugou: PlatformCommentRepository,
    private val qq: PlatformCommentRepository,
    private val isKugouEnabled: suspend () -> Boolean = { true }
) : PlatformCommentRepository {

    override fun getComments(
        target: CommentTarget,
        pageNo: Int,
        pageSize: Int,
        cursor: String,
        sortType: CommentSortType
    ): Flow<Result<UnifiedCommentsPage>> = route(target) {
        it.getComments(target, pageNo, pageSize, cursor, sortType)
    }

    override fun getFloorComments(
        target: CommentTarget,
        parentCommentId: String,
        cursorOrTime: Long,
        pageNo: Int,
        limit: Int,
        kugouSpecialId: String?
    ): Flow<Result<UnifiedFloorCommentsPage>> = route(target) {
        it.getFloorComments(target, parentCommentId, cursorOrTime, pageNo, limit, kugouSpecialId)
    }

    override fun addComment(target: CommentTarget, content: String): Flow<Result<UnifiedComment?>> =
        route(target) { it.addComment(target, content) }

    override fun replyComment(
        target: CommentTarget,
        commentId: String,
        content: String,
        pid: String,
        replyUserName: String?,
        replyContent: String?,
        kugouSpecialId: String?
    ): Flow<Result<UnifiedComment?>> = route(target) {
        it.replyComment(target, commentId, content, pid, replyUserName, replyContent, kugouSpecialId)
    }

    override fun deleteComment(
        target: CommentTarget,
        commentId: String,
        tid: String?,
        kugouSpecialId: String?
    ): Flow<Result<Unit>> = route(target) {
        it.deleteComment(target, commentId, tid, kugouSpecialId)
    }

    override fun likeComment(target: CommentTarget, commentId: String, like: Boolean): Flow<Result<Unit>> =
        route(target) { it.likeComment(target, commentId, like) }

    private fun <T> route(
        target: CommentTarget,
        call: (PlatformCommentRepository) -> Flow<Result<T>>
    ): Flow<Result<T>> = when (target.platform) {
        MusicPlatform.NETEASE -> call(netease)
        MusicPlatform.KUGOU -> flow {
            if (!isKugouEnabled()) {
                emit(Result.failure(IllegalStateException("酷狗音乐平台已关闭")))
            } else {
                emit(call(kugou).first())
            }
        }
        MusicPlatform.QQ,
        MusicPlatform.KUWO,
        MusicPlatform.MIGU,
        MusicPlatform.LX -> call(qq)
        MusicPlatform.LOCAL -> flowOf(Result.failure(UnsupportedOperationException("本地曲目不支持评论")))
    }
}

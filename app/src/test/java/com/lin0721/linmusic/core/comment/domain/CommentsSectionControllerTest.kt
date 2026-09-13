package com.lin0721.linmusic.core.comment.domain

import com.lin0721.linmusic.core.comment.data.CommentSortType
import com.lin0721.linmusic.core.comment.data.CommentsV2Data
import com.lin0721.linmusic.core.comment.data.FloorCommentsData
import com.lin0721.linmusic.core.comment.ui.CommentsState
import com.lin0721.linmusic.core.model.CommentItem
import com.lin0721.linmusic.core.network.AppError
import com.lin0721.linmusic.core.network.ResourceProvider
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlinx.coroutines.ExperimentalCoroutinesApi

@OptIn(ExperimentalCoroutinesApi::class)
class CommentsSectionControllerTest {

    private val fakeResourceProvider = object : ResourceProvider() {
        override fun getString(resId: Int): String = "error"
    }

    private fun newController(repository: FakeCommentRepository, scope: TestScope) =
        CommentsSectionController(
            scope = scope,
            repository = repository,
            onToast = {},
            resourceProvider = fakeResourceProvider
        )

    @Test
    fun `load成功后状态为Success且带上第一页游标信息`() = runTest {
        val repository = FakeCommentRepository().apply {
            commentsV2Result = Result.success(
                CommentsV2Data(
                    comments = listOf(CommentItem(commentId = 1)),
                    totalCount = 100,
                    hasMore = true,
                    cursor = "999",
                    sortType = CommentSortType.LATEST.wireValue
                )
            )
        }
        val controller = newController(repository, this)

        controller.load("R_SO_4_1")
        advanceUntilIdle()

        val state = controller.commentsState.value as CommentsState.Success
        assertEquals(1, state.comments.size)
        assertEquals(100, state.total)
        assertTrue(state.hasMore)
        assertEquals("999", state.cursor)
        assertEquals(CommentSortType.LATEST, state.sortType)
    }

    @Test
    fun `load失败后状态为Error`() = runTest {
        val repository = FakeCommentRepository().apply {
            commentsV2Result = Result.failure(AppError.NetworkError)
        }
        val controller = newController(repository, this)

        controller.load("R_SO_4_1")
        advanceUntilIdle()

        assertTrue(controller.commentsState.value is CommentsState.Error)
    }

    @Test
    fun `loadMore请求第二页并把新评论追加到列表末尾`() = runTest {
        val repository = FakeCommentRepository().apply {
            commentsV2Result = Result.success(
                CommentsV2Data(comments = listOf(CommentItem(commentId = 1)), hasMore = true, cursor = "10")
            )
        }
        val controller = newController(repository, this)
        controller.load("R_SO_4_1")
        advanceUntilIdle()

        repository.commentsV2Result = Result.success(
            CommentsV2Data(comments = listOf(CommentItem(commentId = 2)), hasMore = false, cursor = "20")
        )
        controller.loadMore()
        advanceUntilIdle()

        val state = controller.commentsState.value as CommentsState.Success
        assertEquals(listOf(1L, 2L), state.comments.map { it.commentId })
        assertEquals(2, repository.lastGetCommentsV2Call?.pageNo)
        assertEquals(false, state.hasMore)
    }

    @Test
    fun `changeSort会重置分页并重新从第一页加载`() = runTest {
        val repository = FakeCommentRepository().apply {
            commentsV2Result = Result.success(CommentsV2Data(comments = listOf(CommentItem(commentId = 1))))
        }
        val controller = newController(repository, this)
        controller.load("R_SO_4_1")
        advanceUntilIdle()

        controller.changeSort(CommentSortType.HOT)
        advanceUntilIdle()

        assertEquals(1, repository.lastGetCommentsV2Call?.pageNo)
        assertEquals(CommentSortType.HOT, repository.lastGetCommentsV2Call?.sortType)
        val state = controller.commentsState.value as CommentsState.Success
        assertEquals(CommentSortType.HOT, state.sortType)
    }

    @Test
    fun `点赞先乐观更新再请求成功后保留新状态`() = runTest {
        val repository = FakeCommentRepository().apply {
            commentsV2Result = Result.success(
                CommentsV2Data(comments = listOf(CommentItem(commentId = 1, likedCount = 5, liked = false)))
            )
            likeResult = Result.success(Unit)
        }
        val controller = newController(repository, this)
        controller.load("R_SO_4_1")
        advanceUntilIdle()

        controller.like((controller.commentsState.value as CommentsState.Success).comments.first())
        advanceUntilIdle()

        val comment = (controller.commentsState.value as CommentsState.Success).comments.first()
        assertTrue(comment.liked)
        assertEquals(6, comment.likedCount)
    }

    @Test
    fun `点赞请求失败会回滚到点赞前的状态`() = runTest {
        val repository = FakeCommentRepository().apply {
            commentsV2Result = Result.success(
                CommentsV2Data(comments = listOf(CommentItem(commentId = 1, likedCount = 5, liked = false)))
            )
            likeResult = Result.failure(AppError.NetworkError)
        }
        val controller = newController(repository, this)
        controller.load("R_SO_4_1")
        advanceUntilIdle()

        controller.like((controller.commentsState.value as CommentsState.Success).comments.first())
        advanceUntilIdle()

        val comment = (controller.commentsState.value as CommentsState.Success).comments.first()
        assertEquals(false, comment.liked)
        assertEquals(5, comment.likedCount)
    }

    @Test
    fun `submitComment成功后把新评论插到列表最前面`() = runTest {
        val repository = FakeCommentRepository().apply {
            commentsV2Result = Result.success(CommentsV2Data(comments = listOf(CommentItem(commentId = 1))))
            addCommentResult = Result.success(CommentItem(commentId = 2, content = "hi"))
        }
        val controller = newController(repository, this)
        controller.load("R_SO_4_1")
        advanceUntilIdle()

        controller.submitComment("hi")
        advanceUntilIdle()

        val state = controller.commentsState.value as CommentsState.Success
        assertEquals(listOf(2L, 1L), state.comments.map { it.commentId })
    }

    @Test
    fun `submitComment拿不到新评论对象时不插入但total加1`() = runTest {
        val repository = FakeCommentRepository().apply {
            commentsV2Result = Result.success(CommentsV2Data(comments = listOf(CommentItem(commentId = 1)), totalCount = 10))
            addCommentResult = Result.success(null)
        }
        val controller = newController(repository, this)
        controller.load("R_SO_4_1")
        advanceUntilIdle()

        controller.submitComment("hi")
        advanceUntilIdle()

        val state = controller.commentsState.value as CommentsState.Success
        assertEquals(listOf(1L), state.comments.map { it.commentId })
        assertEquals(11, state.total)
    }

    @Test
    fun `deleteComment成功后从列表移除`() = runTest {
        val repository = FakeCommentRepository().apply {
            commentsV2Result = Result.success(
                CommentsV2Data(comments = listOf(CommentItem(commentId = 1), CommentItem(commentId = 2)), totalCount = 2)
            )
            deleteCommentResult = Result.success(Unit)
        }
        val controller = newController(repository, this)
        controller.load("R_SO_4_1")
        advanceUntilIdle()

        controller.deleteComment(CommentItem(commentId = 1))
        advanceUntilIdle()

        val state = controller.commentsState.value as CommentsState.Success
        assertEquals(listOf(2L), state.comments.map { it.commentId })
        assertEquals(1, state.total)
    }

    @Test
    fun `deleteComment失败后恢复原列表`() = runTest {
        val repository = FakeCommentRepository().apply {
            commentsV2Result = Result.success(CommentsV2Data(comments = listOf(CommentItem(commentId = 1)), totalCount = 1))
            deleteCommentResult = Result.failure(AppError.NetworkError)
        }
        val controller = newController(repository, this)
        controller.load("R_SO_4_1")
        advanceUntilIdle()

        controller.deleteComment(CommentItem(commentId = 1))
        advanceUntilIdle()

        val state = controller.commentsState.value as CommentsState.Success
        assertEquals(listOf(1L), state.comments.map { it.commentId })
        assertEquals(1, state.total)
    }

    @Test
    fun `openFloor成功后floorState为Success`() = runTest {
        val owner = CommentItem(commentId = 1, content = "owner")
        val repository = FakeCommentRepository().apply {
            floorResult = Result.success(
                FloorCommentsData(
                    comments = listOf(CommentItem(commentId = 2, content = "reply", parentCommentId = 1)),
                    hasMore = false,
                    totalCount = 1
                )
            )
        }
        val controller = newController(repository, this)

        controller.openFloor(owner)
        advanceUntilIdle()

        val state = controller.floorState.value as CommentFloorState.Success
        assertEquals(1L, state.ownerComment.commentId)
        assertEquals(listOf(2L), state.replies.map { it.commentId })
        assertEquals(false, state.hasMore)
    }

    @Test
    fun `closeFloor把floorState重置为Idle`() = runTest {
        val repository = FakeCommentRepository()
        val controller = newController(repository, this)
        controller.openFloor(CommentItem(commentId = 1))
        advanceUntilIdle()

        controller.closeFloor()

        assertTrue(controller.floorState.value is CommentFloorState.Idle)
    }
}


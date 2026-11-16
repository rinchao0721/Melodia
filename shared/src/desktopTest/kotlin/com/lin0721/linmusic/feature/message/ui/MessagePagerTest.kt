package com.lin0721.linmusic.feature.message.ui

import com.lin0721.linmusic.feature.message.domain.MessagePage
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class MessagePagerTest {

    private class Harness(scope: TestScope, private val results: ArrayDeque<Result<MessagePage<Int>>>) {
        val requestedCursors = mutableListOf<Long>()
        val toasts = mutableListOf<String>()
        val loadedPages = mutableListOf<List<Int>>()

        val pager = MessagePager<Int>(
            scope = scope,
            initialCursor = -1L,
            fetch = { cursor ->
                requestedCursors += cursor
                flowOf(results.removeFirst())
            },
            keyOf = { it },
            errorMessage = { "错误:${it.message}" },
            onToast = { toasts += it },
            onPageLoaded = { loadedPages += it }
        )
    }

    private fun page(vararg items: Int, hasMore: Boolean = false, next: Long = 0L) =
        Result.success(MessagePage(items.toList(), hasMore, next))

    private fun TestScope.harness(results: List<Result<MessagePage<Int>>>) =
        Harness(this, ArrayDeque(results))

    @Test
    fun `初始为Idle，loadIfIdle只触发一次首次加载`() = runTest {
        val h = harness(listOf(page(1, 2, hasMore = true, next = 50)))
        assertTrue(h.pager.state.value is MessageListState.Idle)

        h.pager.loadIfIdle()
        h.pager.loadIfIdle()
        advanceUntilIdle()

        assertEquals(listOf(-1L), h.requestedCursors)
        val state = h.pager.state.value as MessageListState.Success
        assertEquals(listOf(1, 2), state.items)
        assertEquals(50L, state.cursor)
        assertTrue(state.hasMore)
        assertEquals(listOf(listOf(1, 2)), h.loadedPages)
    }

    @Test
    fun `首次加载失败进入Error并带错误文案`() = runTest {
        val h = harness(listOf(Result.failure(IllegalStateException("boom"))))

        h.pager.refresh()
        advanceUntilIdle()

        assertEquals(MessageListState.Error("错误:boom"), h.pager.state.value)
    }

    @Test
    fun `loadMore使用上一页游标并追加，重复key被过滤`() = runTest {
        val h = harness(
            listOf(
                page(1, 2, hasMore = true, next = 20),
                page(2, 3, hasMore = false, next = 30)
            )
        )
        h.pager.refresh()
        advanceUntilIdle()

        h.pager.loadMore()
        advanceUntilIdle()

        assertEquals(listOf(-1L, 20L), h.requestedCursors)
        val state = h.pager.state.value as MessageListState.Success
        assertEquals(listOf(1, 2, 3), state.items)
        assertFalse(state.hasMore)
        assertFalse(state.isLoadingMore)
    }

    @Test
    fun `没有更多数据时loadMore不发请求`() = runTest {
        val h = harness(listOf(page(1, hasMore = false)))
        h.pager.refresh()
        advanceUntilIdle()

        h.pager.loadMore()
        advanceUntilIdle()

        assertEquals(listOf(-1L), h.requestedCursors)
    }

    @Test
    fun `loadMore失败时保留已有列表、提示错误并恢复可再次加载`() = runTest {
        val h = harness(
            listOf(
                page(1, 2, hasMore = true, next = 20),
                Result.failure(IllegalStateException("网络断开"))
            )
        )
        h.pager.refresh()
        advanceUntilIdle()

        h.pager.loadMore()
        advanceUntilIdle()

        val state = h.pager.state.value as MessageListState.Success
        assertEquals(listOf(1, 2), state.items)
        assertFalse(state.isLoadingMore)
        assertTrue(state.hasMore)
        assertEquals(listOf("错误:网络断开"), h.toasts)
    }

    @Test
    fun `refresh会回到起始游标重新加载`() = runTest {
        val h = harness(
            listOf(
                page(1, hasMore = true, next = 20),
                page(9, hasMore = false, next = 0)
            )
        )
        h.pager.refresh()
        advanceUntilIdle()

        h.pager.refresh()
        advanceUntilIdle()

        assertEquals(listOf(-1L, -1L), h.requestedCursors)
        assertEquals(listOf(9), (h.pager.state.value as MessageListState.Success).items)
    }
}

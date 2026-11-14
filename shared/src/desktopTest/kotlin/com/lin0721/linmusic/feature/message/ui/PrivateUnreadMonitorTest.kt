package com.lin0721.linmusic.feature.message.ui

import com.lin0721.linmusic.feature.message.FakeMessageRepository
import com.lin0721.linmusic.feature.message.createTestReadPreferences
import com.lin0721.linmusic.feature.message.testMessageUser
import com.lin0721.linmusic.feature.message.domain.PrivateConversation
import com.lin0721.linmusic.feature.message.createTestUserPreferences
import com.lin0721.linmusic.feature.message.domain.MessagePage
import com.lin0721.linmusic.feature.message.loginAs
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PrivateUnreadMonitorTest {

    @Test
    fun `未登录不轮询，登录后按间隔轮询newMsgCount，失败保留原值，登出归零并停轮询`() = runTest {
        val repo = FakeMessageRepository()
        var currentUnread = 3
        repo.conversationsHandler = { _, _, _ ->
            Result.success(MessagePage(emptyList(), hasMore = false, nextCursor = 0L, newMsgCount = currentUnread))
        }
        val prefs = createTestUserPreferences()

        // 使用 backgroundScope 承载无限轮询，测试结束自动取消
        val monitor = PrivateUnreadMonitor(
            messageRepository = repo,
            userPreferences = prefs,
            readPreferences = createTestReadPreferences(),
            scope = backgroundScope,
            pollIntervalMs = 60_000L
        )

        runCurrent()
        assertEquals(0, monitor.unreadCount.value)
        assertEquals(0, repo.conversationCalls.size)

        // 登录触发立即拉取一页会话
        prefs.loginAs(100L)
        runCurrent()
        assertEquals(1, repo.conversationCalls.size)
        assertEquals(50, repo.conversationCalls.single().limit)
        assertEquals(3, monitor.unreadCount.value)

        // 推进 60s 触发下次轮询
        currentUnread = 7
        advanceTimeBy(60_000L)
        runCurrent()
        assertEquals(2, repo.conversationCalls.size)
        assertEquals(7, monitor.unreadCount.value)

        // 失败时不覆盖已有未读数
        repo.conversationsHandler = { _, _, _ -> Result.failure(IllegalStateException("network")) }
        advanceTimeBy(60_000L)
        runCurrent()
        assertEquals(3, repo.conversationCalls.size)
        assertEquals(7, monitor.unreadCount.value)

        // 主动 refresh 立即拉取
        repo.conversationsHandler = { _, _, _ ->
            Result.success(MessagePage(emptyList(), hasMore = false, nextCursor = 0L, newMsgCount = 2))
        }
        monitor.refresh()
        runCurrent()
        assertEquals(4, repo.conversationCalls.size)
        assertEquals(2, monitor.unreadCount.value)

        // 登出后归零且不再轮询
        prefs.clearUserProfile()
        runCurrent()
        assertEquals(0, monitor.unreadCount.value)
        val countAfterLogout = repo.conversationCalls.size
        advanceTimeBy(120_000L)
        runCurrent()
        assertEquals(countAfterLogout, repo.conversationCalls.size)
    }

    @Test
    fun `本地已读的会话从总未读里扣除`() = runTest {
        val repo = FakeMessageRepository()
        repo.conversationsHandler = { _, _, _ ->
            Result.success(
                MessagePage(
                    items = listOf(
                        PrivateConversation(1L, testMessageUser(20L), "在吗", 1_000L, 4),
                        PrivateConversation(2L, testMessageUser(30L), "晚安", 2_000L, 2)
                    ),
                    hasMore = false,
                    nextCursor = 2L,
                    newMsgCount = 6
                )
            )
        }
        val prefs = createTestUserPreferences()
        val readPrefs = createTestReadPreferences()
        val monitor = PrivateUnreadMonitor(repo, prefs, readPrefs, backgroundScope, pollIntervalMs = 60_000L)

        prefs.loginAs(100L)
        runCurrent()
        assertEquals(6, monitor.unreadCount.value)

        readPrefs.markRead(100L, 20L, 1_000L)
        monitor.refresh()
        runCurrent()
        assertEquals(2, monitor.unreadCount.value)

        // 其他账号的已读记录不影响
        readPrefs.markRead(999L, 30L, 2_000L)
        monitor.refresh()
        runCurrent()
        assertEquals(2, monitor.unreadCount.value)
    }

    @Test
    fun `退到后台停止轮询，回到前台立即补查`() = runTest {
        val repo = FakeMessageRepository()
        repo.conversationsHandler = { _, _, _ ->
            Result.success(MessagePage(emptyList(), hasMore = false, nextCursor = 0L, newMsgCount = 1))
        }
        val prefs = createTestUserPreferences()
        val monitor = PrivateUnreadMonitor(repo, prefs, createTestReadPreferences(), backgroundScope, pollIntervalMs = 60_000L)

        prefs.loginAs(100L)
        runCurrent()
        assertEquals(1, repo.conversationCalls.size)

        monitor.setForeground(false)
        runCurrent()
        advanceTimeBy(180_000L)
        runCurrent()
        assertEquals(1, repo.conversationCalls.size)

        monitor.setForeground(true)
        runCurrent()
        assertEquals(2, repo.conversationCalls.size)
        advanceTimeBy(60_000L)
        runCurrent()
        assertEquals(3, repo.conversationCalls.size)
    }

    @Test
    fun `拖掉单个会话角标扣减总数，拖掉总角标全部本地已读`() = runTest {
        val repo = FakeMessageRepository()
        val first = PrivateConversation(1L, testMessageUser(20L), "在吗", 1_000L, 4)
        val second = PrivateConversation(2L, testMessageUser(30L), "晚安", 2_000L, 2)
        repo.conversationsHandler = { _, _, _ ->
            Result.success(MessagePage(listOf(first, second), hasMore = false, nextCursor = 2L, newMsgCount = 6))
        }
        val prefs = createTestUserPreferences()
        val readPrefs = createTestReadPreferences()
        val monitor = PrivateUnreadMonitor(repo, prefs, readPrefs, backgroundScope, pollIntervalMs = 60_000L)
        prefs.loginAs(100L)
        runCurrent()
        assertEquals(6, monitor.unreadCount.value)

        monitor.markConversationRead(first)
        runCurrent()
        assertEquals(2, monitor.unreadCount.value)
        assertEquals(mapOf(20L to 1_000L), readPrefs.snapshot(100L))

        monitor.markAllRead()
        runCurrent()
        assertEquals(0, monitor.unreadCount.value)
        assertEquals(mapOf(20L to 1_000L, 30L to 2_000L), readPrefs.snapshot(100L))

        // 下次轮询按本地已读扣减后仍为 0
        advanceTimeBy(60_000L)
        runCurrent()
        assertEquals(0, monitor.unreadCount.value)
    }
}

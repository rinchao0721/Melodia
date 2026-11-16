package com.lin0721.linmusic.feature.message.ui

import com.lin0721.linmusic.core.auth.AccountSwitchEvent
import com.lin0721.linmusic.core.auth.AccountSwitchReason
import com.lin0721.linmusic.feature.message.FakeMessageRepository
import com.lin0721.linmusic.feature.message.createTestReadPreferences
import com.lin0721.linmusic.feature.message.createTestUserPreferences
import com.lin0721.linmusic.feature.message.domain.MessagePage
import com.lin0721.linmusic.feature.message.domain.PrivateConversation
import com.lin0721.linmusic.feature.message.loginAs
import com.lin0721.linmusic.feature.message.testMessageUser
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PrivateConversationPreviewStoreTest {

    private fun conversation(peer: Long, time: Long, unread: Int = 0) =
        PrivateConversation(peer, testMessageUser(peer), "消息$peer", time, unread)

    @Test
    fun `预览只取前3条并套用本地已读`() {
        val items = listOf(
            conversation(1L, 4_000L, unread = 2),
            conversation(2L, 3_000L, unread = 1),
            conversation(3L, 2_000L),
            conversation(4L, 1_000L)
        )
        val preview = buildConversationPreview(items, readTimes = mapOf(1L to 4_000L))
        assertEquals(listOf(1L, 2L, 3L), preview.map { it.user.uid })
        assertEquals(0, preview[0].unreadCount)
        assertEquals(1, preview[1].unreadCount)
        assertTrue(buildConversationPreview(items, emptyMap(), limit = 0).isEmpty())
    }

    @Test
    fun `按账户缓存，切换账户清空，失败保留缓存`() = runTest {
        val repo = FakeMessageRepository()
        val prefs = createTestUserPreferences()
        val readPrefs = createTestReadPreferences()
        val events = MutableSharedFlow<AccountSwitchEvent>(extraBufferCapacity = 4)
        val monitor = PrivateUnreadMonitor(repo, prefs, readPrefs, backgroundScope)
        val store = PrivateConversationPreviewStore(
            messageRepository = repo,
            userPreferences = prefs,
            readPreferences = readPrefs,
            unreadMonitor = monitor,
            scope = backgroundScope,
            accountEvents = events
        )
        repo.conversationsHandler = { self, _, limit ->
            val items = (1L..5L).map { conversation(self * 10 + it, 10_000L - it, unread = 1) }
            Result.success(MessagePage(items.take(limit), hasMore = false, nextCursor = 0L))
        }

        // 未登录不请求
        store.refresh()
        runCurrent()
        assertFalse(store.state.value.loaded)
        assertTrue(repo.conversationCalls.none { it.limit == CONVERSATION_PREVIEW_LIMIT })

        prefs.loginAs(1L)
        runCurrent()
        store.refresh()
        runCurrent()
        assertTrue(store.state.value.loaded)
        assertEquals(listOf(11L, 12L, 13L), store.state.value.items.map { it.user.uid })
        assertEquals(1L, repo.conversationCalls.last { it.limit == CONVERSATION_PREVIEW_LIMIT }.selfUid)

        // 失败时保留原缓存并标记
        repo.conversationsHandler = { _, _, _ -> Result.failure(IllegalStateException("network")) }
        store.refresh()
        runCurrent()
        assertTrue(store.state.value.failed)
        assertEquals(3, store.state.value.items.size)

        // 本地拖掉角标即时清零
        store.markRead(store.state.value.items.first())
        runCurrent()
        assertEquals(0, store.state.value.items.first().unreadCount)

        // 切到另一个账户后缓存清空，不显示上个账户的会话
        prefs.loginAs(2L)
        events.emit(AccountSwitchEvent(previousUid = 1L, currentUid = 2L, reason = AccountSwitchReason.ADDED))
        runCurrent()
        assertFalse(store.state.value.loaded)
        assertTrue(store.state.value.items.isEmpty())

        // 切回原账户也需要重新拉取
        prefs.switchTo(1L)
        events.emit(AccountSwitchEvent(previousUid = 2L, currentUid = 1L, reason = AccountSwitchReason.SWITCHED))
        runCurrent()
        assertFalse(store.state.value.loaded)
    }
}

package com.lin0721.linmusic.feature.message.ui

import com.lin0721.linmusic.feature.message.FakeMessageRepository
import com.lin0721.linmusic.feature.message.FakePlaybackRepository
import com.lin0721.linmusic.feature.message.createTestReadPreferences
import com.lin0721.linmusic.feature.message.createTestUserPreferences
import com.lin0721.linmusic.feature.message.domain.MessagePage
import com.lin0721.linmusic.feature.message.domain.PrivateConversation
import com.lin0721.linmusic.feature.message.loginAs
import com.lin0721.linmusic.feature.message.testMessageUser
import com.lin0721.linmusic.feature.podcast.ui.testResourceProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class MessageViewModelTest {

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `MessageTab以私信排在第一位，默认选中私信，privatePager按登录态拉取会话`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        assertEquals(MessageTab.PRIVATE, MessageTab.entries.first())
        assertEquals("私信", MessageTab.PRIVATE.title)

        val repo = FakeMessageRepository().apply {
            conversationsHandler = { selfUid, cursor, limit ->
                Result.success(
                    MessagePage(
                        items = listOf(PrivateConversation(1L, testMessageUser(20L), "在吗", 500L, 1)),
                        hasMore = false,
                        nextCursor = cursor + 1,
                        newMsgCount = 1
                    )
                )
            }
        }
        val prefs = createTestUserPreferences()
        val vm = MessageViewModel(repo, FakePlaybackRepository(), prefs, testResourceProvider, PrivateUnreadMonitor(FakeMessageRepository(), prefs, createTestReadPreferences(), backgroundScope), createTestReadPreferences())

        assertEquals(MessageTab.PRIVATE, vm.selectedTab.value)

        // 未登录触发 privatePager 进入 Error
        vm.ensureLoaded(MessageTab.PRIVATE)
        advanceUntilIdle()
        assertTrue(vm.privatePager.state.value is MessageListState.Error)

        // 登录后刷新 privatePager 成功拉取会话
        prefs.loginAs(10L)
        vm.privatePager.refresh()
        advanceUntilIdle()
        val success = vm.privatePager.state.value as MessageListState.Success
        assertEquals(1, success.items.size)
        assertEquals(10L, repo.conversationCalls.single().selfUid)
    }

    @Test
    fun `会话变化通知触发私信列表静默重拉且不回到加载态`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val repo = FakeMessageRepository().apply {
            conversationsHandler = { _, cursor, _ ->
                Result.success(
                    MessagePage(
                        items = listOf(PrivateConversation(1L, testMessageUser(20L), "在吗", 500L, 0)),
                        hasMore = false,
                        nextCursor = cursor + 1
                    )
                )
            }
        }
        val prefs = createTestUserPreferences()
        prefs.loginAs(10L)
        val monitor = PrivateUnreadMonitor(FakeMessageRepository(), prefs, createTestReadPreferences(), backgroundScope)
        val vm = MessageViewModel(repo, FakePlaybackRepository(), prefs, testResourceProvider, monitor, createTestReadPreferences())
        vm.ensureLoaded(MessageTab.PRIVATE)
        advanceUntilIdle()
        assertEquals(1, repo.conversationCalls.size)

        monitor.notifyConversationsChanged()
        runCurrent()
        assertTrue(vm.privatePager.state.value is MessageListState.Success)
        advanceUntilIdle()
        assertEquals(2, repo.conversationCalls.size)
        assertTrue(vm.privatePager.state.value is MessageListState.Success)
    }
}

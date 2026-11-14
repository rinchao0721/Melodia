package com.lin0721.linmusic.feature.message.ui

import com.lin0721.linmusic.feature.message.FakeMessageRepository
import com.lin0721.linmusic.feature.message.FakeProfileRepository
import com.lin0721.linmusic.feature.message.createTestReadPreferences
import com.lin0721.linmusic.feature.message.createTestUserPreferences
import com.lin0721.linmusic.feature.message.domain.MessagePage
import com.lin0721.linmusic.feature.message.domain.PrivateConversation
import com.lin0721.linmusic.feature.message.loginAs
import com.lin0721.linmusic.feature.message.testMessageUser
import com.lin0721.linmusic.feature.podcast.ui.testResourceProvider
import com.lin0721.linmusic.feature.profile.domain.ProfileFollowListPage
import com.lin0721.linmusic.feature.profile.domain.ProfileFollowUserItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ShareToFriendViewModelTest {

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `并发加载最近会话与关注列表，支持搜索过滤、关注翻页与分享发送`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val messageRepo = FakeMessageRepository().apply {
            conversationsHandler = { _, _, _ ->
                Result.success(
                    MessagePage(
                        items = listOf(
                            PrivateConversation(1L, testMessageUser(20L, "Alice"), "hi", 100L, 0),
                            PrivateConversation(2L, testMessageUser(20L, "Alice"), "hi2", 90L, 0)
                        ),
                        hasMore = false,
                        nextCursor = 2L
                    )
                )
            }
        }
        val profileRepo = FakeProfileRepository().apply {
            userFollowsHandler = { _, offset, _ ->
                if (offset == 0) {
                    Result.success(
                        ProfileFollowListPage(
                            users = listOf(
                                ProfileFollowUserItem(30L, "Bob", "", "", true),
                                ProfileFollowUserItem(31L, "Charlie", "", "", true)
                            ),
                            hasMore = true
                        )
                    )
                } else {
                    Result.success(
                        ProfileFollowListPage(
                            users = listOf(ProfileFollowUserItem(32L, "alice_two", "", "", true)),
                            hasMore = false
                        )
                    )
                }
            }
        }
        val prefs = createTestUserPreferences().apply { loginAs(10L) }
        val vm = ShareToFriendViewModel(messageRepo, profileRepo, prefs, testResourceProvider, PrivateUnreadMonitor(FakeMessageRepository(), prefs, createTestReadPreferences(), backgroundScope))

        vm.loadIfNeeded()
        advanceUntilIdle()

        val ready = vm.uiState.value as ShareToFriendUiState.Ready
        assertEquals(listOf(20L), ready.recentUsers.map { it.uid })
        assertEquals(listOf(30L, 31L), ready.followUsers.map { it.uid })
        assertTrue(ready.hasMoreFollows)

        vm.loadMoreFollows()
        advanceUntilIdle()
        val afterMore = vm.uiState.value as ShareToFriendUiState.Ready
        assertEquals(listOf(30L, 31L, 32L), afterMore.followUsers.map { it.uid })
        assertEquals(listOf(0 to 30, 2 to 30), profileRepo.followCalls)

        vm.updateQuery(" alice ")
        val filtered = vm.uiState.value as ShareToFriendUiState.Ready
        assertEquals(listOf(20L), filtered.filteredRecentUsers.map { it.uid })
        assertEquals(listOf(32L), filtered.filteredFollowUsers.map { it.uid })

        val completedDeferred = async { vm.shareCompleted.first() }
        runCurrent()
        vm.shareSong(userId = 30L, songId = 999L, songName = "晴天", note = "送给你")
        advanceUntilIdle()

        assertEquals(30L, completedDeferred.await().uid)
        assertEquals(listOf(FakeMessageRepository.SendSongCall(30L, 999L, "送给你")), messageRepo.sendSongCalls)
        assertNull((vm.uiState.value as ShareToFriendUiState.Ready).sendingToUid)
    }

    @Test
    fun `会话与关注双双失败时进入Error状态`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val messageRepo = FakeMessageRepository().apply {
            conversationsHandler = { _, _, _ -> Result.failure(IllegalStateException("fail1")) }
        }
        val profileRepo = FakeProfileRepository().apply {
            userFollowsHandler = { _, _, _ -> Result.failure(IllegalStateException("fail2")) }
        }
        val prefs = createTestUserPreferences().apply { loginAs(10L) }
        val vm = ShareToFriendViewModel(messageRepo, profileRepo, prefs, testResourceProvider, PrivateUnreadMonitor(FakeMessageRepository(), prefs, createTestReadPreferences(), backgroundScope))

        vm.refresh()
        advanceUntilIdle()

        assertTrue(vm.uiState.value is ShareToFriendUiState.Error)
    }
}

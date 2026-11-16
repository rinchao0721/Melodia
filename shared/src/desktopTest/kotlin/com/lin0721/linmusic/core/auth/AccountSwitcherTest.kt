package com.lin0721.linmusic.core.auth

import com.lin0721.linmusic.feature.podcast.ui.InMemoryPreferencesStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AccountSwitcherTest {

    @Test
    fun switchToStopsPlaybackClearsQueueUpdatesActiveUidEmitsEventAndVerifiesSession() = runTest {
        var now = 100L
        val prefs = UserPreferences(InMemoryPreferencesStore(), nowMs = { now })
        prefs.upsertAccount(StoredAccount(uid = 1L, nickname = "One", cookies = "MUSIC_U=1"))
        now = 200L
        prefs.upsertAccount(StoredAccount(uid = 2L, nickname = "Two", cookies = "MUSIC_U=2"))

        val repo = FakeAuthRepository(prefs).apply {
            accountResults = listOf(Result.success(loggedInAccount(uid = 1L, nickname = "One")))
        }
        val player = RecordingPlaybackController()
        val monitor = SessionMonitor(prefs, repo) { now }
        val switcher = AccountSwitcher(
            userPreferences = prefs,
            authRepository = repo,
            playbackControllerProvider = { player },
            sessionMonitorProvider = { monitor }
        )

        val events = mutableListOf<AccountSwitchEvent>()
        val job = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            AccountEvents.switched.take(1).toList(events)
        }

        now = 500L
        assertTrue(switcher.switchTo(1L))
        job.join()

        assertEquals(1, player.stopAndClearCalls)
        assertEquals(1L, prefs.activeUid.first())
        assertEquals(500L, prefs.storedAccounts.first().first { it.uid == 1L }.lastUsedAt)
        assertEquals(1, repo.accountCalls)
        assertEquals(
            listOf(AccountSwitchEvent(previousUid = 2L, currentUid = 1L, reason = AccountSwitchReason.SWITCHED)),
            events
        )
    }

    @Test
    fun switchToSameOrMissingAccountDoesNothing() = runTest {
        val prefs = UserPreferences(InMemoryPreferencesStore())
        prefs.upsertAccount(StoredAccount(uid = 1L, nickname = "One", cookies = "MUSIC_U=1"))
        val repo = FakeAuthRepository(prefs)
        val player = RecordingPlaybackController()
        val switcher = AccountSwitcher(prefs, repo, playbackControllerProvider = { player })

        assertFalse(switcher.switchTo(1L))
        assertFalse(switcher.switchTo(999L))
        assertEquals(0, player.stopAndClearCalls)
    }

    @Test
    fun logoutCurrentLogsOutBeforeRemovingAccountAndFallsBackToRecentAccount() = runTest {
        var now = 100L
        val prefs = UserPreferences(InMemoryPreferencesStore(), nowMs = { now })
        prefs.upsertAccount(StoredAccount(uid = 1L, nickname = "Old", cookies = "MUSIC_U=1"))
        now = 300L
        prefs.upsertAccount(StoredAccount(uid = 2L, nickname = "Recent", cookies = "MUSIC_U=2"))
        now = 500L
        prefs.upsertAccount(StoredAccount(uid = 3L, nickname = "Current", cookies = "MUSIC_U=3"))

        val repo = FakeAuthRepository(prefs)
        val player = RecordingPlaybackController()
        val switcher = AccountSwitcher(prefs, repo, playbackControllerProvider = { player })

        val events = mutableListOf<AccountSwitchEvent>()
        val job = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            AccountEvents.switched.take(2).toList(events)
        }

        val next = switcher.logoutCurrent()
        assertEquals(2L, next?.uid)
        assertEquals(1, player.stopAndClearCalls)
        // logout() 必须在 removeAccount(3L) 之前发出，此时 activeUid 仍为 3L
        assertEquals(listOf<Long?>(3L), repo.logoutActiveUids)
        assertEquals(2L, prefs.activeUid.first())
        assertEquals(listOf(1L, 2L), prefs.accounts.first().map { it.uid })

        // 再退出其余两个账户直到未登录
        switcher.logoutCurrent()
        val finalNext = switcher.logoutCurrent()
        assertNull(finalNext)
        assertNull(prefs.activeUid.first())

        job.cancel()
        assertEquals(
            AccountSwitchEvent(previousUid = 3L, currentUid = 2L, reason = AccountSwitchReason.LOGGED_OUT),
            events.first()
        )
        assertEquals(
            AccountSwitchEvent(previousUid = 2L, currentUid = 1L, reason = AccountSwitchReason.LOGGED_OUT),
            events[1]
        )
    }
}

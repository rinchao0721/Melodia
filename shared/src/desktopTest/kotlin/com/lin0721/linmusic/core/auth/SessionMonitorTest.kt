package com.lin0721.linmusic.core.auth

import com.lin0721.linmusic.core.network.AppError
import java.io.File
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SessionMonitorTest {

    private lateinit var prefs: UserPreferences
    private lateinit var dir: File
    private lateinit var repo: FakeAuthRepository
    private var expiredCount = 0
    private var now = 1_000_000_000L

    @Before
    fun setUp() {
        val (preferences, directory) = tempUserPreferences { now }
        prefs = preferences
        dir = directory
        repo = FakeAuthRepository(prefs)
        expiredCount = 0
    }

    @After
    fun tearDown() {
        dir.deleteRecursively()
    }

    private fun monitor(player: RecordingPlaybackController? = null) = SessionMonitor(
        userPreferences = prefs,
        authRepository = repo,
        playbackControllerProvider = { player },
        metadataCache = null,
        nowMs = { now }
    )

    private suspend fun login() {
        prefs.saveCookies("MUSIC_U=old; os=pc")
        prefs.saveUserProfile(UserProfile(uid = 1, nickname = "测试用户", avatarUrl = ""))
    }

    private val onExpired: suspend () -> Unit = { expiredCount++ }

    @Test
    fun unauthorizedWhileLoggedOutIsIgnored() = runTest {
        monitor().handleUnauthorized(onExpired)
        assertEquals(0, expiredCount)
        assertEquals(0, repo.accountCalls)
    }

    @Test
    fun serverSayingAnonymousExpiresSessionAfterConfirmation() = runTest {
        login()
        repo.accountResults = listOf(Result.success(anonymousAccount()))
        monitor().handleUnauthorized(onExpired)
        assertEquals(1, expiredCount)
        assertEquals(2, repo.accountCalls)
        assertEquals(1, repo.logoutCalls)
        assertNull(prefs.userProfile.first())
        assertNull(prefs.cookies.first())
    }

    @Test
    fun transientAnonymousAnswerDoesNotLogOut() = runTest {
        login()
        repo.accountResults = listOf(Result.success(anonymousAccount()), Result.success(loggedInAccount()))
        monitor().handleUnauthorized(onExpired)
        assertEquals(0, expiredCount)
        assertNotNull(prefs.userProfile.first())
    }

    @Test
    fun unauthorizedErrorExpiresSession() = runTest {
        login()
        repo.accountResults = listOf(Result.failure(AppError.Unauthorized))
        monitor().handleUnauthorized(onExpired)
        assertEquals(1, expiredCount)
    }

    @Test
    fun networkErrorKeepsSession() = runTest {
        login()
        repo.accountResults = listOf(Result.failure(AppError.NetworkError))
        monitor().handleUnauthorized(onExpired)
        assertEquals(0, expiredCount)
        assertEquals(1, repo.accountCalls)
        assertNotNull(prefs.cookies.first())
    }

    @Test
    fun startupRefreshMergesNewCookiesAndRecordsTime() = runTest {
        login()
        repo.accountResults = listOf(Result.success(loggedInAccount()))
        repo.refreshResult = Result.success(listOf("MUSIC_U=fresh; Max-Age=1296000; Path=/"))
        monitor().verifyAtStartup(onExpired)
        assertEquals("MUSIC_U=fresh; os=pc", prefs.cookies.first())
        assertEquals(now, prefs.lastLoginRefreshAt.first())
        assertEquals(0, expiredCount)
    }

    @Test
    fun recentRefreshIsSkipped() = runTest {
        login()
        prefs.saveLastLoginRefreshAt(now - 60_000)
        repo.accountResults = listOf(Result.success(loggedInAccount()))
        monitor().verifyAtStartup(onExpired)
        assertEquals(0, repo.refreshCalls)
    }

    @Test
    fun refreshFailureLeavesCookiesAlone() = runTest {
        login()
        repo.accountResults = listOf(Result.success(loggedInAccount()))
        repo.refreshResult = Result.failure(AppError.NetworkError)
        monitor().verifyAtStartup(onExpired)
        assertEquals("MUSIC_U=old; os=pc", prefs.cookies.first())
        assertEquals(0L, prefs.lastLoginRefreshAt.first())
    }

    @Test
    fun startupWithExpiredSessionClearsAndNotifies() = runTest {
        login()
        repo.accountResults = listOf(Result.success(anonymousAccount()))
        monitor().verifyAtStartup(onExpired)
        assertEquals(1, expiredCount)
        assertEquals(0, repo.refreshCalls)
    }

    @Test
    fun startupWhileLoggedOutDoesNothing() = runTest {
        monitor().verifyAtStartup(onExpired)
        assertEquals(0, repo.accountCalls)
    }

    @Test
    fun expireRemovesOnlyCurrentAccountSwitchesToMostRecentAndEmitsNotice() = runTest {
        now = 100L
        prefs.upsertAccount(StoredAccount(uid = 1L, nickname = "Old", cookies = "MUSIC_U=1"))
        now = 300L
        prefs.upsertAccount(StoredAccount(uid = 2L, nickname = "Fallback", cookies = "MUSIC_U=2"))
        now = 500L
        prefs.upsertAccount(StoredAccount(uid = 3L, nickname = "Expired", cookies = "MUSIC_U=3"))

        val player = RecordingPlaybackController()
        val mon = monitor(player)
        val notices = mutableListOf<ExpiredAccountNotice>()
        val events = mutableListOf<AccountSwitchEvent>()
        val noticeJob = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            mon.expiredNotices.take(1).toList(notices)
        }
        val eventJob = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            AccountEvents.switched.take(1).toList(events)
        }

        repo.accountResults = listOf(Result.success(anonymousAccount()))
        mon.handleUnauthorized(onExpired)
        noticeJob.join()
        eventJob.join()

        assertEquals(1, expiredCount)
        assertEquals(1, player.stopAndClearCalls)
        assertEquals(listOf<Long?>(3L), repo.logoutActiveUids)
        assertEquals(2L, prefs.activeUid.first())
        assertEquals(listOf(1L, 2L), prefs.accounts.first().map { it.uid })
        assertEquals(3L, notices.single().expiredProfile.uid)
        assertEquals(2L, notices.single().nextProfile?.uid)
        assertEquals(
            AccountSwitchEvent(previousUid = 3L, currentUid = 2L, reason = AccountSwitchReason.EXPIRED),
            events.single()
        )
    }

    @Test
    fun startupFallsThroughExpiredAccountToValidSecondaryAccount() = runTest {
        now = 100L
        prefs.upsertAccount(StoredAccount(uid = 1L, nickname = "Valid", cookies = "MUSIC_U=1"))
        now = 500L
        prefs.upsertAccount(StoredAccount(uid = 2L, nickname = "Expired", cookies = "MUSIC_U=2"))

        // 前两次确认 uid=2 失效，切回 uid=1 后第三次验证有效
        repo.accountResults = listOf(
            Result.success(anonymousAccount()),
            Result.success(anonymousAccount()),
            Result.success(loggedInAccount(uid = 1L, nickname = "Valid"))
        )
        monitor().verifyAtStartup(onExpired)

        assertEquals(1, expiredCount)
        assertEquals(1L, prefs.activeUid.first())
        assertEquals(listOf(1L), prefs.accounts.first().map { it.uid })
    }
}

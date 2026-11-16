package com.lin0721.linmusic.core.auth

import com.lin0721.linmusic.core.network.AppError
import java.io.File
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class SyncProfileAfterLoginUseCaseTest {

    private lateinit var prefs: UserPreferences
    private lateinit var dir: File
    private lateinit var repo: FakeAuthRepository

    @Before
    fun setUp() {
        val (preferences, directory) = tempUserPreferences()
        prefs = preferences
        dir = directory
        repo = FakeAuthRepository(prefs)
    }

    @After
    fun tearDown() {
        dir.deleteRecursively()
    }

    @Test
    fun validCookieSavesCookieAndProfile() = runTest {
        repo.accountResults = listOf(Result.success(loggedInAccount()))
        val profile = SyncProfileAfterLoginUseCase(prefs, repo)("MUSIC_U=ok")
        assertEquals("测试用户", profile?.nickname)
        assertEquals("MUSIC_U=ok", prefs.cookies.first())
        assertEquals(1L, prefs.userProfile.first()?.uid)
    }

    @Test
    fun anonymousAnswerClearsTheJustSavedCookie() = runTest {
        repo.accountResults = listOf(Result.success(anonymousAccount()))
        assertNull(SyncProfileAfterLoginUseCase(prefs, repo)("MUSIC_U=bad"))
        assertNull(prefs.cookies.first())
        assertNull(prefs.userProfile.first())
    }

    @Test
    fun networkFailureAlsoClearsCookie() = runTest {
        repo.accountResults = listOf(Result.failure(AppError.NetworkError))
        assertNull(SyncProfileAfterLoginUseCase(prefs, repo)("MUSIC_U=whatever"))
        assertNull(prefs.cookies.first())
    }

    @Test
    fun failedValidationPreservesExistingActiveAccountAndUsesOverrideCookie() = runTest {
        prefs.upsertAccount(StoredAccount(uid = 1L, nickname = "Active", cookies = "MUSIC_U=active"))
        repo.accountResultByCookie = mapOf(
            "MUSIC_U=bad" to Result.success(anonymousAccount()),
            "MUSIC_U=err" to Result.failure(AppError.NetworkError)
        )
        val useCase = SyncProfileAfterLoginUseCase(prefs, repo)

        assertEquals(LoginSyncResult.Failed, useCase.syncAccount("MUSIC_U=bad"))
        assertEquals("MUSIC_U=bad", repo.lastOverrideCookies)
        assertEquals(1L, prefs.activeUid.first())
        assertEquals("MUSIC_U=active", prefs.cookies.first())

        assertEquals(LoginSyncResult.Failed, useCase.syncAccount("MUSIC_U=err"))
        assertEquals(1L, prefs.activeUid.first())
        assertEquals("MUSIC_U=active", prefs.cookies.first())
    }

    @Test
    fun addAccountStopsPlaybackWhenSwitchingFromAnotherAccountAndEnforcesCap() = runTest {
        for (i in 1L..4L) {
            prefs.upsertAccount(StoredAccount(uid = i, nickname = "U$i", cookies = "MUSIC_U=u$i"))
        }
        val player = RecordingPlaybackController()
        val switcher = AccountSwitcher(prefs, repo, playbackControllerProvider = { player })
        val useCase = SyncProfileAfterLoginUseCase(prefs, repo) { switcher }

        val events = mutableListOf<AccountSwitchEvent>()
        val job = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            AccountEvents.switched.take(2).toList(events)
        }

        // 添加第 5 个账户并从 uid=4 切换过去
        repo.accountResultByCookie = mapOf(
            "MUSIC_U=u5" to Result.success(loggedInAccount(uid = 5L, nickname = "U5")),
            "MUSIC_U=u6" to Result.success(loggedInAccount(uid = 6L, nickname = "U6")),
            "MUSIC_U=u2_new" to Result.success(loggedInAccount(uid = 2L, nickname = "U2Updated"))
        )
        val added = useCase.syncAccount("MUSIC_U=u5")
        assertTrue(added is LoginSyncResult.Success)
        assertEquals(5L, prefs.activeUid.first())
        assertEquals(1, player.stopAndClearCalls)

        // 超出第 6 个上限时保留原活跃账户 uid=5
        assertEquals(LoginSyncResult.LimitReached, useCase.syncAccount("MUSIC_U=u6"))
        assertEquals(5L, prefs.activeUid.first())
        assertEquals(1, player.stopAndClearCalls)

        // 已存在 uid=2 更新成功并切换过去
        val updated = useCase.syncAccount("MUSIC_U=u2_new")
        assertTrue(updated is LoginSyncResult.Success)
        assertEquals(2L, prefs.activeUid.first())
        assertEquals("U2Updated", prefs.userProfile.first()?.nickname)
        assertEquals("MUSIC_U=u2_new", prefs.cookies.first())
        assertEquals(2, player.stopAndClearCalls)

        job.join()
        assertEquals(
            listOf(
                AccountSwitchEvent(previousUid = 4L, currentUid = 5L, reason = AccountSwitchReason.ADDED),
                AccountSwitchEvent(previousUid = 5L, currentUid = 2L, reason = AccountSwitchReason.ADDED)
            ),
            events
        )
    }
}

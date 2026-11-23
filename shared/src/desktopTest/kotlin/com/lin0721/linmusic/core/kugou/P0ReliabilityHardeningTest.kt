package com.lin0721.linmusic.core.kugou

import com.lin0721.linmusic.core.auth.FakeAuthRepository
import com.lin0721.linmusic.core.auth.PlatformAuthProvider
import com.lin0721.linmusic.core.auth.PlatformAuthResult
import com.lin0721.linmusic.core.auth.PlatformExpiredAccountNotice
import com.lin0721.linmusic.core.auth.PlatformQrKey
import com.lin0721.linmusic.core.auth.PlatformQrPollResult
import com.lin0721.linmusic.core.auth.PlatformRiskChallenge
import com.lin0721.linmusic.core.auth.PlatformSessionRegistry
import com.lin0721.linmusic.core.auth.PlatformUserDetail
import com.lin0721.linmusic.core.auth.SessionMonitor
import com.lin0721.linmusic.core.auth.StoredAccount
import com.lin0721.linmusic.core.auth.UserPreferences
import com.lin0721.linmusic.core.auth.tempUserPreferences
import com.lin0721.linmusic.core.network.AppError
import com.lin0721.linmusic.core.network.AppString
import com.lin0721.linmusic.core.network.ResourceProvider
import com.lin0721.linmusic.core.network.toUserMessage
import com.lin0721.linmusic.core.platform.AccountId
import com.lin0721.linmusic.core.source.MusicPlatform
import com.lin0721.linmusic.core.source.provider.KugouSearchProvider
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File

@OptIn(ExperimentalCoroutinesApi::class)
class P0ReliabilityHardeningTest {

    private lateinit var prefs: UserPreferences
    private lateinit var dir: File
    private var now = 1_000_000_000L

    @Before
    fun setUp() {
        val (preferences, directory) = tempUserPreferences { now }
        prefs = preferences
        dir = directory
    }

    @After
    fun tearDown() {
        dir.deleteRecursively()
    }

    @Test
    fun runCatchingCancellable_rethrowsCancellationAndWrapsNormalError() {
        var caughtCancellation = false
        try {
            runCatchingCancellable { throw CancellationException("cancelled") }
        } catch (_: CancellationException) {
            caughtCancellation = true
        }
        assertTrue(caughtCancellation)

        val failure = runCatchingCancellable { throw IllegalStateException("boom") }
        assertTrue(failure.isFailure)
        assertTrue(failure.exceptionOrNull() is IllegalStateException)
    }

    @Test
    fun kugouRiskException_toUserMessage_returnsUnifiedRiskControlString() {
        val resourceProvider = object : ResourceProvider() {
            override fun getString(key: AppString): String =
                if (key == AppString.ErrorRiskControl) "当前操作触发风控，请稍后再试" else key.name
        }
        val challenge = PlatformRiskChallenge(
            platform = MusicPlatform.KUGOU,
            message = "raw internal risk detail"
        )
        val msg = KugouRiskException(challenge).toUserMessage(resourceProvider)
        assertEquals("当前操作触发风控，请稍后再试", msg)
    }

    @Test
    fun kugouSearchProvider_returnsEmptyList_whenDisabled() = runTest {
        val provider = KugouSearchProvider(isEnabled = { false })
        val result = provider.search("周杰伦", offset = 0, limit = 10)
        assertTrue(result.isEmpty())
    }

    @Test
    fun sessionMonitor_runtimeKugouUnauthorized_refreshesAndKeepsAccountOnSuccess() = runTest {
        val account = kugouAccount("1001", "old_token")
        val refreshed = kugouAccount("1001", "new_token")
        prefs.upsertPlatformAccount(account, activate = true)

        val provider = FakeKugouAuthProvider(
            refreshResult = Result.success(
                PlatformAuthResult(
                    platform = MusicPlatform.KUGOU,
                    accountId = refreshed.accountId,
                    account = refreshed
                )
            )
        )
        val registry = PlatformSessionRegistry(listOf(provider))
        val monitor = SessionMonitor(
            userPreferences = prefs,
            authRepository = FakeAuthRepository(prefs),
            nowMs = { now },
            platformSessionRegistry = registry,
            isKugouEnabled = { true }
        )

        monitor.handleKugouUnauthorized()

        val active = prefs.activeAccountOf(MusicPlatform.KUGOU).first()
        assertNotNull(active)
        assertEquals("new_token", active?.credentials?.get("token"))
        assertEquals(1, provider.refreshCalls)
    }

    @Test
    fun sessionMonitor_runtimeKugouUnauthorized_expiresAccountOnExplicitInvalidCode() = runTest {
        val account = kugouAccount("1001", "expired_token")
        prefs.upsertPlatformAccount(account, activate = true)

        val provider = FakeKugouAuthProvider(
            refreshResult = Result.failure(AppError.BizError(20018, "token invalid"))
        )
        val registry = PlatformSessionRegistry(listOf(provider))
        val monitor = SessionMonitor(
            userPreferences = prefs,
            authRepository = FakeAuthRepository(prefs),
            nowMs = { now },
            platformSessionRegistry = registry,
            isKugouEnabled = { true }
        )

        val notices = mutableListOf<PlatformExpiredAccountNotice>()
        val job = launch(UnconfinedTestDispatcher(testScheduler)) {
            monitor.platformExpiredNotices.collect { notices.add(it) }
        }

        monitor.handleKugouUnauthorized()
        job.cancel()

        assertNull(prefs.activeAccountOf(MusicPlatform.KUGOU).first())
        assertEquals(1, notices.size)
        assertEquals(MusicPlatform.KUGOU, notices.first().platform)
        assertEquals(account.accountId, notices.first().expiredAccount.accountId)
    }

    @Test
    fun sessionMonitor_runtimeKugouUnauthorized_keepsAccountOnNetworkError() = runTest {
        val account = kugouAccount("1001", "token")
        prefs.upsertPlatformAccount(account, activate = true)

        val provider = FakeKugouAuthProvider(
            refreshResult = Result.failure(AppError.NetworkError())
        )
        val registry = PlatformSessionRegistry(listOf(provider))
        val monitor = SessionMonitor(
            userPreferences = prefs,
            authRepository = FakeAuthRepository(prefs),
            nowMs = { now },
            platformSessionRegistry = registry,
            isKugouEnabled = { true }
        )

        monitor.handleKugouUnauthorized()

        assertNotNull(prefs.activeAccountOf(MusicPlatform.KUGOU).first())
    }

    @Test
    fun sessionMonitor_runtimeKugouUnauthorized_skipsWhenDisabled() = runTest {
        val account = kugouAccount("1001", "token")
        prefs.upsertPlatformAccount(account, activate = true)

        val provider = FakeKugouAuthProvider(
            refreshResult = Result.failure(AppError.BizError(20018, "token invalid"))
        )
        val registry = PlatformSessionRegistry(listOf(provider))
        val monitor = SessionMonitor(
            userPreferences = prefs,
            authRepository = FakeAuthRepository(prefs),
            nowMs = { now },
            platformSessionRegistry = registry,
            isKugouEnabled = { false }
        )

        monitor.handleKugouUnauthorized()

        assertEquals(0, provider.refreshCalls)
        assertNotNull(prefs.activeAccountOf(MusicPlatform.KUGOU).first())
    }

    private fun kugouAccount(uid: String, token: String): StoredAccount = StoredAccount(
        platform = MusicPlatform.KUGOU,
        uid = uid.toLong(),
        platformUid = uid,
        nickname = "KugouUser$uid",
        avatarUrl = "",
        cookies = "",
        credentials = mapOf("userid" to uid, "token" to token)
    )

    private class FakeKugouAuthProvider(
        private val refreshResult: Result<PlatformAuthResult>
    ) : PlatformAuthProvider {
        override val platform: MusicPlatform = MusicPlatform.KUGOU
        var refreshCalls = 0

        override suspend fun createQrLogin(): Result<PlatformQrKey> =
            Result.failure(UnsupportedOperationException())

        override suspend fun pollQrLogin(key: String): Result<PlatformQrPollResult> =
            Result.failure(UnsupportedOperationException())

        override suspend fun refreshSession(account: StoredAccount): Result<PlatformAuthResult> {
            refreshCalls++
            return refreshResult
        }

        override suspend fun fetchUserDetail(account: StoredAccount): Result<PlatformUserDetail> =
            Result.failure(UnsupportedOperationException())

        override suspend fun logout(account: StoredAccount): Result<Unit> = Result.success(Unit)
    }
}

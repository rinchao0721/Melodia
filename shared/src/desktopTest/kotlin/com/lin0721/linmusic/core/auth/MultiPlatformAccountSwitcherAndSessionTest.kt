package com.lin0721.linmusic.core.auth

import com.lin0721.linmusic.core.cache.MetadataCache
import com.lin0721.linmusic.core.network.AppError
import com.lin0721.linmusic.core.platform.AccountId
import com.lin0721.linmusic.core.source.MusicPlatform
import com.lin0721.linmusic.feature.podcast.ui.InMemoryPreferencesStore
import java.io.File
import java.nio.file.Files
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.builtins.serializer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class MultiPlatformAccountSwitcherAndSessionTest {

    private lateinit var cacheDir: File
    private lateinit var neteaseDir: File
    private var now = 1_000_000_000L

    @Before
    fun setUp() {
        cacheDir = Files.createTempDirectory("melodia-multi-cache-test").toFile()
        neteaseDir = Files.createTempDirectory("melodia-netease-prefs-test").toFile()
        now = 1_000_000_000L
    }

    @After
    fun tearDown() {
        cacheDir.deleteRecursively()
        neteaseDir.deleteRecursively()
    }

    @Test
    fun switchPlatformAccountKugouDoesNotAffectNeteaseOrStopPlaybackAndClearsOnlyKugouCache() = runTest {
        val prefs = UserPreferences(InMemoryPreferencesStore(), nowMs = { now })
        val netease = StoredAccount(uid = 1L, nickname = "网易号", cookies = "MUSIC_U=n1")
        val kugou1 = StoredAccount(
            uid = 101L,
            nickname = "酷狗一",
            platform = MusicPlatform.KUGOU,
            platformUid = "101",
            credentials = mapOf("token" to "t1")
        )
        val kugou2 = StoredAccount(
            uid = 102L,
            nickname = "酷狗二",
            platform = MusicPlatform.KUGOU,
            platformUid = "102",
            credentials = mapOf("token" to "t2")
        )
        prefs.upsertPlatformAccount(netease, activate = true)
        now += 10
        prefs.upsertPlatformAccount(kugou1, activate = true)
        now += 10
        prefs.upsertPlatformAccount(kugou2, activate = true)

        // 在本地构造网易与酷狗独立缓存目录，验证切换酷狗仅清理酷狗命名空间
        val cache = MetadataCache(cacheDir) { 1L }
        cache.write("playlist_netease", String.serializer(), "ok")
        val kugouScopeDir = File(cacheDir, "kugou").apply { mkdirs() }
        File(kugouScopeDir, "stale.json").writeText("{}")
        val kugou2Dir = File(cacheDir, "kugou_102").apply { mkdirs() }
        File(kugou2Dir, "stale.json").writeText("{}")

        val player = RecordingPlaybackController()
        val provider = RecordingKugouAuthProvider()
        val switcher = AccountSwitcher(
            userPreferences = prefs,
            authRepository = FakeAuthRepository(prefs),
            metadataCache = cache,
            playbackControllerProvider = { player },
            platformSessionRegistry = PlatformSessionRegistry(listOf(provider))
        )

        val events = mutableListOf<PlatformAccountChangeEvent>()
        val job = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            AccountEvents.platformSwitched.take(1).toList(events)
        }

        assertTrue(switcher.switchPlatformAccount(kugou1.accountId))
        job.join()

        // 酷狗切换到 101，网易仍为 1L，播放未被中断
        assertEquals(kugou1.accountId, prefs.activeAccountOf(MusicPlatform.KUGOU).first()?.accountId)
        assertEquals(1L, prefs.activeUid.first())
        assertEquals(0, player.stopAndClearCalls)

        // 网易缓存完好，酷狗命名空间已被清理
        assertEquals("ok", cache.read("playlist_netease", String.serializer()))
        assertFalse(kugouScopeDir.exists())
        assertFalse(kugou2Dir.exists())

        assertEquals(
            PlatformAccountChangeEvent(
                platform = MusicPlatform.KUGOU,
                previousAccountId = kugou2.accountId,
                currentAccountId = kugou1.accountId,
                reason = AccountSwitchReason.SWITCHED
            ),
            events.single()
        )
    }

    @Test
    fun logoutPlatformAccountKugouCallsProviderLogoutRemovesOnlyKugouAndKeepsNetease() = runTest {
        val prefs = UserPreferences(InMemoryPreferencesStore(), nowMs = { now })
        val netease = StoredAccount(uid = 1L, nickname = "网易号", cookies = "MUSIC_U=n1")
        val kugou = StoredAccount(
            uid = 201L,
            nickname = "酷狗号",
            platform = MusicPlatform.KUGOU,
            platformUid = "201",
            credentials = mapOf("token" to "t201")
        )
        prefs.upsertPlatformAccount(netease, activate = true)
        prefs.upsertPlatformAccount(kugou, activate = true)

        val player = RecordingPlaybackController()
        val provider = RecordingKugouAuthProvider()
        val switcher = AccountSwitcher(
            userPreferences = prefs,
            authRepository = FakeAuthRepository(prefs),
            playbackControllerProvider = { player },
            platformSessionRegistry = PlatformSessionRegistry(listOf(provider))
        )

        val events = mutableListOf<PlatformAccountChangeEvent>()
        val job = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            AccountEvents.platformSwitched.take(1).toList(events)
        }

        val next = switcher.logoutPlatformAccount(kugou.accountId)
        job.join()

        assertNull(next)
        assertEquals(listOf(kugou.accountId), provider.logoutAccounts.map { it.accountId })
        assertNull(prefs.activeAccountOf(MusicPlatform.KUGOU).first())
        assertTrue(prefs.accountsOf(MusicPlatform.KUGOU).first().isEmpty())
        // 网易云登录态与播放状态丝毫未受影响
        assertEquals(1L, prefs.activeUid.first())
        assertEquals(0, player.stopAndClearCalls)
        assertEquals(AccountSwitchReason.LOGGED_OUT, events.single().reason)
        assertEquals(MusicPlatform.KUGOU, events.single().platform)
    }

    @Test
    fun verifyAtStartupRefreshesKugouAfterSixHoursAndUpdatesUserDetail() = runTest {
        val prefs = UserPreferences(InMemoryPreferencesStore(), nowMs = { now })
        val netease = StoredAccount(uid = 1L, nickname = "网易", cookies = "MUSIC_U=1", lastLoginRefreshAt = now)
        val sixHoursMs = 6L * 60 * 60 * 1000
        val kugou = StoredAccount(
            uid = 301L,
            nickname = "旧昵称",
            platform = MusicPlatform.KUGOU,
            platformUid = "301",
            credentials = mapOf("token" to "old_tok"),
            lastLoginRefreshAt = now - sixHoursMs - 1_000L
        )
        prefs.upsertPlatformAccount(netease, activate = true)
        prefs.upsertPlatformAccount(kugou, activate = true)

        val neteaseRepo = FakeAuthRepository(prefs).apply {
            accountResults = listOf(Result.success(loggedInAccount(uid = 1L, nickname = "网易")))
        }
        val refreshedKugou = kugou.copy(
            credentials = mapOf("token" to "fresh_tok"),
            lastLoginRefreshAt = now
        )
        val kugouProvider = RecordingKugouAuthProvider().apply {
            refreshResult = Result.success(
                PlatformAuthResult(
                    platform = MusicPlatform.KUGOU,
                    accountId = kugou.accountId,
                    nickname = "旧昵称",
                    credentials = mapOf("token" to "fresh_tok"),
                    account = refreshedKugou
                )
            )
            detailResult = Result.success(
                PlatformUserDetail(
                    accountId = kugou.accountId,
                    nickname = "酷狗新昵称",
                    avatarUrl = "https://img/new.jpg",
                    vipLabel = "豪华 VIP",
                    isVip = true
                )
            )
        }
        val monitor = SessionMonitor(
            userPreferences = prefs,
            authRepository = neteaseRepo,
            nowMs = { now },
            platformSessionRegistry = PlatformSessionRegistry(listOf(kugouProvider))
        )

        monitor.verifyAtStartup {}

        assertEquals(1, kugouProvider.refreshCalls)
        assertEquals(1, kugouProvider.detailCalls)
        val storedKugou = prefs.activeAccountOf(MusicPlatform.KUGOU).first()
        assertNotNull(storedKugou)
        assertEquals("酷狗新昵称", storedKugou?.nickname)
        assertEquals("豪华 VIP", storedKugou?.vipLabel)
        assertEquals(1L, prefs.activeUid.first())
    }

    @Test
    fun verifyAtStartupEvictsOnlyKugouOnExplicitTokenInvalidError() = runTest {
        val prefs = UserPreferences(InMemoryPreferencesStore(), nowMs = { now })
        val netease = StoredAccount(uid = 1L, nickname = "网易", cookies = "MUSIC_U=1", lastLoginRefreshAt = now)
        val kugou = StoredAccount(
            uid = 401L,
            nickname = "过期酷狗",
            platform = MusicPlatform.KUGOU,
            platformUid = "401",
            credentials = mapOf("token" to "expired_tok"),
            lastLoginRefreshAt = 0L
        )
        prefs.upsertPlatformAccount(netease, activate = true)
        prefs.upsertPlatformAccount(kugou, activate = true)

        val player = RecordingPlaybackController()
        val neteaseRepo = FakeAuthRepository(prefs).apply {
            accountResults = listOf(Result.success(loggedInAccount(uid = 1L, nickname = "网易")))
        }
        val kugouProvider = RecordingKugouAuthProvider().apply {
            refreshResult = Result.failure(AppError.BizError(code = 20018, rawMsg = "token invalid"))
        }
        val monitor = SessionMonitor(
            userPreferences = prefs,
            authRepository = neteaseRepo,
            playbackControllerProvider = { player },
            nowMs = { now },
            platformSessionRegistry = PlatformSessionRegistry(listOf(kugouProvider))
        )

        val notices = mutableListOf<PlatformExpiredAccountNotice>()
        val job = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            monitor.platformExpiredNotices.take(1).toList(notices)
        }

        monitor.verifyAtStartup {}
        job.join()

        assertNull(prefs.activeAccountOf(MusicPlatform.KUGOU).first())
        assertEquals(1L, prefs.activeUid.first())
        assertEquals(0, player.stopAndClearCalls)
        assertEquals(MusicPlatform.KUGOU, notices.single().platform)
        assertEquals(kugou.accountId, notices.single().expiredAccount.accountId)
        assertNull(notices.single().nextAccount)
    }

    @Test
    fun verifyAtStartupKeepsKugouAccountOnNetworkError() = runTest {
        val prefs = UserPreferences(InMemoryPreferencesStore(), nowMs = { now })
        val kugou = StoredAccount(
            uid = 501L,
            nickname = "断网酷狗",
            platform = MusicPlatform.KUGOU,
            platformUid = "501",
            credentials = mapOf("token" to "tok"),
            lastLoginRefreshAt = 0L
        )
        prefs.upsertPlatformAccount(kugou, activate = true)

        val kugouProvider = RecordingKugouAuthProvider().apply {
            refreshResult = Result.failure(AppError.NetworkError)
        }
        val monitor = SessionMonitor(
            userPreferences = prefs,
            authRepository = FakeAuthRepository(prefs),
            nowMs = { now },
            platformSessionRegistry = PlatformSessionRegistry(listOf(kugouProvider))
        )

        monitor.verifyAtStartup {}

        assertEquals(kugou.accountId, prefs.activeAccountOf(MusicPlatform.KUGOU).first()?.accountId)
    }

    @Test
    fun verifyAtStartupSkipsKugouWhenKugouPlatformDisabled() = runTest {
        val prefs = UserPreferences(InMemoryPreferencesStore(), nowMs = { now })
        val kugou = StoredAccount(
            uid = 601L,
            nickname = "酷狗号",
            platform = MusicPlatform.KUGOU,
            platformUid = "601",
            credentials = mapOf("token" to "tok"),
            lastLoginRefreshAt = 0L
        )
        prefs.upsertPlatformAccount(kugou, activate = true)

        val kugouProvider = RecordingKugouAuthProvider().apply {
            refreshResult = Result.failure(AppError.BizError(code = 20018, rawMsg = "token invalid"))
        }
        val monitor = SessionMonitor(
            userPreferences = prefs,
            authRepository = FakeAuthRepository(prefs),
            nowMs = { now },
            platformSessionRegistry = PlatformSessionRegistry(listOf(kugouProvider)),
            isKugouEnabled = { false }
        )

        monitor.verifyAtStartup {}

        assertEquals(0, kugouProvider.refreshCalls)
        assertEquals(0, kugouProvider.detailCalls)
        assertEquals(kugou.accountId, prefs.activeAccountOf(MusicPlatform.KUGOU).first()?.accountId)
    }

    @Test
    fun switchNeteaseClearsOnlyNeteaseCacheScopesAndKeepsKugouCache() = runTest {
        val prefs = UserPreferences(InMemoryPreferencesStore(), nowMs = { now })
        val netease1 = StoredAccount(uid = 1L, nickname = "网易一", cookies = "MUSIC_U=n1")
        val netease2 = StoredAccount(uid = 2L, nickname = "网易二", cookies = "MUSIC_U=n2")
        val kugou = StoredAccount(
            uid = 101L,
            nickname = "酷狗一",
            platform = MusicPlatform.KUGOU,
            platformUid = "101",
            credentials = mapOf("token" to "t1")
        )
        prefs.upsertAccount(netease1)
        now += 10
        prefs.upsertAccount(netease2)
        now += 10
        prefs.upsertPlatformAccount(kugou, activate = true)

        val cache = MetadataCache(cacheDir) { prefs.activeUid.first() ?: 0L }
        val neteaseLegacyDir = File(cacheDir, "2").apply { mkdirs() }
        File(neteaseLegacyDir, "a.json").writeText("{}")
        val neteaseScopedDir = File(cacheDir, "netease_2").apply { mkdirs() }
        File(neteaseScopedDir, "b.json").writeText("{}")
        val kugouDir = File(cacheDir, "kugou").apply { mkdirs() }
        File(kugouDir, "keep.json").writeText("{}")
        val kugouScopedDir = File(cacheDir, "kugou_101").apply { mkdirs() }
        File(kugouScopedDir, "keep.json").writeText("{}")

        val player = RecordingPlaybackController()
        val switcher = AccountSwitcher(
            userPreferences = prefs,
            authRepository = FakeAuthRepository(prefs).apply {
                accountResults = listOf(Result.success(loggedInAccount(uid = 1L, nickname = "网易一")))
            },
            metadataCache = cache,
            playbackControllerProvider = { player }
        )

        assertTrue(switcher.switchTo(1L))
        assertFalse(neteaseLegacyDir.exists())
        assertFalse(neteaseScopedDir.exists())
        assertTrue(File(kugouDir, "keep.json").exists())
        assertTrue(File(kugouScopedDir, "keep.json").exists())
    }
}

private class RecordingKugouAuthProvider : PlatformAuthProvider {
    override val platform: MusicPlatform = MusicPlatform.KUGOU

    var refreshResult: Result<PlatformAuthResult> = Result.failure(AppError.NetworkError)
    var detailResult: Result<PlatformUserDetail> = Result.failure(AppError.NetworkError)
    var refreshCalls = 0
    var detailCalls = 0
    val logoutAccounts = mutableListOf<StoredAccount>()

    override suspend fun createQrLogin(): Result<PlatformQrKey> =
        Result.failure(UnsupportedOperationException())

    override suspend fun pollQrLogin(key: String): Result<PlatformQrPollResult> =
        Result.failure(UnsupportedOperationException())

    override suspend fun refreshSession(account: StoredAccount): Result<PlatformAuthResult> {
        refreshCalls++
        return refreshResult
    }

    override suspend fun fetchUserDetail(account: StoredAccount): Result<PlatformUserDetail> {
        detailCalls++
        return detailResult
    }

    override suspend fun logout(account: StoredAccount): Result<Unit> {
        logoutAccounts.add(account)
        return Result.success(Unit)
    }
}

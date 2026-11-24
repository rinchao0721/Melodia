package com.lin0721.linmusic.core.auth

import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.lin0721.linmusic.core.platform.AccountId
import com.lin0721.linmusic.core.source.MusicPlatform
import com.lin0721.linmusic.feature.podcast.ui.InMemoryPreferencesStore
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class UnifiedAccountSwitchTest {

    private var now = 1_000L

    private fun netease(uid: Long) = StoredAccount(uid = uid, nickname = "网易$uid", cookies = "MUSIC_U=$uid")

    private fun kugou(id: Long) = StoredAccount(
        uid = id,
        nickname = "酷狗$id",
        platform = MusicPlatform.KUGOU,
        platformUid = id.toString(),
        credentials = mapOf("token" to "t$id")
    )

    private suspend fun prefsWith(vararg accounts: StoredAccount): UserPreferences {
        val prefs = UserPreferences(InMemoryPreferencesStore(), nowMs = { now })
        accounts.forEach {
            now += 10
            prefs.upsertPlatformAccount(it, activate = true)
        }
        return prefs
    }

    private fun switcher(prefs: UserPreferences, player: RecordingPlaybackController) = AccountSwitcher(
        userPreferences = prefs,
        authRepository = FakeAuthRepository(prefs),
        playbackControllerProvider = { player }
    )

    @Test
    fun crossPlatformSwitchClearsQueueAndEmitsPlatformEventOnce() = runTest {
        val prefs = prefsWith(netease(1L), kugou(101L))
        prefs.setCurrentPlatform(MusicPlatform.NETEASE)
        val player = RecordingPlaybackController()
        val switcher = switcher(prefs, player)

        val platformEvents = mutableListOf<PlatformAccountChangeEvent>()
        val neteaseEvents = mutableListOf<AccountSwitchEvent>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            AccountEvents.platformSwitched.collect { platformEvents += it }
        }
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            AccountEvents.switched.collect { neteaseEvents += it }
        }

        assertTrue(switcher.switchToAccount(AccountId.kugou("101")))

        assertEquals(1, player.stopAndClearCalls)
        assertEquals(MusicPlatform.KUGOU, prefs.currentPlatform.first())
        assertEquals(AccountId.kugou("101"), prefs.currentAccountId.first())
        assertEquals(1L, prefs.activeUid.first())
        assertEquals(
            listOf(
                PlatformAccountChangeEvent(
                    platform = MusicPlatform.KUGOU,
                    previousAccountId = AccountId.kugou("101"),
                    currentAccountId = AccountId.kugou("101"),
                    reason = AccountSwitchReason.SWITCHED
                )
            ),
            platformEvents
        )
        assertTrue(neteaseEvents.isEmpty())

        assertFalse(switcher.switchToAccount(AccountId.kugou("101")))
        assertEquals(1, player.stopAndClearCalls)
    }

    @Test
    fun samePlatformSwitchKeepsLegacyBehavior() = runTest {
        val prefs = prefsWith(netease(1L), netease(2L))
        val player = RecordingPlaybackController()
        val switcher = switcher(prefs, player)

        val neteaseEvents = mutableListOf<AccountSwitchEvent>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            AccountEvents.switched.collect { neteaseEvents += it }
        }

        assertTrue(switcher.switchTo(1L))

        assertEquals(1, player.stopAndClearCalls)
        assertEquals(1L, prefs.activeUid.first())
        assertEquals(MusicPlatform.NETEASE, prefs.currentPlatform.first())
        assertEquals(
            listOf(AccountSwitchEvent(previousUid = 2L, currentUid = 1L, reason = AccountSwitchReason.SWITCHED)),
            neteaseEvents
        )
        assertFalse(switcher.switchTo(1L))
    }

    @Test
    fun logoutFallsBackSamePlatformThenOtherPlatformThenGuest() = runTest {
        val prefs = prefsWith(netease(1L), kugou(101L), kugou(102L))
        prefs.setCurrentPlatform(MusicPlatform.KUGOU)
        val player = RecordingPlaybackController()
        val switcher = switcher(prefs, player)

        assertEquals(AccountId.kugou("101"), switcher.logout(AccountId.kugou("102")))
        assertEquals(MusicPlatform.KUGOU, prefs.currentPlatform.first())

        assertEquals(AccountId.netease(1L), switcher.logout(AccountId.kugou("101")))
        assertEquals(MusicPlatform.NETEASE, prefs.currentPlatform.first())
        assertEquals(1L, prefs.activeUid.first())

        assertNull(switcher.logout(AccountId.netease(1L)))
        assertEquals(MusicPlatform.NETEASE, prefs.currentPlatform.first())
        assertNull(prefs.currentAccountId.first())
        assertTrue(prefs.allAccounts.first().isEmpty())
        assertEquals(3, player.stopAndClearCalls)
    }

    @Test
    fun logoutBackgroundAccountKeepsCurrentPlatformAndQueue() = runTest {
        val prefs = prefsWith(netease(1L), kugou(101L))
        prefs.setCurrentPlatform(MusicPlatform.NETEASE)
        val player = RecordingPlaybackController()

        assertEquals(AccountId.netease(1L), switcher(prefs, player).logout(AccountId.kugou("101")))
        assertEquals(MusicPlatform.NETEASE, prefs.currentPlatform.first())
        assertEquals(0, player.stopAndClearCalls)
    }

    @Test
    fun browseAsGuestKeepsAccountsAndDropsNeteaseCookies() = runTest {
        val prefs = prefsWith(netease(1L), kugou(101L))
        prefs.setCurrentPlatform(MusicPlatform.KUGOU)
        val player = RecordingPlaybackController()
        val switcher = switcher(prefs, player)

        assertTrue(switcher.browseAsGuest())

        assertEquals(1, player.stopAndClearCalls)
        assertEquals(MusicPlatform.NETEASE, prefs.currentPlatform.first())
        assertNull(prefs.currentAccountId.first())
        assertNull(prefs.activeUid.first())
        assertNull(prefs.cookies.first())
        assertEquals(2, prefs.allAccounts.first().size)
        assertEquals(AccountId.kugou("101"), prefs.activeAccountOf(MusicPlatform.KUGOU).first()?.accountId)
        assertFalse(switcher.browseAsGuest())

        // 游客状态下移除其他网易账号不得自动激活
        prefs.upsertPlatformAccount(netease(2L), activate = false)
        prefs.deactivatePlatform(MusicPlatform.NETEASE)
        prefs.removePlatformAccount(AccountId.netease(2L))
        assertNull(prefs.activeUid.first())

        assertTrue(switcher.switchToAccount(AccountId.netease(1L)))
        assertEquals(1L, prefs.activeUid.first())
        assertEquals("MUSIC_U=1", prefs.cookies.first())
    }

    @Test
    fun loginOnOtherPlatformMovesCurrentPlatform() = runTest {
        val prefs = prefsWith(netease(1L))
        val player = RecordingPlaybackController()
        val switcher = switcher(prefs, player)
        val account = kugou(101L)
        prefs.upsertPlatformAccount(account, activate = true)

        switcher.onPlatformAccountLoggedIn(account, previousAccountId = null)

        assertEquals(MusicPlatform.KUGOU, prefs.currentPlatform.first())
        assertEquals(1, player.stopAndClearCalls)

        switcher.onAccountActivatedAfterLogin(netease(1L).toUserProfile(), previousUid = 1L)
        assertEquals(MusicPlatform.NETEASE, prefs.currentPlatform.first())
        assertEquals(2, player.stopAndClearCalls)
    }

    @Test
    fun allAccountsSortedByLastUsedDescending() = runTest {
        val prefs = prefsWith(netease(1L), kugou(101L), netease(2L))
        assertEquals(
            listOf(AccountId.netease(2L), AccountId.kugou("101"), AccountId.netease(1L)),
            prefs.allAccounts.first().map { it.accountId }
        )
    }

    // ─── 升级迁移 ───

    private val json = Json

    private suspend fun legacyStore(
        accounts: List<StoredAccount>,
        activeMap: Map<String, String>?,
        browse: String? = null,
        activeUid: Long? = null
    ): InMemoryPreferencesStore {
        val store = InMemoryPreferencesStore()
        store.edit { prefs ->
            if (accounts.isNotEmpty()) prefs[stringPreferencesKey("accounts_json")] = json.encodeToString(accounts)
            if (activeMap != null) {
                prefs[stringPreferencesKey("active_accounts_by_platform")] = json.encodeToString(activeMap)
            }
            if (browse != null) prefs[stringPreferencesKey("current_browse_platform")] = browse
            if (activeUid != null) prefs[longPreferencesKey("active_uid")] = activeUid
        }
        return store
    }

    @Test
    fun migrationNeteaseUser() = runTest {
        val n = netease(1L).copy(platformUid = "1", lastUsedAt = 5L)
        val prefs = UserPreferences(legacyStore(listOf(n), mapOf("netease" to n.accountId.canonical), activeUid = 1L))

        assertTrue(prefs.onboardingCompleted.first())
        assertEquals(MusicPlatform.NETEASE, prefs.currentPlatform.first())
        assertEquals(AccountId.netease(1L), prefs.currentAccountId.first())
    }

    @Test
    fun migrationKugouOnlyUser() = runTest {
        val k = kugou(101L).copy(lastUsedAt = 5L)
        val prefs = UserPreferences(legacyStore(listOf(k), mapOf("kugou" to k.accountId.canonical)))

        assertTrue(prefs.onboardingCompleted.first())
        assertEquals(MusicPlatform.KUGOU, prefs.currentPlatform.first())
        assertEquals(AccountId.kugou("101"), prefs.currentAccountId.first())
    }

    @Test
    fun migrationBothPlatformsWithBrowsePlatformLackingAccount() = runTest {
        val n = netease(1L).copy(platformUid = "1", lastUsedAt = 5L)
        val k = kugou(101L).copy(lastUsedAt = 9L)
        // 旧浏览平台指向一个无账号的平台
        val prefs = UserPreferences(
            legacyStore(
                listOf(n, k),
                mapOf("netease" to n.accountId.canonical, "kugou" to k.accountId.canonical),
                browse = "qq",
                activeUid = 1L
            )
        )

        assertTrue(prefs.onboardingCompleted.first())
        assertEquals(MusicPlatform.KUGOU, prefs.currentPlatform.first())
    }

    @Test
    fun migrationBrowseKugouWithoutKugouAccountFallsToNetease() = runTest {
        val n = netease(1L).copy(platformUid = "1", lastUsedAt = 5L)
        val prefs = UserPreferences(
            legacyStore(listOf(n), mapOf("netease" to n.accountId.canonical), browse = "kugou", activeUid = 1L)
        )

        assertEquals(MusicPlatform.NETEASE, prefs.currentPlatform.first())
    }

    @Test
    fun migrationExistingUserWithoutAccountsBecomesGuest() = runTest {
        val prefs = UserPreferences(InMemoryPreferencesStore(), legacyUsageProbe = { true })

        assertTrue(prefs.onboardingCompleted.first())
        assertEquals(MusicPlatform.NETEASE, prefs.currentPlatform.first())
        assertNull(prefs.currentAccountId.first())
    }

    @Test
    fun migrationExistingUserDetectedByOwnPreferences() = runTest {
        val prefs = UserPreferences(legacyStore(emptyList(), activeMap = null, browse = "kugou"))

        assertTrue(prefs.onboardingCompleted.first())
        assertEquals(MusicPlatform.NETEASE, prefs.currentPlatform.first())
    }

    @Test
    fun freshInstallShowsOnboardingUntilCompleted() = runTest {
        val prefs = UserPreferences(InMemoryPreferencesStore())

        assertFalse(prefs.onboardingCompleted.first())
        assertEquals(MusicPlatform.NETEASE, prefs.currentPlatform.first())

        // 首次启动页中登录不应被误判为老用户
        prefs.upsertPlatformAccount(kugou(101L), activate = true)
        assertFalse(prefs.onboardingCompleted.first())

        prefs.setOnboardingCompleted()
        assertTrue(prefs.onboardingCompleted.first())
    }
}

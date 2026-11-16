package com.lin0721.linmusic.core.auth

import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import com.lin0721.linmusic.feature.podcast.ui.InMemoryPreferencesStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UserPreferencesTest {

    private var clockMs = 1_000L

    private fun newPrefs(store: InMemoryPreferencesStore = InMemoryPreferencesStore()) =
        UserPreferences(store, nowMs = { clockMs })

    @Test
    fun legacyKeysMigrateAtomicallyIntoSingleActiveAccountAndRemoveLegacyKeys() = runTest {
        val store = InMemoryPreferencesStore()
        store.edit { raw ->
            raw[stringPreferencesKey("user_profile_json")] = Json.encodeToString(
                UserProfile(uid = 101L, nickname = "旧版用户", avatarUrl = "https://a.png")
            )
            raw[stringPreferencesKey("user_cookies")] = "MUSIC_U=legacy_token"
            raw[longPreferencesKey("last_login_refresh_at")] = 888_888L
            raw[stringSetPreferencesKey("blocked_artist_ids")] = setOf("11", "22")
        }

        val prefs = newPrefs(store)
        assertEquals(101L, prefs.activeUid.first())
        assertEquals("旧版用户", prefs.userProfile.first()?.nickname)
        assertEquals("MUSIC_U=legacy_token", prefs.cookies.first())
        assertEquals(888_888L, prefs.lastLoginRefreshAt.first())
        assertEquals(setOf(11L, 22L), prefs.blockedArtistIds.first())
        assertEquals(listOf(UserProfile(101L, "旧版用户", "https://a.png")), prefs.accounts.first())

        val snapshot = store.data.first()
        assertFalse(snapshot.contains(stringPreferencesKey("user_profile_json")))
        assertFalse(snapshot.contains(stringPreferencesKey("user_cookies")))
        assertFalse(snapshot.contains(longPreferencesKey("last_login_refresh_at")))
        assertFalse(snapshot.contains(stringSetPreferencesKey("blocked_artist_ids")))
        assertTrue(snapshot.contains(stringPreferencesKey("accounts_json")))
        assertEquals(101L, snapshot[longPreferencesKey("active_uid")])
        assertEquals(setOf("11", "22"), snapshot[stringSetPreferencesKey("blocked_artist_ids_101")])
    }

    @Test
    fun enforcesMaxFiveAccountsWhileAllowingExistingAccountUpdate() = runTest {
        val prefs = newPrefs()
        for (i in 1L..5L) {
            clockMs = i * 100L
            val res = prefs.upsertAccount(
                StoredAccount(uid = i, nickname = "User$i", cookies = "MUSIC_U=c$i")
            )
            assertTrue(res is UpsertAccountResult.Added)
        }
        assertEquals(5, prefs.accounts.first().size)
        assertEquals(5L, prefs.activeUid.first())

        // 超出 5 个上限时拒绝新增且保留原活跃账户
        val overflow = prefs.upsertAccount(
            StoredAccount(uid = 6L, nickname = "User6", cookies = "MUSIC_U=c6")
        )
        assertEquals(UpsertAccountResult.LimitReached, overflow)
        assertEquals(5, prefs.accounts.first().size)
        assertEquals(5L, prefs.activeUid.first())

        // 已存在 uid 仍允许更新资料和 Cookie，不触发上限
        clockMs = 999L
        val updated = prefs.upsertAccount(
            StoredAccount(uid = 2L, nickname = "User2-New", cookies = "MUSIC_U=c2_new")
        )
        assertTrue(updated is UpsertAccountResult.Updated)
        assertEquals(2L, prefs.activeUid.first())
        assertEquals("User2-New", prefs.userProfile.first()?.nickname)
        assertEquals("MUSIC_U=c2_new", prefs.cookies.first())
    }

    @Test
    fun switchToUpdatesActiveUidAndLastUsedAt() = runTest {
        val prefs = newPrefs()
        clockMs = 100L
        prefs.upsertAccount(StoredAccount(uid = 1L, nickname = "A", cookies = "MUSIC_U=a"))
        clockMs = 200L
        prefs.upsertAccount(StoredAccount(uid = 2L, nickname = "B", cookies = "MUSIC_U=b"))

        assertFalse(prefs.switchTo(999L))
        assertEquals(2L, prefs.activeUid.first())

        clockMs = 500L
        assertTrue(prefs.switchTo(1L))
        assertEquals(1L, prefs.activeUid.first())
        assertEquals("MUSIC_U=a", prefs.cookies.first())
        val acct1 = prefs.storedAccounts.first().first { it.uid == 1L }
        assertEquals(500L, acct1.lastUsedAt)
    }

    @Test
    fun removeAccountFallsBackToMostRecentlyUsedOrNullWhenEmpty() = runTest {
        val prefs = newPrefs()
        clockMs = 100L
        prefs.upsertAccount(StoredAccount(uid = 1L, nickname = "Old", cookies = "MUSIC_U=1"))
        clockMs = 300L
        prefs.upsertAccount(StoredAccount(uid = 2L, nickname = "Recent", cookies = "MUSIC_U=2"))
        clockMs = 500L
        prefs.upsertAccount(StoredAccount(uid = 3L, nickname = "Active", cookies = "MUSIC_U=3"))

        // 移除非当前活跃账户不影响当前 activeUid
        assertEquals(3L, prefs.removeAccount(1L))
        assertEquals(3L, prefs.activeUid.first())

        // 移除当前活跃账户应切到 lastUsedAt 最近的剩余账户（uid=2）
        assertEquals(2L, prefs.removeAccount(3L))
        assertEquals(2L, prefs.activeUid.first())
        assertEquals("MUSIC_U=2", prefs.cookies.first())

        // 移除最后一个账户后回到未登录态
        assertNull(prefs.removeAccount(2L))
        assertNull(prefs.activeUid.first())
        assertNull(prefs.userProfile.first())
        assertNull(prefs.cookies.first())
    }

    @Test
    fun blockedArtistIdsIsolatesPerAccountAndAnonymousSession() = runTest {
        val prefs = newPrefs()
        // 未登录态屏蔽艺人 10
        prefs.toggleBlockArtist(10L)
        assertEquals(setOf(10L), prefs.blockedArtistIds.first())

        // 登录账户 1 后只看到迁移或账户 1 的屏蔽集；先分别写入后再切回验证隔离
        prefs.upsertAccount(StoredAccount(uid = 1L, nickname = "A", cookies = "MUSIC_U=a"))
        // 首次写入登录后，未登录全局旧键已清理但 _anon 仍保留匿名会话的数据
        assertEquals(emptySet<Long>(), prefs.blockedArtistIds.first())
        prefs.toggleBlockArtist(100L)
        assertEquals(setOf(100L), prefs.blockedArtistIds.first())

        prefs.upsertAccount(StoredAccount(uid = 2L, nickname = "B", cookies = "MUSIC_U=b"))
        assertEquals(emptySet<Long>(), prefs.blockedArtistIds.first())
        prefs.toggleBlockArtist(200L)
        assertEquals(setOf(200L), prefs.blockedArtistIds.first())

        prefs.switchTo(1L)
        assertEquals(setOf(100L), prefs.blockedArtistIds.first())

        // 退出所有账户后切回 _anon 匿名键
        prefs.removeAccount(1L)
        prefs.removeAccount(2L)
        assertEquals(setOf(10L), prefs.blockedArtistIds.first())
    }

    @Test
    fun legacySaveCookiesAndProfileApisOperateOnActiveAccount() = runTest {
        val prefs = newPrefs()
        prefs.saveCookies("MUSIC_U=staged")
        assertEquals("MUSIC_U=staged", prefs.cookies.first())

        prefs.saveUserProfile(UserProfile(uid = 42L, nickname = "Compat", avatarUrl = ""))
        assertEquals(42L, prefs.activeUid.first())
        assertEquals("MUSIC_U=staged", prefs.cookies.first())

        prefs.saveLastLoginRefreshAt(777L)
        assertEquals(777L, prefs.lastLoginRefreshAt.first())

        prefs.clearUserProfile()
        assertNull(prefs.activeUid.first())
        assertNull(prefs.userProfile.first())
        assertNull(prefs.cookies.first())
    }
}

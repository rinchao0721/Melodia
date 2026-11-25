package com.lin0721.linmusic.core.auth

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.lin0721.linmusic.core.platform.AccountId
import com.lin0721.linmusic.core.source.MusicPlatform
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import java.io.File
import kotlin.io.path.createTempDirectory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalModeEntryTest {

    private fun newPrefs(): UserPreferences {
        val dir = createTempDirectory("local_mode_test_").toFile()
        dir.deleteOnExit()
        val dataStore = PreferenceDataStoreFactory.create(
            produceFile = { File(dir, "prefs.preferences_pb") }
        )
        return UserPreferences(dataStore)
    }

    private fun account(platform: MusicPlatform, uid: Long): StoredAccount = StoredAccount(
        uid = uid,
        nickname = "${platform.key}_$uid",
        platform = platform,
        platformUid = uid.toString(),
        cookies = "MUSIC_U=ok"
    )

    @Test
    fun enterLocalModeKeepsAccountsSetsLocalAndEmitsEvent() = runTest {
        val prefs = newPrefs()
        val netease = account(MusicPlatform.NETEASE, 101L)
        val kugou = account(MusicPlatform.KUGOU, 202L)
        prefs.upsertPlatformAccount(netease, activate = true)
        prefs.upsertPlatformAccount(kugou, activate = true)
        prefs.setCurrentPlatform(MusicPlatform.NETEASE)

        val switcher = AccountSwitcher(
            userPreferences = prefs,
            authRepository = FakeAuthRepository()
        )
        var sawLocalEvent = false
        val collectJob = launch {
            AccountEvents.platformSwitched.collect {
                if (it.platform == MusicPlatform.LOCAL && it.currentAccountId == null) {
                    sawLocalEvent = true
                }
            }
        }
        testScheduler.advanceUntilIdle()

        assertTrue(switcher.enterLocalMode())
        testScheduler.advanceUntilIdle()

        assertEquals(MusicPlatform.LOCAL, prefs.currentPlatform.first())
        assertEquals(2, prefs.allAccounts.first().size)
        assertEquals(netease.accountId, prefs.activeAccountOf(MusicPlatform.NETEASE).first()?.accountId)
        assertEquals(kugou.accountId, prefs.activeAccountOf(MusicPlatform.KUGOU).first()?.accountId)
        assertTrue(sawLocalEvent)
        assertFalse(switcher.enterLocalMode())

        collectJob.cancel()
    }

    @Test
    fun switchToAccountFromLocalRestoresPlatform() = runTest {
        val prefs = newPrefs()
        val netease = account(MusicPlatform.NETEASE, 101L)
        prefs.upsertPlatformAccount(netease, activate = true)
        val switcher = AccountSwitcher(
            userPreferences = prefs,
            authRepository = FakeAuthRepository()
        )

        assertTrue(switcher.enterLocalMode())
        assertEquals(MusicPlatform.LOCAL, prefs.currentPlatform.first())

        assertTrue(switcher.switchToAccount(AccountId.netease(101L)))
        assertEquals(MusicPlatform.NETEASE, prefs.currentPlatform.first())
    }
}

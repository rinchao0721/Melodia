package com.lin0721.linmusic.desktop.ui.shell

import com.lin0721.linmusic.core.auth.StoredAccount
import com.lin0721.linmusic.core.auth.UserPreferences
import com.lin0721.linmusic.core.platform.AccountId
import com.lin0721.linmusic.core.source.MusicPlatform
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AccountMenuModelTest {

    private val netease = StoredAccount(uid = 1L, nickname = "网易号", lastUsedAt = 3)
    private val kugou = StoredAccount(uid = 0L, nickname = "", platform = MusicPlatform.KUGOU, platformUid = "k9", lastUsedAt = 2)

    @Test
    fun guestEntryOnTopWhenNoActiveNetease() {
        val entries = buildAccountMenuEntries(listOf(kugou), currentAccountId = kugou.accountId, hasActiveNetease = false, kugouEnabled = true)
        assertEquals(2, entries.size)
        assertTrue(entries[0].isGuest)
        assertEquals("网易云 · 游客", entries[0].displayName)
        assertFalse(entries[0].isCurrent)
        assertTrue(entries[1].isCurrent)
        assertEquals("酷狗音乐用户 k9", entries[1].displayName)
    }

    @Test
    fun guestIsCurrentWhenCurrentAccountIdNull() {
        val entries = buildAccountMenuEntries(listOf(kugou), currentAccountId = null, hasActiveNetease = false, kugouEnabled = true)
        assertTrue(entries.first { it.isGuest }.isCurrent)
        assertNull(entries.first().accountId)
    }

    @Test
    fun noGuestEntryWithActiveNeteaseAndKugouHiddenWhenDisabled() {
        val entries = buildAccountMenuEntries(listOf(netease, kugou), AccountId.netease(1L), hasActiveNetease = true, kugouEnabled = false)
        assertEquals(listOf(AccountId.netease(1L)), entries.map { it.accountId })
        assertTrue(entries.single().isCurrent)
    }

    @Test
    fun confirmOnlyAcrossPlatforms() {
        val entries = buildAccountMenuEntries(listOf(netease, kugou), AccountId.netease(1L), hasActiveNetease = true, kugouEnabled = true)
        val kugouEntry = entries.first { it.platform == MusicPlatform.KUGOU }
        val neteaseEntry = entries.first { it.platform == MusicPlatform.NETEASE }
        assertTrue(needsSwitchConfirm(kugouEntry, MusicPlatform.NETEASE))
        assertFalse(needsSwitchConfirm(neteaseEntry, MusicPlatform.NETEASE))
        assertFalse(needsSwitchConfirm(neteaseEntry.copy(isCurrent = false), MusicPlatform.NETEASE))
    }

    @Test
    fun addablePlatformsRespectLimitAndToggle() {
        val full = (1..UserPreferences.MAX_ACCOUNTS).map { StoredAccount(uid = it.toLong()) }
        assertEquals(listOf(MusicPlatform.KUGOU), addableAccountPlatforms(full, kugouEnabled = true))
        assertEquals(listOf(MusicPlatform.NETEASE), addableAccountPlatforms(emptyList(), kugouEnabled = false))
    }
}

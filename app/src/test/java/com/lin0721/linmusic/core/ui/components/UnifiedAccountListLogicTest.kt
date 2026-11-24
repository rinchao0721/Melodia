package com.lin0721.linmusic.core.ui.components

import com.lin0721.linmusic.core.auth.StoredAccount
import com.lin0721.linmusic.core.source.MusicPlatform
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UnifiedAccountListLogicTest {

    @Test
    fun crossPlatformSwitchRequiresConfirm() {
        assertTrue(requiresQueueClearConfirm(MusicPlatform.NETEASE, MusicPlatform.KUGOU))
        assertTrue(requiresQueueClearConfirm(MusicPlatform.KUGOU, MusicPlatform.NETEASE))
    }

    @Test
    fun samePlatformSwitchSkipsConfirm() {
        assertFalse(requiresQueueClearConfirm(MusicPlatform.NETEASE, MusicPlatform.NETEASE))
        assertFalse(requiresQueueClearConfirm(MusicPlatform.KUGOU, MusicPlatform.KUGOU))
    }

    @Test
    fun guestTargetsNetease() {
        assertEquals(MusicPlatform.NETEASE, PendingAccountSwitch.ToGuest.targetPlatform)
    }

    @Test
    fun avatarModel_neteaseGetsThumbParam_kugouUntouched() {
        val netease = StoredAccount(uid = 1, avatarUrl = "https://p1.music.126.net/a.jpg")
        val kugou = StoredAccount(uid = 2, avatarUrl = "https://imge.kugou.com/a.jpg", platform = MusicPlatform.KUGOU)
        assertEquals("https://p1.music.126.net/a.jpg?param=200y200", accountAvatarModel(netease))
        assertEquals("https://imge.kugou.com/a.jpg", accountAvatarModel(kugou))
        assertNull(accountAvatarModel(StoredAccount(uid = 3)))
        assertNull(accountAvatarModel(null))
    }

    @Test
    fun displayName_fallsBackToPlatformAndUid() {
        val kugou = StoredAccount(uid = 2, platform = MusicPlatform.KUGOU, platformUid = "k9")
        assertEquals("酷狗用户 k9", kugou.displayName())
        assertEquals("小明", StoredAccount(uid = 1, nickname = "小明").displayName())
    }
}

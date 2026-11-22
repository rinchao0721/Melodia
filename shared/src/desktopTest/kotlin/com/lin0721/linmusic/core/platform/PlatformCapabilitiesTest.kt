package com.lin0721.linmusic.core.platform

import com.lin0721.linmusic.core.source.MusicPlatform
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlatformCapabilitiesTest {

    @Test
    fun `网易云具备完整账号与社交能力`() {
        val cap = PlatformCapabilities.of(MusicPlatform.NETEASE)
        assertEquals(MusicPlatform.NETEASE, cap.platform)
        assertTrue(cap.supportsSearch)
        assertTrue(cap.supportsPlayback)
        assertTrue(cap.supportsLogin)
        assertTrue(cap.supportsQrLogin)
        assertTrue(cap.supportsPhoneLogin)
        assertTrue(cap.supportsPersonalFm)
        assertTrue(cap.supportsDailyRecommend)
        assertTrue(cap.supportsCloudDisk)
        assertTrue(cap.supportsCommentRead)
        assertTrue(cap.supportsCommentSend)
        assertTrue(cap.supportsCommentDelete)
        assertTrue(cap.supportsPrivateMessage)
        assertTrue(cap.supportsPodcast)
        assertTrue(cap.supportsListenTogether)
        assertTrue(cap.supportsPlaylistCreateEdit)
        assertTrue(cap.supportsPlaylistSort)
        assertTrue(cap.supportsLikeSync)
        assertTrue(cap.supportsDownload)
        assertTrue(cap.supportsLyrics)
    }

    @Test
    fun `酷狗具备音乐与账号能力但屏蔽网易专属社交与播客`() {
        val cap = PlatformCapabilities.of(MusicPlatform.KUGOU)
        assertTrue(cap.supportsSearch)
        assertTrue(cap.supportsPlayback)
        assertTrue(cap.supportsLogin)
        assertTrue(cap.supportsQrLogin)
        assertTrue(cap.supportsPhoneLogin)
        assertTrue(cap.supportsPersonalFm)
        assertTrue(cap.supportsDailyRecommend)
        assertTrue(cap.supportsCloudDisk)
        assertTrue(cap.supportsCommentRead)
        assertTrue(cap.supportsCommentSend)
        assertTrue(cap.supportsCommentDelete)
        assertTrue(cap.supportsPlaylistCreateEdit)
        assertTrue(cap.supportsPlaylistSort)
        assertTrue(cap.supportsLikeSync)
        assertTrue(cap.supportsDownload)
        assertTrue(cap.supportsLyrics)

        assertFalse(cap.supportsPrivateMessage)
        assertFalse(cap.supportsPodcast)
        assertFalse(cap.supportsListenTogether)
    }

    @Test
    fun `QQ 酷我 咪咕 LX 与本地音乐能力边界清晰`() {
        val qq = PlatformCapabilities.of(MusicPlatform.QQ)
        assertTrue(qq.supportsSearch)
        assertTrue(qq.supportsPlayback)
        assertTrue(qq.supportsCommentRead)
        assertTrue(qq.supportsLyrics)
        assertFalse(qq.supportsLogin)
        assertFalse(qq.supportsCommentSend)

        val kuwo = PlatformCapabilities.of(MusicPlatform.KUWO)
        assertTrue(kuwo.supportsSearch)
        assertTrue(kuwo.supportsPlayback)
        assertFalse(kuwo.supportsLogin)
        assertFalse(kuwo.supportsCommentRead)

        val migu = PlatformCapabilities.of(MusicPlatform.MIGU)
        assertFalse(migu.supportsSearch)
        assertTrue(migu.supportsPlayback)
        assertFalse(migu.supportsLogin)

        val lx = PlatformCapabilities.of(MusicPlatform.LX)
        assertTrue(lx.supportsSearch)
        assertTrue(lx.supportsPlayback)
        assertTrue(lx.supportsDownload)
        assertTrue(lx.supportsLyrics)
        assertFalse(lx.supportsLogin)

        val local = PlatformCapabilities.of(MusicPlatform.LOCAL)
        assertFalse(local.supportsSearch)
        assertTrue(local.supportsPlayback)
        assertTrue(local.supportsLyrics)
        assertFalse(local.supportsLogin)
        assertFalse(local.supportsCloudDisk)
        assertFalse(local.supportsDownload)
        assertFalse(local.supportsLikeSync)
    }

    @Test
    fun `所有 MusicPlatform 枚举项均有对应的能力矩阵`() {
        MusicPlatform.entries.forEach { platform ->
            val cap = PlatformCapabilities.of(platform)
            assertEquals(platform, cap.platform)
        }
    }
}

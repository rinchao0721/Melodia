package com.lin0721.linmusic.desktop.data.local

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LocalTrackQualityTest {

    @Test
    fun inferDesktopAudioQualityUsesHeaderPropertiesAndExtensionFallback() {
        assertEquals("hires", inferDesktopAudioQuality("flac", 1411, 96_000, 16, 0L, 0L))
        assertEquals("hires", inferDesktopAudioQuality("wav", 1411, 48_000, 24, 0L, 0L))
        assertEquals("lossless", inferDesktopAudioQuality("flac", 900, 44_100, 16, 0L, 0L))
        assertEquals("lossless", inferDesktopAudioQuality("ape", 0, 0, 0, 0L, 0L))
        assertEquals("exhigh", inferDesktopAudioQuality("mp3", 320, 44_100, 16, 0L, 0L))
        assertEquals("higher", inferDesktopAudioQuality("m4a", 192, 44_100, 16, 0L, 0L))
        assertEquals("standard", inferDesktopAudioQuality("mp3", 128, 44_100, 16, 0L, 0L))
        // 读不到头部码率时按文件大小与时长估算（4_000_000 * 8 / 100_000 = 320 kbps）
        assertEquals("exhigh", inferDesktopAudioQuality("mp3", 0, 0, 0, 4_000_000L, 100_000L))
        assertEquals("", inferDesktopAudioQuality("mp3", 0, 0, 0, 0L, 0L))
    }

    @Test
    fun resolvedQualitySupportsStoredScannedAndLegacyFallbackValues() {
        val scanned = LocalTrack(path = "/music/song.flac", title = "A", artist = "B", quality = "hires")
        assertEquals("hires", scanned.resolvedQuality())

        val unknownLossy = LocalTrack(path = "/music/song.mp3", title = "A", artist = "B", quality = "")
        assertNull(unknownLossy.resolvedQuality())

        val legacyLossless = LocalTrack(path = "/music/old.flac", title = "A", artist = "B", quality = null)
        assertEquals("lossless", legacyLossless.resolvedQuality())

        val legacyEstimatedMp3 = LocalTrack(
            path = "/music/old.mp3",
            title = "A",
            artist = "B",
            durationMs = 100_000L,
            size = 4_000_000L,
            quality = null
        )
        assertEquals("exhigh", legacyEstimatedMp3.resolvedQuality())
    }
}

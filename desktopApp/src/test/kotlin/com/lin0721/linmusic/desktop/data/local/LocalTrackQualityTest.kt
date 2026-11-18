package com.lin0721.linmusic.desktop.data.local

import org.jaudiotagger.audio.mp4.EncoderType
import org.jaudiotagger.audio.mp4.Mp4AudioHeader
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
    fun inferDesktopAudioQualityHandlesAlacAndAacInsideM4a() {
        val alacHeader = Mp4AudioHeader().apply {
            encodingType = EncoderType.APPLE_LOSSLESS.description
            format = EncoderType.APPLE_LOSSLESS.description
            setSamplingRate(44_100)
            bitsPerSample = 24
            setBitRate(1577)
        }
        val alacCodec = resolveDesktopAudioCodec(alacHeader)
        assertEquals("alac", alacCodec)
        assertEquals(
            "hires",
            inferDesktopAudioQuality("m4a", 1577, 44_100, 24, 0L, 0L, codec = alacCodec)
        )
        assertEquals(
            "lossless",
            inferDesktopAudioQuality("m4a", 900, 44_100, 16, 0L, 0L, codec = alacCodec)
        )

        val aacHeader = Mp4AudioHeader().apply {
            encodingType = EncoderType.AAC.description
            format = EncoderType.AAC.description
            setSamplingRate(44_100)
            setBitRate(256)
        }
        val aacCodec = resolveDesktopAudioCodec(aacHeader)
        assertEquals("aac", aacCodec)
        assertEquals(
            "exhigh",
            inferDesktopAudioQuality("m4a", 256, 44_100, 16, 0L, 0L, codec = aacCodec)
        )

        // 编码读取失败时回退为扩展名 + 码率估算，不崩溃
        assertNull(resolveDesktopAudioCodec(null))
        assertEquals("", inferDesktopAudioQuality("m4a", 0, 0, 0, 0L, 0L, codec = null))
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

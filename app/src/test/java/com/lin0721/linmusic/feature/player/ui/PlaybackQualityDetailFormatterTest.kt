package com.lin0721.linmusic.feature.player.ui

import com.lin0721.linmusic.core.model.OutputAudioInfo
import com.lin0721.linmusic.core.model.PlaybackQuality
import com.lin0721.linmusic.core.model.QualityBadge
import com.lin0721.linmusic.core.model.StreamAudioInfo
import com.lin0721.linmusic.core.model.playbackQualityLabel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PlaybackQualityDetailFormatterTest {

    @Test
    fun `format helpers render readable values and return null when missing`() {
        assertEquals("FLAC", formatCodecLabel("audio/flac"))
        assertEquals("AAC", formatCodecLabel("audio/mp4a-latm"))
        assertEquals("MP3", formatCodecLabel("audio/mpeg"))
        assertEquals("E-AC-3 JOC", formatCodecLabel("audio/eac3-joc"))
        assertEquals("DSD", formatCodecLabel("audio/dsd"))
        assertNull(formatCodecLabel("  "))
        assertNull(formatCodecLabel(null))

        assertEquals("44.1 kHz", formatSampleRateLabel(44_100))
        assertEquals("48 kHz", formatSampleRateLabel(48_000))
        assertEquals("88.2 kHz", formatSampleRateLabel(88_200))
        assertEquals("96 kHz", formatSampleRateLabel(96_000))
        assertNull(formatSampleRateLabel(0))
        assertNull(formatSampleRateLabel(null))

        assertEquals("24 bit", formatBitDepthLabel(24))
        assertNull(formatBitDepthLabel(0))

        assertEquals("320 kbps", formatBitrateLabel(320_000))
        assertNull(formatBitrateLabel(-1))

        assertEquals("单声道", formatChannelCountLabel(1))
        assertEquals("立体声", formatChannelCountLabel(2))
        assertEquals("6 声道", formatChannelCountLabel(6))
        assertNull(formatChannelCountLabel(0))
    }

    @Test
    fun `buildPlaybackQualityDetailItems populates complete Hi-Res item list`() {
        val quality = PlaybackQuality(
            mediaId = "1001",
            declaredLevel = "hires",
            stream = StreamAudioInfo(
                codec = "audio/flac",
                sampleRateHz = 96_000,
                bitDepth = 24,
                bitrateBps = 2_800_000,
                channels = 2
            ),
            output = OutputAudioInfo(deviceName = "Sony WH-1000XM5")
        )

        val items = buildPlaybackQualityDetailItems(quality)
        assertEquals(
            listOf(
                PlaybackQualityDetailItem("判定档位", "Hi-Res", QualityBadge.HiRes.colorArgb),
                PlaybackQualityDetailItem("接口声明档位", "Hi-Res"),
                PlaybackQualityDetailItem("编码", "FLAC"),
                PlaybackQualityDetailItem("采样率", "96 kHz"),
                PlaybackQualityDetailItem("位深", "24 bit"),
                PlaybackQualityDetailItem("码率", "2800 kbps"),
                PlaybackQualityDetailItem("声道", "立体声"),
                PlaybackQualityDetailItem("输出设备", "Sony WH-1000XM5")
            ),
            items
        )
    }

    @Test
    fun `buildPlaybackQualityDetailItems renders missing fields as em-dash`() {
        val quality = PlaybackQuality(
            mediaId = "-42",
            declaredLevel = null,
            stream = StreamAudioInfo(codec = "audio/mpeg", bitrateBps = 192_000),
            output = null
        )

        val items = buildPlaybackQualityDetailItems(quality).associateBy { it.label }
        assertEquals("较高", items.getValue("判定档位").value)
        assertNull(items.getValue("判定档位").accentColorArgb)
        assertEquals("—", items.getValue("接口声明档位").value)
        assertEquals("MP3", items.getValue("编码").value)
        assertEquals("—", items.getValue("采样率").value)
        assertEquals("—", items.getValue("位深").value)
        assertEquals("192 kbps", items.getValue("码率").value)
        assertEquals("—", items.getValue("声道").value)
        assertEquals("—", items.getValue("输出设备").value)
    }

    @Test
    fun `display label switches from declared tier to stream params`() {
        val declared = PlaybackQuality(mediaId = "1", declaredLevel = "lossless")
        val withStream = declared.copy(
            stream = StreamAudioInfo(codec = "audio/flac", sampleRateHz = 44_100, bitDepth = 16, channels = 2)
        )
        assertEquals("Lossless", playbackQualityLabel(declared))
        assertEquals("16bit/44.1kHz", playbackQualityLabel(withStream))
        assertEquals(
            "16bit/44.1kHz",
            playbackQualityLabel(withStream.copy(stream = withStream.stream?.copy(bitrateBps = 900_000)))
        )
    }
}

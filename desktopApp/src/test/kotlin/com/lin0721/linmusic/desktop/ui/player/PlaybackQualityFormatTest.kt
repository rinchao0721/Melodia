package com.lin0721.linmusic.desktop.ui.player

import com.lin0721.linmusic.core.model.OutputAudioInfo
import org.junit.Assert.assertEquals
import org.junit.Test

class PlaybackQualityFormatTest {

    @Test
    fun missingValuesFormatAsEmDash() {
        assertEquals(QUALITY_MISSING_TEXT, formatCodecText(null))
        assertEquals(QUALITY_MISSING_TEXT, formatCodecText("   "))
        assertEquals(QUALITY_MISSING_TEXT, formatSampleRateText(null))
        assertEquals(QUALITY_MISSING_TEXT, formatSampleRateText(0))
        assertEquals(QUALITY_MISSING_TEXT, formatBitDepthText(null))
        assertEquals(QUALITY_MISSING_TEXT, formatBitrateText(null))
        assertEquals(QUALITY_MISSING_TEXT, formatChannelsText(null))
        assertEquals(QUALITY_MISSING_TEXT, formatOutputDeviceText(null))
        assertEquals(QUALITY_MISSING_TEXT, formatOutputFormatText(null))
        assertEquals(QUALITY_MISSING_TEXT, formatOutputFormatText(OutputAudioInfo()))
        assertEquals(QUALITY_MISSING_TEXT, formatResampledText(null))
        assertEquals(QUALITY_MISSING_TEXT, formatDeclaredLevelText(null))
    }

    @Test
    fun presentValuesFormatAsExpected() {
        assertEquals("FLAC", formatCodecText("flac"))
        assertEquals("44.1 kHz", formatSampleRateText(44100))
        assertEquals("96 kHz", formatSampleRateText(96000))
        assertEquals("24 bit", formatBitDepthText(24))
        assertEquals("320 kbps", formatBitrateText(320000))
        assertEquals("1 声道 (单声道)", formatChannelsText(1))
        assertEquals("2 声道 (立体声)", formatChannelsText(2))
        assertEquals("6 声道", formatChannelsText(6))
        assertEquals("48 kHz / floatp", formatOutputFormatText(OutputAudioInfo(sampleRateHz = 48000, format = "floatp")))
        assertEquals("是", formatResampledText(true))
        assertEquals("否", formatResampledText(false))
        assertEquals("Hi-Res", formatDeclaredLevelText("hires"))
        assertEquals("无损 (FLAC)", formatDeclaredLevelText("lossless"))
    }
}

package com.lin0721.linmusic.desktop.player.mpv

import com.lin0721.linmusic.core.model.OutputAudioInfo
import com.lin0721.linmusic.core.model.PlaybackQuality
import com.lin0721.linmusic.core.model.StreamAudioInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MpvAudioInfoTest {

    @Test
    fun parseAudioParamsExtractsFieldsAndHandlesChannelFallbacks() {
        val params = parseAudioParams("""{"samplerate":96000,"channel-count":2,"format":"s32"}""")
        assertEquals(MpvAudioParams(sampleRateHz = 96000, channels = 2, format = "s32"), params)

        val fallback = parseAudioParams("""{"samplerate":48000,"channels":"5.1(side)","format":"floatp"}""")
        assertEquals(MpvAudioParams(sampleRateHz = 48000, channels = 6, format = "floatp"), fallback)

        assertNull(parseAudioParams(null))
        assertNull(parseAudioParams("   "))
        assertNull(parseAudioParams("{}"))
        assertNull(parseAudioParams("not-json"))
    }

    @Test
    fun parseAudioBitrateHandlesIntFloatAndInvalidInputs() {
        assertEquals(320000, parseAudioBitrate("320000"))
        assertEquals(1024001, parseAudioBitrate("1024000.6"))
        assertNull(parseAudioBitrate("0"))
        assertNull(parseAudioBitrate("-128"))
        assertNull(parseAudioBitrate(""))
        assertNull(parseAudioBitrate(null))
    }

    @Test
    fun bitDepthPrefersTrackListDemuxAndRejectsS32AndFloat() {
        val trackList24 = """
            [
              {"id":1,"type":"video","selected":true},
              {"id":2,"type":"audio","selected":false,"codec":"aac","demux-bits":16},
              {"id":3,"type":"audio","selected":true,"codec":"flac","demux-bits":24,"demux-samplerate":96000,"demux-channel-count":2}
            ]
        """.trimIndent()
        val demux24 = parseSelectedAudioTrackDemux(trackList24)
        assertEquals(24, extractBitDepth(demux24, decodedFormat = "s32", codec = "flac"))

        // 缺少 demux 位深时不能用解码后的 s32 冒充 32bit
        assertNull(extractBitDepth(demuxInfo = null, decodedFormat = "s32", codec = "flac"))
        assertNull(extractBitDepth(demuxInfo = null, decodedFormat = "s32p", codec = "flac"))
        // float 解码格式一律置 null
        assertNull(extractBitDepth(demuxInfo = null, decodedFormat = "float", codec = "flac"))
        assertNull(extractBitDepth(demuxInfo = null, decodedFormat = "floatp", codec = "opus"))
        assertNull(extractBitDepth(demuxInfo = null, decodedFormat = "dbl", codec = "wav"))
        // 有损编码即使解码为 s16p 也不标位深
        assertNull(extractBitDepth(demuxInfo = null, decodedFormat = "s16p", codec = "mp3"))
        // 无损 16bit 解码为 s16 时可安全识别
        assertEquals(16, extractBitDepth(demuxInfo = null, decodedFormat = "s16", codec = "flac"))
        // PCM 编码可从 codec 名提取，float PCM 返回 null
        assertEquals(24, extractBitDepth(demuxInfo = null, decodedFormat = "s32", codec = "pcm_s24le"))
        assertNull(extractBitDepth(demuxInfo = null, decodedFormat = "float", codec = "pcm_f32le"))
    }

    @Test
    fun parseMpvAudioInfoCombinesStreamOutputAndResampleStatus() {
        val deviceList = """
            [
              {"name":"auto","description":"Autoselect device"},
              {"name":"wasapi/{dac-1}","description":"扬声器 (USB HiFi DAC)"}
            ]
        """.trimIndent()
        val trackList = """
            [{"id":1,"type":"audio","selected":true,"codec":"flac","demux-bits":24}]
        """.trimIndent()

        val resampledInfo = parseMpvAudioInfo(
            audioCodecName = "flac",
            audioParamsJson = """{"samplerate":96000,"channel-count":2,"format":"s32"}""",
            audioOutParamsJson = """{"samplerate":48000,"channel-count":2,"format":"floatp"}""",
            audioBitrateRaw = "2850000",
            trackListJson = trackList,
            currentAo = "wasapi",
            audioDevice = "wasapi/{dac-1}",
            audioDeviceListJson = deviceList
        )

        assertEquals(
            StreamAudioInfo(
                codec = "flac",
                sampleRateHz = 96000,
                bitDepth = 24,
                bitrateBps = 2850000,
                channels = 2
            ),
            resampledInfo.stream
        )
        assertEquals(
            OutputAudioInfo(
                deviceName = "扬声器 (USB HiFi DAC)",
                sampleRateHz = 48000,
                format = "floatp",
                resampled = true
            ),
            resampledInfo.output
        )
        assertTrue(resampledInfo.output?.resampled == true)

        val bitPerfectInfo = parseMpvAudioInfo(
            audioCodecName = "flac",
            audioParamsJson = """{"samplerate":96000,"channel-count":2,"format":"s32"}""",
            audioOutParamsJson = """{"samplerate":96000,"channel-count":2,"format":"s24"}""",
            audioBitrateRaw = null,
            trackListJson = trackList,
            currentAo = "wasapi",
            audioDevice = "auto",
            audioDeviceListJson = deviceList
        )
        assertFalse(bitPerfectInfo.output?.resampled ?: true)
        assertEquals("系统默认 (wasapi)", bitPerfectInfo.output?.deviceName)
    }

    @Test
    fun mergePlaybackQualitySupportsPreStreamAndLocalPlayback() {
        assertNull(mergePlaybackQuality("1001", null, null))

        val declaredOnly = mergePlaybackQuality("1001", "hires", null)
        assertEquals(PlaybackQuality(mediaId = "1001", declaredLevel = "hires"), declaredOnly)

        val localStreamOnly = mergePlaybackQuality(
            mediaId = "-10",
            declaredLevel = null,
            audioInfo = MpvAudioInfo(
                stream = StreamAudioInfo(codec = "mp3", sampleRateHz = 44100, bitrateBps = 320000, channels = 2),
                output = OutputAudioInfo(deviceName = "系统默认 (wasapi)", sampleRateHz = 48000, format = "floatp", resampled = true)
            )
        )
        assertNull(localStreamOnly?.declaredLevel)
        assertEquals("-10", localStreamOnly?.mediaId)
        assertEquals(320000, localStreamOnly?.stream?.bitrateBps)
        assertEquals(true, localStreamOnly?.output?.resampled)
    }
}

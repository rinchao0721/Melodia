package com.lin0721.linmusic.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

// 实际流参数判档与音质展示文案单测
class PlaybackQualityTest {

    @Test
    fun `hires声明遇到16bit_44_1k_flac降级为lossless`() {
        val stream = StreamAudioInfo(
            codec = "flac",
            sampleRateHz = 44_100,
            bitDepth = 16,
            bitrateBps = 900_000,
            channels = 2
        )
        assertEquals("lossless", resolveQualityLevel("hires", stream))
    }

    @Test
    fun `lossless声明遇到mp3_320k降级为exhigh`() {
        val stream = StreamAudioInfo(
            codec = "mp3",
            sampleRateHz = 44_100,
            bitDepth = null,
            bitrateBps = 320_000,
            channels = 2
        )
        assertEquals("exhigh", resolveQualityLevel("lossless", stream))
    }

    @Test
    fun `jymaster声明遇到24bit_96k_flac保留jymaster`() {
        val stream = StreamAudioInfo(
            codec = "flac",
            sampleRateHz = 96_000,
            bitDepth = 24,
            bitrateBps = 3_100_000,
            channels = 2
        )
        assertEquals("jymaster", resolveQualityLevel("jymaster", stream))
    }

    @Test
    fun `sky与jyeffect在无损流下保留原声明档位`() {
        val cdFlac = StreamAudioInfo(codec = "flac", sampleRateHz = 44_100, bitDepth = 16)
        assertEquals("sky", resolveQualityLevel("sky", cdFlac))
        assertEquals("jyeffect", resolveQualityLevel("jyeffect", cdFlac))
    }

    @Test
    fun `无损编码映射覆盖alac_pcm_ape_wavpack_aiff`() {
        val alacHiRes = StreamAudioInfo(codec = "alac", sampleRateHz = 96_000, bitDepth = 24)
        val pcmLossless = StreamAudioInfo(codec = "pcm_s16le", sampleRateHz = 48_000, bitDepth = 16)
        val wavpackHiRes = StreamAudioInfo(codec = "wavpack", sampleRateHz = 44_100, bitDepth = 24)
        val apeLossless = StreamAudioInfo(codec = "ape", sampleRateHz = 44_100, bitDepth = 16)
        val aiffHiRes = StreamAudioInfo(codec = "aiff", sampleRateHz = 88_200, bitDepth = 24)

        assertEquals("hires", resolveQualityLevel("lossless", alacHiRes))
        assertEquals("lossless", resolveQualityLevel("hires", pcmLossless))
        assertEquals("hires", resolveQualityLevel(null, wavpackHiRes))
        assertEquals("lossless", resolveQualityLevel(null, apeLossless))
        assertEquals("hires", resolveQualityLevel(null, aiffHiRes))
    }

    @Test
    fun `有损编码无论声明多高均按码率判档`() {
        assertEquals(
            "higher",
            resolveQualityLevel("jymaster", StreamAudioInfo(codec = "aac", bitrateBps = 192_000))
        )
        assertEquals(
            "standard",
            resolveQualityLevel("hires", StreamAudioInfo(codec = "mp3", bitrateBps = 128_000))
        )
    }

    @Test
    fun `无stream时回退declared`() {
        assertEquals("hires", resolveQualityLevel("hires", null))
        assertEquals("dolby", resolveQualityLevel("dolby", null))
        assertNull(resolveQualityLevel(null, null))
    }

    @Test
    fun `位深缺失时96kHz判为hires且文案显示96kHz`() {
        val stream = StreamAudioInfo(
            codec = "flac",
            sampleRateHz = 96_000,
            bitDepth = null,
            channels = 2
        )
        assertEquals("hires", resolveQualityLevel("lossless", stream))
        assertEquals("96kHz", playbackQualityLabel(PlaybackQuality("1", "lossless", stream)))
    }

    @Test
    fun `eac3等杜比编码判为dolby`() {
        assertEquals("dolby", resolveQualityLevel("lossless", StreamAudioInfo(codec = "eac3")))
        assertEquals("dolby", resolveQualityLevel(null, StreamAudioInfo(codec = "ac3")))
        assertEquals("dolby", resolveQualityLevel(null, StreamAudioInfo(codec = "ac4")))
        assertEquals("dolby", resolveQualityLevel(null, StreamAudioInfo(codec = "truehd")))
    }

    @Test
    fun `codec缺失但参数足够时尽量判档否则回退declared`() {
        assertEquals(
            "hires",
            resolveQualityLevel("lossless", StreamAudioInfo(codec = null, sampleRateHz = 96_000))
        )
        assertEquals(
            "exhigh",
            resolveQualityLevel(null, StreamAudioInfo(codec = null, bitrateBps = 320_000))
        )
        assertEquals(
            "lossless",
            resolveQualityLevel("lossless", StreamAudioInfo(codec = null, sampleRateHz = 44_100, bitDepth = 16))
        )
    }

    @Test
    fun `文案各分支格式正确`() {
        // 无损类有位深与采样率，去掉多余小数零
        assertEquals(
            "24bit/96kHz",
            playbackQualityLabel(
                PlaybackQuality(
                    mediaId = "1",
                    declaredLevel = "hires",
                    stream = StreamAudioInfo(codec = "flac", sampleRateHz = 96_000, bitDepth = 24)
                )
            )
        )
        assertEquals(
            "16bit/44.1kHz",
            playbackQualityLabel(
                PlaybackQuality(
                    mediaId = "2",
                    declaredLevel = "lossless",
                    stream = StreamAudioInfo(codec = "flac", sampleRateHz = 44_100, bitDepth = 16)
                )
            )
        )
        assertEquals(
            "24bit/48kHz",
            playbackQualityLabel(
                PlaybackQuality(
                    mediaId = "3",
                    declaredLevel = "jymaster",
                    stream = StreamAudioInfo(codec = "flac", sampleRateHz = 48_000, bitDepth = 24)
                )
            )
        )

        // 杜比无位深时展示采样率，无采样率时回退 Dolby 短标签
        assertEquals(
            "48kHz",
            playbackQualityLabel(
                PlaybackQuality(
                    mediaId = "4",
                    declaredLevel = "dolby",
                    stream = StreamAudioInfo(codec = "eac3", sampleRateHz = 48_000, bitrateBps = 640_000)
                )
            )
        )
        assertEquals(
            "Dolby",
            playbackQualityLabel(
                PlaybackQuality(
                    mediaId = "5",
                    declaredLevel = null,
                    stream = StreamAudioInfo(codec = "eac3")
                )
            )
        )

        // 有损类展示 kbps
        assertEquals(
            "320kbps",
            playbackQualityLabel(
                PlaybackQuality(
                    mediaId = "6",
                    declaredLevel = "lossless",
                    stream = StreamAudioInfo(codec = "mp3", sampleRateHz = 44_100, bitrateBps = 320_000)
                )
            )
        )

        // 参数不足或无 stream 回退档位短标签
        assertEquals(
            "ExHigh",
            playbackQualityLabel(
                PlaybackQuality(
                    mediaId = "7",
                    declaredLevel = "exhigh",
                    stream = StreamAudioInfo(codec = "mp3")
                )
            )
        )
        assertEquals(
            "Hi-Res",
            playbackQualityLabel(
                PlaybackQuality(
                    mediaId = "8",
                    declaredLevel = "hires",
                    stream = StreamAudioInfo(codec = "flac")
                )
            )
        )
        assertEquals(
            "Master",
            playbackQualityLabel(PlaybackQuality(mediaId = "9", declaredLevel = "jymaster", stream = null))
        )
        assertNull(playbackQualityLabel(PlaybackQuality(mediaId = "10", declaredLevel = null, stream = null)))
    }
}

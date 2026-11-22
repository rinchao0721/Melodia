package com.lin0721.linmusic.core.platform

import com.lin0721.linmusic.core.model.QualityBadge
import com.lin0721.linmusic.core.model.qualityBadgeOf
import com.lin0721.linmusic.core.model.qualityRank
import com.lin0721.linmusic.core.source.MusicPlatform
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UnifiedAudioQualityTest {

    @Test
    fun `网易云全音质码双向映射正确`() {
        val expected = mapOf(
            "standard" to UnifiedAudioQuality.STANDARD,
            "higher" to UnifiedAudioQuality.HIGHER,
            "exhigh" to UnifiedAudioQuality.EXHIGH,
            "lossless" to UnifiedAudioQuality.LOSSLESS,
            "hires" to UnifiedAudioQuality.HIRES,
            "jyeffect" to UnifiedAudioQuality.SURROUND,
            "sky" to UnifiedAudioQuality.IMMERSIVE,
            "dolby" to UnifiedAudioQuality.DOLBY,
            "jymaster" to UnifiedAudioQuality.MASTER
        )

        expected.forEach { (code, quality) ->
            assertEquals(quality, UnifiedAudioQuality.fromNeteaseCode(code))
            assertEquals(quality, UnifiedAudioQuality.fromNeteaseCode(" ${code.uppercase()} "))
            assertEquals(quality, UnifiedAudioQuality.fromCode(code, MusicPlatform.NETEASE))
            assertEquals(code, quality.toNeteaseCode())
        }
        assertNull(UnifiedAudioQuality.fromNeteaseCode("unknown"))
        assertNull(UnifiedAudioQuality.fromNeteaseCode(null))
    }

    @Test
    fun `酷狗音质码归一化映射正确`() {
        assertEquals(UnifiedAudioQuality.STANDARD, UnifiedAudioQuality.fromKugouCode("128"))
        assertEquals(UnifiedAudioQuality.EXHIGH, UnifiedAudioQuality.fromKugouCode("320"))
        assertEquals(UnifiedAudioQuality.LOSSLESS, UnifiedAudioQuality.fromKugouCode("flac"))
        assertEquals(UnifiedAudioQuality.HIRES, UnifiedAudioQuality.fromKugouCode("high"))
        assertEquals(UnifiedAudioQuality.MASTER, UnifiedAudioQuality.fromKugouCode("super"))
        assertEquals(UnifiedAudioQuality.SURROUND, UnifiedAudioQuality.fromKugouCode("viper_clear"))
        assertEquals(UnifiedAudioQuality.MASTER, UnifiedAudioQuality.fromKugouCode("viper_tape"))
        assertEquals(UnifiedAudioQuality.IMMERSIVE, UnifiedAudioQuality.fromKugouCode("viper_atmos"))

        assertEquals(UnifiedAudioQuality.LOSSLESS, UnifiedAudioQuality.fromCode(" FLAC ", MusicPlatform.KUGOU))
        assertEquals("128", UnifiedAudioQuality.STANDARD.toKugouCode())
        assertEquals("320", UnifiedAudioQuality.EXHIGH.toKugouCode())
        assertEquals("flac", UnifiedAudioQuality.LOSSLESS.toKugouCode())
        assertEquals("high", UnifiedAudioQuality.HIRES.toKugouCode())
        assertEquals("viper_clear", UnifiedAudioQuality.SURROUND.toKugouCode())
        assertEquals("viper_atmos", UnifiedAudioQuality.IMMERSIVE.toKugouCode())
        assertEquals("viper_tape", UnifiedAudioQuality.MASTER.toKugouCode())
        assertNull(UnifiedAudioQuality.fromKugouCode("unknown"))
    }

    @Test
    fun `音质档位顺序与旧版徽标和 rank 衔接正确`() {
        val ordered = UnifiedAudioQuality.ORDERED
        assertEquals(UnifiedAudioQuality.entries.size, ordered.size)
        for (i in 0 until ordered.lastIndex) {
            assertTrue(ordered[i].rank < ordered[i + 1].rank)
        }

        // 与旧版 QualityBadge 对齐
        UnifiedAudioQuality.entries.forEach { quality ->
            val expectedBadge = qualityBadgeOf(quality.neteaseCode)
            assertEquals(expectedBadge, quality.toQualityBadge())
            assertEquals(qualityRank(quality.neteaseCode), quality.toLegacyQualityRank())
        }

        assertEquals(UnifiedAudioQuality.STANDARD, UnifiedAudioQuality.fromQualityBadge(QualityBadge.Standard))
        assertEquals(UnifiedAudioQuality.EXHIGH, UnifiedAudioQuality.fromQualityBadge(QualityBadge.ExHigh))
        assertEquals(UnifiedAudioQuality.LOSSLESS, UnifiedAudioQuality.fromQualityBadge(QualityBadge.Lossless))
        assertEquals(UnifiedAudioQuality.HIRES, UnifiedAudioQuality.fromQualityBadge(QualityBadge.HiRes))
        assertEquals(UnifiedAudioQuality.SURROUND, UnifiedAudioQuality.fromQualityBadge(QualityBadge.Surround))
        assertEquals(UnifiedAudioQuality.IMMERSIVE, UnifiedAudioQuality.fromQualityBadge(QualityBadge.Immersive))
        assertEquals(UnifiedAudioQuality.DOLBY, UnifiedAudioQuality.fromQualityBadge(QualityBadge.Dolby))
        assertEquals(UnifiedAudioQuality.MASTER, UnifiedAudioQuality.fromQualityBadge(QualityBadge.Master))
        assertNull(UnifiedAudioQuality.fromQualityBadge(null))
    }
}

package com.lin0721.linmusic.core.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import kotlin.math.log10

class LoudnessPolicyTest {

    @Test
    fun `增益受最大提升与最小衰减限制`() {
        // -25 LUFS 需提升 11 dB，受限于 MAX_BOOST_DB (+6)
        assertEquals(LoudnessPolicy.MAX_BOOST_DB, LoudnessPolicy.gainDb(-25.0, null), 1e-9)
        // -23 LUFS 且峰值很低 (0.2 -> 峰值余量约 +12.98 dB)，仍受限于 +6 dB
        assertEquals(LoudnessPolicy.MAX_BOOST_DB, LoudnessPolicy.gainDb(-23.0, 0.2), 1e-9)
        // +5 LUFS 需压低 19 dB，受限于 MIN_GAIN_DB (-15)
        assertEquals(LoudnessPolicy.MIN_GAIN_DB, LoudnessPolicy.gainDb(5.0, null), 1e-9)
        assertEquals(LoudnessPolicy.MIN_GAIN_DB, LoudnessPolicy.gainDb(2.0, 1.0), 1e-9)
        // 正常范围内按 -14 LUFS 对齐
        assertEquals(-4.0, LoudnessPolicy.gainDb(-10.0, 1.0), 1e-9)
    }

    @Test
    fun `峰值余量不足时增益被峰值上限截断`() {
        // -23 LUFS 期望 +6 dB，但 peak=0.5 (-6.02 dBFS) 在 -1 dBFS 上限下最多提升 +5.02 dB
        val expectedCap = LoudnessPolicy.PEAK_CEILING_DBFS - 20.0 * log10(0.5)
        assertEquals(expectedCap, LoudnessPolicy.gainDb(-23.0, 0.5), 1e-9)

        // -16 LUFS 期望 +2 dB，但 peak=0.9 在 -1 dBFS 上限下必须压低
        val tightCap = LoudnessPolicy.PEAK_CEILING_DBFS - 20.0 * log10(0.9)
        assertEquals(tightCap, LoudnessPolicy.gainDb(-16.0, 0.9), 1e-9)

        // peak <= 0 或未知时不做峰值约束
        assertEquals(LoudnessPolicy.MAX_BOOST_DB, LoudnessPolicy.gainDb(-23.0, 0.0), 1e-9)
        assertEquals(LoudnessPolicy.MAX_BOOST_DB, LoudnessPolicy.gainDb(-23.0, -0.5), 1e-9)
        assertEquals(LoudnessPolicy.MAX_BOOST_DB, LoudnessPolicy.gainDb(-23.0, null), 1e-9)
    }

    @Test
    fun `ReplayGain 2_0 标签按 -18 LUFS 换算`() {
        val track = LoudnessPolicy.fromReplayGain("-7.32 dB", "0.95")!!
        assertEquals(-10.68, track.lufs, 1e-9)
        assertEquals(0.95, track.peak!!, 1e-9)
        assertEquals(-3.32, LoudnessPolicy.gainDb(track), 1e-9)

        val quiet = LoudnessPolicy.fromReplayGain("+2.10 dB")!!
        assertEquals(-20.10, quiet.lufs, 1e-9)
        assertNull(quiet.peak)
        assertEquals(LoudnessPolicy.MAX_BOOST_DB, LoudnessPolicy.gainDb(quiet), 1e-9)

        // 无效峰值忽略但不影响响度解析
        val badPeak = LoudnessPolicy.fromReplayGain("-4.0 dB", "0.0")!!
        assertEquals(-14.0, badPeak.lufs, 1e-9)
        assertNull(badPeak.peak)

        assertNull(LoudnessPolicy.fromReplayGain("dB"))
        assertNull(LoudnessPolicy.fromReplayGain(null))
    }

    @Test
    fun `Opus R128_TRACK_GAIN 按 Q7_8 与 -23 LUFS 换算`() {
        // -1536 / 256 = -6.0 dB -> -23 - (-6) = -17.0 LUFS
        val loud = LoudnessPolicy.fromR128Tag("-1536")!!
        assertEquals(-17.0, loud.lufs, 1e-9)
        assertNull(loud.peak)
        assertEquals(3.0, LoudnessPolicy.gainDb(loud), 1e-9)

        // +512 / 256 = +2.0 dB -> -25.0 LUFS
        val quiet = LoudnessPolicy.fromR128Tag(" 512 ")!!
        assertEquals(-25.0, quiet.lufs, 1e-9)
        assertEquals(LoudnessPolicy.MAX_BOOST_DB, LoudnessPolicy.gainDb(quiet), 1e-9)

        assertNull(LoudnessPolicy.fromR128Tag("not_int"))
        assertNull(LoudnessPolicy.fromR128Tag("1.5 dB"))
        assertNull(LoudnessPolicy.fromR128Tag(null))
    }

    @Test
    fun `网易下发 gain 与 peak 换算，peak 非正视为无效`() {
        val loud = LoudnessPolicy.fromNetease(-6.0, 0.9)!!
        assertEquals(-8.0, loud.lufs, 1e-9)
        assertEquals(0.9, loud.peak!!, 1e-9)
        assertEquals(-6.0, LoudnessPolicy.gainDb(loud), 1e-9)

        val quiet = LoudnessPolicy.fromNetease(4.0, 0.5)!!
        assertEquals(-18.0, quiet.lufs, 1e-9)
        assertEquals(4.0, LoudnessPolicy.gainDb(quiet), 1e-9)

        assertNull(LoudnessPolicy.fromNetease(1.5, 0.0))
        assertNull(LoudnessPolicy.fromNetease(-3.0, -0.1))
        assertNull(LoudnessPolicy.fromNetease(null, 1.0))
        assertNull(LoudnessPolicy.fromNetease(-8.0, null))
    }

    @Test
    fun `幅度与 mpv 音量系数换算`() {
        assertEquals(0.5012f, LoudnessPolicy.amplitude(-6.0), 1e-3f)
        assertEquals(1f, LoudnessPolicy.amplitude(0.0), 1e-6f)
        val factor = LoudnessPolicy.mpvVolumeFactor(-6.0)
        assertEquals(0.5012, factor * factor * factor, 1e-3)
    }
}

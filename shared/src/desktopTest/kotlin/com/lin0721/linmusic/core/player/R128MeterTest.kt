package com.lin0721.linmusic.core.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import kotlin.math.PI
import kotlin.math.pow
import kotlin.math.sin

class R128MeterTest {

    // 生成指定 dBFS 峰值幅度的立体声正弦交错 float PCM
    private fun stereoSine(
        sampleRate: Int,
        seconds: Double,
        peakDbfs: Double,
        freqHz: Double = 997.0
    ): FloatArray {
        val amplitude = 10.0.pow(peakDbfs / 20.0)
        val frames = (sampleRate * seconds).toInt()
        val pcm = FloatArray(frames * 2)
        for (i in 0 until frames) {
            val s = (amplitude * sin(2.0 * PI * freqHz * i / sampleRate)).toFloat()
            pcm[i * 2] = s
            pcm[i * 2 + 1] = s
        }
        return pcm
    }

    private fun silence(sampleRate: Int, seconds: Double, channels: Int = 2): FloatArray =
        FloatArray((sampleRate * seconds).toInt() * channels)

    @Test
    fun `48kHz 与 44_1kHz 立体声正弦在 -23 与 -33 dBFS 峰值下符合 R128 参考值`() {
        for (sr in listOf(48_000, 44_100)) {
            val res23 = R128Meter.measure(stereoSine(sr, 5.0, -23.0, 997.0), sr, 2)
            assertNotNull("sr=$sr -23dBFS 不应为空", res23)
            assertEquals(-23.0, res23!!.integratedLufs, 0.1)
            assertEquals(10.0.pow(-23.0 / 20.0), res23.samplePeak, 1e-4)

            val res33 = R128Meter.measure(stereoSine(sr, 5.0, -33.0, 997.0), sr, 2)
            assertNotNull("sr=$sr -33dBFS 不应为空", res33)
            assertEquals(-33.0, res33!!.integratedLufs, 0.1)
            assertEquals(10.0.pow(-33.0 / 20.0), res33.samplePeak, 1e-4)

            val res1k = R128Meter.measure(stereoSine(sr, 5.0, -23.0, 1000.0), sr, 2)
            assertNotNull("sr=$sr 1kHz 不应为空", res1k)
            assertEquals(-23.0, res1k!!.integratedLufs, 0.1)
        }
    }

    @Test
    fun `含长静音或低于绝对门限段时积分响度不被拉低`() {
        val sr = 48_000
        val tone = stereoSine(sr, 10.0, -23.0)
        val shortTail = R128Meter.measure(tone + silence(sr, 1.0), sr, 2)!!
        val longSilence = tone + silence(sr, 20.0) + stereoSine(sr, 5.0, -85.0)
        val longResult = R128Meter.measure(longSilence, sr, 2)!!

        // 额外添加 20s 静音与 5s -85dBFS 段后，门限结果应完全一致且保持在 -23.0±0.1 LUFS
        assertEquals(shortTail.integratedLufs, longResult.integratedLufs, 1e-6)
        assertEquals(-23.0, longResult.integratedLufs, 0.1)
    }

    @Test
    fun `相对门限排除低于主能量 10 LU 的弱信号段`() {
        val sr = 48_000
        val loud = stereoSine(sr, 4.0, -20.0)
        val quiet = stereoSine(sr, 6.0, -35.0)
        val combined = loud + quiet

        val result = R128Meter.measure(combined, sr, 2)!!
        assertEquals(-20.0, result.integratedLufs, 0.2)
    }

    @Test
    fun `多段独立滤波后合并块统一门限且保留全局峰值`() {
        val sr = 44_100
        val meter = R128Meter()
        meter.addSegment(stereoSine(sr, 4.0, -23.0), sr, 2)
        meter.addSegment(silence(sr, 6.0), sr, 2)
        meter.addSegment(stereoSine(sr, 4.0, -23.0), sr, 2)

        val result = meter.analyze()!!
        assertEquals(-23.0, result.integratedLufs, 0.1)
        assertEquals(10.0.pow(-23.0 / 20.0), result.samplePeak, 1e-4)
    }

    @Test
    fun `流式分片喂入与整块喂入结果一致`() {
        val sr = 48_000
        val pcm = stereoSine(sr, 3.0, -18.0)
        val expected = R128Meter.measure(pcm, sr, 2)!!

        val meter = R128Meter()
        val seg = R128Meter.Segment(sr, 2)
        var offset = 0
        val step = 1024
        while (offset < pcm.size) {
            val count = minOf(step, pcm.size - offset)
            seg.process(pcm, offset, count)
            offset += count
        }
        meter.addSegment(seg)
        val actual = meter.analyze()!!

        assertEquals(expected.integratedLufs, actual.integratedLufs, 1e-9)
        assertEquals(expected.samplePeak, actual.samplePeak, 1e-9)
    }

    @Test
    fun `全程静音或过短音频返回 null`() {
        val sr = 48_000
        assertNull(R128Meter.measure(silence(sr, 2.0), sr, 2))
        assertNull(R128Meter.measure(stereoSine(sr, 2.0, -80.0), sr, 2))
        assertNull(R128Meter.measure(stereoSine(sr, 0.2, -14.0), sr, 2))
        assertNull(R128Meter().analyze())
    }
}

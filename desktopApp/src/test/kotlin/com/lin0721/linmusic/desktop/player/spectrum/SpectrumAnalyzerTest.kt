package com.lin0721.linmusic.desktop.player.spectrum

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.pow
import kotlin.math.sin

class SpectrumAnalyzerTest {
    private val rate = 48_000

    private fun sine(hz: Double, seconds: Double, amplitude: Double = 0.5, start: Int = 0) =
        FloatArray((rate * seconds).toInt()) { (amplitude * sin(2 * PI * hz * (it + start) / rate)).toFloat() }

    private fun bandOf(hz: Double): Int {
        val ratio = 800.0.pow(1.0 / SPECTRUM_BANDS)
        return generateSequence(0) { it + 1 }.first { 20.0 * ratio.pow(it + 1) > hz }
    }

    private fun peak(bands: FloatArray) = bands.indices.maxBy { bands[it] }

    @Test
    fun `正弦波落在对应频段`() {
        for (hz in listOf(100.0, 1_000.0, 8_000.0)) {
            val last = SpectrumAnalyzer(rate).process(sine(hz, 1.0)).last()
            assertTrue("$hz Hz 落在 ${peak(last.bands)}", kotlin.math.abs(peak(last.bands) - bandOf(hz)) <= 1)
        }
    }

    @Test
    fun `幅度减半时电平下降 6 dB`() {
        val loud = SpectrumAnalyzer(rate).process(sine(1_000.0, 1.0, 0.5)).last().bands.max()
        val quiet = SpectrumAnalyzer(rate).process(sine(1_000.0, 1.0, 0.25)).last().bands.max()
        assertEquals(6.02f / 70f, loud - quiet, 0.01f)
    }

    @Test
    fun `满幅正弦接近满格`() {
        val level = SpectrumAnalyzer(rate).process(sine(1_000.0, 1.0, 1.0)).last().bands.max()
        assertEquals(1f, level, 0.01f)
    }

    @Test
    fun `约每秒 30 帧`() {
        val frames = SpectrumAnalyzer(rate).process(sine(440.0, 2.0))
        assertTrue("实际 ${frames.size}", frames.size in 58..60)
        assertTrue("实际 ${frames.last().timeMs}", frames.last().timeMs in 1_960L..2_000L)
    }

    @Test
    fun `攒够一个窗口前不出帧`() {
        assertTrue(SpectrumAnalyzer(rate).process(FloatArray(2_047)).isEmpty())
    }

    @Test
    fun `分块喂入和一次喂入结果相同`() {
        val samples = sine(1_000.0, 0.5)
        val whole = SpectrumAnalyzer(rate).process(samples)
        val analyzer = SpectrumAnalyzer(rate)
        val split = samples.toList().chunked(777).flatMap { analyzer.process(it.toFloatArray()) }
        assertEquals(whole.size, split.size)
        assertArrayEquals(whole.last().bands, split.last().bands, 0f)
        assertArrayEquals(whole.last().waveform, split.last().waveform, 0f)
    }

    @Test
    fun `波形是最新的采样`() {
        val samples = sine(1_000.0, 0.5)
        val frames = SpectrumAnalyzer(rate).process(samples)
        val end = (frames.last().timeMs * rate / 1000).toInt()
        val expected = samples.copyOfRange(end - SPECTRUM_WAVEFORM_SIZE, end)
        assertArrayEquals(expected, frames.last().waveform, 0f)
    }

    @Test
    fun `停声后衰减到零`() {
        val analyzer = SpectrumAnalyzer(rate)
        analyzer.process(sine(1_000.0, 0.5))
        val last = analyzer.process(FloatArray(rate * 2)).last()
        assertTrue(last.bands.all { it == 0f })
    }

    @Test
    fun `非法采样按零处理`() {
        val samples = FloatArray(rate) { if (it % 2 == 0) Float.NaN else Float.POSITIVE_INFINITY }
        val last = SpectrumAnalyzer(rate).process(samples).last()
        assertTrue(last.bands.all { it == 0f })
    }

    @Test
    fun `其他采样率也落在对应频段`() {
        val other = 44_100
        val samples = FloatArray(other) { (0.5 * sin(2 * PI * 1_000.0 * it / other)).toFloat() }
        val last = SpectrumAnalyzer(other).process(samples).last()
        assertTrue(kotlin.math.abs(peak(last.bands) - bandOf(1_000.0)) <= 1)
    }
}

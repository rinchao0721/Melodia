package com.lin0721.linmusic.feature.recognition.engine

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.Base64

class PcmMathTest {

    // ======================= RMS 与归一化 =======================

    @Test
    fun `常量信号的RMS等于其绝对值`() {
        assertEquals(0.5f, PcmMath.rms(FloatArray(100) { -0.5f }), 1e-6f)
    }

    @Test
    fun `空区间RMS为0`() {
        assertEquals(0f, PcmMath.rms(FloatArray(0)), 0f)
        assertEquals(0f, PcmMath.rms(FloatArray(10) { 1f }, 5, 5), 0f)
    }

    @Test
    fun `弱信号被放大到目标RMS`() {
        val result = PcmMath.normalize(FloatArray(100) { 0.01f })
        assertEquals(0.1f, PcmMath.rms(result), 1e-5f)
    }

    @Test
    fun `极弱信号增益封顶为50倍`() {
        val result = PcmMath.normalize(FloatArray(100) { 0.0001f })
        assertEquals(0.005f, PcmMath.rms(result), 1e-6f)
    }

    @Test
    fun `足够响的信号与全零信号原样返回`() {
        val loud = FloatArray(100) { 0.3f }
        assertSame(loud, PcmMath.normalize(loud))
        val silent = FloatArray(100)
        assertSame(silent, PcmMath.normalize(silent))
    }

    @Test
    fun `base64按小端Float32编码`() {
        val samples = floatArrayOf(0.25f, -1f, 0.5f)
        val bytes = Base64.getDecoder().decode(PcmMath.toLittleEndianBase64(samples))
        val decoded = FloatArray(samples.size)
        ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).asFloatBuffer().get(decoded)
        assertArrayEquals(samples, decoded, 0f)
    }

    // ======================= 降采样 =======================

    @Test
    fun `同采样率直通`() {
        val input = floatArrayOf(1f, 2f, 3f)
        assertArrayEquals(floatArrayOf(1f, 2f), Downsampler(8000, 8000).process(input, 2), 0f)
    }

    @Test
    fun `整数倍降采样按区间取平均`() {
        val input = floatArrayOf(1f, 3f, 5f, 7f, 9f, 11f)
        assertArrayEquals(floatArrayOf(2f, 6f, 10f), Downsampler(16000, 8000).process(input), 1e-6f)
    }

    @Test
    fun `分块输入与整段输入结果一致`() {
        val input = FloatArray(4410) { (it % 37) / 37f }
        val whole = Downsampler(44100, 8000).process(input)

        val chunked = Downsampler(44100, 8000)
        val parts = ArrayList<Float>()
        var offset = 0
        for (size in listOf(441, 1000, 7, 2962)) {
            parts += chunked.process(input.copyOfRange(offset, offset + size)).toList()
            offset += size
        }
        assertArrayEquals(whole, parts.toFloatArray(), 1e-6f)
    }

    @Test
    fun `输出样本数符合采样率比例`() {
        val output = Downsampler(48000, 8000).process(FloatArray(48000))
        assertEquals(8000, output.size)
    }
}

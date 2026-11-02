package com.lin0721.linmusic.desktop.player

import com.lin0721.linmusic.core.player.TrackEdges
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.file.Files
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test

class MpvPcmDecoderTest {

    private val rate = 22050

    // 幅度 8000 的正弦约为 -15.3dBFS
    private val toneDb = -15.26

    private lateinit var dir: File

    @Before
    fun setUp() {
        dir = Files.createTempDirectory("melodia-edge-test").toFile()
    }

    @After
    fun tearDown() {
        if (::dir.isInitialized) dir.deleteRecursively()
    }

    // 依次拼接 (秒数, 是否有声) 片段
    private fun pcm(vararg parts: Pair<Int, Boolean>): ByteArray {
        val total = parts.sumOf { it.first } * rate
        val samples = ByteBuffer.allocate(total * 2).order(ByteOrder.LITTLE_ENDIAN)
        var index = 0
        for ((seconds, audible) in parts) {
            repeat(seconds * rate) {
                val value = if (audible) (sin(2 * PI * 440.0 * index / rate) * 8000).toInt() else 0
                samples.putShort(value.toShort())
                index++
            }
        }
        return samples.array()
    }

    private fun wav(data: ByteArray, declaredSize: Int = data.size): ByteArray =
        ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN).apply {
            put("RIFF".toByteArray()); putInt(36 + data.size); put("WAVE".toByteArray())
            put("fmt ".toByteArray()); putInt(16); putShort(1); putShort(1); putInt(rate); putInt(rate * 2); putShort(2); putShort(16)
            put("data".toByteArray()); putInt(declaredSize)
        }.array() + data

    @Test
    fun `按100ms窗口计算响度并从起点偏移计时`() {
        val windows = WavLoudness.windows(wav(pcm(1 to true, 1 to false)), fromMs = 5_000L)!!
        assertEquals(20, windows.size)
        assertEquals(5_000L, windows.first().startMs)
        assertEquals(5_900L, windows[9].startMs)
        assertEquals(toneDb, windows[3].dbfs, 0.1)
        assertTrue(windows[15].dbfs.isInfinite())
    }

    @Test
    fun `未收尾的长度字段按实际剩余字节解析`() {
        assertEquals(10, WavLoudness.windows(wav(pcm(1 to true), declaredSize = 0), 0L)!!.size)
        assertEquals(10, WavLoudness.windows(wav(pcm(1 to true), declaredSize = -1), 0L)!!.size)
    }

    @Test
    fun `非wav或不支持的编码返回null`() {
        assertNull(WavLoudness.windows(ByteArray(64), 0L))
        val eightBit = wav(pcm(1 to true)).also { ByteBuffer.wrap(it).order(ByteOrder.LITTLE_ENDIAN).putShort(34, 8) }
        assertNull(WavLoudness.windows(eightBit, 0L))
    }

    private fun requireLibMpv() {
        val dll = File("native/libmpv-2.dll")
        assumeTrue("缺少 libmpv-2.dll", dll.isFile)
        System.setProperty("compose.application.resources.dir", dll.parentFile.absolutePath)
    }

    // 真实 libmpv 导出首尾片段；缺少 DLL 时跳过
    @Test
    fun `真实解码找出首尾有效位置`() {
        requireLibMpv()
        val file = File(dir, "edges.wav").apply { writeBytes(wav(pcm(3 to false, 6 to true, 4 to false))) }
        val analyzer = MpvEdgeAnalyzer(MpvPcmDecoder())
        runBlocking {
            val startMs = analyzer.effectiveStartMs(1L, file.absolutePath)!!
            assertTrue("有效开头应在 3 秒前一个窗口附近：$startMs", abs(startMs - 2_900L) <= 150L)
            val endMs = analyzer.effectiveEndMs(1L, file.absolutePath, 13_000L)!!
            assertTrue("有效结尾应在 9 秒附近：$endMs", endMs in 8_900L..9_400L)
        }
    }

    @Test
    fun `按曲长比例截取片段`() {
        requireLibMpv()
        // 前一半有声、后一半静音：25% 处为有声，75% 处为静音
        val file = File(dir, "half.wav").apply { writeBytes(wav(pcm(20 to true, 20 to false))) }
        val decoder = MpvPcmDecoder()
        runBlocking {
            val early = decoder.decode(file.absolutePath, 0.25, 2_000L)!!
            assertTrue("片段时长约 2 秒：${early.size}", early.size in 18..22)
            assertEquals(toneDb, TrackEdges.audibleLoudnessDb(early)!!, 0.5)
            val late = decoder.decode(file.absolutePath, 0.75, 2_000L)!!
            assertNull(TrackEdges.audibleLoudnessDb(late))
        }
    }
}

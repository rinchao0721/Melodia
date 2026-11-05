package com.lin0721.linmusic.core.player

import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import java.io.File
import java.nio.file.Files
import java.util.concurrent.atomic.AtomicInteger

class LoudnessTest {

    private lateinit var dir: File

    @Before
    fun setUp() {
        dir = Files.createTempDirectory("melodia-loudness").toFile()
    }

    @After
    fun tearDown() {
        dir.deleteRecursively()
    }

    private fun windows(db: Double, count: Int = 10) = List(count) { LoudnessWindow(it * TrackEdges.WINDOW_MS, db) }

    @Test
    fun `过响的歌按目标压低，过轻的歌不提升`() {
        assertEquals(-4.0, LoudnessPolicy.measuredGainDb(listOf(windows(-12.0)))!!, 1e-6)
        assertEquals(0.0, LoudnessPolicy.measuredGainDb(listOf(windows(-25.0)))!!, 1e-6)
        assertEquals(LoudnessPolicy.MIN_GAIN_DB, LoudnessPolicy.measuredGainDb(listOf(windows(-1.0)))!!, 1e-6)
        assertNull(LoudnessPolicy.measuredGainDb(listOf(windows(-80.0))))
    }

    @Test
    fun `解析 ReplayGain 标签`() {
        assertEquals(-7.32, LoudnessPolicy.replayGainDb("-7.32 dB")!!, 1e-9)
        assertEquals(0.0, LoudnessPolicy.replayGainDb("+2.10 dB")!!, 1e-9)
        assertEquals(LoudnessPolicy.MIN_GAIN_DB, LoudnessPolicy.replayGainDb("-20 dB")!!, 1e-9)
        assertNull(LoudnessPolicy.replayGainDb("dB"))
        assertNull(LoudnessPolicy.replayGainDb(null))
    }

    @Test
    fun `网易 gain 平移到本地目标，peak 为 0 视为占位`() {
        // 网易对齐约 -20.5dBFS，本地目标 -16，需上移 4.5dB
        assertEquals(-4.35, LoudnessPolicy.neteaseGainDb(-8.85, 1.0)!!, 1e-9)
        assertEquals(0.0, LoudnessPolicy.neteaseGainDb(-2.0, 1.0)!!, 1e-9)
        assertEquals(LoudnessPolicy.MIN_GAIN_DB, LoudnessPolicy.neteaseGainDb(-20.0, 0.9)!!, 1e-9)
        assertNull(LoudnessPolicy.neteaseGainDb(1.5, 0.0))
        assertNull(LoudnessPolicy.neteaseGainDb(null, 1.0))
        assertNull(LoudnessPolicy.neteaseGainDb(-8.0, null))
    }

    @Test
    fun `幅度与 mpv 音量系数换算`() {
        assertEquals(0.5012f, LoudnessPolicy.amplitude(-6.0), 1e-3f)
        assertEquals(1f, LoudnessPolicy.amplitude(0.0), 1e-6f)
        // 系数三次方后应等于幅度
        val factor = LoudnessPolicy.mpvVolumeFactor(-6.0)
        assertEquals(0.5012, factor * factor * factor, 1e-3)
    }

    @Test
    fun `缓存写盘后可重新读回`() = runBlocking {
        val file = File(dir, "gain.json")
        LoudnessStore(file).put(1L, -3.5)
        assertEquals(-3.5, LoudnessStore(file).get(1L)!!, 1e-9)
        assertNull(LoudnessStore(file).get(2L))
    }

    @Test
    fun `损坏的缓存文件按空处理且可继续写入`() = runBlocking {
        val file = File(dir, "gain.json").apply { writeText("{broken") }
        val store = LoudnessStore(file)
        assertNull(store.get(1L))
        store.put(1L, -1.0)
        assertEquals(-1.0, LoudnessStore(file).get(1L)!!, 1e-9)
    }

    @Test
    fun `超过上限时淘汰最久未用的条目`() = runBlocking {
        val store = LoudnessStore(null)
        repeat(2001) { store.put(it.toLong(), -1.0) }
        assertNull(store.get(0L))
        assertEquals(-1.0, store.get(2000L)!!, 1e-9)
    }

    @Test
    fun `自测三段并写回缓存，命中缓存不再解码`() = runBlocking {
        val calls = AtomicInteger()
        val meter = TrackLoudnessMeter(LoudnessStore(null)) { _, _, _ ->
            calls.incrementAndGet()
            windows(-10.0)
        }
        assertEquals(-6.0, meter.measure(7L, "x")!!, 1e-6)
        assertEquals(3, calls.get())
        assertEquals(-6.0, meter.measure(7L, "x")!!, 1e-6)
        assertEquals(3, calls.get())
        assertEquals(-6.0, meter.cachedGainDb(7L)!!, 1e-6)
    }

    @Test
    fun `任一片段解码失败则放弃且不写缓存`() = runBlocking {
        val meter = TrackLoudnessMeter(LoudnessStore(null)) { _, fraction, _ ->
            if (fraction == 0.5) null else windows(-10.0)
        }
        assertNull(meter.measure(7L, "x"))
        assertNull(meter.cachedGainDb(7L))
    }
}

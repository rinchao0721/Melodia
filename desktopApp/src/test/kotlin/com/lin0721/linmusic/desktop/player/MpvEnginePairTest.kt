package com.lin0721.linmusic.desktop.player

import com.lin0721.linmusic.desktop.player.mpv.MpvEngine
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.file.Files
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.PI
import kotlin.math.sin
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test

// 用无声输出的真实 libmpv 验证事件路由与交叉淡化；缺少 DLL 时跳过
class MpvEnginePairTest {

    private val positions = CopyOnWriteArrayList<Long>()
    private val ended = AtomicInteger(0)
    private lateinit var dir: File
    private lateinit var scope: CoroutineScope
    private var pair: MpvEnginePair? = null

    private val listener = object : MpvEngine.Listener {
        override fun onPositionChanged(positionMs: Long) { positions += positionMs }
        override fun onDurationChanged(durationMs: Long) = Unit
        override fun onPauseChanged(paused: Boolean) = Unit
        override fun onFileLoaded() = Unit
        override fun onEnded(isError: Boolean) { ended.incrementAndGet() }
    }

    @Before
    fun setUp() {
        val dll = File("native/libmpv-2.dll")
        assumeTrue("缺少 libmpv-2.dll", dll.isFile)
        System.setProperty("compose.application.resources.dir", dll.parentFile.absolutePath)
        dir = Files.createTempDirectory("melodia-pair-test").toFile()
        scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    }

    @After
    fun tearDown() {
        pair?.release()
        if (::scope.isInitialized) scope.cancel()
        if (::dir.isInitialized) dir.deleteRecursively()
    }

    private fun wav(name: String, seconds: Int, hz: Double): String {
        val rate = 22050
        val samples = ByteBuffer.allocate(rate * seconds * 2).order(ByteOrder.LITTLE_ENDIAN)
        repeat(rate * seconds) { samples.putShort((sin(2 * PI * hz * it / rate) * 8000).toInt().toShort()) }
        val data = samples.array()
        val header = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN).apply {
            put("RIFF".toByteArray()); putInt(36 + data.size); put("WAVE".toByteArray())
            put("fmt ".toByteArray()); putInt(16); putShort(1); putShort(1); putInt(rate); putInt(rate * 2); putShort(2); putShort(16)
            put("data".toByteArray()); putInt(data.size)
        }.array()
        val file = File(dir, name)
        ByteArrayOutputStream().use { out ->
            out.write(header); out.write(data)
            file.writeBytes(out.toByteArray())
        }
        return file.absolutePath
    }

    private fun await(timeoutMs: Long, condition: () -> Boolean): Boolean {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            if (condition()) return true
            Thread.sleep(20)
        }
        return condition()
    }

    private fun newPair(): MpvEnginePair = MpvEnginePair(listener, scope, mapOf("ao" to "null")).also { pair = it }

    @Test
    fun promoteSwitchesEventSourceAndRampsVolumes() {
        val pair = newPair()
        pair.load(wav("a.wav", 4, 440.0), 0L)
        assertTrue(await(5_000) { (positions.lastOrNull() ?: 0L) > 800L })

        assertTrue(pair.prepareSpare(wav("b.wav", 4, 660.0)))
        assertTrue("备用引擎应在超时前载入完成", await(5_000) { pair.spareReady })
        // 预载的备用引擎不应发声也不应干扰上层事件
        assertEquals(0.0, pair.volumes().second ?: -1.0, 0.01)

        val beforePromote = positions.last()
        val sizeBefore = positions.size
        val state = pair.promote(1_000L)
        assertTrue(state != null)
        assertTrue(pair.isFading)

        Thread.sleep(500)
        val (activeMid, outgoingMid) = pair.volumes()
        assertTrue("渐入中的音量应介于 0 与 100 之间：$activeMid", activeMid != null && activeMid > 5 && activeMid < 95)
        assertTrue("渐出中的音量应介于 0 与 100 之间：$outgoingMid", outgoingMid != null && outgoingMid > 5 && outgoingMid < 95)
        // 上层收到的进度来自新主引擎，从头开始计
        assertTrue(positions.drop(sizeBefore).first() < beforePromote)

        assertTrue(await(3_000) { !pair.isFading })
        assertEquals(100.0, pair.volumes().first ?: -1.0, 0.01)
        assertEquals(0, ended.get())
    }

    @Test
    fun onlyFinalEngineReportsNaturalEnd() {
        val pair = newPair()
        pair.load(wav("a.wav", 3, 440.0), 0L)
        assertTrue(await(5_000) { positions.isNotEmpty() })
        assertTrue(pair.prepareSpare(wav("b.wav", 2, 660.0)))
        assertTrue(await(5_000) { pair.spareReady })
        pair.promote(500L)

        assertTrue("新主引擎播完应触发一次结束事件", await(6_000) { ended.get() >= 1 })
        // 渐出引擎已被停止，不会再报自然结束
        Thread.sleep(2_000)
        assertEquals(1, ended.get())
    }

    @Test
    fun pauseDuringFadeFinishesRampImmediately() {
        val pair = newPair()
        pair.load(wav("a.wav", 4, 440.0), 0L)
        assertTrue(await(5_000) { positions.isNotEmpty() })
        assertTrue(pair.prepareSpare(wav("b.wav", 4, 660.0)))
        assertTrue(await(5_000) { pair.spareReady })
        pair.promote(3_000L)
        Thread.sleep(300)

        pair.setPaused(true)
        assertFalse(pair.isFading)
        assertEquals(100.0, pair.volumes().first ?: -1.0, 0.01)
    }

    @Test
    fun loadDiscardsPreparedSpare() {
        val pair = newPair()
        pair.load(wav("a.wav", 4, 440.0), 0L)
        assertTrue(await(5_000) { positions.isNotEmpty() })
        assertTrue(pair.prepareSpare(wav("b.wav", 4, 660.0)))
        assertTrue(await(5_000) { pair.spareReady })

        pair.load(wav("c.wav", 4, 880.0), 0L)
        assertFalse(pair.spareReady)
        assertEquals(null, pair.promote(500L))
    }

    // -6dB 对应 mpv 音量系数约 0.794
    @Test
    fun trackGainAppliesOnLoadAndRampsAtRuntime() {
        val pair = newPair()
        pair.load(wav("a.wav", 6, 440.0), 0L, gainDb = -6.0)
        assertTrue(await(5_000) { positions.isNotEmpty() })
        assertEquals(79.4, pair.volumes().first ?: -1.0, 0.2)

        pair.setTrackGain(0.0)
        Thread.sleep(800)
        val mid = pair.volumes().first ?: -1.0
        assertTrue("增益应平滑变化：$mid", mid > 80.0 && mid < 99.5)
        assertTrue(await(3_000) { (pair.volumes().first ?: 0.0) > 99.9 })
    }

    @Test
    fun crossfadeEndsAtIncomingTrackGain() {
        val pair = newPair()
        pair.load(wav("a.wav", 4, 440.0), 0L)
        assertTrue(await(5_000) { positions.isNotEmpty() })
        assertTrue(pair.prepareSpare(wav("b.wav", 4, 660.0), gainDb = -6.0))
        assertTrue(await(5_000) { pair.spareReady })
        pair.promote(500L)
        assertTrue(await(3_000) { !pair.isFading })
        assertEquals(79.4, pair.volumes().first ?: -1.0, 0.2)
    }
}

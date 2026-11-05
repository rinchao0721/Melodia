package com.lin0721.linmusic.desktop.player.spectrum

import com.lin0721.linmusic.desktop.platform.windows.audio.LoopbackBlock
import com.lin0721.linmusic.plugin.api.PluginApiException
import com.lin0721.linmusic.plugin.api.SpectrumFrame
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.sin

@OptIn(ExperimentalCoroutinesApi::class)
class SpectrumHubTest {
    private val rate = 48_000
    private val blockSize = rate / 50

    private fun tone(index: Int) = FloatArray(blockSize) { (0.5 * sin(2 * PI * 1_000.0 * (index * blockSize + it) / rate)).toFloat() }

    // 与真实采集相同，每 20ms 一块
    private fun source(sample: (Int) -> FloatArray): Flow<LoopbackBlock> = flow {
        var index = 0
        while (true) {
            emit(LoopbackBlock(sample(index++), rate))
            delay(20)
        }
    }

    private fun TestScope.hub(blocks: () -> Flow<LoopbackBlock>, supported: Boolean = true) =
        SpectrumHub(blocks, backgroundScope, supported)

    @Test
    fun `多个订阅者共用一路采集，全部取消后停止`() = runTest {
        var starts = 0
        var cancels = 0
        val hub = hub({
            flow {
                starts++
                try {
                    source(::tone).collect { emit(it) }
                } finally {
                    cancels++
                }
            }
        })
        assertEquals(0, starts)

        val first = launch { hub.frames.collect {} }
        val second = launch { hub.frames.collect {} }
        advanceTimeBy(1_000)
        runCurrent()
        assertEquals(1, starts)
        assertEquals(0, cancels)

        first.cancel()
        advanceTimeBy(2_000)
        runCurrent()
        assertEquals(0, cancels)

        second.cancel()
        advanceTimeBy(2_000)
        runCurrent()
        assertEquals(1, cancels)

        val third = launch { hub.frames.collect {} }
        advanceTimeBy(100)
        runCurrent()
        assertEquals(2, starts)
        third.cancel()
    }

    @Test
    fun `每个订阅者都收到帧`() = runTest {
        val hub = hub({ source(::tone) })
        val counts = IntArray(2)
        val jobs = counts.indices.map { index -> launch { hub.frames.collect { counts[index]++ } } }
        advanceTimeBy(1_000)
        runCurrent()
        jobs.forEach { it.cancel() }
        assertTrue(counts.toList().toString(), counts.all { it in 20..35 })
    }

    @Test
    fun `采集失败后重试`() = runTest {
        var attempts = 0
        val hub = hub({
            flow {
                if (++attempts == 1) throw IllegalStateException("设备不可用")
                source(::tone).collect { emit(it) }
            }
        })
        val frame = hub.frames.first()
        assertEquals(2, attempts)
        assertEquals(SPECTRUM_BANDS, frame.bands.size)
        assertEquals(SPECTRUM_WAVEFORM_SIZE, frame.waveform.size)
    }

    @Test
    fun `无声一段时间后停发，有声后恢复`() = runTest {
        val hub = hub({ source { index -> if (index in 50 until 500) FloatArray(blockSize) else tone(index) } })
        val times = mutableListOf<Long>()
        val job = launch { hub.frames.collect { times += testScheduler.currentTime } }

        advanceTimeBy(9_000)
        runCurrent()
        val lastDuringSilence = times.last()
        assertTrue("实际 $lastDuringSilence", lastDuringSilence < 4_000)

        advanceTimeBy(2_000)
        runCurrent()
        assertTrue(times.last() > 9_000)
        job.cancel()
    }

    @Test
    fun `采样率变化时换分析器`() = runTest {
        val hub = hub({
            flow {
                emit(LoopbackBlock(FloatArray(48_000 * 5) { sin(it * 0.1f) }, 48_000))
                delay(100)
                emit(LoopbackBlock(FloatArray(44_100) { sin(it * 0.1f) }, 44_100))
                awaitCancellation()
            }
        })
        val frames = ArrayList<SpectrumFrame>()
        val job = launch { hub.frames.collect { frames += it } }
        advanceTimeBy(1_000)
        runCurrent()
        job.cancel()
        assertTrue(frames.first().timeMs > 4_000)
        assertTrue(frames.last().timeMs <= 1_000)
    }

    @Test(expected = PluginApiException::class)
    fun `不支持的系统收集时报错`() = runTest {
        hub({ source(::tone) }, supported = false).frames.toList()
        Unit
    }
}

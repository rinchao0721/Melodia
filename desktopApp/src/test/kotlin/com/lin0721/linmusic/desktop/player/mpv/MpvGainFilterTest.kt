package com.lin0721.linmusic.desktop.player.mpv

import com.lin0721.linmusic.core.player.R128Meter
import com.lin0721.linmusic.desktop.player.WavLoudness
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.file.Files
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.log10
import kotlin.math.sin

class MpvGainFilterTest {

    private lateinit var dir: File

    @Before
    fun setUp() {
        dir = Files.createTempDirectory("melodia-gain-filter-test").toFile()
    }

    @After
    fun tearDown() {
        if (::dir.isInitialized) dir.deleteRecursively()
    }

    @Test
    fun buildsExpectedAfSpecAndFormatsGain() {
        assertEquals(
            "@mlgain:lavfi=[volume=-6.000dB,alimiter=limit=0.891:attack=5:release=100:level=disabled]",
            MpvGainFilter.buildAfSpec(-6.0)
        )
        assertEquals(
            "@mlgain:lavfi=[volume=3.250dB,alimiter=limit=0.891:attack=5:release=100:level=disabled]",
            MpvGainFilter.buildAfSpec(3.25)
        )
        assertEquals("0.000dB", MpvGainFilter.formatVolumeArg(Double.NaN))
        assertEquals("0.000dB", MpvGainFilter.formatVolumeArg(Double.POSITIVE_INFINITY))
    }

    @Test
    fun interpolatesGainDbWithClampedProgress() {
        assertEquals(-6.0, MpvGainFilter.interpolateGainDb(-6.0, 2.0, -0.5), 1e-6)
        assertEquals(-6.0, MpvGainFilter.interpolateGainDb(-6.0, 2.0, 0.0), 1e-6)
        assertEquals(-2.0, MpvGainFilter.interpolateGainDb(-6.0, 2.0, 0.5), 1e-6)
        assertEquals(2.0, MpvGainFilter.interpolateGainDb(-6.0, 2.0, 1.0), 1e-6)
        assertEquals(2.0, MpvGainFilter.interpolateGainDb(-6.0, 2.0, 1.5), 1e-6)
    }

    @Test
    fun computesRampProgress() {
        assertEquals(0.0, MpvGainFilter.rampProgress(0.0, 2_000L), 1e-9)
        assertEquals(0.25, MpvGainFilter.rampProgress(500.0, 2_000L), 1e-9)
        assertEquals(1.0, MpvGainFilter.rampProgress(5_000.0, 2_000L), 1e-9)
        assertEquals(1.0, MpvGainFilter.rampProgress(10.0, 0L), 1e-9)
    }

    private fun requireLibMpv() {
        val dll = File("native/libmpv-2.dll")
        assumeTrue("缺少 libmpv-2.dll", dll.isFile)
        System.setProperty("compose.application.resources.dir", dll.parentFile.absolutePath)
    }

    private fun writeSineWav(name: String, seconds: Int, amplitude: Double, rate: Int = 44100): File {
        val totalSamples = rate * seconds
        val samples = ByteBuffer.allocate(totalSamples * 2).order(ByteOrder.LITTLE_ENDIAN)
        repeat(totalSamples) { i ->
            val v = (sin(2.0 * PI * 440.0 * i / rate) * amplitude).toInt().coerceIn(-32767, 32767)
            samples.putShort(v.toShort())
        }
        val data = samples.array()
        val header = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN).apply {
            put("RIFF".toByteArray()); putInt(36 + data.size); put("WAVE".toByteArray())
            put("fmt ".toByteArray()); putInt(16); putShort(1); putShort(1); putInt(rate); putInt(rate * 2); putShort(2); putShort(16)
            put("data".toByteArray()); putInt(data.size)
        }.array()
        return File(dir, name).apply { writeBytes(header + data) }
    }

    private fun measureWavPeakAndLufs(file: File): Pair<Double, Double> {
        val meter = R128Meter()
        var seg: R128Meter.Segment? = null
        assertTrue("应成功读取输出 WAV", WavLoudness.streamPcm(file) { samples, count, rate, ch ->
            val s = seg ?: R128Meter.Segment(rate, ch).also { seg = it }
            s.process(samples, 0, count)
        })
        meter.addSegment(seg!!)
        val analysis = meter.analyze()!!
        return analysis.integratedLufs to analysis.samplePeak
    }

    @Test
    fun realMpvAppliesFilterAndAdjustsGainAtRuntime() = runBlocking {
        requireLibMpv()
        val input = writeSineWav("in.wav", seconds = 2, amplitude = 16_000.0)

        // 1. 静态验证 -6dB 滤镜确实压低了输出约 6dB
        val baselineOut = File(dir, "out_0db.wav")
        val attenOut = File(dir, "out_minus6db.wav")
        runPcmRender(input, baselineOut, gainDb = null)
        runPcmRender(input, attenOut, gainDb = -6.0)
        val (baseLufs, _) = measureWavPeakAndLufs(baselineOut)
        val (attenLufs, _) = measureWavPeakAndLufs(attenOut)
        assertEquals(-6.0, attenLufs - baseLufs, 0.5)

        // 2. 验证正增益 +12dB 时 alimiter 将峰值限制在 -1 dBFS (0.891) 左右
        val limitedOut = File(dir, "out_plus12db.wav")
        runPcmRender(input, limitedOut, gainDb = 12.0)
        val (_, limitedPeak) = measureWavPeakAndLufs(limitedOut)
        assertTrue("alimiter 应限制峰值接近 0.891，实际 $limitedPeak", limitedPeak <= 0.92)

        // 3. 播放中调整增益：-12dB 起步，暂停时升到 0dB，已缓冲的头段保持旧增益，尾段为新增益
        val runtimeOut = File(dir, "out_runtime.wav")
        val loaded = CompletableDeferred<Unit>()
        val ended = CompletableDeferred<Boolean>()
        val engine = MpvEngine(
            object : MpvEngine.Listener {
                override fun onPositionChanged(positionMs: Long) = Unit
                override fun onDurationChanged(durationMs: Long) = Unit
                override fun onPauseChanged(paused: Boolean) = Unit
                override fun onFileLoaded() { loaded.complete(Unit) }
                override fun onEnded(isError: Boolean) { ended.complete(isError) }
            },
            mapOf("ao" to "pcm", "ao-pcm-file" to runtimeOut.absolutePath, "ao-pcm-waveheader" to "yes")
        )
        try {
            val longInput = writeSineWav("long.wav", seconds = 3, amplitude = 16_000.0)
            engine.setTrackGainFilter(-12.0)
            engine.load(longInput.absolutePath, 0L, paused = true)
            assertTrue("应正常加载", withTimeoutOrNull(5_000) { loaded.await() } != null)
            Thread.sleep(400)
            if (!engine.tryLiveTrackGain(0.0)) engine.replaceTrackGain(0.0)
            Thread.sleep(100)
            engine.setPaused(false)
            assertEquals(false, withTimeoutOrNull(6_000) { ended.await() })
        } finally {
            engine.release()
        }
        val samples = readWavSamples(runtimeOut)
        val headPeak = samples.take(44100 / 20).maxOf { abs(it.toInt()) }
        val tailPeak = samples.takeLast(44100 / 2).maxOf { abs(it.toInt()) }
        val deltaDb = 20.0 * log10(tailPeak.toDouble() / headPeak.coerceAtLeast(1).toDouble())
        assertEquals(12.0, deltaDb, 1.5)
    }

    private suspend fun runPcmRender(input: File, output: File, gainDb: Double?) {
        val ended = CompletableDeferred<Boolean>()
        val engine = MpvEngine(
            object : MpvEngine.Listener {
                override fun onPositionChanged(positionMs: Long) = Unit
                override fun onDurationChanged(durationMs: Long) = Unit
                override fun onPauseChanged(paused: Boolean) = Unit
                override fun onFileLoaded() = Unit
                override fun onEnded(isError: Boolean) { ended.complete(isError) }
            },
            mapOf(
                "ao" to "pcm",
                "ao-pcm-file" to output.absolutePath,
                "ao-pcm-waveheader" to "yes",
                "untimed" to "yes"
            )
        )
        try {
            engine.setTrackGainFilter(gainDb)
            engine.load(input.absolutePath, 0L)
            assertEquals(false, withTimeoutOrNull(10_000) { ended.await() })
        } finally {
            engine.release()
        }
    }

    private fun readWavSamples(file: File): ShortArray {
        val list = ArrayList<Short>()
        WavLoudness.streamPcm(file) { samples, count, _, _ ->
            for (i in 0 until count) {
                list += (samples[i] * 32767f).toInt().toShort()
            }
        }
        return list.toShortArray()
    }
}

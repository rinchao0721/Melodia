package com.lin0721.linmusic.core.player

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Collections
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.PI
import kotlin.math.pow
import kotlin.math.sin

class TrackLoudnessMeterTest {

    private fun stereoTone(peakDbfs: Double, seconds: Double = 1.0, sampleRate: Int = 48_000): FloatArray {
        val amp = 10.0.pow(peakDbfs / 20.0)
        val frames = (sampleRate * seconds).toInt()
        val pcm = FloatArray(frames * 2)
        for (i in 0 until frames) {
            val s = (amp * sin(2.0 * PI * 997.0 * i / sampleRate)).toFloat()
            pcm[i * 2] = s
            pcm[i * 2 + 1] = s
        }
        return pcm
    }

    @Test
    fun `在线抽样三段并写回 MEASURED_SAMPLED 缓存，命中缓存不再解码`() = runBlocking {
        val calls = AtomicInteger()
        val fractions = Collections.synchronizedList(mutableListOf<Double>())
        val tone = stereoTone(-10.0)
        val meter = TrackLoudnessMeter(LoudnessStore(null), SegmentDecoder { _, fraction, spanMs, consumer ->
            calls.incrementAndGet()
            fractions += fraction
            assertEquals(LoudnessPolicy.SAMPLE_SPAN_MS, spanMs)
            consumer.onPcm(tone, tone.size, 48_000, 2)
            true
        })

        val gain = meter.measure(7L, "http://example/7.flac", fullTrack = false)!!
        assertEquals(-4.0, gain, 0.1)
        assertEquals(3, calls.get())
        assertEquals(LoudnessPolicy.SAMPLE_FRACTIONS.toSet(), fractions.toSet())
        assertEquals(LoudnessSource.MEASURED_SAMPLED, meter.cachedEntry(7L)!!.source)

        assertEquals(gain, meter.measure(7L, "http://example/7.flac", fullTrack = false)!!, 1e-9)
        assertEquals(3, calls.get())
        assertEquals(gain, meter.cachedGainDb(7L)!!, 1e-9)
    }

    @Test
    fun `本地整首测量从 0 解码到结尾并写入 MEASURED_FULL`() = runBlocking {
        val calls = AtomicInteger()
        val tone = stereoTone(-25.0)
        val meter = TrackLoudnessMeter(LoudnessStore(null), SegmentDecoder { _, fraction, spanMs, consumer ->
            calls.incrementAndGet()
            assertEquals(0.0, fraction, 1e-9)
            assertNull(spanMs)
            consumer.onPcm(tone, tone.size, 48_000, 2)
            true
        })

        val gain = meter.measure(9L, "file:///music/9.flac", fullTrack = true)!!
        assertEquals(LoudnessPolicy.MAX_BOOST_DB, gain, 1e-9)
        assertEquals(1, calls.get())
        assertEquals(LoudnessSource.MEASURED_FULL, meter.cachedEntry(9L)!!.source)
    }

    @Test
    fun `任一片段解码失败则放弃且不写缓存`() = runBlocking {
        val tone = stereoTone(-10.0)
        val meter = TrackLoudnessMeter(LoudnessStore(null), SegmentDecoder { _, fraction, _, consumer ->
            if (fraction == 0.5) return@SegmentDecoder false
            consumer.onPcm(tone, tone.size, 48_000, 2)
            true
        })
        assertNull(meter.measure(7L, "x"))
        assertNull(meter.cachedGainDb(7L))
    }

    @Test
    fun `并发测量同一首歌时共享 Deferred 结果且只解码一次`() = runBlocking {
        val segmentCalls = AtomicInteger()
        val gate = CompletableDeferred<Unit>()
        val started = AtomicInteger()
        val tone = stereoTone(-10.0)

        val meter = TrackLoudnessMeter(LoudnessStore(null), SegmentDecoder { _, _, _, consumer ->
            segmentCalls.incrementAndGet()
            started.incrementAndGet()
            gate.await()
            consumer.onPcm(tone, tone.size, 48_000, 2)
            true
        })

        val d1 = async { meter.measure(42L, "http://example/42.mp3", fullTrack = false) }
        val d2 = async { meter.measure(42L, "http://example/42.mp3", fullTrack = false) }
        while (started.get() < 3) delay(10)
        gate.complete(Unit)

        val results = listOf(d1, d2).awaitAll()
        assertNotNull(results[0])
        assertEquals(results[0]!!, results[1]!!, 1e-9)
        assertEquals(-4.0, results[0]!!, 0.1)
        assertEquals(3, segmentCalls.get())
    }

    @Test
    fun `起播前等待超时返回 null，但测量继续完成后写缓存并回调`() = runBlocking {
        val decodeGate = CompletableDeferred<Unit>()
        val delayedCallback = CompletableDeferred<Double>()
        val tone = stereoTone(-10.0)

        val meter = TrackLoudnessMeter(LoudnessStore(null), SegmentDecoder { _, _, _, consumer ->
            decodeGate.await()
            consumer.onPcm(tone, tone.size, 48_000, 2)
            true
        })

        val immediate = meter.measureBeforeStart(
            songId = 100L,
            uri = "file:///100.flac",
            fullTrack = true,
            timeoutMs = 50L,
            onDelayedResult = { delayedCallback.complete(it) }
        )
        assertNull(immediate)
        assertNull(meter.cachedGainDb(100L))

        decodeGate.complete(Unit)
        val callbackGain = delayedCallback.await()
        assertEquals(-4.0, callbackGain, 0.1)
        assertEquals(callbackGain, meter.cachedGainDb(100L)!!, 1e-9)
    }

    @Test
    fun `起播前等待在超时内完成时不重复触发延迟回调`() = runBlocking {
        var delayedCalled = false
        val tone = stereoTone(-10.0)
        val meter = TrackLoudnessMeter(LoudnessStore(null), SegmentDecoder { _, _, _, consumer ->
            consumer.onPcm(tone, tone.size, 48_000, 2)
            true
        })

        val gain = meter.measureBeforeStart(
            songId = 101L,
            uri = "file:///101.flac",
            fullTrack = true,
            timeoutMs = 2_000L,
            onDelayedResult = { delayedCalled = true }
        )!!
        assertEquals(-4.0, gain, 0.1)
        delay(50)
        assertFalse(delayedCalled)
    }

    @Test
    fun `预测队列严格串行执行并跳过已缓存曲目，cancelAll 停止后续任务`() = runBlocking {
        val store = LoudnessStore(null)
        store.put(2L, -14.0, 0.8, LoudnessSource.NETEASE)

        val activeCount = AtomicInteger()
        val maxActive = AtomicInteger()
        val decodedUris = Collections.synchronizedList(mutableListOf<String>())
        val firstStarted = CompletableDeferred<Unit>()
        val releaseFirst = CompletableDeferred<Unit>()
        val thirdFinished = CompletableDeferred<Unit>()
        val tone = stereoTone(-10.0)

        val meter = TrackLoudnessMeter(store, SegmentDecoder { uri, _, _, consumer ->
            val current = activeCount.incrementAndGet()
            maxActive.updateAndGet { prev -> maxOf(prev, current) }
            try {
                decodedUris += uri
                if (uri == "uri://1") {
                    firstStarted.complete(Unit)
                    releaseFirst.await()
                }
                consumer.onPcm(tone, tone.size, 48_000, 2)
                if (uri == "uri://3") thirdFinished.complete(Unit)
                true
            } finally {
                activeCount.decrementAndGet()
            }
        })

        meter.enqueue(
            listOf(
                LoudnessMeasureRequest(1L, "uri://1", fullTrack = true),
                LoudnessMeasureRequest(2L, "uri://2", fullTrack = true),
                LoudnessMeasureRequest(3L, "uri://3", fullTrack = true)
            )
        )
        firstStarted.await()
        releaseFirst.complete(Unit)
        thirdFinished.await()

        assertEquals(1, maxActive.get())
        assertEquals(listOf("uri://1", "uri://3"), decodedUris)

        // 验证 cancelAll 清空未开始的队列项
        val blockSong4 = CompletableDeferred<Unit>()
        val song4Started = CompletableDeferred<Unit>()
        val cancelMeter = TrackLoudnessMeter(LoudnessStore(null), SegmentDecoder { uri, _, _, consumer ->
            decodedUris += uri
            if (uri == "uri://4") {
                song4Started.complete(Unit)
                blockSong4.await()
            }
            consumer.onPcm(tone, tone.size, 48_000, 2)
            true
        })
        decodedUris.clear()
        cancelMeter.enqueue(
            listOf(
                LoudnessMeasureRequest(4L, "uri://4", fullTrack = true),
                LoudnessMeasureRequest(5L, "uri://5", fullTrack = true),
                LoudnessMeasureRequest(6L, "uri://6", fullTrack = true)
            )
        )
        song4Started.await()
        cancelMeter.cancelAll()
        blockSong4.complete(Unit)
        delay(100)

        assertEquals(listOf("uri://4"), decodedUris)
        assertTrue(cancelMeter.cachedGainDb(5L) == null && cancelMeter.cachedGainDb(6L) == null)
    }
}

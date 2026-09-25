package com.lin0721.linmusic.feature.recognition.engine

import com.lin0721.linmusic.feature.recognition.domain.AttemptStatus
import com.lin0721.linmusic.feature.recognition.domain.RecognitionCandidate
import com.lin0721.linmusic.feature.recognition.domain.RecognitionException
import com.lin0721.linmusic.feature.recognition.domain.RecognitionFailure
import com.lin0721.linmusic.feature.recognition.domain.RecognitionOutcome
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class RecognitionSessionTest {

    private val candidate = RecognitionCandidate(
        songId = 1970864953L,
        title = "红灯笼",
        artists = "洛天依, 陈秋桦",
        album = "小桦作品集",
        coverUrl = "https://example.invalid/cover.jpg",
        startTimeMs = 60_000L,
        durationMs = 240_072L
    )

    // 每 100 ms 推送 800 个样本，模拟真实麦克风节奏；记录推送了多少块，用于断言命中后停止录音
    private class FakeCapture(private val amplitude: Float) : AudioCapture {
        var emittedChunks = 0
        override fun capture() = flow {
            while (true) {
                delay(100)
                emittedChunks++
                emit(FloatArray(800) { if (it % 2 == 0) amplitude else -amplitude })
            }
        }
    }

    private class RecordingFingerprinter : FingerprintGenerator {
        var calls = 0
        override suspend fun generate(pcm: FloatArray): String {
            calls++
            return "fp$calls"
        }
    }

    @Test
    fun `第二个窗口命中后停止录音并返回候选`() = runTest {
        val capture = FakeCapture(0.2f)
        val session = RecognitionSession(capture, RecordingFingerprinter()) { fp, duration ->
            assertEquals(3, duration)
            if (fp == "fp2") listOf(candidate) else emptyList()
        }

        val outcome = session.run()

        assertTrue(outcome is RecognitionOutcome.Found)
        outcome as RecognitionOutcome.Found
        assertEquals(1, outcome.windowStartSecond)
        assertEquals(listOf(candidate), outcome.candidates)
        assertEquals(
            listOf(AttemptStatus.MISSED, AttemptStatus.HIT),
            session.progress.value.attempts.map { it.status }
        )
        assertEquals(listOf(0, 1), session.progress.value.attempts.map { it.startSecond })
        // 4 秒即命中，不应录满 8 秒
        assertTrue(capture.emittedChunks < 80)
    }

    @Test
    fun `全部窗口未命中返回NotFound且录满8秒`() = runTest {
        val fingerprinter = RecordingFingerprinter()
        val session = RecognitionSession(FakeCapture(0.2f), fingerprinter) { _, _ -> emptyList() }

        val outcome = session.run()

        assertEquals(RecognitionOutcome.NotFound, outcome)
        assertEquals(RecognitionSession.MAX_WINDOWS, fingerprinter.calls)
        val progress = session.progress.value
        assertEquals((0..5).toList(), progress.attempts.map { it.startSecond })
        assertTrue(progress.attempts.all { it.status == AttemptStatus.MISSED })
        assertEquals(RecognitionSession.LEVEL_COUNT, progress.levels.size)
        assertEquals(8_000L, progress.recordedMs)
    }

    @Test
    fun `全静音输入不计算指纹直接返回Silent`() = runTest {
        val fingerprinter = RecordingFingerprinter()
        val session = RecognitionSession(FakeCapture(0f), fingerprinter) { _, _ ->
            fail("静音窗口不应请求匹配")
            emptyList()
        }

        val outcome = session.run()

        assertEquals(RecognitionOutcome.Silent, outcome)
        assertEquals(0, fingerprinter.calls)
        assertTrue(session.progress.value.attempts.all { it.status == AttemptStatus.SILENT })
    }

    @Test
    fun `匹配失败时异常向上抛出`() = runTest {
        val session = RecognitionSession(FakeCapture(0.2f), RecordingFingerprinter()) { _, _ ->
            throw RecognitionException(RecognitionFailure.NETWORK, "断网")
        }

        try {
            session.run()
            fail("应抛出网络异常")
        } catch (e: RecognitionException) {
            assertEquals(RecognitionFailure.NETWORK, e.failure)
        }
    }

    @Test
    fun `录音异常时终止并抛出`() = runTest {
        val broken = AudioCapture {
            flow<FloatArray> { throw RecognitionException(RecognitionFailure.RECORDER_UNAVAILABLE, "被占用") }
        }
        val fingerprinter = RecordingFingerprinter()
        val session = RecognitionSession(broken, fingerprinter) { _, _ -> emptyList() }

        try {
            session.run()
            fail("应抛出录音异常")
        } catch (e: RecognitionException) {
            assertEquals(RecognitionFailure.RECORDER_UNAVAILABLE, e.failure)
        }
        assertEquals(0, fingerprinter.calls)
        assertFalse(session.progress.value.attempts.isNotEmpty())
    }
}

package com.lin0721.linmusic.desktop.data.recognition

import com.lin0721.linmusic.feature.recognition.domain.RecognitionException
import com.lin0721.linmusic.feature.recognition.domain.RecognitionFailure
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Test
import java.security.MessageDigest

class GraalAfpFingerprintEngineTest {

    // 整数 LCG 生成，保证与 Node 侧基准脚本逐位一致
    private fun referencePcm(): FloatArray {
        var x = 12345L
        return FloatArray(24_000) { i ->
            x = (x * 1_664_525L + 1_013_904_223L) % 4_294_967_296L
            ((x / 4_294_967_296.0 - 0.5) * 0.6 + 0.3 * (if (i % 40 < 20) 1 else -1)).toFloat()
        }
    }

    private fun sha256(text: String): String =
        MessageDigest.getInstance("SHA-256").digest(text.toByteArray()).joinToString("") { "%02x".format(it) }

    @Test
    fun fingerprintMatchesNodeReference() = runBlocking {
        val fingerprint = GraalAfpFingerprintEngine().generate(referencePcm())
        // 基准取自 Node 运行同一份 afp.js 的输出
        assertEquals(1176, fingerprint.length)
        assertEquals("c965aa6f8486ac8bcaec8acc773f5a837d7e62eac2a0000c99f6e120c69b398f", sha256(fingerprint))
    }

    @Test
    fun emptyPcmIsRejected() = runBlocking {
        try {
            GraalAfpFingerprintEngine().generate(FloatArray(0))
            fail("空 PCM 应抛出异常")
        } catch (e: RecognitionException) {
            assertEquals(RecognitionFailure.ENGINE_UNAVAILABLE, e.failure)
        }
    }
}

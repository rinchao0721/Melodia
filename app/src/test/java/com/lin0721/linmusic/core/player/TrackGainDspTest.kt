package com.lin0721.linmusic.core.player

import android.media.AudioDeviceInfo
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.audio.AudioOutput
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

@UnstableApi
class TrackGainDspTest {

    private val sampleRate = 48_000
    private val channels = 2

    private fun newDsp(rampMs: Long = 100L) = TrackGainDsp(rampMs).also { it.configure(sampleRate, channels) }

    private fun sine(frames: Int, amplitude: Float): FloatArray {
        val out = FloatArray(frames * channels)
        for (i in 0 until frames) {
            val v = (amplitude * sin(2.0 * PI * 440.0 * i / sampleRate)).toFloat()
            for (c in 0 until channels) out[i * channels + c] = v
        }
        return out
    }

    private fun runAll(dsp: TrackGainDsp, input: FloatArray, chunkFrames: Int = 480): FloatArray {
        val out = FloatArray(input.size)
        val total = input.size / channels
        var pos = 0
        while (pos < total) {
            val n = minOf(chunkFrames, total - pos)
            val chunk = input.copyOfRange(pos * channels, (pos + n) * channels)
            val buf = FloatArray(n * channels)
            dsp.process(chunk, n, buf)
            buf.copyInto(out, pos * channels)
            pos += n
        }
        return out
    }

    @Test
    fun `增益为 1 时直通且数据不变`() {
        val dsp = newDsp()
        assertTrue(dsp.isPassThrough())
        val input = sine(4800, 0.95f)
        val out = runAll(dsp, input)
        for (i in input.indices) assertEquals(input[i], out[i], 0f)
    }

    @Test
    fun `增益小于 1 时只缩放不压限`() {
        val dsp = newDsp()
        dsp.setGainImmediate(0.95f)
        val input = sine(4800, 0.99f)
        val out = runAll(dsp, input)
        for (i in input.indices) assertEquals(input[i] * 0.95f, out[i], 1e-6f)
        assertTrue(out.maxOf { abs(it) } > TrackGainDsp.LIMITER_THRESHOLD)
    }

    @Test
    fun `加 6 dB 满幅信号不超过限幅阈值`() {
        val dsp = newDsp()
        dsp.setGainImmediate(LoudnessPolicy.amplitude(6.0).toFloat())
        val out = runAll(dsp, sine(48_000, 1f))
        assertTrue(out.maxOf { abs(it) } <= TrackGainDsp.LIMITER_THRESHOLD + 1e-6f)
    }

    @Test
    fun `加 6 dB 小信号按线性增益放大`() {
        val dsp = newDsp()
        val gain = LoudnessPolicy.amplitude(6.0).toFloat()
        dsp.setGainImmediate(gain)
        val input = sine(48_000, 0.1f)
        val out = runAll(dsp, input)
        for (i in input.indices) assertEquals(input[i] * gain, out[i], 1e-5f)
    }

    @Test
    fun `增益变化按斜坡线性平滑过渡`() {
        val dsp = newDsp(rampMs = 100L)
        dsp.setGainImmediate(1f)
        dsp.setGainSmooth(0.5f)
        val out = runAll(dsp, FloatArray(9_600 * channels) { 1f })
        val rampFrames = sampleRate / 10
        assertEquals(1f, out[0], 0.001f)
        assertEquals(0.75f, out[(rampFrames / 2) * channels], 0.01f)
        assertEquals(0.5f, out[(rampFrames + 100) * channels], 1e-6f)
        for (i in 1 until rampFrames) assertTrue(out[i * channels] <= out[(i - 1) * channels] + 1e-6f)
    }

    @Test
    fun `立即设置增益不经过斜坡`() {
        val dsp = newDsp()
        dsp.setGainImmediate(0.5f)
        val out = runAll(dsp, FloatArray(100 * channels) { 1f })
        assertEquals(0.5f, out[0], 1e-6f)
    }

    @Test
    fun `增益回落到 1 后限幅释放完恢复直通`() {
        val dsp = newDsp(rampMs = 20L)
        dsp.setGainImmediate(2f)
        runAll(dsp, sine(48_000, 0.9f))
        dsp.setGainSmooth(1f)
        runAll(dsp, sine(96_000, 0.3f))
        assertTrue(dsp.isPassThrough())
    }

    @Test
    fun `非法增益回退为 1`() {
        val dsp = newDsp()
        dsp.setGainImmediate(Float.NaN)
        assertEquals(1f, dsp.currentGain(), 0f)
        dsp.setGainImmediate(-3f)
        assertEquals(1f, dsp.currentGain(), 0f)
    }

    @Test
    fun `输出重建后沿用当前增益`() {
        val dsp = newDsp()
        dsp.setGainImmediate(0.5f)
        dsp.configure(44_100, 2)
        dsp.flush()
        val out = runAll(dsp, FloatArray(10 * channels) { 1f })
        assertEquals(0.5f, out[0], 1e-6f)
    }

    // 每次只消费剩余数据的一半，模拟 AudioTrack 部分写入
    private class HalfWriteOutput : AudioOutput {
        val received = ByteArrayOutputStream()
        val buffers = ArrayList<ByteBuffer>()
        var writeCalls = 0

        override fun write(buffer: ByteBuffer, encodedAccessUnitCount: Int, presentationTimeUs: Long): Boolean {
            writeCalls++
            buffers += buffer
            val remaining = buffer.remaining()
            val take = if (remaining <= 8) remaining else (remaining / 2) / 8 * 8
            val bytes = ByteArray(take)
            buffer.get(bytes)
            received.write(bytes)
            return !buffer.hasRemaining()
        }

        override fun play() = Unit
        override fun pause() = Unit
        override fun flush() = Unit
        override fun stop() = Unit
        override fun release() = Unit
        override fun setVolume(volume: Float) = Unit
        override fun isOffloadedPlayback(): Boolean = false
        override fun getAudioSessionId(): Int = 0
        override fun getSampleRate(): Int = 48_000
        override fun getBufferSizeInFrames(): Long = 0L
        override fun getPositionUs(): Long = 0L
        override fun getPlaybackParameters(): PlaybackParameters = PlaybackParameters.DEFAULT
        override fun isStalled(): Boolean = false
        override fun addListener(listener: AudioOutput.Listener) = Unit
        override fun removeListener(listener: AudioOutput.Listener) = Unit
        override fun setPlaybackParameters(playbackParams: PlaybackParameters) = Unit
        override fun setOffloadDelayPadding(delayInFrames: Int, paddingInFrames: Int) = Unit
        override fun setOffloadEndOfStream() = Unit
        override fun attachAuxEffect(effectId: Int) = Unit
        override fun setAuxEffectSendLevel(level: Float) = Unit
        override fun setPreferredDevice(preferredDevice: AudioDeviceInfo?) = Unit
    }

    private fun floatBuffer(frames: Int, value: Float): ByteBuffer {
        val b = ByteBuffer.allocate(frames * channels * 4).order(ByteOrder.nativeOrder())
        repeat(frames * channels) { b.putFloat(value) }
        b.flip()
        return b
    }

    private fun shortBuffer(frames: Int, value: Short): ByteBuffer {
        val b = ByteBuffer.allocate(frames * channels * 2).order(ByteOrder.nativeOrder())
        repeat(frames * channels) { b.putShort(value) }
        b.flip()
        return b
    }

    // 反复用同一缓冲写到完成，并校验每轮 position 单调前移
    private fun writeUntilDone(output: TrackGainAudioOutput, buffer: ByteBuffer) {
        var guard = 0
        var last = buffer.position()
        while (!output.write(buffer, 1, 1_000L)) {
            assertTrue(buffer.position() > last)
            last = buffer.position()
            assertTrue(++guard < 100)
        }
        assertEquals(buffer.limit(), buffer.position())
    }

    @Test
    fun `部分写入时浮点数据只施加一次增益且 position 正确前移`() {
        val dsp = newDsp()
        dsp.setGainImmediate(0.5f)
        val delegate = HalfWriteOutput()
        val output = TrackGainAudioOutput(delegate, dsp, true, channels, channels * 4)
        val frames = 100
        val input = floatBuffer(frames, 0.8f).asReadOnlyBuffer().order(ByteOrder.nativeOrder())
        writeUntilDone(output, input)
        assertTrue(delegate.writeCalls > 1)
        val result = ByteBuffer.wrap(delegate.received.toByteArray()).order(ByteOrder.nativeOrder())
        assertEquals(frames * channels * 4, result.remaining())
        while (result.hasRemaining()) assertEquals(0.4f, result.getFloat(), 1e-6f)
    }

    @Test
    fun `部分写入时 16 位数据只施加一次增益`() {
        val dsp = newDsp()
        dsp.setGainImmediate(0.5f)
        val delegate = HalfWriteOutput()
        val output = TrackGainAudioOutput(delegate, dsp, false, channels, channels * 2)
        val frames = 100
        val input = shortBuffer(frames, 16_000)
        writeUntilDone(output, input)
        assertTrue(delegate.writeCalls > 1)
        val result = ByteBuffer.wrap(delegate.received.toByteArray()).order(ByteOrder.nativeOrder())
        assertEquals(frames * channels * 2, result.remaining())
        while (result.hasRemaining()) assertEquals(8_000, result.getShort().toInt())
    }

    @Test
    fun `增益 1 时直接把原缓冲交给委托且字节不变`() {
        val dsp = newDsp()
        val delegate = HalfWriteOutput()
        val output = TrackGainAudioOutput(delegate, dsp, false, channels, channels * 2)
        val input = shortBuffer(100, 12_345)
        val expected = ByteArray(input.remaining()).also { input.duplicate().get(it) }
        writeUntilDone(output, input)
        assertSame(input, delegate.buffers.first())
        assertArrayEquals(expected, delegate.received.toByteArray())
    }

    @Test
    fun `新一轮写入重新处理而不是误判为重试`() {
        val dsp = newDsp()
        dsp.setGainImmediate(0.5f)
        val delegate = HalfWriteOutput()
        val output = TrackGainAudioOutput(delegate, dsp, true, channels, channels * 4)
        val first = floatBuffer(50, 0.8f)
        writeUntilDone(output, first)
        val second = floatBuffer(50, 0.6f)
        writeUntilDone(output, second)
        val result = ByteBuffer.wrap(delegate.received.toByteArray()).order(ByteOrder.nativeOrder())
        repeat(50 * channels) { assertEquals(0.4f, result.getFloat(), 1e-6f) }
        repeat(50 * channels) { assertEquals(0.3f, result.getFloat(), 1e-6f) }
    }

    @Test
    fun `16 位满幅加 6 dB 经输出层后不超过阈值`() {
        val dsp = newDsp()
        dsp.setGainImmediate(LoudnessPolicy.amplitude(6.0).toFloat())
        val delegate = HalfWriteOutput()
        val output = TrackGainAudioOutput(delegate, dsp, false, channels, channels * 2)
        writeUntilDone(output, shortBuffer(4_800, 32_767))
        val result = ByteBuffer.wrap(delegate.received.toByteArray()).order(ByteOrder.nativeOrder())
        var max = 0
        while (result.hasRemaining()) max = maxOf(max, abs(result.getShort().toInt()))
        assertTrue(max <= (TrackGainDsp.LIMITER_THRESHOLD * 32_768f).toInt() + 2)
    }
}

package com.lin0721.linmusic.core.player

import android.media.AudioDeviceInfo
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.util.UnstableApi
import androidx.media3.common.util.Util
import androidx.media3.exoplayer.audio.AudioOutput
import androidx.media3.exoplayer.audio.AudioOutputProvider
import java.nio.ByteBuffer
import java.nio.ByteOrder

// 高精度源走浮点输出时会绕过 AudioProcessor，增益改在输出层施加；卸载与隧道模式不包装
@OptIn(UnstableApi::class)
internal class TrackGainAudioOutputProvider(
    private val inner: AudioOutputProvider,
    private val state: TrackGainState
) : AudioOutputProvider {

    override fun getFormatSupport(formatConfig: AudioOutputProvider.FormatConfig) = inner.getFormatSupport(formatConfig)

    override fun getOutputConfig(formatConfig: AudioOutputProvider.FormatConfig) = inner.getOutputConfig(formatConfig)

    override fun getAudioOutput(config: AudioOutputProvider.OutputConfig): AudioOutput {
        val output = inner.getAudioOutput(config)
        val pcm = config.encoding == C.ENCODING_PCM_16BIT || config.encoding == C.ENCODING_PCM_FLOAT
        if (config.isOffload || config.isTunneling || !pcm) return output
        val channels = Integer.bitCount(config.channelMask)
        if (channels <= 0 || config.sampleRate <= 0) return output
        state.dsp.configure(config.sampleRate, channels)
        return TrackGainAudioOutput(
            delegate = output,
            dsp = state.dsp,
            isFloat = config.encoding == C.ENCODING_PCM_FLOAT,
            channels = channels,
            bytesPerFrame = Util.getPcmFrameSize(config.encoding, channels)
        )
    }

    override fun addListener(listener: AudioOutputProvider.Listener) = inner.addListener(listener)

    override fun removeListener(listener: AudioOutputProvider.Listener) = inner.removeListener(listener)

    override fun hasPendingReleases(): Boolean = inner.hasPendingReleases()

    override fun release() = inner.release()
}

// 输入可能是只读的解码缓冲，处理后写入自有缓冲；委托部分写入时会用同一缓冲与时间戳重试，不重复施加增益
@OptIn(UnstableApi::class)
internal class TrackGainAudioOutput(
    private val delegate: AudioOutput,
    private val dsp: TrackGainDsp,
    private val isFloat: Boolean,
    private val channels: Int,
    private val bytesPerFrame: Int
) : AudioOutput {

    private var scratch = FloatArray(0)
    private var own: ByteBuffer = ByteBuffer.allocateDirect(0).order(ByteOrder.nativeOrder())

    private var pendingSource: ByteBuffer? = null
    private var pendingPresentationUs = 0L
    private var pendingSourceStart = 0
    private var pendingTotalBytes = 0

    override fun write(buffer: ByteBuffer, encodedAccessUnitCount: Int, presentationTimeUs: Long): Boolean {
        val retry = pendingSource === buffer && pendingPresentationUs == presentationTimeUs && own.hasRemaining()
        if (!retry) {
            clearPending()
            val frames = buffer.remaining() / bytesPerFrame
            if (frames <= 0 || dsp.isPassThrough()) {
                return delegate.write(buffer, encodedAccessUnitCount, presentationTimeUs)
            }
            prepareOwn(buffer, frames)
            pendingSource = buffer
            pendingPresentationUs = presentationTimeUs
            pendingSourceStart = buffer.position()
            pendingTotalBytes = frames * bytesPerFrame
        }
        val done = delegate.write(own, encodedAccessUnitCount, presentationTimeUs)
        if (done || !own.hasRemaining()) {
            buffer.position(buffer.limit())
            clearPending()
            return true
        }
        buffer.position(pendingSourceStart + (pendingTotalBytes - own.remaining()))
        return false
    }

    private fun prepareOwn(buffer: ByteBuffer, frames: Int) {
        val samples = frames * channels
        val bytes = frames * bytesPerFrame
        if (scratch.size < samples) scratch = FloatArray(samples)
        if (own.capacity() < bytes) own = ByteBuffer.allocateDirect(bytes).order(ByteOrder.nativeOrder())
        val source = buffer.duplicate().order(buffer.order())
        PcmGainCodec.toFloat(source, frames, channels, isFloat, scratch)
        dsp.process(scratch, frames, scratch)
        own.clear()
        PcmGainCodec.fromFloat(scratch, frames, channels, isFloat, own)
        own.flip()
    }

    private fun clearPending() {
        pendingSource = null
        own.clear().limit(0)
    }

    override fun flush() {
        clearPending()
        dsp.flush()
        delegate.flush()
    }

    override fun stop() {
        clearPending()
        delegate.stop()
    }

    override fun release() {
        clearPending()
        delegate.release()
    }

    override fun play() = delegate.play()

    override fun pause() = delegate.pause()

    override fun setVolume(volume: Float) = delegate.setVolume(volume)

    override fun isOffloadedPlayback(): Boolean = delegate.isOffloadedPlayback

    override fun getAudioSessionId(): Int = delegate.audioSessionId

    override fun getSampleRate(): Int = delegate.sampleRate

    override fun getBufferSizeInFrames(): Long = delegate.bufferSizeInFrames

    override fun getPositionUs(): Long = delegate.positionUs

    override fun getPlaybackParameters(): PlaybackParameters = delegate.playbackParameters

    override fun isStalled(): Boolean = delegate.isStalled

    override fun addListener(listener: AudioOutput.Listener) = delegate.addListener(listener)

    override fun removeListener(listener: AudioOutput.Listener) = delegate.removeListener(listener)

    override fun setPlaybackParameters(playbackParams: PlaybackParameters) = delegate.setPlaybackParameters(playbackParams)

    override fun setOffloadDelayPadding(delayInFrames: Int, paddingInFrames: Int) =
        delegate.setOffloadDelayPadding(delayInFrames, paddingInFrames)

    override fun setOffloadEndOfStream() = delegate.setOffloadEndOfStream()

    override fun attachAuxEffect(effectId: Int) = delegate.attachAuxEffect(effectId)

    override fun setAuxEffectSendLevel(level: Float) = delegate.setAuxEffectSendLevel(level)

    override fun setPreferredDevice(preferredDevice: AudioDeviceInfo?) = delegate.setPreferredDevice(preferredDevice)
}

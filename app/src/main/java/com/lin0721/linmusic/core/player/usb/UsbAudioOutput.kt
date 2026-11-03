package com.lin0721.linmusic.core.player.usb

import android.media.AudioDeviceInfo
import androidx.annotation.OptIn
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.audio.AudioOutput
import java.nio.ByteBuffer
import java.util.concurrent.CopyOnWriteArraySet

// DefaultAudioSink 写进来的 PCM 交给已接管的 DAC；进度按 DAC 实际收走的帧数计算
@OptIn(UnstableApi::class)
internal class UsbAudioOutput(
    private val session: UsbAudioSession,
    private val sampleRate: Int,
    private val frameBytes: Int,
    private val bufferFrames: Long,
    private val audioSessionId: Int,
    // 播放器音量（含音量平衡增益），由设备管理与独占音量合成后下发
    private val onPlayerVolume: (Float) -> Unit
) : AudioOutput {

    private val listeners = CopyOnWriteArraySet<AudioOutput.Listener>()
    private var writtenFrames = 0L
    private var playing = false
    private var advancingReported = false
    private var released = false

    override fun play() {
        playing = true
        session.setPaused(this, false)
    }

    override fun pause() {
        playing = false
        session.setPaused(this, true)
    }

    override fun write(buffer: ByteBuffer, encodedAccessUnitCount: Int, presentationTimeUs: Long): Boolean {
        val error = session.lastError(this)
        if (error != 0) throw AudioOutput.WriteException(error, true)
        val remaining = buffer.remaining()
        if (remaining > 0) {
            val accepted = session.write(this, buffer, buffer.position(), remaining)
            buffer.position(buffer.position() + accepted)
            writtenFrames += accepted / frameBytes
        }
        if (playing && !advancingReported && session.framesPlayed(this) > 0) {
            advancingReported = true
            val now = System.currentTimeMillis()
            listeners.forEach { it.onPositionAdvancing(now) }
        }
        return !buffer.hasRemaining()
    }

    override fun flush() {
        session.flush(this)
        writtenFrames = 0L
        advancingReported = false
    }

    // 写完的数据会继续播完，进度追上写入量后 DefaultAudioSink 判定结束
    override fun stop() = Unit

    override fun release() {
        if (released) return
        released = true
        session.stop(this)
        listeners.forEach { it.onReleased() }
    }

    override fun setVolume(volume: Float) {
        onPlayerVolume(volume)
    }

    override fun isOffloadedPlayback(): Boolean = false

    override fun getAudioSessionId(): Int = audioSessionId

    override fun getSampleRate(): Int = sampleRate

    override fun getBufferSizeInFrames(): Long = bufferFrames

    override fun getPositionUs(): Long {
        val frames = session.framesPlayed(this).coerceAtMost(writtenFrames)
        return frames * 1_000_000L / sampleRate
    }

    override fun getPlaybackParameters(): PlaybackParameters = PlaybackParameters.DEFAULT

    override fun isStalled(): Boolean = false

    override fun addListener(listener: AudioOutput.Listener) {
        listeners += listener
    }

    override fun removeListener(listener: AudioOutput.Listener) {
        listeners -= listener
    }

    override fun setPlaybackParameters(playbackParams: PlaybackParameters) = Unit

    override fun setOffloadDelayPadding(delayInFrames: Int, paddingInFrames: Int) = Unit

    override fun setOffloadEndOfStream() = Unit

    override fun attachAuxEffect(effectId: Int) = Unit

    override fun setAuxEffectSendLevel(level: Float) = Unit

    override fun setPreferredDevice(preferredDevice: AudioDeviceInfo?) = Unit
}

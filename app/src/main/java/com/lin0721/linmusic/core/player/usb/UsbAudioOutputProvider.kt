package com.lin0721.linmusic.core.player.usb

import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import androidx.media3.common.util.Util
import androidx.media3.exoplayer.audio.AudioOutput
import androidx.media3.exoplayer.audio.AudioOutputProvider
import java.util.concurrent.CopyOnWriteArraySet

private const val BUFFER_MS = 200

// DAC 能原样播放的 PCM 走 USB 直出，其余（未开独占、未授权、采样率不支持等）交给系统输出
@OptIn(UnstableApi::class)
internal class UsbAudioOutputProvider(
    private val fallback: AudioOutputProvider,
    private val manager: UsbAudioDeviceManager
) : AudioOutputProvider {

    private val listeners = CopyOnWriteArraySet<AudioOutputProvider.Listener>()
    private val availabilityListener: () -> Unit = { listeners.forEach { it.onFormatSupportChanged() } }

    init {
        manager.addAvailabilityListener(availabilityListener)
    }

    override fun getFormatSupport(formatConfig: AudioOutputProvider.FormatConfig): AudioOutputProvider.FormatSupport {
        if (!usbCapable(formatConfig.format, report = false)) {
            return withoutDirectFloat(formatConfig.format, fallback.getFormatSupport(formatConfig))
        }
        return AudioOutputProvider.FormatSupport.Builder()
            .setFormatSupportLevel(AudioOutputProvider.FORMAT_SUPPORTED_DIRECTLY)
            .build()
    }

    override fun getOutputConfig(formatConfig: AudioOutputProvider.FormatConfig): AudioOutputProvider.OutputConfig {
        val format = formatConfig.format
        if (!usbCapable(format, report = true)) return fallback.getOutputConfig(formatConfig)
        val frameBytes = Util.getPcmFrameSize(format.pcmEncoding, format.channelCount)
        return AudioOutputProvider.OutputConfig.Builder()
            .setEncoding(format.pcmEncoding)
            .setSampleRate(format.sampleRate)
            .setChannelMask(Util.getAudioTrackChannelConfig(format.channelCount))
            .setBufferSize(format.sampleRate / 1000 * BUFFER_MS * frameBytes)
            .setAudioAttributes(formatConfig.audioAttributes)
            .setAudioSessionId(formatConfig.audioSessionId)
            .build()
    }

    override fun getAudioOutput(config: AudioOutputProvider.OutputConfig): AudioOutput {
        if (config.isOffload || config.isTunneling) return fallback.getAudioOutput(config)
        val channels = Integer.bitCount(config.channelMask)
        if (!isPcm(config.encoding) || !manager.supports(config.sampleRate, channels)) {
            manager.releaseForFallback()
            return fallback.getAudioOutput(config)
        }
        val session = manager.acquireSession() ?: return fallback.getAudioOutput(config)
        val usbFormat = session.formatFor(config.sampleRate, channels) ?: return fallback.getAudioOutput(config)

        val frameBytes = Util.getPcmFrameSize(config.encoding, channels)
        val output = UsbAudioOutput(
            session = session,
            sampleRate = config.sampleRate,
            frameBytes = frameBytes,
            bufferFrames = config.sampleRate.toLong() * BUFFER_MS / 1000,
            audioSessionId = config.audioSessionId,
            onPlayerVolume = manager::setPlayerGain
        )
        if (!session.start(output, usbFormat, config.sampleRate, config.encoding, BUFFER_MS)) {
            manager.reportFallback(UsbFallbackReason.START_FAILED)
            return fallback.getAudioOutput(config)
        }
        session.setPaused(output, true)
        manager.applyVolume()
        return output
    }

    override fun addListener(listener: AudioOutputProvider.Listener) {
        listeners += listener
        fallback.addListener(listener)
    }

    override fun removeListener(listener: AudioOutputProvider.Listener) {
        listeners -= listener
        fallback.removeListener(listener)
    }

    override fun hasPendingReleases(): Boolean = fallback.hasPendingReleases()

    override fun release() {
        manager.removeAvailabilityListener(availabilityListener)
        fallback.release()
    }

    // 只对开启独占且 DAC 可用时的 PCM 生效；DAC 不支持该格式时交还系统驱动，report 为真时提示用户
    private fun usbCapable(format: Format, report: Boolean): Boolean {
        if (!manager.enabled || format.sampleMimeType != MimeTypes.AUDIO_RAW || !isPcm(format.pcmEncoding)) return false
        if (manager.supports(format.sampleRate, format.channelCount)) return true
        if (manager.knowsDevice()) {
            manager.releaseForFallback()
            if (report) manager.reportFallback(UsbFallbackReason.UNSUPPORTED_FORMAT, format.sampleRate)
        }
        return false
    }

    // DefaultAudioSink 交给输出层的只有 16 位整数与浮点（高精度源开启浮点输出后转成浮点），两者取值与原生层一致
    private fun isPcm(encoding: Int): Boolean = encoding == C.ENCODING_PCM_16BIT || encoding == C.ENCODING_PCM_FLOAT
}

// 输出层声明可直接播浮点时，MediaCodecAudioRenderer 会向解码器请求浮点输出；三星 c2.sec.flac.decoder
// 部分文件标称浮点但帧数只有一半，导致倍速与杂音（#163）。走系统输出时降为需转码，高精度源仍由
// DefaultAudioSink 自行转成浮点，USB 直出保留浮点请求以免 24 位 FLAC 被解码器截成 16 位
@OptIn(UnstableApi::class)
internal fun withoutDirectFloat(format: Format, support: AudioOutputProvider.FormatSupport): AudioOutputProvider.FormatSupport {
    val isFloatPcm = format.sampleMimeType == MimeTypes.AUDIO_RAW && format.pcmEncoding == C.ENCODING_PCM_FLOAT
    if (!isFloatPcm || support.supportLevel != AudioOutputProvider.FORMAT_SUPPORTED_DIRECTLY) return support
    return AudioOutputProvider.FormatSupport.Builder()
        .setFormatSupportLevel(AudioOutputProvider.FORMAT_SUPPORTED_WITH_TRANSCODING)
        .setIsFormatSupportedForOffload(support.isFormatSupportedForOffload)
        .setIsGaplessSupportedForOffload(support.isGaplessSupportedForOffload)
        .setIsSpeedChangeSupportedForOffload(support.isSpeedChangeSupportedForOffload)
        .build()
}

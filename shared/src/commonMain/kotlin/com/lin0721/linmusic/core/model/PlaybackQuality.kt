package com.lin0721.linmusic.core.model

import com.lin0721.linmusic.core.download.recognition.guessAudioQuality
import com.lin0721.linmusic.core.download.recognition.isDolbyAudioCodec
import com.lin0721.linmusic.core.download.recognition.mapLosslessCodecToExtension
import com.lin0721.linmusic.core.download.recognition.normalizeAudioCodec
import kotlin.math.roundToInt

// 实际音频流解码/解封装参数
data class StreamAudioInfo(
    val codec: String? = null,
    val sampleRateHz: Int? = null,
    val bitDepth: Int? = null,
    val bitrateBps: Int? = null,
    val channels: Int? = null
)

// 音频输出设备与输出格式信息
data class OutputAudioInfo(
    val deviceName: String? = null,
    val sampleRateHz: Int? = null,
    val format: String? = null,
    val resampled: Boolean? = null
)

// 当前曲目的实际播放音质快照，按 mediaId 绑定以兼容本地歌曲
data class PlaybackQuality(
    val mediaId: String,
    val declaredLevel: String? = null,
    val stream: StreamAudioInfo? = null,
    val output: OutputAudioInfo? = null
)

private val PRESERVABLE_LOSSLESS_LEVELS = setOf("jymaster", "sky", "jyeffect")

private val LOSSLESS_TIER_LEVELS = setOf("lossless", "hires", "jymaster", "sky", "jyeffect")

private val LOSSY_LEVELS = setOf("standard", "higher", "exhigh")

private val LOSSLESS_DISPLAY_LEVELS = setOf("lossless", "hires", "dolby", "jymaster", "sky", "jyeffect")

private fun StreamAudioInfo.bitrateKbps(): Int =
    bitrateBps?.takeIf { it > 0 }?.let { (it / 1000.0).roundToInt() } ?: 0

// 优先依据流参数判定实际档位；无流参数或参数不足时回退声明档位
fun resolveQualityLevel(declared: String?, stream: StreamAudioInfo?): String? {
    val declaredLevel = declared?.trim()?.takeIf { it.isNotEmpty() }
    if (stream == null) return declaredLevel

    val codec = normalizeAudioCodec(stream.codec)
    val sampleRateHz = stream.sampleRateHz?.takeIf { it > 0 } ?: 0
    val bitDepth = stream.bitDepth?.takeIf { it > 0 } ?: 0
    val bitrateKbps = stream.bitrateKbps()

    if (codec != null) {
        if (isDolbyAudioCodec(codec)) return "dolby"

        val losslessExt = mapLosslessCodecToExtension(codec)
        if (losslessExt != null) {
            if (sampleRateHz == 0 && bitDepth == 0 && bitrateKbps == 0) {
                return if (declaredLevel in LOSSLESS_TIER_LEVELS) declaredLevel else "lossless"
            }
            val guessed = guessAudioQuality(losslessExt, bitrateKbps, sampleRateHz, bitDepth)
            return if (declaredLevel in PRESERVABLE_LOSSLESS_LEVELS) declaredLevel else guessed
        }

        // 确认为有损编码，无论声明多高均按码率降级
        if (bitrateKbps > 0) {
            return guessAudioQuality(codec, bitrateKbps, sampleRateHz, bitDepth)
        }
        return if (declaredLevel in LOSSY_LEVELS) declaredLevel else guessAudioQuality(codec, 0)
    }

    // codec 缺失时按高解析参数或码率尽量推断，无法判断时回退 declared
    val inferredHires = guessAudioQuality("flac", bitrateKbps, sampleRateHz, bitDepth)
    if (inferredHires == "hires") {
        return if (declaredLevel in PRESERVABLE_LOSSLESS_LEVELS) declaredLevel else "hires"
    }
    if (bitrateKbps > 0 && (declaredLevel == null || declaredLevel in LOSSY_LEVELS)) {
        return guessAudioQuality("", bitrateKbps, sampleRateHz, bitDepth)
    }
    return declaredLevel
}

// 采样率转为 kHz 字符串并去掉多余末尾小数零
private fun formatSampleRateKhz(sampleRateHz: Int): String {
    if (sampleRateHz % 1000 == 0) return "${sampleRateHz / 1000}kHz"
    val whole = sampleRateHz / 1000
    val frac = (sampleRateHz % 1000).toString().padStart(3, '0').trimEnd('0')
    return "$whole.${frac}kHz"
}

// 生成播放音质标签文案：无损类展示位深/采样率，有损类展示码率，参数不足回退档位短标签
fun playbackQualityLabel(quality: PlaybackQuality): String? {
    val level = resolveQualityLevel(quality.declaredLevel, quality.stream) ?: return null
    val stream = quality.stream ?: return getQualityBadgeLabel(level)

    if (level in LOSSLESS_DISPLAY_LEVELS) {
        val sampleRateHz = stream.sampleRateHz?.takeIf { it > 0 }
        if (sampleRateHz != null) {
            val rateText = formatSampleRateKhz(sampleRateHz)
            val bitDepth = stream.bitDepth?.takeIf { it > 0 }
            return if (bitDepth != null) "${bitDepth}bit/$rateText" else rateText
        }
        return getQualityBadgeLabel(level)
    }

    val kbps = stream.bitrateKbps().takeIf { it > 0 }
    return if (kbps != null) "${kbps}kbps" else getQualityBadgeLabel(level)
}

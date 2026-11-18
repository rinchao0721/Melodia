package com.lin0721.linmusic.desktop.player.mpv

import com.lin0721.linmusic.core.model.OutputAudioInfo
import com.lin0721.linmusic.core.model.PlaybackQuality
import com.lin0721.linmusic.core.model.StreamAudioInfo
import com.lin0721.linmusic.desktop.player.AUTO_AUDIO_DEVICE
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.longOrNull
import kotlin.math.roundToInt

// mpv 引擎采集到的流参数与输出参数快照
data class MpvAudioInfo(
    val stream: StreamAudioInfo? = null,
    val output: OutputAudioInfo? = null
)

internal data class MpvAudioParams(
    val sampleRateHz: Int? = null,
    val channels: Int? = null,
    val format: String? = null
)

internal data class MpvTrackDemuxInfo(
    val codec: String? = null,
    val sampleRateHz: Int? = null,
    val channels: Int? = null,
    val bitrateBps: Int? = null,
    val bitDepth: Int? = null,
    val demuxFormat: String? = null
)

private val LOSSY_CODECS = setOf(
    "mp3", "aac", "opus", "vorbis", "eac3", "eac3-joc", "ac3", "ac4",
    "wmav1", "wmav2", "wmapro", "mpc", "mpc8", "speex", "amrnb", "amrwb"
)

private val DEMUX_BIT_KEYS = listOf(
    "demux-bits",
    "demux-bits-per-sample",
    "demux-bit-depth",
    "bits-per-sample",
    "demux-sample-size"
)

private val PCM_CODEC_BITS_REGEX = Regex("""pcm_[su](8|16|24|32)(?:le|be)?""")
private val INTEGER_FORMAT_BITS_REGEX = Regex("""(?:^|[su])(8|16|24|32)(?:le|be|p)?$""")

// 解析 mpv audio-params / audio-out-params JSON 对象
internal fun parseAudioParams(json: String?): MpvAudioParams? {
    val obj = parseJsonObject(json) ?: return null
    val sampleRateHz = obj.positiveInt("samplerate")
    val channels = obj.positiveInt("channel-count")
        ?: parseChannelCount(obj.nonBlankString("channels") ?: obj.nonBlankString("hr-channels"))
    val format = obj.nonBlankString("format")
    if (sampleRateHz == null && channels == null && format == null) return null
    return MpvAudioParams(sampleRateHz = sampleRateHz, channels = channels, format = format)
}

// 从 mpv track-list JSON 数组中提取当前选中音轨的 demux 字段
internal fun parseSelectedAudioTrackDemux(trackListJson: String?): MpvTrackDemuxInfo? {
    val array = parseJsonArray(trackListJson) ?: return null
    val selected = array.asSequence()
        .mapNotNull { it as? JsonObject }
        .firstOrNull { track ->
            track.nonBlankString("type")?.equals("audio", ignoreCase = true) == true &&
                (track["selected"] as? JsonPrimitive)?.booleanOrNull == true
        } ?: return null

    val codec = selected.nonBlankString("codec")
    val sampleRateHz = selected.positiveInt("demux-samplerate")
    val channels = selected.positiveInt("demux-channel-count")
        ?: parseChannelCount(selected.nonBlankString("demux-channels"))
    val bitrateBps = selected.positiveInt("demux-bitrate")
    val bitDepth = DEMUX_BIT_KEYS.firstNotNullOfOrNull { key ->
        selected.positiveInt(key)?.takeIf { it in 1..64 }
    }
    val demuxFormat = selected.nonBlankString("demux-format") ?: selected.nonBlankString("demux-sample-format")
    if (codec == null && sampleRateHz == null && channels == null && bitrateBps == null && bitDepth == null && demuxFormat == null) {
        return null
    }
    return MpvTrackDemuxInfo(
        codec = codec,
        sampleRateHz = sampleRateHz,
        channels = channels,
        bitrateBps = bitrateBps,
        bitDepth = bitDepth,
        demuxFormat = demuxFormat
    )
}

// 解析 mpv audio-bitrate 字符串（单位 bps，常见为整数或浮点数文本）
internal fun parseAudioBitrate(raw: String?): Int? {
    val value = raw?.trim()?.toDoubleOrNull() ?: return null
    if (!value.isFinite() || value <= 0.0) return null
    return value.roundToInt().takeIf { it > 0 }
}

// 结合 audio-device / current-ao / audio-device-list 解析可读输出设备名称
internal fun resolveOutputDeviceDescription(
    audioDevice: String?,
    currentAo: String?,
    audioDeviceListJson: String?
): String? {
    val device = audioDevice?.trim()?.takeIf { it.isNotEmpty() }
    val ao = currentAo?.trim()?.takeIf { it.isNotEmpty() }
    if (device == null) return ao
    if (device == AUTO_AUDIO_DEVICE) {
        return if (ao != null) "系统默认 ($ao)" else "系统默认"
    }
    val list = parseJsonArray(audioDeviceListJson)
    if (list != null) {
        val matched = list.asSequence()
            .mapNotNull { it as? JsonObject }
            .firstOrNull { it.nonBlankString("name") == device }
            ?.nonBlankString("description")
        if (matched != null) return matched
    }
    return device
}

// 优先从 track-list demux 字段取真实位深；禁止用解码后的 s32 冒充 32bit，float 格式一律置 null
internal fun extractBitDepth(
    demuxInfo: MpvTrackDemuxInfo?,
    decodedFormat: String?,
    codec: String?
): Int? {
    demuxInfo?.bitDepth?.let { return it }

    demuxInfo?.demuxFormat?.let { rawFormat ->
        if (isFloatSampleFormat(rawFormat)) return null
        parseIntegerBitDepth(rawFormat)?.let { return it }
    }

    val normalizedCodec = codec?.trim()?.lowercase()?.removePrefix("audio/")
    if (normalizedCodec != null && normalizedCodec.startsWith("pcm_")) {
        if ("f32" in normalizedCodec || "f64" in normalizedCodec) return null
        PCM_CODEC_BITS_REGEX.find(normalizedCodec)?.groupValues?.getOrNull(1)?.toIntOrNull()?.let { return it }
    }

    val fmt = decodedFormat?.trim()?.lowercase() ?: return null
    if (isFloatSampleFormat(fmt)) return null
    // 解码后 s32 常见于 24bit FLAC 升位，不能当作真实 32bit
    if (fmt.startsWith("s32") || fmt.startsWith("u32")) return null
    if (normalizedCodec != null && normalizedCodec in LOSSY_CODECS) return null
    return parseIntegerBitDepth(fmt)
}

// 汇总 mpv 多项属性为 StreamAudioInfo 与 OutputAudioInfo
internal fun parseMpvAudioInfo(
    audioCodecName: String?,
    audioParamsJson: String?,
    audioOutParamsJson: String?,
    audioBitrateRaw: String?,
    trackListJson: String?,
    currentAo: String?,
    audioDevice: String?,
    audioDeviceListJson: String?
): MpvAudioInfo {
    val demux = parseSelectedAudioTrackDemux(trackListJson)
    val inParams = parseAudioParams(audioParamsJson)
    val outParams = parseAudioParams(audioOutParamsJson)

    val codec = audioCodecName?.trim()?.takeIf { it.isNotEmpty() } ?: demux?.codec
    val sampleRateHz = inParams?.sampleRateHz ?: demux?.sampleRateHz
    val channels = inParams?.channels ?: demux?.channels
    val bitrateBps = parseAudioBitrate(audioBitrateRaw) ?: demux?.bitrateBps
    val bitDepth = extractBitDepth(demux, inParams?.format, codec)

    val stream = if (codec != null || sampleRateHz != null || bitDepth != null || bitrateBps != null || channels != null) {
        StreamAudioInfo(
            codec = codec,
            sampleRateHz = sampleRateHz,
            bitDepth = bitDepth,
            bitrateBps = bitrateBps,
            channels = channels
        )
    } else null

    val outSampleRate = outParams?.sampleRateHz
    val outFormat = outParams?.format
    val deviceName = resolveOutputDeviceDescription(audioDevice, currentAo, audioDeviceListJson)
    val resampled = if (sampleRateHz != null && outSampleRate != null) outSampleRate != sampleRateHz else null
    val output = if (deviceName != null || outSampleRate != null || outFormat != null || resampled != null) {
        OutputAudioInfo(
            deviceName = deviceName,
            sampleRateHz = outSampleRate,
            format = outFormat,
            resampled = resampled
        )
    } else null

    return MpvAudioInfo(stream = stream, output = output)
}

// 按 mediaId 合并声明档位与引擎采集到的流/输出参数
internal fun mergePlaybackQuality(
    mediaId: String,
    declaredLevel: String?,
    audioInfo: MpvAudioInfo?
): PlaybackQuality? {
    val level = declaredLevel?.trim()?.takeIf { it.isNotEmpty() }
    val stream = audioInfo?.stream
    val output = audioInfo?.output
    if (level == null && stream == null && output == null) return null
    return PlaybackQuality(
        mediaId = mediaId,
        declaredLevel = level,
        stream = stream,
        output = output
    )
}

private fun isFloatSampleFormat(format: String): Boolean {
    val lower = format.trim().lowercase()
    return lower.startsWith("float") || lower.startsWith("flt") || lower.startsWith("dbl") ||
        lower.startsWith("f32") || lower.startsWith("f64")
}

private fun parseIntegerBitDepth(format: String): Int? =
    INTEGER_FORMAT_BITS_REGEX.matchEntire(format.trim().lowercase())?.groupValues?.getOrNull(1)?.toIntOrNull()

private fun parseChannelCount(layout: String?): Int? {
    val normalized = layout?.trim()?.lowercase()?.takeIf { it.isNotEmpty() } ?: return null
    return when {
        normalized == "mono" || normalized == "1.0" -> 1
        normalized == "stereo" || normalized == "2.0" || normalized == "downmix" -> 2
        normalized == "2.1" -> 3
        normalized == "3.1" || normalized == "quad" || normalized == "4.0" -> 4
        normalized == "5.0" || normalized.startsWith("5.0(") -> 5
        normalized == "5.1" || normalized.startsWith("5.1(") -> 6
        normalized == "6.1" || normalized.startsWith("6.1(") -> 7
        normalized == "7.1" || normalized.startsWith("7.1(") -> 8
        else -> null
    }
}

private fun parseJsonObject(json: String?): JsonObject? {
    val text = json?.trim()?.takeIf { it.isNotEmpty() } ?: return null
    return runCatching { Json.parseToJsonElement(text) }.getOrNull() as? JsonObject
}

private fun parseJsonArray(json: String?): JsonArray? {
    val text = json?.trim()?.takeIf { it.isNotEmpty() } ?: return null
    return runCatching { Json.parseToJsonElement(text) }.getOrNull() as? JsonArray
}

private fun JsonObject.nonBlankString(key: String): String? =
    (this[key] as? JsonPrimitive)?.contentOrNull?.trim()?.takeIf { it.isNotEmpty() }

private fun JsonObject.positiveInt(key: String): Int? {
    val primitive = this[key] as? JsonPrimitive ?: return null
    val intValue = primitive.intOrNull
        ?: primitive.longOrNull?.takeIf { it in 1L..Int.MAX_VALUE.toLong() }?.toInt()
        ?: primitive.doubleOrNull?.takeIf { it.isFinite() && it > 0.0 && it <= Int.MAX_VALUE }?.roundToInt()
    return intValue?.takeIf { it > 0 }
}

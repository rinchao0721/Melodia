package com.lin0721.linmusic.core.download.recognition

private val LOSSLESS_EXTENSIONS = setOf("flac", "wav", "ape", "aiff", "aif", "wv")
private val CONTAINER_AUDIO_EXTENSIONS = setOf("m4a", "mp4", "m4b", "m4r", "3gp")
private val CONTAINER_CODEC_NAMES = setOf("mp4", "m4a", "x-m4a", "m4b", "m4r", "3gp", "3gpp", "octet-stream")
private val DOLBY_CODECS = setOf("eac3", "eac3-joc", "ac3", "ac4", "truehd", "true-hd")
private const val HIGHER_MIN_KBPS = 160
private const val EXHIGH_MIN_KBPS = 256
// 16bit/44.1k 的 FLAC 一般在 1100kbps 以内，读不到采样率时按码率判断高解析
private const val HIRES_MIN_KBPS = 1600
private const val CD_SAMPLE_RATE_MAX = 48_000
private const val CD_BIT_DEPTH = 16

// 判断扩展名是否为常见无损音频格式
fun isLosslessAudioExtension(extension: String): Boolean =
    extension.trim().trimStart('.').lowercase() in LOSSLESS_EXTENSIONS

// 判断扩展名是否为可封装多种有损/无损编码的音频容器格式
fun isContainerAudioExtension(extension: String): Boolean =
    extension.trim().trimStart('.').lowercase() in CONTAINER_AUDIO_EXTENSIONS

fun normalizeAudioCodec(raw: String?): String? =
    raw?.trim()?.lowercase()?.removePrefix("audio/")?.takeIf { it.isNotEmpty() }

fun isDolbyAudioCodec(rawCodec: String?): Boolean =
    normalizeAudioCodec(rawCodec)?.let { it in DOLBY_CODECS } == true

// 仅做无损编码到扩展名的映射，具体档位阈值复用 guessAudioQuality
fun mapLosslessCodecToExtension(rawCodec: String?): String? {
    val codec = normalizeAudioCodec(rawCodec) ?: return null
    return when {
        codec == "flac" || codec == "x-flac" -> "flac"
        codec == "alac" || codec == "apple lossless" || codec == "tak" || codec == "tta" || codec == "mlp" -> "flac"
        codec.startsWith("dsd") -> "flac"
        codec == "wav" || codec == "x-wav" || codec == "vnd.wave" || codec == "raw" -> "wav"
        codec == "pcm" || codec.startsWith("pcm_") || codec.startsWith("pcm-") -> "wav"
        codec == "ape" || codec == "monkeys_audio" || codec == "x-ape" -> "ape"
        codec == "wavpack" || codec == "wv" -> "wv"
        codec == "aiff" || codec == "aif" || codec == "x-aiff" -> "aiff"
        else -> null
    }
}

// 由文件扩展名与音频属性推断对应的网易下载档位；环绕声、母带无法从文件区分，最高只认 hires
fun guessAudioQuality(extension: String, bitrateKbps: Int, sampleRateHz: Int = 0, bitDepth: Int = 0): String {
    val ext = extension.trim().trimStart('.').lowercase()
    if (ext in LOSSLESS_EXTENSIONS) {
        val hires = sampleRateHz > CD_SAMPLE_RATE_MAX || bitDepth > CD_BIT_DEPTH ||
            (sampleRateHz <= 0 && bitDepth <= 0 && bitrateKbps > HIRES_MIN_KBPS)
        return if (hires) "hires" else "lossless"
    }
    return when {
        bitrateKbps >= EXHIGH_MIN_KBPS -> "exhigh"
        bitrateKbps >= HIGHER_MIN_KBPS -> "higher"
        else -> "standard"
    }
}

// 结合扩展名、容器内实际音频编码与音频参数推断本地音质档位；信息不足时返回 null
fun inferAudioQuality(
    extension: String,
    codec: String? = null,
    bitrateKbps: Int = 0,
    sampleRateHz: Int = 0,
    bitDepth: Int = 0,
    sizeBytes: Long = 0L,
    durationMs: Long = 0L
): String? {
    val ext = extension.trim().trimStart('.').lowercase()
    val effectiveBitrate = bitrateKbps.takeIf { it > 0 } ?: estimateBitrateKbps(sizeBytes, durationMs)
    val normalizedCodec = normalizeAudioCodec(codec)?.takeIf { it !in CONTAINER_CODEC_NAMES }
    if (normalizedCodec != null) {
        if (isDolbyAudioCodec(normalizedCodec)) return "dolby"
        val losslessExt = mapLosslessCodecToExtension(normalizedCodec)
        if (losslessExt != null) {
            return guessAudioQuality(losslessExt, effectiveBitrate, sampleRateHz, bitDepth)
        }
        if (effectiveBitrate <= 0) return null
        return guessAudioQuality(ext, effectiveBitrate, sampleRateHz, bitDepth)
    }
    if (ext.isBlank()) {
        if (effectiveBitrate <= 0 && sampleRateHz <= 0 && bitDepth <= 0) return null
        return guessAudioQuality("", effectiveBitrate, sampleRateHz, bitDepth)
    }
    if (isLosslessAudioExtension(ext)) {
        return guessAudioQuality(ext, effectiveBitrate, sampleRateHz, bitDepth)
    }
    if (effectiveBitrate <= 0) return null
    return guessAudioQuality(ext, effectiveBitrate, sampleRateHz, bitDepth)
}

// 读不到码率时按文件大小与时长估算平均码率
fun estimateBitrateKbps(sizeBytes: Long, durationMs: Long): Int {
    if (sizeBytes <= 0 || durationMs <= 0) return 0
    return (sizeBytes * 8 / durationMs).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
}

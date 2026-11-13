package com.lin0721.linmusic.core.download.recognition

private val LOSSLESS_EXTENSIONS = setOf("flac", "wav", "ape", "aiff", "aif", "wv")
private const val HIGHER_MIN_KBPS = 160
private const val EXHIGH_MIN_KBPS = 256
// 16bit/44.1k 的 FLAC 一般在 1100kbps 以内，读不到采样率时按码率判断高解析
private const val HIRES_MIN_KBPS = 1600
private const val CD_SAMPLE_RATE_MAX = 48_000
private const val CD_BIT_DEPTH = 16

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

// 读不到码率时按文件大小与时长估算平均码率
fun estimateBitrateKbps(sizeBytes: Long, durationMs: Long): Int {
    if (sizeBytes <= 0 || durationMs <= 0) return 0
    return (sizeBytes * 8 / durationMs).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
}

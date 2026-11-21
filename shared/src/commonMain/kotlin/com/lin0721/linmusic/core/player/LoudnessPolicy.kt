package com.lin0721.linmusic.core.player

import kotlin.math.log10
import kotlin.math.pow

// 统一响度描述：积分响度 (LUFS) 与可选线性采样峰值（<= 0 或 null 表示未知）
data class TrackLoudness(
    val lufs: Double,
    val peak: Double? = null
)

// 基于 LUFS 的曲目响度归一化策略：允许适度提升并受峰值上限保护，避免削波
object LoudnessPolicy {

    // 写入 MediaItem extras，服务侧据此设定曲目初始增益
    const val EXTRA_TRACK_GAIN_DB = "trackGainDb"

    const val TARGET_LUFS = -14.0
    const val MAX_BOOST_DB = 6.0
    const val MIN_GAIN_DB = -15.0
    const val FALLBACK_GAIN_DB = -6.0
    const val PEAK_CEILING_DBFS = -1.0
    const val PRE_START_TIMEOUT_MS = 1_500L

    // 增益变化的平滑时长
    const val RAMP_MS = 2_000L

    // 在线曲目抽样位置与每段时长
    val SAMPLE_FRACTIONS = listOf(0.25, 0.5, 0.75)
    const val SAMPLE_SPAN_MS = 10_000L

    // 网易下发 gain 的参考响度基准（初值 -14.0 LUFS，待真机样本进一步标定）
    private const val NETEASE_REFERENCE_LUFS = -14.0
    private const val REPLAY_GAIN_REFERENCE_LUFS = -18.0
    private const val R128_TAG_REFERENCE_LUFS = -23.0

    private val NUMBER_REGEX = Regex("""[-+]?\d+(?:\.\d+)?""")

    // 根据响度与采样峰值计算增益 (dB)；peak <= 0 或未知时不做峰值约束
    fun gainDb(lufs: Double, peak: Double? = null): Double {
        if (!lufs.isFinite()) return FALLBACK_GAIN_DB
        val clamped = (TARGET_LUFS - lufs).coerceIn(MIN_GAIN_DB, MAX_BOOST_DB)
        if (peak == null || !peak.isFinite() || peak <= 0.0) return clamped
        val peakCeilingGain = PEAK_CEILING_DBFS - 20.0 * log10(peak)
        return minOf(clamped, peakCeilingGain)
    }

    fun gainDb(loudness: TrackLoudness): Double = gainDb(loudness.lufs, loudness.peak)

    // 网易接口下发字段：peak <= 0 视为占位无效值返回 null
    fun fromNetease(gain: Double?, peak: Double?): TrackLoudness? {
        if (gain == null || !gain.isFinite()) return null
        if (peak == null || !peak.isFinite() || peak <= 0.0) return null
        return TrackLoudness(lufs = NETEASE_REFERENCE_LUFS - gain, peak = peak)
    }

    // ReplayGain 2.0 标签（参考 -18 LUFS），如 REPLAYGAIN_TRACK_GAIN="-7.32 dB"
    fun fromReplayGain(gainTag: String?, peakTag: String? = null): TrackLoudness? {
        val gain = parseFiniteNumber(gainTag) ?: return null
        val peak = parseFiniteNumber(peakTag)?.takeIf { it > 0.0 }
        return TrackLoudness(lufs = REPLAY_GAIN_REFERENCE_LUFS - gain, peak = peak)
    }

    // Opus R128_TRACK_GAIN 标签（Q7.8 定点整数，参考 -23 LUFS）
    fun fromR128Tag(gainTag: String?): TrackLoudness? {
        val raw = gainTag?.trim()?.toIntOrNull() ?: return null
        return TrackLoudness(lufs = R128_TAG_REFERENCE_LUFS - raw / 256.0, peak = null)
    }

    // 线性幅度增益
    fun amplitude(db: Double): Float = 10.0.pow(db / 20.0).toFloat()

    // mpv 软件音量按三次方映射为幅度，系数需开三次方（桌面第 3 步替换）
    fun mpvVolumeFactor(db: Double): Double = 10.0.pow(db / 60.0)

    private fun parseFiniteNumber(raw: String?): Double? =
        raw?.let { NUMBER_REGEX.find(it)?.value?.toDoubleOrNull() }?.takeIf { it.isFinite() }
}

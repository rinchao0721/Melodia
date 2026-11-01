package com.lin0721.linmusic.core.player

import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.roundToInt

// 播放器级增益：在系统媒体音量之上再乘一层软件增益（0~1，只能衰减）。
//
// 为什么需要：系统媒体音量在低端档位跨度偏大，蓝牙耳机（绝对音量 AVRCP）下最小非静音档
// 常常仍然偏响，靠系统音量已经压不下去。这里把 1/100 档 ≈ 0.4 dB 的细调放到应用侧，
// 系统音量（音量键 / 通知栏 / 车机）语义完全不变，两者相乘得到更细的档位。
object PlaybackGain {

    // 100 = 0 dB（完全不衰减，默认），1 = -40 dB，0 = 静音
    const val MAX_STEP = 100
    const val MIN_DB = -40.0
    const val DEFAULT_STEP = MAX_STEP

    fun normalizeStep(step: Int): Int = step.coerceIn(0, MAX_STEP)

    fun dbForStep(step: Int): Double {
        val s = normalizeStep(step)
        if (s == 0) return Double.NEGATIVE_INFINITY
        return MIN_DB * (MAX_STEP - s).toDouble() / (MAX_STEP - 1)
    }

    // 线性幅度（ExoPlayer / MediaPlayer 的 volume 语义），0f 为静音，1f 为原样
    fun amplitude(step: Int): Float {
        val s = normalizeStep(step)
        if (s == 0) return 0f
        return 10.0.pow(dbForStep(s) / 20.0).toFloat().coerceIn(0f, 1f)
    }

    fun label(step: Int): String {
        val s = normalizeStep(step)
        if (s == 0) return "静音"
        val db = dbForStep(s)
        if (s == MAX_STEP) return "0 dB"
        return "${oneDecimal(db)} dB"
    }

    private fun oneDecimal(value: Double): String {
        val scaled = (value * 10).roundToInt()
        val sign = if (scaled < 0) "-" else ""
        val absValue = abs(scaled)
        return "$sign${absValue / 10}.${absValue % 10}"
    }
}

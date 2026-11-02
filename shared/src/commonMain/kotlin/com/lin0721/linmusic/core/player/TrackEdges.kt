package com.lin0721.linmusic.core.player

import kotlin.math.log10
import kotlin.math.pow
import kotlin.math.sqrt

// 100ms 窗口的响度
data class LoudnessWindow(val startMs: Long, val dbfs: Double)

// 首尾有效位置判定，与解码解耦便于单测
object TrackEdges {
    const val WINDOW_MS = 100L
    const val AUDIBLE_DBFS = -45.0

    // 起音前留一个窗口，避免切掉起音
    private const val HEAD_PADDING_MS = 100L

    // 尾巴低于片段峰值这么多即视为结束，识别底噪大或长淡出的尾奏
    private const val TAIL_RELATIVE_DB = 30.0
    private const val SMOOTH_WINDOWS = 5

    fun dbfs(sumSquares: Double, sampleCount: Long): Double {
        if (sampleCount <= 0L || sumSquares <= 0.0) return Double.NEGATIVE_INFINITY
        return 20 * log10(sqrt(sumSquares / sampleCount))
    }

    fun effectiveEndMs(windows: List<LoudnessWindow>): Long? {
        if (windows.isEmpty()) return null
        val smoothed = smooth(windows.map { it.dbfs })
        val peak = smoothed.max()
        if (peak <= AUDIBLE_DBFS) return null
        val threshold = maxOf(AUDIBLE_DBFS, peak - TAIL_RELATIVE_DB)
        return windows[smoothed.indexOfLast { it > threshold }].startMs + WINDOW_MS
    }

    fun effectiveStartMs(windows: List<LoudnessWindow>): Long? =
        windows.firstOrNull { it.dbfs > AUDIBLE_DBFS }?.let { (it.startMs - HEAD_PADDING_MS).coerceAtLeast(0L) }

    // 只统计有声窗口的平均功率，避免句间停顿拉低响度；全程无声返回 null
    fun audibleLoudnessDb(windows: List<LoudnessWindow>): Double? {
        val audible = windows.filter { it.dbfs > AUDIBLE_DBFS }
        if (audible.isEmpty()) return null
        return toDb(audible.sumOf { toPower(it.dbfs) } / audible.size)
    }

    // 在功率域做居中滑动平均，静音窗口按零功率参与
    private fun smooth(db: List<Double>): List<Double> {
        val power = db.map(::toPower)
        val half = SMOOTH_WINDOWS / 2
        return power.indices.map { i ->
            val from = (i - half).coerceAtLeast(0)
            val to = (i + half).coerceAtMost(power.lastIndex)
            toDb((from..to).sumOf { power[it] } / (to - from + 1))
        }
    }

    private fun toPower(db: Double): Double = if (db.isInfinite()) 0.0 else 10.0.pow(db / 10)

    private fun toDb(power: Double): Double = if (power <= 0.0) Double.NEGATIVE_INFINITY else 10 * log10(power)
}

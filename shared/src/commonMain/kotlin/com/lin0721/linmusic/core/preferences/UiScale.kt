package com.lin0721.linmusic.core.preferences

// 界面缩放：以百分比存储，两端共用同一套范围与换算
object UiScale {

    const val MIN_PERCENT = 80
    const val MAX_PERCENT = 250
    const val DEFAULT_PERCENT = 100

    fun normalizePercent(percent: Int): Int = percent.coerceIn(MIN_PERCENT, MAX_PERCENT)

    fun toFactor(percent: Int): Float = normalizePercent(percent) / 100f
}

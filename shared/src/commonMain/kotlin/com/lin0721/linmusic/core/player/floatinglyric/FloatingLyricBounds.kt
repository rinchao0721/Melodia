package com.lin0721.linmusic.core.player.floatinglyric

import kotlin.math.roundToInt

// 像素坐标；旧数据没有屏幕尺寸时为 0
data class FloatingLyricBounds(
    val x: Int,
    val y: Int,
    val width: Int,
    val screenWidth: Int = 0,
    val screenHeight: Int = 0
) {
    // 按新旧屏幕比例换算并限制在屏幕内
    fun fitTo(screenWidth: Int, screenHeight: Int, minWidth: Int, viewHeight: Int): FloatingLyricBounds {
        val scaleX = if (this.screenWidth > 0) screenWidth.toFloat() / this.screenWidth else 1f
        val scaleY = if (this.screenHeight > 0) screenHeight.toFloat() / this.screenHeight else 1f
        val fittedWidth = (width * scaleX).roundToInt().coerceIn(minWidth.coerceAtMost(screenWidth), screenWidth.coerceAtLeast(1))
        return FloatingLyricBounds(
            x = (x * scaleX).roundToInt().coerceIn(0, (screenWidth - fittedWidth).coerceAtLeast(0)),
            y = (y * scaleY).roundToInt().coerceIn(0, (screenHeight - viewHeight).coerceAtLeast(0)),
            width = fittedWidth,
            screenWidth = screenWidth,
            screenHeight = screenHeight
        )
    }
}

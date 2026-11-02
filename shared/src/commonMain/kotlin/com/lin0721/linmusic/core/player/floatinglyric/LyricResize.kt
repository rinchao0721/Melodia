package com.lin0721.linmusic.core.player.floatinglyric

import kotlin.math.roundToInt

// 左右两侧只调宽度，右下角宽度与字号等比缩放
enum class LyricResizeHandle { LEFT, RIGHT, CORNER }

// 单位由调用方决定：桌面端为 dp，Android 为 px
data class LyricResizeState(val x: Float, val width: Float, val fontSizeSp: Int)

data class LyricResizeLimits(val minWidth: Float, val maxWidth: Float, val minFontSp: Int, val maxFontSp: Int)

object LyricResize {

    // dx 为本次拖动相对按下时的累计横向位移
    fun apply(handle: LyricResizeHandle, start: LyricResizeState, dx: Float, limits: LyricResizeLimits): LyricResizeState {
        val maxWidth = limits.maxWidth.coerceAtLeast(limits.minWidth)
        return when (handle) {
            LyricResizeHandle.LEFT -> {
                val width = (start.width - dx).coerceIn(limits.minWidth, maxWidth)
                // 右边缘保持不动
                start.copy(x = start.x + (start.width - width), width = width)
            }
            LyricResizeHandle.RIGHT -> start.copy(width = (start.width + dx).coerceIn(limits.minWidth, maxWidth))
            LyricResizeHandle.CORNER -> {
                val width = (start.width + dx).coerceIn(limits.minWidth, maxWidth)
                val scale = if (start.width > 0f) width / start.width else 1f
                val font = (start.fontSizeSp * scale).roundToInt().coerceIn(limits.minFontSp, limits.maxFontSp)
                start.copy(width = width, fontSizeSp = font)
            }
        }
    }
}

package com.lin0721.linmusic.desktop.ui.miniplayer

import com.lin0721.linmusic.desktop.data.MiniPlayerSize
import com.lin0721.linmusic.desktop.data.MiniPlayerStyle
import kotlin.math.roundToInt

enum class MiniResizeHandle { LEFT, RIGHT, TOP, BOTTOM, CORNER }

fun MiniPlayerStyle.allows(handle: MiniResizeHandle): Boolean = when (handle) {
    MiniResizeHandle.LEFT, MiniResizeHandle.RIGHT -> resizableWidth
    MiniResizeHandle.TOP, MiniResizeHandle.BOTTOM -> resizableHeight
    MiniResizeHandle.CORNER -> true
}

// x/y 为窗口左上角屏幕坐标（dp）
data class MiniResizeState(val x: Float, val y: Float, val size: MiniPlayerSize)

object MiniResize {

    // dx/dy 为相对按下位置的累计屏幕位移；布局尺寸的 1dp 在屏幕上占 scale × uiScale
    fun apply(
        style: MiniPlayerStyle,
        handle: MiniResizeHandle,
        start: MiniResizeState,
        dx: Float,
        dy: Float,
        uiScale: Float
    ): MiniResizeState {
        if (!style.allows(handle)) return start
        val size = start.size
        val unit = (size.scale * uiScale).takeIf { it > 0f } ?: return start
        return when (handle) {
            MiniResizeHandle.LEFT -> {
                val width = (size.width - dx / unit).roundToInt().coerceIn(style.minWidth, style.maxWidth)
                // 右边缘保持不动
                start.copy(x = start.x + (size.width - width) * unit, size = size.copy(width = width))
            }
            MiniResizeHandle.RIGHT ->
                start.copy(size = size.copy(width = (size.width + dx / unit).roundToInt().coerceIn(style.minWidth, style.maxWidth)))
            MiniResizeHandle.TOP -> {
                val height = (size.height - dy / unit).roundToInt().coerceIn(style.minHeight, style.maxHeight)
                start.copy(y = start.y + (size.height - height) * unit, size = size.copy(height = height))
            }
            MiniResizeHandle.BOTTOM ->
                start.copy(size = size.copy(height = (size.height + dy / unit).roundToInt().coerceIn(style.minHeight, style.maxHeight)))
            MiniResizeHandle.CORNER -> {
                // 位移投影到对角线方向，横竖拖都能缩放
                val span = (size.width + size.height) * unit
                val scale = (size.scale * (1f + (dx + dy) / span)).coerceIn(MiniPlayerSize.MIN_SCALE, MiniPlayerSize.MAX_SCALE)
                start.copy(size = size.copy(scale = scale))
            }
        }
    }
}

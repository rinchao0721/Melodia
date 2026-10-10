package com.lin0721.linmusic.desktop.platform

import com.lin0721.linmusic.desktop.platform.native.UiScale
import java.awt.GraphicsEnvironment
import java.awt.HeadlessException
import kotlin.math.roundToInt

const val MIN_WINDOW_WIDTH = 960
const val MIN_WINDOW_HEIGHT = 600

private const val TITLE_STRIP_HEIGHT = 40
private const val MIN_VISIBLE_WIDTH = 100

// AWT 逻辑坐标（与 Compose 的 dp 一一对应）
data class WindowBounds(val x: Int, val y: Int, val width: Int, val height: Int) {

    fun encode(): String = "$x,$y,$width,$height"

    // 标题栏条带落在某块屏幕内才可操作，显示器拔掉后窗口不会留在屏外
    fun isReachableOn(screens: List<WindowBounds>): Boolean = screens.any { screen ->
        val overlapWidth = minOf(x + width, screen.x + screen.width) - maxOf(x, screen.x)
        overlapWidth >= MIN_VISIBLE_WIDTH && y >= screen.y && y + TITLE_STRIP_HEIGHT <= screen.y + screen.height
    }

    // 夹紧到重叠面积最大的屏幕内：优先平移位置，尺寸超出屏幕时收缩。
    // 用于旧版本按像素保存的坐标改按逻辑坐标换算后可能落在屏外的场景
    fun clampInto(screens: List<WindowBounds>): WindowBounds {
        val screen = screens.maxByOrNull { candidate ->
            val overlapWidth = minOf(x + width, candidate.x + candidate.width) - maxOf(x, candidate.x)
            val overlapHeight = minOf(y + height, candidate.y + candidate.height) - maxOf(y, candidate.y)
            maxOf(0, overlapWidth) * maxOf(0, overlapHeight)
        } ?: return this

        val clampedWidth = width.coerceAtMost(screen.width)
        val clampedHeight = height.coerceAtMost(screen.height)
        return WindowBounds(
            x.coerceIn(screen.x, screen.x + screen.width - clampedWidth),
            y.coerceIn(screen.y, screen.y + screen.height - clampedHeight),
            clampedWidth,
            clampedHeight
        )
    }

    companion object {
        fun decode(raw: String): WindowBounds? {
            val parts = raw.split(',').map { it.trim().toIntOrNull() ?: return null }
            if (parts.size != 4) return null
            val (x, y, width, height) = parts
            if (width < MIN_WINDOW_WIDTH || height < MIN_WINDOW_HEIGHT) return null
            return WindowBounds(x, y, width, height)
        }
    }
}

// bounds 为最近一次浮动形态的位置与大小
data class SavedWindow(val bounds: WindowBounds?, val maximized: Boolean)

// 屏幕边界（逻辑坐标，与 WindowBounds 一致；AWT 像素按桌面缩放换算）
fun currentScreenBounds(): List<WindowBounds> = try {
    val scale = UiScale.current.factor
    GraphicsEnvironment.getLocalGraphicsEnvironment().screenDevices.map { device ->
        device.defaultConfiguration.bounds.let {
            WindowBounds(
                (it.x / scale).roundToInt(),
                (it.y / scale).roundToInt(),
                (it.width / scale).roundToInt(),
                (it.height / scale).roundToInt()
            )
        }
    }
} catch (_: HeadlessException) {
    emptyList()
}

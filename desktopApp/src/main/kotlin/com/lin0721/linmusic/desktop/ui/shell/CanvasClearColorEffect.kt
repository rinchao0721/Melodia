package com.lin0721.linmusic.desktop.ui.shell

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.window.FrameWindowScope
import com.lin0721.linmusic.core.log.AppLogger
import kotlinx.coroutines.delay
import org.jetbrains.skiko.SkiaLayer
import java.awt.Component
import java.awt.Container
import java.awt.Color as AwtColor

private const val TAG = "CanvasClearColor"
private const val DISPLAYABLE_POLL_MS = 50L

// 缩放时新帧尺寸大于 Compose 布局，多出的区域会露出 Skiko 的白色清屏色和 AWT 默认的浅灰底色。
// 画布置为透明后清屏改为透明，不透明的 D3D 交换链会把它渲染成黑色；须在交换链创建后再设置，否则窗口会变成真透明
@Composable
fun FrameWindowScope.CanvasClearColorEffect(windowBackground: Color) {
    LaunchedEffect(window) {
        while (!window.isDisplayable) delay(DISPLAYABLE_POLL_MS)
        findSkiaLayer(window)?.let { layer ->
            AppLogger.i(TAG, "实际渲染后端 ${layer.renderApi}")
            layer.transparency = true
        }
    }
    // 切换主题时同步窗口底色
    LaunchedEffect(window, windowBackground) {
        while (!window.isDisplayable) delay(DISPLAYABLE_POLL_MS)
        val background = AwtColor(windowBackground.toArgb())
        window.background = background
        window.contentPane.background = background
    }
}

private fun findSkiaLayer(component: Component): SkiaLayer? {
    if (component is SkiaLayer) return component
    if (component !is Container) return null
    for (child in component.components) {
        findSkiaLayer(child)?.let { return it }
    }
    return null
}

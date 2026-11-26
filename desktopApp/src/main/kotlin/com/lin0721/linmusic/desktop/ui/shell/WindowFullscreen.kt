package com.lin0721.linmusic.desktop.ui.shell

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.window.WindowPlacement
import androidx.compose.ui.window.WindowState
import com.lin0721.linmusic.desktop.platform.BorderlessFullscreenRestore
import com.lin0721.linmusic.desktop.platform.WindowEffects
import java.awt.Window

// 主窗口的全屏切换：支持原生无边框全屏时绕开 AWT setFullScreenWindow，退出时回到进入前的形态
@Stable
class FullscreenState internal constructor(
    private val windowState: WindowState,
    private val windowEffects: WindowEffects? = null
) {
    private var restorePlacement = WindowPlacement.Floating
    private var attachedWindow: Window? = null
    private var borderlessRestore: BorderlessFullscreenRestore? = null
    private var pendingExitRestore: BorderlessFullscreenRestore? = null

    internal var isBorderlessFullscreen by mutableStateOf(false)
        private set

    // 在 Maximized 与无边框全屏之间切换时递增，驱动 Compose 帧后与 EDT 的原生位置补同步
    internal var syncGeneration by mutableStateOf(0)
        private set

    val isFullscreen: Boolean
        get() = isBorderlessFullscreen || windowState.placement == WindowPlacement.Fullscreen

    fun attachWindow(window: Window) {
        attachedWindow = window
    }

    fun detachWindow(window: Window) {
        if (attachedWindow === window) {
            attachedWindow = null
            borderlessRestore = null
            pendingExitRestore = null
            isBorderlessFullscreen = false
        }
    }

    fun toggle() {
        if (isFullscreen) exit() else enter()
    }

    fun exit() {
        if (!isFullscreen) return
        if (isBorderlessFullscreen) {
            val window = attachedWindow
            val restore = borderlessRestore
            val effects = windowEffects
            isBorderlessFullscreen = false
            borderlessRestore = null
            windowState.placement = restorePlacement
            if (window != null && effects != null && restore != null && window.isDisplayable) {
                effects.exitBorderlessFullscreen(window, restore)
                if (restore.wasMaximized) {
                    pendingExitRestore = restore
                    syncGeneration++
                }
            }
        } else if (windowState.placement == WindowPlacement.Fullscreen) {
            windowState.placement = restorePlacement
        }
    }

    internal fun syncBorderlessWindow() {
        val window = attachedWindow ?: return
        val effects = windowEffects ?: return
        if (!window.isDisplayable) return
        if (isBorderlessFullscreen) {
            val restore = borderlessRestore ?: return
            effects.reapplyBorderlessFullscreen(window, restore)
        } else {
            val exitRestore = pendingExitRestore ?: return
            pendingExitRestore = null
            if (windowState.placement == WindowPlacement.Maximized) {
                effects.exitBorderlessFullscreen(window, exitRestore)
            }
        }
    }

    private fun enter() {
        if (isFullscreen) return
        pendingExitRestore = null
        restorePlacement = windowState.placement
        val window = attachedWindow
        val effects = windowEffects
        if (window != null && effects != null && effects.supportsBorderlessFullscreen && window.isDisplayable) {
            val restore = effects.enterBorderlessFullscreen(window)
            if (restore != null) {
                borderlessRestore = restore
                isBorderlessFullscreen = true
                if (windowState.placement != WindowPlacement.Floating) {
                    windowState.placement = WindowPlacement.Floating
                    syncGeneration++
                }
                return
            }
        }
        borderlessRestore = null
        isBorderlessFullscreen = false
        windowState.placement = WindowPlacement.Fullscreen
    }
}

@Composable
fun rememberFullscreenState(
    windowState: WindowState,
    windowEffects: WindowEffects? = null
): FullscreenState =
    remember(windowState, windowEffects) { FullscreenState(windowState, windowEffects) }

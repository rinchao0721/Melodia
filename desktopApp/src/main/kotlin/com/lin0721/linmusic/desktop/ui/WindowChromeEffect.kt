package com.lin0721.linmusic.desktop.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.window.WindowScope
import com.lin0721.linmusic.desktop.platform.native.WindowDecoration
import com.lin0721.linmusic.desktop.ui.theme.WindowBorder
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.delay
import java.awt.event.WindowAdapter
import java.awt.event.WindowEvent

private const val DISPLAYABLE_POLL_MS = 50L

// 主窗口的系统圆角与边框线；窗口未获焦点时强调色退为中性线
@Composable
fun WindowScope.WindowChromeEffect(maximized: Boolean, decoration: WindowDecoration) {
    var focused by remember(window) { mutableStateOf(window.isFocused) }
    val accent by WindowBorder.accentRgb.collectAsState()

    DisposableEffect(window) {
        val listener = object : WindowAdapter() {
            override fun windowGainedFocus(e: WindowEvent) {
                focused = true
            }

            override fun windowLostFocus(e: WindowEvent) {
                focused = false
            }
        }
        window.addWindowFocusListener(listener)
        onDispose { window.removeWindowFocusListener(listener) }
    }
    // 用户在系统设置里改色后，回到窗口时同步
    LaunchedEffect(focused) { if (focused) WindowBorder.refresh() }
    LaunchedEffect(window, decoration) {
        while (!window.isDisplayable) delay(DISPLAYABLE_POLL_MS)
        decoration.enableSystemAnimations(window)
        try {
            awaitCancellation()
        } finally {
            decoration.restoreWindowProc(window)
        }
    }
    LaunchedEffect(window, decoration, maximized, focused, accent) {
        while (!window.isDisplayable) delay(DISPLAYABLE_POLL_MS)
        decoration.applyFrame(window, maximized, WindowBorder.opaqueRgb(accent.takeIf { focused }, focused))
    }
}

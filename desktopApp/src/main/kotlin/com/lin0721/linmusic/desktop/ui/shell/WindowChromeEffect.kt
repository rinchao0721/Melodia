package com.lin0721.linmusic.desktop.ui.shell

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.window.WindowScope
import com.lin0721.linmusic.core.ui.theme.MelodiaPalette
import com.lin0721.linmusic.desktop.platform.WindowEffects
import com.lin0721.linmusic.desktop.ui.theme.WindowBorder
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.delay
import java.awt.event.WindowAdapter
import java.awt.event.WindowEvent

private const val DISPLAYABLE_POLL_MS = 50L

// 主窗口的系统圆角与边框线；窗口未获焦点时强调色退为中性线
@Composable
fun WindowScope.WindowChromeEffect(effects: WindowEffects, maximized: Boolean, palette: MelodiaPalette) {
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
    LaunchedEffect(focused) { if (focused) WindowBorder.refresh(effects) }
    LaunchedEffect(window) {
        while (!window.isDisplayable) delay(DISPLAYABLE_POLL_MS)
        effects.install(window)
        try {
            awaitCancellation()
        } finally {
            effects.uninstall(window)
        }
    }
    LaunchedEffect(window, maximized, focused, accent, palette) {
        while (!window.isDisplayable) delay(DISPLAYABLE_POLL_MS)
        effects.applyFrame(window, maximized, WindowBorder.opaqueRgb(accent.takeIf { focused }, focused, palette))
    }
}

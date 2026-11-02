package com.lin0721.linmusic.desktop.ui.plugin

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.awt.ComposePanel
import androidx.compose.ui.awt.SwingPanel
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.WindowExceptionHandler
import com.lin0721.linmusic.core.plugin.ComposePageExtension
import com.lin0721.linmusic.desktop.ui.components.DesktopOverlays
import com.lin0721.linmusic.desktop.ui.theme.DesktopColors

// Compose 没有 try-catch 式的错误边界：同一组合里抛异常会带崩整个窗口，所以插件页面单独一个 ComposePanel
// 面板是重量级组件会盖住弹层，弹层打开时缩成零尺寸让开；不移除，页面状态得以保留
@OptIn(ExperimentalComposeUiApi::class)
@Composable
internal fun PluginComposeSlot(page: ComposePageExtension, modifier: Modifier = Modifier) {
    var failure by remember(page) { mutableStateOf<Throwable?>(null) }
    val error = failure
    if (error != null) {
        Text("页面出错：${error.message ?: error.javaClass.simpleName}", color = DesktopColors.Error, fontSize = 13.sp, modifier = modifier)
        return
    }
    Box(modifier) {
        SwingPanel(
            background = DesktopColors.WindowBackground,
            modifier = if (DesktopOverlays.active > 0) Modifier.size(0.dp) else Modifier.fillMaxSize(),
            factory = {
                ComposePanel().apply {
                    exceptionHandler = WindowExceptionHandler { thrown ->
                        page.onError(thrown)
                        failure = thrown
                    }
                    setContent(page.content)
                }
            }
        )
    }
}

package com.lin0721.linmusic.desktop.ui.shell

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.window.WindowScope
import com.lin0721.linmusic.core.player.PlaybackController
import com.lin0721.linmusic.desktop.platform.TaskbarThumbnailService
import kotlinx.coroutines.delay

private const val DISPLAYABLE_POLL_MS = 50L

// 主窗口显示后接管任务栏预览小窗；窗口销毁时释放
@Composable
fun WindowScope.TaskbarThumbnailEffect(service: TaskbarThumbnailService, controller: PlaybackController) {
    LaunchedEffect(window, service, controller) {
        while (!window.isDisplayable) delay(DISPLAYABLE_POLL_MS)
        service.attach(window)
        try {
            service.bind(controller)
        } finally {
            service.detach()
        }
    }
}

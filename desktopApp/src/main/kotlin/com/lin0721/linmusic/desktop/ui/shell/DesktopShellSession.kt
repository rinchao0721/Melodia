package com.lin0721.linmusic.desktop.ui.shell

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.SaveableStateHolder
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import com.lin0721.linmusic.desktop.ui.lyricsview.LyricsViewState
import com.lin0721.linmusic.desktop.ui.navigation.DesktopFrameHost
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

// 主窗口外层的保存键，与按栈帧 id（Long）保存的页面现场互不冲突
private const val SHELL_STATE_KEY = "shell"

// 主窗口销毁重建之间保留的界面现场：返回栈与各开关、栏宽、各页滚动位置与页面 ViewModel
@Stable
class DesktopShellSession internal constructor(
    val scope: CoroutineScope,
    val pane: PaneLayoutState,
    val state: DesktopAppState,
    val frameHost: DesktopFrameHost,
    private val saveableStateHolder: SaveableStateHolder
) {
    // 侧栏等不属于任何栈帧的 rememberSaveable 状态，窗口销毁时随之存下
    @Composable
    fun Provide(content: @Composable () -> Unit) {
        saveableStateHolder.SaveableStateProvider(SHELL_STATE_KEY, content)
    }
}

// 须在主窗口之外调用，才能跨窗口重建存活
@Composable
fun rememberDesktopShellSession(deps: DesktopAppDependencies, lyricsView: LyricsViewState): DesktopShellSession {
    val scope = rememberCoroutineScope()
    val pane = rememberPaneLayoutState(deps.desktopPreferences, scope)
    val saveableStateHolder = rememberSaveableStateHolder()
    return remember(lyricsView, pane, saveableStateHolder) {
        val state = DesktopAppState(lyricsView, pane, deps.libraryViewModel.asViewControl()) { open ->
            scope.launch { deps.desktopPreferences.saveNowPlayingPanelOpen(open) }
        }
        val frameHost = DesktopFrameHost(
            saveableStateHolder = saveableStateHolder,
            liveEntryIds = { state.backStack.liveEntryIds },
            scope = scope,
            onToast = state::showMessage
        )
        DesktopShellSession(scope, pane, state, frameHost, saveableStateHolder)
    }
}

package com.lin0721.linmusic.desktop.ui.listentogether

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lin0721.linmusic.desktop.ui.components.DesktopDialog
import com.lin0721.linmusic.desktop.ui.components.DialogButton
import com.lin0721.linmusic.desktop.ui.components.copyTextToClipboard
import com.lin0721.linmusic.desktop.ui.components.readTextFromClipboard
import com.lin0721.linmusic.desktop.ui.shell.LocalDesktopNavigator
import com.lin0721.linmusic.desktop.ui.theme.DesktopColors
import com.lin0721.linmusic.feature.listentogether.ui.ListenTogetherViewModel

val LocalListenTogetherViewModel = staticCompositionLocalOf<ListenTogetherViewModel?> { null }

// 未入房时的发起 / 粘贴邀请加入对话框；进入房间后交给 onRoomReady 打开房间页
@Composable
fun ListenTogetherDialog(viewModel: ListenTogetherViewModel, onDismiss: () -> Unit, onRoomReady: () -> Unit) {
    val navigator = LocalDesktopNavigator.current
    val state by viewModel.state.collectAsState()
    val busy by viewModel.busy.collectAsState()

    LaunchedEffect(viewModel) { viewModel.resumeExisting() }
    LaunchedEffect(viewModel) { viewModel.messages.collect { navigator.showMessage(it) } }
    LaunchedEffect(state.active) { if (state.active) onRoomReady() }

    fun copyInvite(text: String) {
        if (text.isBlank()) return
        navigator.showMessage(if (copyTextToClipboard(text)) "邀请文案已复制，发给好友粘贴加入" else "复制失败")
    }

    DesktopDialog(
        title = "一起听",
        onDismiss = onDismiss,
        width = 380.dp,
        actions = {
            DialogButton("粘贴邀请加入", { viewModel.joinByInvite(readTextFromClipboard()) }, enabled = !busy)
            DialogButton("发起并复制邀请", { viewModel.startRoom(::copyInvite) }, primary = true, loading = busy)
        }
    ) {
        state.message?.let { Text(it, color = DesktopColors.TextGray, fontSize = 12.sp) }
        Text("与好友同步收听，房间队列由大家一起点歌，双方各自播放。", color = DesktopColors.TextGray, fontSize = 13.sp)
    }
}

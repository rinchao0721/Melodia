package com.lin0721.linmusic.desktop.ui.listentogether

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
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
import kotlinx.coroutines.launch

val LocalListenTogetherViewModel = staticCompositionLocalOf<ListenTogetherViewModel?> { null }

// 「一起听」对话框：未入房时发起 / 粘贴邀请加入，入房后显示成员并退出或结束
@Composable
fun ListenTogetherDialog(viewModel: ListenTogetherViewModel, onDismiss: () -> Unit) {
    val navigator = LocalDesktopNavigator.current
    val state by viewModel.state.collectAsState()
    val busy by viewModel.busy.collectAsState()
    val scope = rememberCoroutineScope()

    LaunchedEffect(viewModel) { viewModel.resumeExisting() }
    LaunchedEffect(viewModel) { viewModel.messages.collect { navigator.showMessage(it) } }

    fun copyInvite(text: String) {
        if (text.isBlank()) return
        navigator.showMessage(if (copyTextToClipboard(text)) "邀请文案已复制，发给好友粘贴加入" else "复制失败")
    }

    DesktopDialog(
        title = "一起听",
        onDismiss = onDismiss,
        width = 380.dp,
        actions = {
            if (!state.active) {
                DialogButton("粘贴邀请加入", { viewModel.joinByInvite(readTextFromClipboard()) }, enabled = !busy)
                DialogButton("发起并复制邀请", { viewModel.startRoom(::copyInvite) }, primary = true, loading = busy)
            } else {
                if (state.isHost) {
                    DialogButton("复制邀请", { scope.launch { copyInvite(viewModel.currentInviteText()) } })
                }
                DialogButton(if (state.isHost) "结束房间" else "退出", { viewModel.leave() }, danger = true)
            }
        }
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            state.message?.let { Text(it, color = DesktopColors.TextGray, fontSize = 12.sp) }
            if (!state.active) {
                Text("与好友同步播放当前队列中的网易云歌曲，双方各自播放。", color = DesktopColors.TextGray, fontSize = 13.sp)
            } else {
                Text(
                    if (state.isHost) "你是房主，好友会跟随你的播放" else "正在跟随房主播放",
                    color = DesktopColors.TextPrimary,
                    fontSize = 14.sp
                )
                if (state.members.isNotEmpty()) {
                    Text(
                        "成员：" + state.members.joinToString("、") { it.nickname.ifBlank { it.userId.toString() } },
                        color = DesktopColors.TextGray,
                        fontSize = 12.sp
                    )
                }
            }
        }
    }
}

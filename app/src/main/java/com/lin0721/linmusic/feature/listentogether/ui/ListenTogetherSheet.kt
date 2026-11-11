package com.lin0721.linmusic.feature.listentogether.ui

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.lin0721.linmusic.core.ui.components.MelodiaButton
import com.lin0721.linmusic.core.ui.components.MelodiaDragHandle
import com.lin0721.linmusic.core.ui.theme.BottomSheetShape
import com.lin0721.linmusic.core.ui.theme.MelodiaSpacing
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel

// 播放页「一起听」弹层：未入房时发起 / 粘贴邀请加入，入房后显示成员并退出或结束
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ListenTogetherSheet(
    onDismiss: () -> Unit,
    viewModel: ListenTogetherViewModel = koinViewModel()
) {
    val state by viewModel.state.collectAsState()
    val busy by viewModel.busy.collectAsState()
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) { viewModel.resumeExisting() }
    LaunchedEffect(Unit) {
        viewModel.messages.collect { Toast.makeText(context, it, Toast.LENGTH_SHORT).show() }
    }

    fun copyInvite(text: String) {
        if (text.isBlank()) return
        clipboard.setText(AnnotatedString(text))
        Toast.makeText(context, "邀请文案已复制，发给好友粘贴加入", Toast.LENGTH_SHORT).show()
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.background,
        shape = BottomSheetShape,
        dragHandle = { MelodiaDragHandle() }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(start = MelodiaSpacing.lg, end = MelodiaSpacing.lg, bottom = MelodiaSpacing.lg),
            verticalArrangement = Arrangement.spacedBy(MelodiaSpacing.md)
        ) {
            Text(
                text = "一起听",
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 20.sp,
                fontWeight = FontWeight.ExtraBold
            )
            state.message?.let {
                Text(text = it, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
            }

            if (!state.active) {
                Text(
                    text = "与好友同步播放当前队列中的网易云歌曲，双方各自播放，互不占用流量。",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 13.sp
                )
                MelodiaButton(
                    onClick = { viewModel.startRoom(::copyInvite) },
                    enabled = !busy,
                    modifier = Modifier.fillMaxWidth()
                ) { Text("发起一起听并复制邀请") }
                MelodiaButton(
                    onClick = { viewModel.joinByInvite(clipboard.getText()?.text.orEmpty()) },
                    enabled = !busy,
                    modifier = Modifier.fillMaxWidth()
                ) { Text("粘贴邀请加入") }
            } else {
                Text(
                    text = if (state.isHost) "你是房主，好友会跟随你的播放" else "正在跟随房主播放",
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = 15.sp
                )
                if (state.members.isNotEmpty()) {
                    Text(
                        text = "成员：" + state.members.joinToString("、") { it.nickname.ifBlank { it.userId.toString() } },
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 13.sp
                    )
                }
                if (state.isHost) {
                    MelodiaButton(
                        onClick = { scope.launch { copyInvite(viewModel.currentInviteText()) } },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("复制邀请文案") }
                }
                Row(modifier = Modifier.fillMaxWidth()) {
                    MelodiaButton(
                        onClick = { viewModel.leave() },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text(if (state.isHost) "结束房间" else "退出一起听") }
                }
            }
        }
    }
}

package com.lin0721.linmusic.feature.listentogether.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Group
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lin0721.linmusic.core.ui.theme.MelodiaSpacing
import com.lin0721.linmusic.core.ui.theme.uiTween
import org.koin.androidx.compose.koinViewModel

// 全屏播放页里的一起听入口：未入房弹发起/加入弹层，入房后进房间页；房间中在顶部显示状态标识
@Composable
fun ListenTogetherPlayerEntry(
    requested: Boolean,
    onRequestHandled: () -> Unit,
    viewModel: ListenTogetherViewModel = koinViewModel()
) {
    val state by viewModel.state.collectAsState()
    var showRoom by remember { mutableStateOf(false) }
    var showSetup by remember { mutableStateOf(false) }

    LaunchedEffect(requested) {
        if (!requested) return@LaunchedEffect
        if (state.active) showRoom = true else showSetup = true
        onRequestHandled()
    }
    // 发起或加入成功后从弹层切到房间页
    LaunchedEffect(state.active) {
        if (state.active && showSetup) {
            showSetup = false
            showRoom = true
        }
        if (!state.active) showRoom = false
    }

    Box(modifier = Modifier.fillMaxSize()) {
        AnimatedVisibility(
            visible = state.active && !showRoom,
            enter = fadeIn(uiTween(200)),
            exit = fadeOut(uiTween(160)),
            modifier = Modifier
                .align(Alignment.TopCenter)
                .statusBarsPadding()
                .padding(top = MelodiaSpacing.sm)
        ) {
            Row(
                modifier = Modifier
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.35f))
                    .clickable { showRoom = true }
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .clip(CircleShape)
                        .background(if (state.message != null) Color(0xFFFFC107) else Color(0xFF4CAF50))
                )
                Spacer(Modifier.width(6.dp))
                Icon(Icons.Rounded.Group, contentDescription = null, tint = Color.White, modifier = Modifier.size(14.dp))
                Spacer(Modifier.width(4.dp))
                Text(
                    text = if (state.isHost) "一起听 · ${state.members.size} 人" else "一起听 · 跟随中",
                    color = Color.White,
                    fontSize = 12.sp
                )
            }
        }
    }

    if (showSetup) {
        ListenTogetherSheet(onDismiss = { showSetup = false }, viewModel = viewModel)
    }
    if (showRoom && state.active) {
        ListenTogetherRoomScreen(viewModel = viewModel, onClose = { showRoom = false })
    }
}

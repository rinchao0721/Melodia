package com.lin0721.linmusic.feature.listentogether.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Group
import androidx.compose.material3.Icon
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
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.lin0721.linmusic.core.ui.theme.MelodiaColors
import org.koin.androidx.compose.koinViewModel

// 全屏播放页里的一起听入口：未入房弹发起/加入弹层，入房后展开房间页
@Composable
fun ListenTogetherPlayerEntry(
    requested: Boolean,
    onRequestHandled: () -> Unit,
    viewModel: ListenTogetherViewModel = koinViewModel()
) {
    val state by viewModel.state.collectAsState()
    val roomOpen by viewModel.roomOpen.collectAsState()
    var showSetup by remember { mutableStateOf(false) }

    LaunchedEffect(requested) {
        if (!requested) return@LaunchedEffect
        if (state.active) viewModel.openRoom() else showSetup = true
        onRequestHandled()
    }
    // 发起或加入成功后从弹层切到房间页
    LaunchedEffect(state.active) {
        if (state.active && showSetup) {
            showSetup = false
            viewModel.openRoom()
        }
    }

    if (showSetup) {
        ListenTogetherSheet(onDismiss = { showSetup = false }, viewModel = viewModel)
    }
    if (roomOpen && state.active) {
        ListenTogetherRoomScreen(viewModel = viewModel, onClose = viewModel::closeRoom)
    }
}

// 迷你栏封面角标：房间中显示，不在房间时不占位
@Composable
fun ListenTogetherCoverBadge(
    modifier: Modifier = Modifier,
    size: Dp = 16.dp,
    viewModel: ListenTogetherViewModel = koinViewModel()
) {
    val state by viewModel.state.collectAsState()
    if (!state.active) return
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(if (state.message != null) MelodiaColors.TextTertiary else MelodiaColors.Success),
        contentAlignment = Alignment.Center
    ) {
        Icon(Icons.Rounded.Group, contentDescription = "一起听中", tint = MelodiaColors.OnPlayer, modifier = Modifier.size(size * 0.65f))
    }
}

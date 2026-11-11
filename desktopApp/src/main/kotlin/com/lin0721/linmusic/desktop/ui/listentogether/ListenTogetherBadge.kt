package com.lin0721.linmusic.desktop.ui.listentogether

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Group
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lin0721.linmusic.desktop.ui.shell.LocalDesktopNavigator
import com.lin0721.linmusic.desktop.ui.theme.DesktopColors

// 房间中的状态标识，点击进入房间页；不在房间时不占位
@Composable
fun ListenTogetherBadge(modifier: Modifier = Modifier) {
    val viewModel = LocalListenTogetherViewModel.current ?: return
    val navigator = LocalDesktopNavigator.current
    val state by viewModel.state.collectAsState()
    if (!state.active) return
    Row(
        modifier.clip(CircleShape)
            .background(DesktopColors.SurfaceLight.copy(alpha = 0.6f))
            .pointerHoverIcon(PointerIcon.Hand)
            .clickable { navigator.openListenTogether() }
            .padding(horizontal = 10.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier.size(6.dp).clip(CircleShape)
                .background(if (state.message != null) Color(0xFFFFC107) else Color(0xFF4CAF50))
        )
        Spacer(Modifier.width(6.dp))
        Icon(Icons.Rounded.Group, null, tint = DesktopColors.TextGray, modifier = Modifier.size(14.dp))
        Spacer(Modifier.width(4.dp))
        Text(if (state.isHost) "一起听 · ${state.members.size} 人" else "一起听", color = DesktopColors.TextGray, fontSize = 12.sp)
    }
}

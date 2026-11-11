package com.lin0721.linmusic.desktop.ui.listentogether

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Group
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.unit.dp
import com.lin0721.linmusic.desktop.ui.components.Cover
import com.lin0721.linmusic.desktop.ui.components.DesktopTooltip
import com.lin0721.linmusic.desktop.ui.shell.LocalDesktopNavigator
import com.lin0721.linmusic.desktop.ui.theme.DesktopColors

private val AvatarSize = 20.dp
private val AvatarOverlap = 7.dp
private const val MAX_AVATARS = 3

// 房间中的状态标识：成员头像叠放加同步圆点，点击在房间页与上一页之间切换；不在房间时不占位
@Composable
fun ListenTogetherBadge(modifier: Modifier = Modifier) {
    val viewModel = LocalListenTogetherViewModel.current ?: return
    val navigator = LocalDesktopNavigator.current
    val state by viewModel.state.collectAsState()
    if (!state.active) return

    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val open = navigator.isListenTogetherOpen
    val fill = when {
        open -> DesktopColors.Selected
        hovered -> DesktopColors.Hover
        else -> DesktopColors.Fill
    }
    // 头像描边与胶囊同色，叠放时彼此分开
    val ring = DesktopColors.Pane
    val members = state.members.take(MAX_AVATARS)
    val tooltip = buildString {
        append(if (state.isHost) "一起听 · 你是房主" else "一起听 · 跟随中")
        if (state.members.isNotEmpty()) append("\n").append(state.members.joinToString("、") { it.nickname.ifBlank { it.userId.toString() } })
        append(if (open) "\n点击返回" else "\n点击进入房间")
    }

    DesktopTooltip(tooltip, modifier) {
        Row(
            Modifier.clip(CircleShape)
                .background(fill)
                .hoverable(interaction)
                .pointerHoverIcon(PointerIcon.Hand)
                .clickable { if (open) navigator.goBack() else navigator.openListenTogether() }
                .padding(start = 3.dp, end = 8.dp, top = 3.dp, bottom = 3.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (members.isEmpty()) {
                Box(
                    Modifier.size(AvatarSize).clip(CircleShape).background(DesktopColors.SurfaceLight),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Rounded.Group, null, tint = DesktopColors.TextGray, modifier = Modifier.size(13.dp))
                }
            } else {
                Box {
                    members.forEachIndexed { index, member ->
                        Cover(
                            member.avatarUrl.ifBlank { null },
                            AvatarSize,
                            shape = CircleShape,
                            modifier = Modifier.offset(x = (AvatarSize - AvatarOverlap) * index)
                                .border(1.5.dp, ring, CircleShape)
                        )
                    }
                }
                Spacer(Modifier.width((AvatarSize - AvatarOverlap) * (members.size - 1)))
            }
            Spacer(Modifier.width(6.dp))
            Box(
                Modifier.size(7.dp).clip(CircleShape)
                    .background(if (state.message != null) DesktopColors.TextTertiary else DesktopColors.Success)
            )
        }
    }
}

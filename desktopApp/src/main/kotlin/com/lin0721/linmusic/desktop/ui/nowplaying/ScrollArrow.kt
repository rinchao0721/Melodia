package com.lin0721.linmusic.desktop.ui.nowplaying

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.lin0721.linmusic.core.ui.theme.themedShadow
import com.lin0721.linmusic.desktop.ui.components.HoverReveal
import com.lin0721.linmusic.desktop.ui.theme.DesktopColors

val ScrollArrowSize = 28.dp
private val ArrowIconSize = 20.dp

// 悬停才出现的翻页箭头：实心深色小圆钮，带轻微阴影，横向列表与歌手轮播共用
@Composable
fun ScrollArrow(revealed: Boolean, left: Boolean, description: String, modifier: Modifier, onClick: () -> Unit) {
    HoverReveal(revealed = revealed, modifier = modifier) {
        IconButton(
            onClick = onClick,
            enabled = revealed,
            modifier = Modifier.size(ScrollArrowSize).themedShadow(4.dp, CircleShape).clip(CircleShape)
                .background(DesktopColors.Surface)
        ) {
            Icon(
                if (left) Icons.AutoMirrored.Rounded.KeyboardArrowLeft else Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                description,
                tint = DesktopColors.TextPrimary,
                modifier = Modifier.size(ArrowIconSize)
            )
        }
    }
}

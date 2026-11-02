package com.lin0721.linmusic.feature.player.ui

import androidx.compose.material.icons.rounded.AllInclusive
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.MoreVert
import android.media.AudioDeviceInfo
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.PlaylistPlay
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.SpeakerGroup
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lin0721.linmusic.core.player.PlayMode
import com.lin0721.linmusic.core.plugin.CONTROL_PLAY_MODE
import com.lin0721.linmusic.core.ui.components.MelodiaIconButton
import com.lin0721.linmusic.core.ui.interaction.pressable
import com.lin0721.linmusic.core.ui.theme.MelodiaColors
import com.lin0721.linmusic.core.ui.theme.MelodiaPress
import com.lin0721.linmusic.core.ui.theme.InfoCardRadius
import com.lin0721.linmusic.core.ui.theme.MelodiaSpacing

import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight

// 插件布局用的操作行：按给定按钮与顺序排布
@Composable
fun PlayerActionRow(
    buttons: List<String>,
    playMode: PlayMode,
    onCyclePlayMode: () -> Unit,
    onOutputDeviceClick: () -> Unit,
    onQueueClick: () -> Unit,
    onShareClick: () -> Unit,
    onMoreClick: () -> Unit,
    connectedDevice: AudioDeviceInfo?,
    qualityLabel: String?,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(MelodiaSpacing.xs),
        verticalAlignment = Alignment.CenterVertically
    ) {
        buttons.forEach { button ->
            when (button) {
                // 播放模式：图标随模式变化，点击在 顺序 → 列表循环 → 单曲循环 → 随机 之间轮转
                CONTROL_PLAY_MODE -> MelodiaIconButton(onClick = onCyclePlayMode) {
                    Icon(
                        playMode.modeIcon(),
                        contentDescription = playMode.modeLabel(),
                        tint = MelodiaColors.TextSecondary,
                        modifier = Modifier.size(24.dp)
                    )
                }
                "device" -> MelodiaIconButton(onClick = onOutputDeviceClick) {
                    Icon(
                        connectedDevice?.let { deviceIcon(it.type) } ?: Icons.Rounded.SpeakerGroup,
                        contentDescription = "连接设备",
                        tint = if (connectedDevice != null) MaterialTheme.colorScheme.primary else MelodiaColors.TextSecondary,
                        modifier = Modifier.size(24.dp)
                    )
                }
                "queue" -> MelodiaIconButton(onClick = onQueueClick) {
                    Icon(Icons.AutoMirrored.Rounded.PlaylistPlay, contentDescription = "播放队列", tint = MelodiaColors.TextSecondary, modifier = Modifier.size(30.dp))
                }
                "share" -> MelodiaIconButton(onClick = onShareClick) {
                    Icon(Icons.Rounded.Share, contentDescription = "分享", tint = MelodiaColors.TextSecondary, modifier = Modifier.size(24.dp))
                }
                "quality" -> if (qualityLabel != null) {
                    Text(qualityLabel, color = MelodiaColors.TextSecondary, fontSize = 12.sp, fontWeight = FontWeight.Medium, maxLines = 1)
                }
                "more" -> MelodiaIconButton(onClick = onMoreClick) {
                    Icon(Icons.Rounded.MoreVert, contentDescription = "更多", tint = MelodiaColors.TextSecondary, modifier = Modifier.size(24.dp))
                }
            }
        }
    }
}

// 控件区底部次级入口：歌词翻译/音译切换与音质标签。
// 分享、输出设备在封面右上角，播放队列在传输键右侧，这里不再重复放入口
@Composable
fun ActionButtons(
    // 实际音质短标签，为空时不显示
    qualityLabel: String? = null,
    showLyricsControls: Boolean = false,
    secondaryMode: String = "translation",
    hasTranslation: Boolean = false,
    hasRoma: Boolean = false,
    onToggleSecondaryMode: (() -> Unit)? = null,
    onLyricsSettingsClick: (() -> Unit)? = null
) {
    if (!showLyricsControls && qualityLabel == null) return
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = MelodiaSpacing.lg)
            .padding(top = MelodiaSpacing.sm, bottom = MelodiaSpacing.md),
        contentAlignment = Alignment.Center
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(MelodiaSpacing.xs)
        ) {
            if (showLyricsControls) {
                val hasAnySecondary = hasTranslation || hasRoma
                val (badgeText, badgeSub) = when (secondaryMode) {
                    "roma" -> "音" to "A"
                    else -> "文" to "A"
                }
                val badgeTint = when {
                    !hasAnySecondary -> MelodiaColors.TextSecondary.copy(alpha = 0.35f)
                    secondaryMode == "none" -> MelodiaColors.TextSecondary.copy(alpha = 0.65f)
                    else -> MaterialTheme.colorScheme.primary
                }
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .pressable(if (hasAnySecondary && onToggleSecondaryMode != null) MelodiaPress.Icon else MelodiaPress.None) {
                            if (hasAnySecondary) onToggleSecondaryMode?.invoke()
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(
                            text = badgeText,
                            color = badgeTint,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = badgeSub,
                            color = badgeTint,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.ExtraBold,
                            modifier = Modifier.padding(start = 1.dp, bottom = 1.dp)
                        )
                    }
                }
                if (onLyricsSettingsClick != null) {
                    MelodiaIconButton(onClick = onLyricsSettingsClick) {
                        Icon(
                            imageVector = Icons.Rounded.Tune,
                            contentDescription = "歌词设置",
                            tint = MelodiaColors.TextSecondary,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }
            }
            if (qualityLabel != null) {
                Text(
                    text = qualityLabel,
                    color = MelodiaColors.TextSecondary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1
                )
            }
        }
    }
}

package com.lin0721.linmusic.feature.player.ui

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

// 控件区底部次级入口：输出设备、分享、播放队列，平板横屏下支持整合歌词控制（文A/音A切换、歌词设置）
// connectedDevice 非空时说明当前连着非扬声器设备（有线/蓝牙），图标换类型、变主题色，并在图标后显示设备名；扬声器播放时保持默认
@Composable
fun ActionButtons(
    onOutputDeviceClick: () -> Unit,
    onQueueClick: () -> Unit,
    onShareClick: () -> Unit,
    connectedDevice: AudioDeviceInfo? = null,
    // 实际音质短标签，为空时不显示
    qualityLabel: String? = null,
    showLyricsControls: Boolean = false,
    secondaryMode: String = "translation",
    hasTranslation: Boolean = false,
    hasRoma: Boolean = false,
    onToggleSecondaryMode: (() -> Unit)? = null,
    onLyricsSettingsClick: (() -> Unit)? = null
) {
    val outputDeviceIcon = connectedDevice?.let { deviceIcon(it.type) } ?: Icons.Rounded.SpeakerGroup
    val outputDeviceTint = if (connectedDevice != null) MaterialTheme.colorScheme.primary else MelodiaColors.TextSecondary

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = MelodiaSpacing.lg)
            .padding(top = MelodiaSpacing.sm, bottom = MelodiaSpacing.md)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .weight(1f, fill = false)
                    // 反馈交给里面的图标按钮，外层只是把设备名也纳入点击区
                    .pressable(MelodiaPress.None, onClick = onOutputDeviceClick)
            ) {
                MelodiaIconButton(
                    onClick = onOutputDeviceClick,
                    modifier = Modifier.offset(x = (-12).dp)
                ) {
                    Icon(outputDeviceIcon, contentDescription = "连接设备", tint = outputDeviceTint, modifier = Modifier.size(24.dp))
                }
                if (connectedDevice != null) {
                    Text(
                        text = deviceLabel(connectedDevice),
                        color = outputDeviceTint,
                        fontSize = 12.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .offset(x = (-8).dp)
                            .widthIn(max = 140.dp)
                    )
                }
            }
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

                MelodiaIconButton(onClick = onShareClick) {
                    Icon(
                        imageVector = Icons.Rounded.Share,
                        contentDescription = "分享",
                        tint = MelodiaColors.TextSecondary,
                        modifier = Modifier.size(24.dp)
                    )
                }
                MelodiaIconButton(
                    onClick = onQueueClick,
                    modifier = if (showLyricsControls) Modifier else Modifier.offset(x = 12.dp)
                ) {
                    Icon(Icons.AutoMirrored.Rounded.PlaylistPlay, contentDescription = null, tint = MelodiaColors.TextSecondary, modifier = Modifier.size(30.dp))
                }
            }
        }
        if (qualityLabel != null) {
            Text(
                text = qualityLabel,
                color = MelodiaColors.TextSecondary,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                modifier = Modifier.align(Alignment.Center)
            )
        }
    }
}

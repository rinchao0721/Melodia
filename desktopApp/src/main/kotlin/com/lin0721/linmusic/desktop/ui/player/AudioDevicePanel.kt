package com.lin0721.linmusic.desktop.ui.player

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Computer
import androidx.compose.material.icons.rounded.Headphones
import androidx.compose.material.icons.rounded.Speaker
import androidx.compose.material.icons.rounded.Tv
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lin0721.linmusic.desktop.player.AUTO_AUDIO_DEVICE
import com.lin0721.linmusic.desktop.player.AudioDevice
import com.lin0721.linmusic.desktop.player.AudioDeviceKind
import com.lin0721.linmusic.desktop.player.AudioOutputControl
import com.lin0721.linmusic.desktop.ui.components.DesktopTooltip
import com.lin0721.linmusic.desktop.ui.components.HoverScrollbarBox
import com.lin0721.linmusic.desktop.ui.components.OverlayPanelHeader
import com.lin0721.linmusic.desktop.ui.components.TooltipSide
import com.lin0721.linmusic.desktop.ui.theme.DesktopColors
import kotlinx.coroutines.delay

private const val REFRESH_INTERVAL_MS = 2_000L

// 右侧栏的输出设备面板：顶部是正在输出的设备，其余设备点击即切换。
// 面板可见期间定时刷新设备列表，插拔耳机后无需重开
@Composable
fun AudioDevicePanel(
    control: AudioOutputControl,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    val devices by control.audioDevices.collectAsState()
    val currentName by control.audioDevice.collectAsState()

    LaunchedEffect(control) {
        while (true) {
            control.refreshAudioDevices()
            delay(REFRESH_INTERVAL_MS)
        }
    }

    val current = devices.firstOrNull { it.name == currentName }
        ?: AudioDevice(currentName, currentName, AudioDeviceKind.SPEAKER)
    val others = devices.filter { it.name != currentName }
    val scrollState = rememberScrollState()

    Column(modifier.fillMaxSize().padding(top = 16.dp)) {
        OverlayPanelHeader("输出设备", "关闭输出设备", onClose, Modifier.padding(horizontal = 16.dp))
        if (devices.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("未检测到输出设备", color = DesktopColors.TextGray, fontSize = 14.sp)
            }
        } else {
            HoverScrollbarBox(scrollState, Modifier.weight(1f).padding(top = 8.dp)) {
                Column(Modifier.fillMaxSize().verticalScroll(scrollState).padding(horizontal = 8.dp, vertical = 4.dp)) {
                    CurrentDeviceCard(current)
                    if (others.isNotEmpty()) {
                        Text(
                            "其他设备",
                            color = DesktopColors.TextGray,
                            fontSize = 12.sp,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 12.dp)
                        )
                        others.forEach { device -> DeviceRow(device) { control.setAudioDevice(device.name) } }
                    }
                }
            }
        }
    }
}

@Composable
private fun CurrentDeviceCard(device: AudioDevice) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(DesktopColors.CardSurface).padding(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(device.icon(), null, tint = DesktopColors.Accent, modifier = Modifier.size(26.dp))
        Column(Modifier.weight(1f).padding(start = 12.dp)) {
            Text(
                device.title,
                color = DesktopColors.AccentText,
                fontWeight = FontWeight.Medium,
                fontSize = 14.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                listOfNotNull(device.subtitle, "正在输出").joinToString(" · "),
                color = DesktopColors.TextGray,
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun DeviceRow(device: AudioDevice, onClick: () -> Unit) {
    DesktopTooltip(device.description, side = TooltipSide.Left, modifier = Modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(4.dp)).pointerHoverIcon(PointerIcon.Hand)
                .clickable(onClick = onClick).padding(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                Modifier.size(36.dp).clip(RoundedCornerShape(4.dp)).background(DesktopColors.CoverPlaceholder),
                contentAlignment = Alignment.Center
            ) {
                Icon(device.icon(), null, tint = DesktopColors.TextGray, modifier = Modifier.size(20.dp))
            }
            Column(Modifier.weight(1f).padding(start = 12.dp)) {
                Text(device.title, color = DesktopColors.TextPrimary, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                device.subtitle?.let {
                    Text(it, color = DesktopColors.TextGray, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}

// 手动指定的输出设备；跟随系统默认时为 null
@Composable
fun collectSelectedAudioDevice(control: AudioOutputControl): AudioDevice? {
    val devices by control.audioDevices.collectAsState()
    val currentName by control.audioDevice.collectAsState()
    if (currentName == AUTO_AUDIO_DEVICE) return null
    return devices.firstOrNull { it.name == currentName } ?: AudioDevice(currentName, currentName, AudioDeviceKind.SPEAKER)
}

internal fun AudioDevice.icon(): ImageVector = when (kind) {
    AudioDeviceKind.SYSTEM_DEFAULT -> Icons.Rounded.Computer
    AudioDeviceKind.SPEAKER -> Icons.Rounded.Speaker
    AudioDeviceKind.HEADPHONES -> Icons.Rounded.Headphones
    AudioDeviceKind.DISPLAY -> Icons.Rounded.Tv
}

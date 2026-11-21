package com.lin0721.linmusic.desktop.ui.player

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import com.lin0721.linmusic.core.lanlink.LanLinkDesktopHost
import com.lin0721.linmusic.core.lanlink.LanLinkRemoteCoordinator
import com.lin0721.linmusic.core.lanlink.PlaybackTarget
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
import kotlinx.coroutines.flow.flowOf
import com.lin0721.linmusic.core.ui.icons.MelodiaIcons
import com.lin0721.linmusic.core.ui.icons.Check
import com.lin0721.linmusic.core.ui.icons.Computer
import com.lin0721.linmusic.core.ui.icons.Headphones
import com.lin0721.linmusic.core.ui.icons.Phone
import com.lin0721.linmusic.core.ui.icons.Speaker
import com.lin0721.linmusic.core.ui.icons.Tv
import com.lin0721.linmusic.core.ui.icons.WifiOff
import androidx.compose.ui.draw.alpha

private const val REFRESH_INTERVAL_MS = 2_000L

// 右侧栏的统一设备面板：顶部展示当前播放设备，下方列出可切换的联动对端、本机音频输出与离线已配对设备。
@Composable
fun AudioDevicePanel(
    control: AudioOutputControl?,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    lanLink: LanLinkDesktopHost? = null,
    remoteCoordinator: LanLinkRemoteCoordinator? = null
) {
    val devices by (control?.audioDevices ?: flowOf(emptyList())).collectAsState(emptyList())
    val currentName by (control?.audioDevice ?: flowOf(AUTO_AUDIO_DEVICE)).collectAsState(AUTO_AUDIO_DEVICE)
    val lanLinkEnabled by (lanLink?.enabled ?: flowOf(false)).collectAsState(false)
    val connectedPeer by (lanLink?.connectedDevice ?: flowOf(null)).collectAsState(null)
    val pairedDevices by (lanLink?.pairedDevices ?: flowOf(emptyList())).collectAsState(emptyList())
    val activeTarget by (remoteCoordinator?.activeTarget ?: flowOf(PlaybackTarget.Local)).collectAsState(PlaybackTarget.Local)

    val peerForSwitch = connectedPeer?.takeIf { lanLinkEnabled }
    val activeRemotePeer = (activeTarget as? PlaybackTarget.Remote)?.peer
    val isRemote = activeRemotePeer != null
    val offlinePaired = pairedDevices.filter { paired -> peerForSwitch == null || paired.deviceId != peerForSwitch.deviceId }

    LaunchedEffect(control) {
        if (control == null) return@LaunchedEffect
        while (true) {
            control.refreshAudioDevices()
            delay(REFRESH_INTERVAL_MS)
        }
    }

    val current = devices.firstOrNull { it.name == currentName }
        ?: AudioDevice(currentName, currentName, AudioDeviceKind.SPEAKER)
    val listedLocalDevices = if (isRemote) devices else devices.filter { it.name != currentName }
    val hasOtherDevices = (!isRemote && peerForSwitch != null && remoteCoordinator != null) ||
        (control != null && listedLocalDevices.isNotEmpty()) ||
        (isRemote && control == null && remoteCoordinator != null) ||
        offlinePaired.isNotEmpty()
    val scrollState = rememberScrollState()

    Column(modifier.fillMaxSize().padding(top = 16.dp)) {
        OverlayPanelHeader(
            if (peerForSwitch != null || pairedDevices.isNotEmpty()) "连接设备" else "输出设备",
            "关闭设备面板",
            onClose,
            Modifier.padding(horizontal = 16.dp)
        )
        if (devices.isEmpty() && peerForSwitch == null && offlinePaired.isEmpty() && activeRemotePeer == null) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("未检测到输出设备", color = DesktopColors.TextGray, fontSize = 14.sp)
            }
        } else {
            HoverScrollbarBox(scrollState, Modifier.weight(1f).padding(top = 8.dp)) {
                Column(Modifier.fillMaxSize().verticalScroll(scrollState).padding(horizontal = 8.dp, vertical = 4.dp)) {
                    Text(
                        "当前播放设备",
                        color = DesktopColors.TextGray,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)
                    )
                    if (activeRemotePeer != null) {
                        CurrentTargetCard(
                            icon = MelodiaIcons.Phone,
                            title = activeRemotePeer.deviceName,
                            subtitle = "正在输出"
                        )
                    } else if (control != null && devices.isNotEmpty()) {
                        CurrentDeviceCard(current)
                    } else {
                        CurrentTargetCard(
                            icon = MelodiaIcons.Computer,
                            title = "本机",
                            subtitle = "正在输出"
                        )
                    }

                    if (hasOtherDevices) {
                        Text(
                            "其他设备",
                            color = DesktopColors.TextGray,
                            fontSize = 12.sp,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 12.dp)
                        )
                        if (!isRemote && peerForSwitch != null && remoteCoordinator != null) {
                            PeerDeviceRow(
                                icon = MelodiaIcons.Phone,
                                title = peerForSwitch.deviceName,
                                subtitle = "局域网联动设备",
                                enabled = true,
                                onClick = { remoteCoordinator.switchToRemote() }
                            )
                        }
                        if (isRemote && control == null && remoteCoordinator != null) {
                            PeerDeviceRow(
                                icon = MelodiaIcons.Computer,
                                title = "本机",
                                subtitle = "切换到本机播放",
                                enabled = true,
                                onClick = { remoteCoordinator.switchToLocal() }
                            )
                        }
                        if (control != null) {
                            listedLocalDevices.forEach { device ->
                                DeviceRow(device) {
                                    if (remoteCoordinator == null || remoteCoordinator.switchToLocal()) {
                                        control.setAudioDevice(device.name)
                                    }
                                }
                            }
                        }
                        offlinePaired.forEach { paired ->
                            PeerDeviceRow(
                                icon = MelodiaIcons.WifiOff,
                                title = paired.deviceName,
                                subtitle = "不在当前网络",
                                enabled = false,
                                onClick = {
                                    remoteCoordinator?.emitMessage("需与该设备连接同一 Wi‑Fi 并开启局域网联动")
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CurrentTargetCard(
    icon: ImageVector,
    title: String,
    subtitle: String
) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(DesktopColors.CardSurface).padding(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, tint = DesktopColors.Accent, modifier = Modifier.size(26.dp))
        Column(Modifier.weight(1f).padding(start = 12.dp)) {
            Text(
                title,
                color = DesktopColors.AccentText,
                fontWeight = FontWeight.Medium,
                fontSize = 14.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                subtitle,
                color = DesktopColors.TextGray,
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Icon(
            MelodiaIcons.Check,
            contentDescription = null,
            tint = DesktopColors.Accent,
            modifier = Modifier.size(18.dp)
        )
    }
}

@Composable
private fun PeerDeviceRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    enabled: Boolean,
    onClick: () -> Unit
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(4.dp))
            .pointerHoverIcon(PointerIcon.Hand)
            .clickable(onClick = onClick)
            .alpha(if (enabled) 1f else 0.55f)
            .padding(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier.size(36.dp).clip(RoundedCornerShape(4.dp)).background(DesktopColors.CoverPlaceholder),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, null, tint = DesktopColors.TextGray, modifier = Modifier.size(20.dp))
        }
        Column(Modifier.weight(1f).padding(start = 12.dp)) {
            Text(title, color = DesktopColors.TextPrimary, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(subtitle, color = DesktopColors.TextGray, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
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
    AudioDeviceKind.SYSTEM_DEFAULT -> MelodiaIcons.Computer
    AudioDeviceKind.SPEAKER -> MelodiaIcons.Speaker
    AudioDeviceKind.HEADPHONES -> MelodiaIcons.Headphones
    AudioDeviceKind.DISPLAY -> MelodiaIcons.Tv
}

package com.lin0721.linmusic.feature.player.ui

import android.content.Context
import android.content.Intent
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lin0721.linmusic.core.lanlink.LanLinkAndroidClient
import com.lin0721.linmusic.core.lanlink.PlaybackTarget
import com.lin0721.linmusic.core.lanlink.RoutingPlaybackController
import com.lin0721.linmusic.core.player.PlayerManager
import com.lin0721.linmusic.core.player.formatOutputDeviceLabel
import com.lin0721.linmusic.core.player.listOutputDevices
import com.lin0721.linmusic.core.player.resolveEffectiveDeviceId
import com.lin0721.linmusic.core.ui.components.MelodiaDragHandle
import com.lin0721.linmusic.core.ui.theme.BottomSheetShape
import com.lin0721.linmusic.core.ui.theme.MelodiaColors
import com.lin0721.linmusic.core.ui.theme.MelodiaSpacing
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.koin.compose.koinInject
import com.lin0721.linmusic.core.ui.icons.MelodiaIcons
import com.lin0721.linmusic.core.ui.icons.Bluetooth
import com.lin0721.linmusic.core.ui.icons.Check
import com.lin0721.linmusic.core.ui.icons.Computer
import com.lin0721.linmusic.core.ui.icons.Headphones
import com.lin0721.linmusic.core.ui.icons.Phone
import com.lin0721.linmusic.core.ui.icons.WifiOff
import kotlin.math.roundToInt

private const val REMOTE_VOLUME_THROTTLE_MS = 100L
private const val REMOTE_VOLUME_HOLD_MS = 450L

@Composable
private fun rememberOutputDevices(): List<AudioDeviceInfo> {
    val context = LocalContext.current
    val audioManager = remember {
        context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    }
    var devices by remember { mutableStateOf(listOutputDevices(audioManager)) }

    DisposableEffect(audioManager) {
        val callback = object : AudioDeviceCallback() {
            override fun onAudioDevicesAdded(addedDevices: Array<out AudioDeviceInfo>) {
                devices = listOutputDevices(audioManager)
            }

            override fun onAudioDevicesRemoved(removedDevices: Array<out AudioDeviceInfo>) {
                devices = listOutputDevices(audioManager)
            }
        }
        audioManager.registerAudioDeviceCallback(callback, Handler(Looper.getMainLooper()))
        onDispose {
            audioManager.unregisterAudioDeviceCallback(callback)
        }
    }

    return devices
}

@Composable
internal fun rememberCurrentOutputDevice(): AudioDeviceInfo? {
    val playerManager = koinInject<PlayerManager>()
    val preferredId by playerManager.preferredOutputDeviceId.collectAsStateWithLifecycle()
    val devices = rememberOutputDevices()

    return remember(devices, preferredId) {
        val id = resolveEffectiveDeviceId(devices, preferredId)
        devices.firstOrNull { it.id == id }?.takeIf { it.type != AudioDeviceInfo.TYPE_BUILTIN_SPEAKER }
    }
}

internal fun deviceIcon(type: Int): ImageVector = when (type) {
    AudioDeviceInfo.TYPE_BUILTIN_SPEAKER -> MelodiaIcons.Phone
    AudioDeviceInfo.TYPE_BLUETOOTH_A2DP, AudioDeviceInfo.TYPE_BLE_HEADSET -> MelodiaIcons.Bluetooth
    else -> MelodiaIcons.Headphones
}

internal fun deviceLabel(device: AudioDeviceInfo): String = formatOutputDeviceLabel(device)

// 名称之下的设备类型子标题
private fun deviceTypeLabel(type: Int): String = when (type) {
    AudioDeviceInfo.TYPE_BUILTIN_SPEAKER -> "手机扬声器"
    AudioDeviceInfo.TYPE_BLUETOOTH_A2DP, AudioDeviceInfo.TYPE_BLE_HEADSET -> "蓝牙音频"
    AudioDeviceInfo.TYPE_WIRED_HEADSET, AudioDeviceInfo.TYPE_WIRED_HEADPHONES -> "有线耳机"
    AudioDeviceInfo.TYPE_USB_HEADSET -> "USB 音频"
    else -> "音频设备"
}

private fun openBluetoothSettings(context: Context) {
    context.startActivity(Intent(Settings.ACTION_BLUETOOTH_SETTINGS))
}

// "连接设备"输出切换弹层：顶部展示当前播放设备，下方统一列出可切换的对端设备、本机输出与离线已配对设备
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OutputDeviceSheet(
    onDeviceSelected: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val playerManager = koinInject<PlayerManager>()
    val routingController = koinInject<RoutingPlaybackController>()
    val lanLinkClient = koinInject<LanLinkAndroidClient>()

    val preferredId by playerManager.preferredOutputDeviceId.collectAsStateWithLifecycle()
    val lanLinkEnabled by lanLinkClient.enabled.collectAsStateWithLifecycle()
    val connectedPeer by lanLinkClient.connectedDevice.collectAsStateWithLifecycle()
    val pairedDevices by lanLinkClient.pairedDevices.collectAsStateWithLifecycle()
    val activeTarget by routingController.activeTarget.collectAsStateWithLifecycle()
    val remoteVolume by routingController.coordinator.remoteController.volume.collectAsStateWithLifecycle()

    val peerForSwitch = connectedPeer?.takeIf { lanLinkEnabled }
    val activeRemotePeer = (activeTarget as? PlaybackTarget.Remote)?.peer
    val isRemote = activeRemotePeer != null
    val offlinePaired = pairedDevices.filter { paired -> peerForSwitch == null || paired.deviceId != peerForSwitch.deviceId }

    val devices = rememberOutputDevices()
    val effectiveSelectedId = remember(devices, preferredId) { resolveEffectiveDeviceId(devices, preferredId) }
    val currentLocalDevice = remember(devices, effectiveSelectedId) {
        devices.firstOrNull { it.id == effectiveSelectedId }
    }
    val listedLocalDevices = remember(devices, isRemote, effectiveSelectedId) {
        if (isRemote) devices else devices.filter { it.id != effectiveSelectedId }
    }

    var connectingDeviceId by remember { mutableStateOf<String?>(null) }

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val nestedScrollConnection = remember(sheetState) {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (sheetState.targetValue == SheetValue.Expanded && available.y < 0) {
                    return Offset.Zero
                }
                return Offset.Zero
            }

            override suspend fun onPreFling(available: Velocity): Velocity {
                if (sheetState.targetValue == SheetValue.Expanded && available.y < 0) {
                    return Velocity(0f, available.y)
                }
                return Velocity.Zero
            }
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.background,
        shape = BottomSheetShape,
        dragHandle = { MelodiaDragHandle() }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .nestedScroll(nestedScrollConnection)
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(start = MelodiaSpacing.lg, end = MelodiaSpacing.lg, bottom = MelodiaSpacing.lg)
        ) {
            Text(
                text = "连接设备",
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 20.sp,
                fontWeight = FontWeight.ExtraBold,
                modifier = Modifier.padding(bottom = MelodiaSpacing.md)
            )

            Text(
                text = "当前播放设备",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(bottom = MelodiaSpacing.xs)
            )
            Column(
                verticalArrangement = Arrangement.spacedBy(MelodiaSpacing.sm),
                modifier = Modifier.padding(bottom = MelodiaSpacing.md)
            ) {
                if (activeRemotePeer != null) {
                    PlaybackTargetRow(
                        icon = MelodiaIcons.Computer,
                        title = activeRemotePeer.deviceName,
                        subtitle = "正在播放",
                        selected = true,
                        onClick = {}
                    )
                    RemoteVolumeCard(
                        volume = remoteVolume,
                        onVolumeChange = { routingController.coordinator.remoteController.setVolume(it) }
                    )
                } else if (currentLocalDevice != null) {
                    PlaybackTargetRow(
                        icon = deviceIcon(currentLocalDevice.type),
                        title = deviceLabel(currentLocalDevice),
                        subtitle = "${deviceTypeLabel(currentLocalDevice.type)} · 正在输出",
                        selected = true,
                        onClick = {}
                    )
                } else {
                    PlaybackTargetRow(
                        icon = MelodiaIcons.Phone,
                        title = "本机",
                        subtitle = "正在输出",
                        selected = true,
                        onClick = {}
                    )
                }
            }

            Text(
                text = "其他设备",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(bottom = MelodiaSpacing.xs)
            )

            Column(verticalArrangement = Arrangement.spacedBy(MelodiaSpacing.sm)) {
                if (!isRemote && peerForSwitch != null) {
                    PlaybackTargetRow(
                        icon = MelodiaIcons.Computer,
                        title = peerForSwitch.deviceName,
                        subtitle = "局域网联动设备",
                        selected = false,
                        onClick = { routingController.coordinator.switchToRemote() }
                    )
                }
                if (isRemote && devices.isEmpty()) {
                    PlaybackTargetRow(
                        icon = MelodiaIcons.Phone,
                        title = "本机",
                        subtitle = "切换到本机播放",
                        selected = false,
                        onClick = { routingController.coordinator.switchToLocal() }
                    )
                }
                listedLocalDevices.forEach { device ->
                    PlaybackTargetRow(
                        icon = deviceIcon(device.type),
                        title = deviceLabel(device),
                        subtitle = deviceTypeLabel(device.type),
                        selected = false,
                        onClick = {
                            if (routingController.coordinator.switchToLocal()) {
                                onDeviceSelected(device.id)
                            }
                        }
                    )
                }
                offlinePaired.forEach { paired ->
                    val isConnecting = connectingDeviceId == paired.deviceId
                    PlaybackTargetRow(
                        icon = MelodiaIcons.WifiOff,
                        title = paired.deviceName,
                        subtitle = if (isConnecting) "正在尝试连接…" else "不在当前网络",
                        selected = false,
                        dimmed = !isConnecting,
                        onClick = {
                            if (connectingDeviceId == null) {
                                scope.launch {
                                    connectingDeviceId = paired.deviceId
                                    try {
                                        val connected = lanLinkClient.connectToPairedDevice(paired.deviceId)
                                        if (connected) {
                                            routingController.coordinator.switchToRemote()
                                        } else {
                                            routingController.coordinator.emitMessage("需与该设备连接同一 Wi‑Fi 并开启局域网联动")
                                        }
                                    } finally {
                                        connectingDeviceId = null
                                    }
                                }
                            }
                        }
                    )
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .clickable { openBluetoothSettings(context) }
                        .padding(horizontal = MelodiaSpacing.md, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(MelodiaColors.Fill),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = MelodiaIcons.Plus,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(MelodiaSpacing.sm))
                    Text(
                        text = "添加新设备",
                        color = MaterialTheme.colorScheme.onSurface,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
    }
}

@Composable
private fun RemoteVolumeCard(
    volume: Int,
    onVolumeChange: (Int) -> Unit
) {
    var dragVolume by remember { mutableStateOf<Int?>(null) }
    var isDragging by remember { mutableStateOf(false) }
    var lastSentAtMs by remember { mutableStateOf(0L) }
    var pendingEmitVolume by remember { mutableStateOf<Int?>(null) }
    var holdGeneration by remember { mutableStateOf(0) }

    val displayVolume = dragVolume ?: volume
    val currentOnVolumeChange by rememberUpdatedState(onVolumeChange)

    LaunchedEffect(isDragging, volume, dragVolume) {
        if (!isDragging && dragVolume != null && volume == dragVolume) {
            dragVolume = null
        }
    }

    LaunchedEffect(holdGeneration, isDragging) {
        if (!isDragging && dragVolume != null) {
            delay(REMOTE_VOLUME_HOLD_MS)
            dragVolume = null
        }
    }

    LaunchedEffect(pendingEmitVolume, isDragging) {
        val pending = pendingEmitVolume ?: return@LaunchedEffect
        if (!isDragging) return@LaunchedEffect
        val waitMs = (REMOTE_VOLUME_THROTTLE_MS - (System.currentTimeMillis() - lastSentAtMs)).coerceAtLeast(0L)
        if (waitMs > 0L) delay(waitMs)
        if (isDragging && pendingEmitVolume != null) {
            val latest = pendingEmitVolume!!
            pendingEmitVolume = null
            lastSentAtMs = System.currentTimeMillis()
            currentOnVolumeChange(latest)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(MelodiaColors.Overlay.copy(alpha = 0.05f))
            .padding(horizontal = MelodiaSpacing.md, vertical = 10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "对端音量",
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = "$displayVolume%",
                color = MaterialTheme.colorScheme.primary,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold
            )
        }
        Slider(
            value = displayVolume.toFloat(),
            onValueChange = { value ->
                val target = value.roundToInt().coerceIn(0, 100)
                isDragging = true
                dragVolume = target
                val now = System.currentTimeMillis()
                if (now - lastSentAtMs >= REMOTE_VOLUME_THROTTLE_MS) {
                    lastSentAtMs = now
                    pendingEmitVolume = null
                    currentOnVolumeChange(target)
                } else {
                    pendingEmitVolume = target
                }
            },
            onValueChangeFinished = {
                val finalVol = pendingEmitVolume ?: dragVolume
                pendingEmitVolume = null
                isDragging = false
                holdGeneration++
                if (finalVol != null) {
                    lastSentAtMs = System.currentTimeMillis()
                    currentOnVolumeChange(finalVol)
                }
            },
            valueRange = 0f..100f
        )
    }
}

@Composable
private fun PlaybackTargetRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    selected: Boolean,
    dimmed: Boolean = false,
    onClick: () -> Unit
) {
    val rowBackground = if (selected) {
        MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
    } else {
        MelodiaColors.Overlay.copy(alpha = 0.05f)
    }
    val badgeBackground = if (selected) {
        MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
    } else {
        MelodiaColors.Overlay.copy(alpha = 0.08f)
    }
    val iconTint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(rowBackground)
            .clickable(onClick = onClick)
            .alpha(if (dimmed) 0.55f else 1f)
            .padding(horizontal = MelodiaSpacing.md, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(badgeBackground),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = iconTint,
                modifier = Modifier.size(20.dp)
            )
        }
        Spacer(modifier = Modifier.width(MelodiaSpacing.sm))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = subtitle,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 12.sp
            )
        }
        if (selected) {
            Icon(
                imageVector = MelodiaIcons.Check,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

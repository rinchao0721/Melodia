package com.lin0721.linmusic.feature.settings.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lin0721.linmusic.LocalBottomOverlayInset
import com.lin0721.linmusic.core.lanlink.DiscoveredLanDevice
import com.lin0721.linmusic.core.lanlink.LanLinkPlaybackMode
import com.lin0721.linmusic.core.lanlink.LanLinkSyncState
import com.lin0721.linmusic.core.ui.components.MelodiaButton
import com.lin0721.linmusic.core.ui.components.MelodiaOutlinedButton
import com.lin0721.linmusic.core.ui.components.MelodiaTextButton
import com.lin0721.linmusic.core.ui.components.PlaceholderTextField
import com.lin0721.linmusic.core.ui.icons.Computer
import com.lin0721.linmusic.core.ui.icons.MelodiaIcons
import com.lin0721.linmusic.core.ui.icons.Warning
import com.lin0721.linmusic.core.ui.theme.MelodiaColors
import com.lin0721.linmusic.core.ui.theme.MelodiaSpacing

@Composable
fun NetworkSettingsView(viewModel: SettingsViewModel) {
    val useRealIp by viewModel.useRealIp.collectAsStateWithLifecycle()
    val realIpValue by viewModel.realIpValue.collectAsStateWithLifecycle()
    val wifiOnlyPlay by viewModel.wifiOnlyPlay.collectAsStateWithLifecycle()
    val mobileAlert by viewModel.mobileAlert.collectAsStateWithLifecycle()

    val lanLinkEnabled by viewModel.lanLinkEnabled.collectAsStateWithLifecycle()
    val lanLinkBackgroundKeepalive by viewModel.lanLinkBackgroundKeepalive.collectAsStateWithLifecycle()
    val lanLinkPlaybackMode by viewModel.lanLinkPlaybackMode.collectAsStateWithLifecycle()
    val lanLinkDiscovering by viewModel.lanLinkIsDiscovering.collectAsStateWithLifecycle()
    val discoveredDevices by viewModel.lanLinkDiscoveredDevices.collectAsStateWithLifecycle()
    val pairedDevices by viewModel.lanLinkPairedDevices.collectAsStateWithLifecycle()
    val connectedDevice by viewModel.lanLinkConnectedDevice.collectAsStateWithLifecycle()
    val syncState by viewModel.lanLinkSyncState.collectAsStateWithLifecycle()
    val syncStatusMessage by viewModel.lanLinkSyncStatusMessage.collectAsStateWithLifecycle()

    var pairTarget by remember { mutableStateOf<DiscoveredLanDevice?>(null) }
    var pairingCode by remember { mutableStateOf("") }
    var pairingSubmitting by remember { mutableStateOf(false) }
    var pairingError by remember { mutableStateOf<String?>(null) }

    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(MelodiaSpacing.md),
        contentPadding = PaddingValues(top = 8.dp, bottom = LocalBottomOverlayInset.current + 16.dp)
    ) {
        item {
            SettingsGroupCard(SettingsSubMenu.NETWORK.sectionTitles[0]) {
                SettingsSwitchRow(
                    title = "仅 Wi-Fi 网络下联网播放",
                    subtitle = "开启后，在移动网络环境将无法播放在线曲目",
                    checked = wifiOnlyPlay,
                    onCheckedChange = { viewModel.updateWifiOnlyPlay(it) }
                )
                HorizontalDivider(color = MelodiaColors.Divider)
                SettingsSwitchRow(
                    title = "流量播放警告提示",
                    subtitle = "从 Wi-Fi 切换为移动数据时弹出提醒",
                    checked = mobileAlert,
                    onCheckedChange = { viewModel.updateMobileAlert(it) }
                )
            }
        }

        item {
            SettingsGroupCard(SettingsSubMenu.NETWORK.sectionTitles[1]) {
                SettingsSwitchRow(
                    title = "使用国内 IP 地址",
                    subtitle = "在海外IP可能会受到限制，可开启此处尝试解决",
                    checked = useRealIp,
                    onCheckedChange = { viewModel.updateUseRealIp(it) }
                )

                if (useRealIp) {
                    HorizontalDivider(color = MelodiaColors.Divider)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f).padding(end = MelodiaSpacing.md)) {
                            Text("国内 IP 地址", color = MaterialTheme.colorScheme.onSurface, fontSize = 15.sp)
                            Spacer(modifier = Modifier.height(2.dp))
                            Text("可在此处输入国内 IP，不填写则为随机", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
                        }

                        PlaceholderTextField(
                            value = realIpValue,
                            onValueChange = { viewModel.updateRealIpValue(it) },
                            placeholder = "IP 127.0.0.1",
                            modifier = Modifier.width(150.dp),
                            shape = RoundedCornerShape(8.dp)
                        )
                    }
                }
            }
        }

        item {
            SettingsGroupCard(SettingsSubMenu.NETWORK.sectionTitles[2]) {
                SettingsSwitchRow(
                    title = "局域网联动",
                    subtitle = "开启后可与同一局域网内的桌面端配对，双向同步歌单外部歌曲与外部红心",
                    checked = lanLinkEnabled,
                    onCheckedChange = { viewModel.updateLanLinkEnabled(it) }
                )

                if (lanLinkEnabled) {
                    HorizontalDivider(color = MelodiaColors.Divider)
                    SettingsSwitchRow(
                        title = "后台保持连接",
                        subtitle = "开启后使用低功耗通知保持连接并在系统媒体面板控播；关闭后切至后台可能会断开联动",
                        checked = lanLinkBackgroundKeepalive,
                        onCheckedChange = viewModel::updateLanLinkBackgroundKeepalive
                    )
                    HorizontalDivider(color = MelodiaColors.Divider)
                    LanLinkPlaybackModeSection(
                        currentMode = lanLinkPlaybackMode,
                        onSelectMode = viewModel::updateLanLinkPlaybackMode
                    )
                    HorizontalDivider(color = MelodiaColors.Divider)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val isMismatch = syncState == LanLinkSyncState.ACCOUNT_MISMATCH
                        if (isMismatch) {
                            Icon(
                                imageVector = MelodiaIcons.Warning,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(Modifier.width(10.dp))
                        }
                        Column(modifier = Modifier.weight(1f).padding(end = MelodiaSpacing.sm)) {
                            Text(
                                text = connectedDevice?.deviceName ?: "联动状态",
                                color = MaterialTheme.colorScheme.onSurface,
                                fontSize = 15.sp
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = syncStatusMessage,
                                color = if (isMismatch) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 12.sp
                            )
                        }
                        if (pairedDevices.isNotEmpty() && connectedDevice == null) {
                            MelodiaTextButton(onClick = viewModel::triggerLanReconnect) {
                                Text("立即重连")
                            }
                        }
                    }

                    HorizontalDivider(color = MelodiaColors.Divider)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f).padding(end = MelodiaSpacing.sm)) {
                            Text("搜索桌面端设备", color = MaterialTheme.colorScheme.onSurface, fontSize = 15.sp)
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "请确保桌面端处于同一 Wi-Fi 并已开启局域网联动",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 12.sp
                            )
                        }
                        MelodiaOutlinedButton(
                            onClick = viewModel::discoverLanDesktops,
                            enabled = !lanLinkDiscovering
                        ) {
                            if (lanLinkDiscovering) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    strokeWidth = 2.dp
                                )
                                Spacer(Modifier.width(6.dp))
                                Text("搜索中")
                            } else {
                                Text("搜索设备")
                            }
                        }
                    }

                    discoveredDevices.forEach { device ->
                        HorizontalDivider(color = MelodiaColors.Divider)
                        val alreadyPaired = pairedDevices.any { it.deviceId == device.deviceId }
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = MelodiaIcons.Computer,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f).padding(end = MelodiaSpacing.sm)) {
                                Text(device.deviceName, color = MaterialTheme.colorScheme.onSurface, fontSize = 14.sp)
                                Text(
                                    text = "${device.hostAddress}:${device.tcpPort}",
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontSize = 12.sp
                                )
                            }
                            MelodiaButton(
                                onClick = {
                                    pairTarget = device
                                    pairingCode = ""
                                    pairingError = null
                                }
                            ) {
                                Text(if (alreadyPaired) "重新配对" else "配对")
                            }
                        }
                    }
                }

                if (lanLinkEnabled || pairedDevices.isNotEmpty()) {
                    HorizontalDivider(color = MelodiaColors.Divider)
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 12.dp)
                    ) {
                        Text(
                            text = "已配对设备",
                            color = MaterialTheme.colorScheme.onSurface,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium
                        )
                        if (pairedDevices.isEmpty()) {
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text = "暂无已配对桌面端设备",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 12.sp
                            )
                        } else {
                            pairedDevices.forEach { paired ->
                                val isConnected = connectedDevice?.deviceId == paired.deviceId
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(top = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = MelodiaIcons.Computer,
                                        contentDescription = null,
                                        tint = if (isConnected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(Modifier.width(12.dp))
                                    Column(modifier = Modifier.weight(1f).padding(end = MelodiaSpacing.sm)) {
                                        Text(paired.deviceName, color = MaterialTheme.colorScheme.onSurface, fontSize = 14.sp)
                                        Text(
                                            text = if (isConnected) "当前已连接 · $syncStatusMessage" else "已配对",
                                            color = if (isConnected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                            fontSize = 12.sp
                                        )
                                    }
                                    MelodiaTextButton(onClick = { viewModel.unpairLanDevice(paired.deviceId) }) {
                                        Text("取消配对", color = MaterialTheme.colorScheme.error)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    pairTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { if (!pairingSubmitting) pairTarget = null },
            title = { Text("配对 ${target.deviceName}") },
            text = {
                Column {
                    Text(
                        text = "请输入桌面端设置页「配对新设备」显示的 6 位数字配对码：",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(
                        value = pairingCode,
                        onValueChange = { input ->
                            pairingCode = input.filter(Char::isDigit).take(6)
                            pairingError = null
                        },
                        label = { Text("6 位配对码") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        isError = pairingError != null,
                        modifier = Modifier.fillMaxWidth()
                    )
                    pairingError?.let { err ->
                        Spacer(Modifier.height(6.dp))
                        Text(err, color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
                    }
                }
            },
            confirmButton = {
                MelodiaButton(
                    onClick = {
                        pairingSubmitting = true
                        pairingError = null
                        viewModel.pairLanDevice(target, pairingCode) { success, err ->
                            pairingSubmitting = false
                            if (success) {
                                pairTarget = null
                                pairingCode = ""
                            } else {
                                pairingError = err
                            }
                        }
                    },
                    enabled = pairingCode.length == 6 && !pairingSubmitting
                ) {
                    Text(if (pairingSubmitting) "配对中…" else "确认配对")
                }
            },
            dismissButton = {
                MelodiaTextButton(
                    onClick = { pairTarget = null },
                    enabled = !pairingSubmitting
                ) {
                    Text("取消")
                }
            }
        )
    }
}

@Composable
private fun LanLinkPlaybackModeSection(
    currentMode: LanLinkPlaybackMode,
    onSelectMode: (LanLinkPlaybackMode) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Column {
            Text(
                text = "联动播放模式",
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 15.sp
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = "选择多端连接时的播放协作方式，修改后会自动同步到对端",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 12.sp
            )
        }
        LanLinkPlaybackModeOptionCard(
            title = "单一播放设备",
            description = "两端同步播放状态，同时仅在一台设备上发声并可跨端接力",
            selected = currentMode == LanLinkPlaybackMode.SINGLE_DEVICE,
            onClick = { onSelectMode(LanLinkPlaybackMode.SINGLE_DEVICE) }
        )
        LanLinkPlaybackModeOptionCard(
            title = "各自独立播放",
            description = "两端互不打断各自独立播放，仅手动切换输出设备时进行遥控或接力",
            selected = currentMode == LanLinkPlaybackMode.INDEPENDENT,
            onClick = { onSelectMode(LanLinkPlaybackMode.INDEPENDENT) }
        )
    }
}

@Composable
private fun LanLinkPlaybackModeOptionCard(
    title: String,
    description: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        color = MelodiaColors.Surface,
        border = BorderStroke(
            width = if (selected) 1.5.dp else 1.dp,
            color = if (selected) MaterialTheme.colorScheme.primary else MelodiaColors.Divider
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            RadioButton(
                selected = selected,
                onClick = onClick,
                colors = RadioButtonDefaults.colors(
                    selectedColor = MaterialTheme.colorScheme.primary,
                    unselectedColor = MaterialTheme.colorScheme.onSurfaceVariant
                )
            )
            Spacer(Modifier.width(6.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = description,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 12.sp
                )
            }
        }
    }
}

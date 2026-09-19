package com.lin0721.linmusic.feature.settings.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lin0721.linmusic.LocalBottomOverlayInset
import com.lin0721.linmusic.core.ui.theme.BottomSheetShape
import com.lin0721.linmusic.core.ui.theme.DragHandleShape
import com.lin0721.linmusic.core.ui.theme.MelodiaSpacing
import com.lin0721.linmusic.feature.player.ui.LyricCapsuleSlider

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExtensionsSettingsView(viewModel: SettingsViewModel) {
    val showLockscreen by viewModel.showLockscreen.collectAsStateWithLifecycle()
    val carMode by viewModel.carMode.collectAsStateWithLifecycle()
    val showCreateEntry by viewModel.showCreateEntry.collectAsStateWithLifecycle()
    val fullScreenLyricTextSize by viewModel.fullScreenLyricTextSize.collectAsStateWithLifecycle()
    val fullScreenLyricAlignment by viewModel.fullScreenLyricAlignment.collectAsStateWithLifecycle()
    val fullScreenKaraokeAdvancedEffect by viewModel.fullScreenKaraokeAdvancedEffect.collectAsStateWithLifecycle()

    var showAlignmentSheet by remember { mutableStateOf(false) }
    val alignmentLabel = when (fullScreenLyricAlignment) {
        "center" -> "居中对齐"
        "right" -> "右对齐"
        else -> "左对齐"
    }

    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(MelodiaSpacing.md),
            contentPadding = PaddingValues(top = 8.dp, bottom = LocalBottomOverlayInset.current + 16.dp)
        ) {
            item {
                SettingsGroupCard("全屏歌词") {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 12.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("歌词字号大小", color = MaterialTheme.colorScheme.onSurface, fontSize = 15.sp)
                            Text(
                                text = "${fullScreenLyricTextSize} sp",
                                color = MaterialTheme.colorScheme.primary,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Spacer(modifier = Modifier.height(10.dp))
                        LyricCapsuleSlider(
                            value = fullScreenLyricTextSize,
                            onValueChange = { viewModel.updateFullScreenLyricTextSize(it) },
                            valueRange = 16f..32f
                        )
                    }
                    HorizontalDivider(color = Color.White.copy(alpha = 0.08f))
                    SettingsRow(
                        title = "歌词对齐方式",
                        subtitle = alignmentLabel,
                        onClick = { showAlignmentSheet = true }
                    )
                    HorizontalDivider(color = Color.White.copy(alpha = 0.08f))
                    SettingsSwitchRow(
                        title = "逐字歌词流光动效",
                        subtitle = "开启柔和渐变推进边缘",
                        checked = fullScreenKaraokeAdvancedEffect,
                        onCheckedChange = { viewModel.updateFullScreenKaraokeAdvancedEffect(it) }
                    )
                }
            }

            item {
                SettingsGroupCard("悬浮与桌面") {
                    SettingsSwitchRow(
                        title = "启用系统锁屏显示",
                        subtitle = "在锁屏界面展示播放控制器与歌词面板",
                        checked = showLockscreen,
                        onCheckedChange = { viewModel.updateShowLockscreen(it) }
                    )
                }
            }

            item {
                SettingsGroupCard("设备与集成") {
                    SettingsSwitchRow(
                        title = "车载模式蓝牙自动启动",
                        subtitle = "连接车载蓝牙设备时自动恢复媒体播放",
                        checked = carMode,
                        onCheckedChange = { viewModel.updateCarMode(it) }
                    )
                }
            }

            item {
                SettingsGroupCard("底部导航栏") {
                    SettingsSwitchRow(
                        title = "显示底栏创建入口",
                        subtitle = "关闭后可在音乐库页面通过右上角按钮创建歌单",
                        checked = showCreateEntry,
                        onCheckedChange = { viewModel.updateShowCreateEntry(it) }
                    )
                }
            }
        }

        if (showAlignmentSheet) {
            val alignments = listOf(
                "left" to "左对齐",
                "center" to "居中对齐",
                "right" to "右对齐"
            )
            val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

            ModalBottomSheet(
                onDismissRequest = { showAlignmentSheet = false },
                sheetState = sheetState,
                containerColor = MaterialTheme.colorScheme.background,
                shape = BottomSheetShape,
                dragHandle = {
                    Box(
                        modifier = Modifier
                            .padding(top = 12.dp, bottom = MelodiaSpacing.xs)
                            .width(36.dp)
                            .height(4.dp)
                            .clip(DragHandleShape)
                            .background(Color.White.copy(alpha = 0.3f))
                    )
                }
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(start = MelodiaSpacing.lg, end = MelodiaSpacing.lg, bottom = MelodiaSpacing.lg)
                ) {
                    Text(
                        text = "选择歌词对齐方式",
                        color = MaterialTheme.colorScheme.onSurface,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.ExtraBold,
                        modifier = Modifier.padding(bottom = MelodiaSpacing.md)
                    )

                    alignments.forEach { (key, label) ->
                        val isSelected = fullScreenLyricAlignment == key
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    viewModel.updateFullScreenLyricAlignment(key)
                                    showAlignmentSheet = false
                                }
                                .padding(vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = label,
                                color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                                fontSize = 15.sp
                            )
                            if (isSelected) {
                                Icon(Icons.Default.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            }
                        }
                    }
                }
            }
        }
    }
}


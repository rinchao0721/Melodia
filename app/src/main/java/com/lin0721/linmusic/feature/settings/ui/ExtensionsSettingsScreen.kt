package com.lin0721.linmusic.feature.settings.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lin0721.linmusic.LocalBottomOverlayInset
import com.lin0721.linmusic.core.ui.theme.MelodiaSpacing

@Composable
fun ExtensionsSettingsView(viewModel: SettingsViewModel) {
    val showLockscreen by viewModel.showLockscreen.collectAsStateWithLifecycle()
    val carMode by viewModel.carMode.collectAsStateWithLifecycle()
    val showCreateEntry by viewModel.showCreateEntry.collectAsStateWithLifecycle()

    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(MelodiaSpacing.md),
            contentPadding = PaddingValues(top = 8.dp, bottom = LocalBottomOverlayInset.current + 16.dp)
        ) {
            item {
                SettingsGroupCard(SettingsSubMenu.EXTENSIONS.sectionTitles[0]) {
                    SettingsSwitchRow(
                        title = "启用系统锁屏显示",
                        subtitle = "在锁屏界面展示播放控制器与歌词面板",
                        checked = showLockscreen,
                        onCheckedChange = { viewModel.updateShowLockscreen(it) }
                    )
                }
            }

            item {
                SettingsGroupCard(SettingsSubMenu.EXTENSIONS.sectionTitles[1]) {
                    SettingsSwitchRow(
                        title = "车载模式蓝牙自动启动",
                        subtitle = "连接车载蓝牙设备时自动恢复媒体播放",
                        checked = carMode,
                        onCheckedChange = { viewModel.updateCarMode(it) }
                    )
                }
            }

            item {
                SettingsGroupCard(SettingsSubMenu.EXTENSIONS.sectionTitles[2]) {
                    SettingsSwitchRow(
                        title = "显示底栏创建入口",
                        subtitle = "关闭后可在音乐库页面通过右上角按钮创建歌单",
                        checked = showCreateEntry,
                        onCheckedChange = { viewModel.updateShowCreateEntry(it) }
                    )
                }
            }
        }
    }
}


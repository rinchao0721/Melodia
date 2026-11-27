package com.lin0721.linmusic.feature.settings.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lin0721.linmusic.LocalBottomOverlayInset
import com.lin0721.linmusic.core.model.DOWNLOAD_QUALITY_LEVELS
import com.lin0721.linmusic.core.model.getQualityDisplayName
import com.lin0721.linmusic.core.ui.components.MelodiaDragHandle
import com.lin0721.linmusic.core.ui.theme.BottomSheetShape
import com.lin0721.linmusic.core.ui.theme.MelodiaColors
import com.lin0721.linmusic.core.ui.theme.MelodiaSpacing
import com.lin0721.linmusic.core.ui.icons.MelodiaIcons
import com.lin0721.linmusic.core.ui.icons.Check

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AudioQualitySettingsView(viewModel: SettingsViewModel) {
    val wifiQuality by viewModel.wifiQuality.collectAsStateWithLifecycle()
    val mobileQuality by viewModel.mobileQuality.collectAsStateWithLifecycle()
    val downloadQuality by viewModel.downloadQuality.collectAsStateWithLifecycle()
    val downloadFallbackHighestQuality by viewModel.downloadFallbackHighestQuality.collectAsStateWithLifecycle()
    val showPlaybackQuality by viewModel.showPlaybackQuality.collectAsStateWithLifecycle()
    val showPlaybackSource by viewModel.showPlaybackSource.collectAsStateWithLifecycle()
    val showSongMaxQuality by viewModel.showSongMaxQuality.collectAsStateWithLifecycle()

    var qualityDialogTarget by remember { mutableStateOf<String?>(null) }

    // 渲染音质的子设置项
    SettingsPageColumn {
        item {
            SettingsGroupCard(SettingsSubMenu.QUALITY_SOURCES.sectionTitles[0]) {
                SettingsRow(
                    title = "Wi-Fi 环境播放音质",
                    subtitle = getQualityDisplayName(wifiQuality),
                    onClick = { qualityDialogTarget = "wifi" }
                )
                HorizontalDivider(color = MelodiaColors.Divider)
                SettingsRow(
                    title = "移动网络环境播放音质",
                    subtitle = getQualityDisplayName(mobileQuality),
                    onClick = { qualityDialogTarget = "mobile" }
                )
                HorizontalDivider(color = MelodiaColors.Divider)
                SettingsRow(
                    title = "默认下载音质",
                    subtitle = getQualityDisplayName(downloadQuality),
                    onClick = { qualityDialogTarget = "download" }
                )
                HorizontalDivider(color = MelodiaColors.Divider)
                SettingsSwitchRow(
                    title = "无目标音质时自动下载最高可用音质",
                    subtitle = "开启后，若媒体没有所选音质（如无损），自动向下匹配实际可用的最高音质",
                    checked = downloadFallbackHighestQuality,
                    onCheckedChange = { viewModel.updateDownloadFallbackHighestQuality(it) }
                )
            }
        }

        item {
            SettingsGroupCard(SettingsSubMenu.QUALITY_SOURCES.sectionTitles[1]) {
                SettingsSwitchRow(
                    title = "显示实际音质",
                    subtitle = "在全屏播放页底部显示当前歌曲接口实际下发的音质",
                    checked = showPlaybackQuality,
                    onCheckedChange = { viewModel.updateShowPlaybackQuality(it) }
                )
                HorizontalDivider(color = MelodiaColors.Divider)
                SettingsSwitchRow(
                    title = "显示播放来源",
                    subtitle = "在全屏播放页底部显示当前歌曲的播放来源，如官方平台或社区音源插件",
                    checked = showPlaybackSource,
                    onCheckedChange = { viewModel.updateShowPlaybackSource(it) }
                )
                HorizontalDivider(color = MelodiaColors.Divider)
                SettingsSwitchRow(
                    title = "显示歌曲最高音质",
                    subtitle = "在歌曲列表的歌手名前标出该歌曲可用的最高音质",
                    checked = showSongMaxQuality,
                    onCheckedChange = { viewModel.updateShowSongMaxQuality(it) }
                )
            }
        }
    }

    // 音质单选选择弹层
    val qualityTarget = qualityDialogTarget
    if (qualityTarget != null) {
        val qualities = if (qualityTarget == "download") {
            DOWNLOAD_QUALITY_LEVELS.map { it to getQualityDisplayName(it) }
        } else {
            listOf(
                "standard" to "标准音质",
                "exhigh" to "极高音质",
                "lossless" to "无损音质 (FLAC)",
                "hires" to "Hi-Res 无损",
                "jymaster" to "超清母带"
            )
        }
        val activeQuality = when (qualityTarget) {
            "wifi" -> wifiQuality
            "download" -> downloadQuality
            else -> mobileQuality
        }
        val titleText = when (qualityTarget) {
            "wifi" -> "选择 Wi-Fi 播放音质"
            "download" -> "选择默认下载音质"
            else -> "选择移动网络播放音质"
        }
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

        ModalBottomSheet(
            onDismissRequest = { qualityDialogTarget = null },
            sheetState = sheetState,
            containerColor = MaterialTheme.colorScheme.background,
            shape = BottomSheetShape,
            dragHandle = { MelodiaDragHandle() }
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(start = MelodiaSpacing.lg, end = MelodiaSpacing.lg, bottom = MelodiaSpacing.lg)
            ) {
                Text(
                    text = titleText,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.ExtraBold,
                    modifier = Modifier.padding(bottom = MelodiaSpacing.md)
                )

                qualities.forEach { (key, label) ->
                    val isSelected = activeQuality == key
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                when (qualityTarget) {
                                    "wifi" -> viewModel.updateWifiQuality(key)
                                    "download" -> viewModel.updateDownloadQuality(key)
                                    else -> viewModel.updateMobileQuality(key)
                                }
                                qualityDialogTarget = null
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
                            Icon(MelodiaIcons.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
            }
        }
    }
}

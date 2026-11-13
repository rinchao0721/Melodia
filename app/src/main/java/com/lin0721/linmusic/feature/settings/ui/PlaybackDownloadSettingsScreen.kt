package com.lin0721.linmusic.feature.settings.ui

import android.content.Intent
import android.net.Uri
import android.os.Environment
import android.provider.DocumentsContract
import com.lin0721.linmusic.core.ui.theme.MelodiaColors
import java.io.File
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lin0721.linmusic.LocalBottomOverlayInset
import com.lin0721.linmusic.core.download.DownloadFolderScanner
import com.lin0721.linmusic.core.player.CrossfadePolicy
import com.lin0721.linmusic.feature.player.ui.LyricCapsuleSlider
import com.lin0721.linmusic.core.ui.components.ToastManager
import com.lin0721.linmusic.core.ui.theme.MelodiaSpacing
import org.koin.compose.koinInject

private fun resolveDisplayPath(context: android.content.Context, uriString: String?): String {
    if (uriString.isNullOrBlank()) {
        return "${Environment.getExternalStorageDirectory().absolutePath}/Music/Melodia"
    }
    val uri = Uri.parse(uriString)
    return runCatching {
        val treeDocId = DocumentsContract.getTreeDocumentId(uri)
        val (volumeId, relativePath) = treeDocId.split(":", limit = 2).let { it[0] to it.getOrElse(1) { "" } }
        if (volumeId == "primary") {
            "${Environment.getExternalStorageDirectory().absolutePath}/$relativePath".trimEnd('/')
        } else {
            "$volumeId/$relativePath".trimEnd('/')
        }
    }.getOrElse {
        DocumentFile.fromTreeUri(context, uri)?.name ?: "自定义目录"
    }
}

private fun resolveInitialFolderUri(uriString: String?): Uri {
    if (!uriString.isNullOrBlank()) {
        return Uri.parse(uriString)
    }
    runCatching {
        val defaultDir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC), "Melodia")
        if (!defaultDir.exists()) {
            defaultDir.mkdirs()
        }
    }
    return DocumentsContract.buildDocumentUri(
        "com.android.externalstorage.documents",
        "primary:Music/Melodia"
    )
}

@Composable
fun PlaybackSettingsView(viewModel: SettingsViewModel) {
    val autoPlayNext by viewModel.autoPlayNext.collectAsStateWithLifecycle()
    val playWithOtherApps by viewModel.playWithOtherApps.collectAsStateWithLifecycle()
    val audioOffload by viewModel.audioOffload.collectAsStateWithLifecycle()
    val exclusiveOutput by viewModel.exclusiveOutput.collectAsStateWithLifecycle()
    val resumeAfterExternalInterruption by viewModel.resumeAfterExternalInterruption.collectAsStateWithLifecycle()
    val crossfadeEnabled by viewModel.crossfadeEnabled.collectAsStateWithLifecycle()
    val crossfadeDurationMs by viewModel.crossfadeDurationMs.collectAsStateWithLifecycle()
    val smartCrossfadeEnabled by viewModel.smartCrossfadeEnabled.collectAsStateWithLifecycle()
    val loudnessNormalizationEnabled by viewModel.loudnessNormalizationEnabled.collectAsStateWithLifecycle()

    // 渲染播放的子设置项
    SettingsPageColumn {
        item {
            SettingsGroupCard(SettingsSubMenu.PLAYBACK.sectionTitles[0]) {
                SettingsSwitchRow(
                    title = "自动播放推荐新歌",
                    subtitle = "当前曲目播放完毕后自动接入相似推荐",
                    checked = autoPlayNext,
                    onCheckedChange = { viewModel.updateAutoPlayNext(it) }
                )
                HorizontalDivider(color = MelodiaColors.Divider)
                SettingsSwitchRow(
                    title = "与其他应用同时播放",
                    subtitle = "开启后不被其他应用打断播放",
                    checked = playWithOtherApps,
                    onCheckedChange = { viewModel.updatePlayWithOtherApps(it) }
                )
                HorizontalDivider(color = MelodiaColors.Divider)
                SettingsSwitchRow(
                    title = "硬件音频解码",
                    subtitle = "交给音频芯片解码，后台播放更省电；与切歌淡入淡出不能同时开启",
                    checked = audioOffload,
                    onCheckedChange = { viewModel.updateAudioOffload(it) }
                )
                HorizontalDivider(color = MelodiaColors.Divider)
                SettingsSwitchRow(
                    title = "USB 独占输出",
                    subtitle = "接 USB 解码器时绕过系统混音直接输出；期间系统和其他应用的声音改从手机扬声器播放，与切歌淡入淡出、硬件音频解码不能同时开启",
                    checked = exclusiveOutput,
                    onCheckedChange = { viewModel.updateExclusiveOutput(it) }
                )
                HorizontalDivider(color = MelodiaColors.Divider)
                SettingsSwitchRow(
                    title = "外部音视频停止后自动恢复",
                    subtitle = "被其他音视频打断暂停后，在对方停止播放时尝试自动继续",
                    checked = resumeAfterExternalInterruption,
                    onCheckedChange = { viewModel.updateResumeAfterExternalInterruption(it) }
                )
                HorizontalDivider(color = MelodiaColors.Divider)
                SettingsSwitchRow(
                    title = "音量平衡（实验性）",
                    subtitle = "按每首歌的响度统一音量；在线歌曲仅 Wi-Fi 下分析",
                    checked = loudnessNormalizationEnabled,
                    onCheckedChange = { viewModel.updateLoudnessNormalizationEnabled(it) }
                )
                HorizontalDivider(color = MelodiaColors.Divider)
                SettingsSwitchRow(
                    title = "切歌淡入淡出",
                    subtitle = "歌曲临近结束时与下一首交叠过渡，手动切歌不触发",
                    checked = crossfadeEnabled,
                    onCheckedChange = { viewModel.updateCrossfadeEnabled(it) }
                )
                if (crossfadeEnabled) {
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
                            Text("过渡时长", color = MaterialTheme.colorScheme.onSurface, fontSize = 15.sp)
                            Text(
                                text = "${crossfadeDurationMs} ms",
                                color = MaterialTheme.colorScheme.primary,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Spacer(modifier = Modifier.height(10.dp))
                        LyricCapsuleSlider(
                            value = crossfadeDurationMs,
                            onValueChange = { viewModel.updateCrossfadeDurationMs(CrossfadePolicy.normalizeDurationMs(it)) },
                            valueRange = CrossfadePolicy.MIN_DURATION_MS.toFloat()..CrossfadePolicy.MAX_DURATION_MS.toFloat(),
                            startLabel = "短",
                            endLabel = "长"
                        )
                    }
                    SettingsSwitchRow(
                        title = "智能过渡（实验性）",
                        subtitle = "跳过歌曲首尾的静音段；仅 Wi-Fi 或本地歌曲生效",
                        checked = smartCrossfadeEnabled,
                        onCheckedChange = { viewModel.updateSmartCrossfadeEnabled(it) }
                    )
                }
            }
        }
    }
}

@Composable
fun DownloadSettingsView(viewModel: SettingsViewModel) {
    val context = LocalContext.current
    val streamCacheEnabled by viewModel.streamCacheEnabled.collectAsStateWithLifecycle()
    val downloadFolderUri by viewModel.downloadFolderUri.collectAsStateWithLifecycle()
    val downloadLyricsEnabled by viewModel.downloadLyricsEnabled.collectAsStateWithLifecycle()
    val folderScanner: DownloadFolderScanner = koinInject()
    val scanning by folderScanner.scanning.collectAsStateWithLifecycle()

    val folderPickerLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        context.contentResolver.takePersistableUriPermission(
            uri,
            Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        )
        // 保留旧目录持久权限，避免已有下载文件丢失读取授权
        viewModel.updateDownloadFolderUri(uri.toString())
        ToastManager.showToast("下载目录已更新")
    }

    val scanPickerLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        folderScanner.launchScan(uri) { count ->
            ToastManager.showToast(
                when {
                    count == null -> "无法读取该目录"
                    count == 0 -> "该目录下没有找到音频文件"
                    else -> "已扫描 $count 个音频文件，打开歌单或下载时会自动识别"
                }
            )
        }
    }

    val downloadFolderSubtitle = remember(downloadFolderUri) {
        resolveDisplayPath(context, downloadFolderUri)
    }

    SettingsPageColumn {
        item {
            SettingsGroupCard(SettingsSubMenu.DOWNLOAD_STORAGE.sectionTitles[0]) {
                SettingsSwitchRow(
                    title = "边听边存",
                    subtitle = "在线播放时自动以当前音质保存歌曲到「边听边存」目录",
                    checked = streamCacheEnabled,
                    onCheckedChange = { viewModel.updateStreamCacheEnabled(it) }
                )
                HorizontalDivider(color = MelodiaColors.Divider)
                SettingsRow(
                    title = "下载目录",
                    subtitle = downloadFolderSubtitle,
                    onClick = {
                        val initialUri = resolveInitialFolderUri(downloadFolderUri)
                        folderPickerLauncher.launch(initialUri)
                    }
                )
                HorizontalDivider(color = MelodiaColors.Divider)
                SettingsRow(
                    title = "识别已下载歌曲",
                    subtitle = if (scanning) "正在扫描…" else "选择之前的下载目录，已有歌曲不再重复下载",
                    onClick = {
                        if (!scanning) scanPickerLauncher.launch(resolveInitialFolderUri(downloadFolderUri))
                    }
                )
                HorizontalDivider(color = MelodiaColors.Divider)
                SettingsSwitchRow(
                    title = "内嵌歌词",
                    subtitle = "下载歌曲与边听边存时将逐字歌词内嵌写入音频文件",
                    checked = downloadLyricsEnabled,
                    onCheckedChange = { viewModel.updateDownloadLyricsEnabled(it) }
                )
                if (!downloadFolderUri.isNullOrBlank()) {
                    HorizontalDivider(color = MelodiaColors.Divider)
                    SettingsRow(
                        title = "恢复默认下载目录",
                        subtitle = "改回 Music/Melodia",
                        onClick = {
                            viewModel.updateDownloadFolderUri(null)
                            ToastManager.showToast("已恢复默认下载目录")
                        }
                    )
                }
            }
        }
    }
}

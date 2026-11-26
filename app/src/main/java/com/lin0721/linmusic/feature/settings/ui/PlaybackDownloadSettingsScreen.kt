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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
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
import com.lin0721.linmusic.core.player.ffmpeg.FfmpegCatalogInfo
import com.lin0721.linmusic.core.player.ffmpeg.FfmpegComponentState
import com.lin0721.linmusic.core.player.ffmpeg.formatComponentSize
import com.lin0721.linmusic.core.ui.components.MelodiaTextButton
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
    val notificationButtons by viewModel.notificationButtons.collectAsStateWithLifecycle()
    val ffmpegState by viewModel.ffmpegComponentState.collectAsStateWithLifecycle()
    val ffmpegCatalogInfo by viewModel.ffmpegCatalogInfo.collectAsStateWithLifecycle()
    var showNotificationButtonsSheet by remember { mutableStateOf(false) }
    var showFfmpegDialog by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        viewModel.refreshFfmpegCatalog()
    }

    // 渲染播放的子设置项
    SettingsPageColumn {
        item {
            SettingsGroupCard(SettingsSubMenu.PLAYBACK.sectionTitles[0]) {
                val enabledButtonsSummary = remember(notificationButtons) {
                    val titles = notificationButtons.filter { it.visible }.map { it.item.title }
                    if (titles.isEmpty()) "仅显示固定播控按钮" else "当前启用：${titles.joinToString("、")}"
                }
                SettingsRow(
                    title = "通知栏按钮",
                    subtitle = enabledButtonsSummary,
                    onClick = { showNotificationButtonsSheet = true }
                )
                HorizontalDivider(color = MelodiaColors.Divider)
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
                HorizontalDivider(color = MelodiaColors.Divider)
                val hasFfmpegUpdate = viewModel.hasFfmpegUpdate(ffmpegState, ffmpegCatalogInfo)
                val ffmpegStatusText = when (val state = ffmpegState) {
                    FfmpegComponentState.NotInstalled ->
                        ffmpegCatalogInfo.remoteSizeBytes?.let { "未安装（约 ${formatComponentSize(it)}）" } ?: "未安装"
                    is FfmpegComponentState.Downloading -> {
                        val pct = state.progress?.let { " ${(it * 100).toInt()}%" }.orEmpty()
                        "正在下载$pct…"
                    }
                    is FfmpegComponentState.Installed -> buildString {
                        append("已安装 ${state.version}（${formatComponentSize(state.sizeBytes)}）")
                        if (hasFfmpegUpdate && ffmpegCatalogInfo.remoteVersion != null) {
                            append(" · 可更新至 ${ffmpegCatalogInfo.remoteVersion}")
                        }
                    }
                    is FfmpegComponentState.Failed -> "失败：${state.reason}"
                }
                SettingsRow(
                    title = "ffmpeg 解码组件",
                    subtitle = "用于播放 ALAC 等系统不支持的音频格式 · $ffmpegStatusText",
                    onClick = {
                        viewModel.refreshFfmpegCatalog()
                        showFfmpegDialog = true
                    }
                )
            }
        }
    }

    if (showNotificationButtonsSheet) {
        NotificationButtonsSheet(
            layout = notificationButtons,
            onLayoutChange = { viewModel.updateNotificationButtons(it) },
            onDismiss = { showNotificationButtonsSheet = false }
        )
    }

    if (showFfmpegDialog) {
        FfmpegComponentDialog(
            state = ffmpegState,
            catalogInfo = ffmpegCatalogInfo,
            hasUpdate = viewModel.hasFfmpegUpdate(ffmpegState, ffmpegCatalogInfo),
            onInstallOrUpdate = { viewModel.installOrUpdateFfmpegComponent() },
            onRefreshCatalog = { viewModel.refreshFfmpegCatalog() },
            onDelete = {
                viewModel.deleteFfmpegComponent()
                showFfmpegDialog = false
            },
            onDismiss = {
                viewModel.cancelFfmpegInstallOrRestore()
                showFfmpegDialog = false
            }
        )
    }
}

@Composable
private fun FfmpegComponentDialog(
    state: FfmpegComponentState,
    catalogInfo: FfmpegCatalogInfo,
    hasUpdate: Boolean,
    onInstallOrUpdate: () -> Unit,
    onRefreshCatalog: () -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit
) {
    val isDownloading = state is FfmpegComponentState.Downloading
    val isInstalled = state is FfmpegComponentState.Installed

    AlertDialog(
        onDismissRequest = { if (!isDownloading) onDismiss() },
        title = {
            Text("ffmpeg 解码组件", color = MelodiaColors.TextPrimary, fontWeight = FontWeight.Bold)
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "用于在系统解码器不支持时兜底播放 ALAC 等音频格式，不影响系统原本可解码的音频。",
                    color = MelodiaColors.TextSecondary,
                    fontSize = 13.sp,
                    lineHeight = 18.sp
                )
                when (state) {
                    FfmpegComponentState.NotInstalled -> {
                        Text("当前状态：未安装", color = MelodiaColors.TextPrimary, fontSize = 13.sp)
                    }
                    is FfmpegComponentState.Installed -> {
                        Text(
                            text = "已安装版本：${state.version}（${formatComponentSize(state.sizeBytes)}）",
                            color = MelodiaColors.TextPrimary,
                            fontSize = 13.sp
                        )
                    }
                    is FfmpegComponentState.Downloading -> {
                        val progress = state.progress
                        if (progress != null) {
                            LinearProgressIndicator(
                                progress = { progress },
                                modifier = Modifier.fillMaxWidth()
                            )
                            Text(
                                text = "正在下载… ${(progress * 100).toInt()}%（${formatComponentSize(state.downloadedBytes)} / ${formatComponentSize(state.totalBytes)}）",
                                color = MelodiaColors.TextSecondary,
                                fontSize = 12.sp
                            )
                        } else {
                            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                            Text("正在连接下载服务器…", color = MelodiaColors.TextSecondary, fontSize = 12.sp)
                        }
                    }
                    is FfmpegComponentState.Failed -> {
                        Text(
                            text = state.reason,
                            color = MelodiaColors.Error,
                            fontSize = 13.sp,
                            lineHeight = 18.sp
                        )
                    }
                }
                when {
                    catalogInfo.checking -> {
                        Text("正在检查远端组件版本…", color = MelodiaColors.TextTertiary, fontSize = 12.sp)
                    }
                    catalogInfo.remoteVersion != null -> {
                        val sizePart = catalogInfo.remoteSizeBytes?.let { "（约 ${formatComponentSize(it)}）" }.orEmpty()
                        Text(
                            text = "远端版本：${catalogInfo.remoteVersion}$sizePart",
                            color = MelodiaColors.TextSecondary,
                            fontSize = 12.sp
                        )
                    }
                    catalogInfo.error != null -> {
                        Text(
                            text = "目录检查失败：${catalogInfo.error}",
                            color = MelodiaColors.TextTertiary,
                            fontSize = 12.sp
                        )
                    }
                }
            }
        },
        confirmButton = {
            when {
                isDownloading -> {
                    MelodiaTextButton(enabled = false, onClick = {}) {
                        Text("下载中…", color = MelodiaColors.TextTertiary)
                    }
                }
                isInstalled && !hasUpdate -> {
                    MelodiaTextButton(
                        enabled = !catalogInfo.checking,
                        onClick = onRefreshCatalog
                    ) {
                        Text(
                            text = if (catalogInfo.checking) "检查中…" else "检查更新",
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
                else -> {
                    val label = when {
                        isInstalled && hasUpdate -> "更新"
                        state is FfmpegComponentState.Failed -> "重试"
                        else -> "下载"
                    }
                    MelodiaTextButton(onClick = onInstallOrUpdate) {
                        Text(label, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                    }
                }
            }
        },
        dismissButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                if (isInstalled && !isDownloading) {
                    MelodiaTextButton(onClick = onDelete) {
                        Text("删除", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)
                    }
                }
                MelodiaTextButton(onClick = onDismiss) {
                    Text(if (isDownloading) "取消" else "关闭", color = MaterialTheme.colorScheme.onSurface)
                }
            }
        },
        containerColor = MaterialTheme.colorScheme.surface,
        shape = MaterialTheme.shapes.medium
    )
}

@Composable
fun DownloadSettingsView(viewModel: SettingsViewModel) {
    val context = LocalContext.current
    val streamCacheEnabled by viewModel.streamCacheEnabled.collectAsStateWithLifecycle()
    val downloadFolderUri by viewModel.downloadFolderUri.collectAsStateWithLifecycle()
    val downloadLyricsEnabled by viewModel.downloadLyricsEnabled.collectAsStateWithLifecycle()
    val downloadFuzzyLegacyId by viewModel.downloadFuzzyLegacyId.collectAsStateWithLifecycle()
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
                    title = "旧 ID 模糊匹配",
                    subtitle = "带旧歌曲 ID 的已下载文件，按歌名、歌手与时长谨慎匹配，命中的歌曲标记为问号",
                    checked = downloadFuzzyLegacyId,
                    onCheckedChange = { viewModel.updateDownloadFuzzyLegacyId(it) }
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

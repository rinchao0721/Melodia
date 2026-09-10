package com.lin0721.linmusic.feature.settings.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lin0721.linmusic.core.ui.components.MelodiaTextButton
import com.lin0721.linmusic.core.ui.theme.NeteaseRed
import com.lin0721.linmusic.core.ui.theme.SurfaceDark
import com.lin0721.linmusic.core.ui.theme.TextGray
import com.lin0721.linmusic.core.update.UpdateUiState

// 全局更新弹窗，跟随 UpdateManager 的状态在"发现更新/下载中/下载完成/下载失败"间切换展示
@Composable
fun UpdateDialog(
    state: UpdateUiState,
    onDismiss: () -> Unit,
    onIgnore: () -> Unit,
    onStartDownload: () -> Unit,
    onInstall: () -> Unit
) {
    val info = when (state) {
        is UpdateUiState.Available -> state.info
        is UpdateUiState.Downloading -> state.info
        is UpdateUiState.ReadyToInstall -> state.info
        is UpdateUiState.DownloadFailed -> state.info
        else -> null
    } ?: return

    val isDownloading = state is UpdateUiState.Downloading

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            val channelSuffix = if (info.isPrerelease) "（测试版）" else ""
            Text("发现新版本 ${info.versionName}$channelSuffix", color = Color.White, fontWeight = FontWeight.Bold)
        },
        text = {
            Column {
                Text(
                    text = info.changelog.ifBlank { "暂无更新说明" },
                    color = TextGray,
                    fontSize = 13.sp,
                    lineHeight = 18.sp,
                    modifier = Modifier
                        .heightIn(max = 240.dp)
                        .verticalScroll(rememberScrollState())
                )
                when (state) {
                    is UpdateUiState.Downloading -> {
                        Spacer(modifier = Modifier.height(12.dp))
                        LinearProgressIndicator(
                            progress = { state.progress / 100f },
                            modifier = Modifier.fillMaxWidth(),
                            color = NeteaseRed
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text("下载中 ${state.progress}%", color = TextGray, fontSize = 12.sp)
                    }
                    is UpdateUiState.DownloadFailed -> {
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(state.message, color = NeteaseRed, fontSize = 12.sp)
                    }
                    is UpdateUiState.ReadyToInstall -> {
                        Spacer(modifier = Modifier.height(12.dp))
                        Text("下载完成，点击安装", color = TextGray, fontSize = 12.sp)
                    }
                    else -> Unit
                }
            }
        },
        confirmButton = {
            MelodiaTextButton(
                onClick = {
                    when (state) {
                        is UpdateUiState.ReadyToInstall -> onInstall()
                        is UpdateUiState.Downloading -> onDismiss()
                        else -> onStartDownload()
                    }
                }
            ) {
                val label = when (state) {
                    is UpdateUiState.ReadyToInstall -> "安装"
                    is UpdateUiState.DownloadFailed -> "重试"
                    is UpdateUiState.Downloading -> "后台下载"
                    else -> "立即更新"
                }
                Text(label, color = NeteaseRed, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            Row {
                if (!isDownloading) {
                    MelodiaTextButton(onClick = onIgnore) {
                        Text("忽略此版本", color = TextGray)
                    }
                    Spacer(modifier = Modifier.width(4.dp))
                }
                MelodiaTextButton(onClick = onDismiss) {
                    Text(if (isDownloading) "收起" else "稍后", color = Color.White)
                }
            }
        },
        containerColor = SurfaceDark
    )
}

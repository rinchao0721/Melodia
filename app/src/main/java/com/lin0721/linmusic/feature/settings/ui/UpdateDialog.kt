package com.lin0721.linmusic.feature.settings.ui

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lin0721.linmusic.core.log.AppLogger
import com.lin0721.linmusic.core.ui.components.MelodiaTextButton
import com.lin0721.linmusic.core.ui.components.ToastManager
import com.lin0721.linmusic.core.ui.theme.MelodiaColors
import com.lin0721.linmusic.core.update.domain.UpdateInfo

private const val TAG = "UpdateDialog"

// 全局更新弹窗：展示更新日志，"前往下载"跳转该版本的 GitHub 发布页
@Composable
fun UpdateDialog(
    info: UpdateInfo,
    onDismiss: () -> Unit,
    onHide: () -> Unit,
    onIgnore: () -> Unit
) {
    val uriHandler = LocalUriHandler.current

    AlertDialog(
        onDismissRequest = onHide,
        title = {
            val channelSuffix = if (info.isPrerelease) "（测试版）" else ""
            Text("发现新版本 ${info.versionName}$channelSuffix", color = MelodiaColors.TextPrimary, fontWeight = FontWeight.Bold)
        },
        text = {
            Text(
                text = info.changelog.ifBlank { "暂无更新说明" },
                color = MelodiaColors.TextSecondary,
                fontSize = 13.sp,
                lineHeight = 18.sp,
                modifier = Modifier
                    .heightIn(max = 240.dp)
                    .verticalScroll(rememberScrollState())
            )
        },
        confirmButton = {
            MelodiaTextButton(
                onClick = {
                    runCatching { uriHandler.openUri(info.releasePageUrl) }
                        .onSuccess { onHide() }
                        .onFailure { e ->
                            AppLogger.e(TAG, "打开发布页失败", e)
                            ToastManager.showToast("无法打开浏览器，请手动访问 GitHub 发布页")
                        }
                }
            ) {
                Text("前往下载", color = MelodiaColors.AccentText, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            Row {
                MelodiaTextButton(onClick = onIgnore) {
                    Text("忽略此版本", color = MelodiaColors.TextSecondary)
                }
                Spacer(modifier = Modifier.width(4.dp))
                MelodiaTextButton(onClick = onDismiss) {
                    Text("稍后", color = MelodiaColors.TextPrimary)
                }
            }
        },
        containerColor = MelodiaColors.Surface
    )
}

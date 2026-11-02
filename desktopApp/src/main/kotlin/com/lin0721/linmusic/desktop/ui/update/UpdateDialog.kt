package com.lin0721.linmusic.desktop.ui.update

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lin0721.linmusic.core.log.AppLogger
import com.lin0721.linmusic.core.update.domain.UpdateInfo
import com.lin0721.linmusic.desktop.ui.components.DesktopDialog
import com.lin0721.linmusic.desktop.ui.components.DialogButton
import com.lin0721.linmusic.desktop.ui.components.HoverScrollbarBox
import com.lin0721.linmusic.desktop.ui.components.copyTextToClipboard
import com.lin0721.linmusic.desktop.ui.theme.DesktopColors
import java.awt.Desktop
import java.net.URI

private const val TAG = "UpdateDialog"

// 发现新版本弹窗：展示更新日志，"前往下载"打开该版本的 GitHub 发布页
@Composable
internal fun UpdateDialog(
    info: UpdateInfo,
    onDismiss: () -> Unit,
    onHide: () -> Unit,
    onIgnore: () -> Unit,
    onMessage: (String) -> Unit
) {
    val channelSuffix = if (info.isPrerelease) "（测试版）" else ""
    DesktopDialog(
        title = "发现新版本 ${info.versionName}$channelSuffix",
        onDismiss = onHide,
        width = 440.dp,
        actions = {
            DialogButton("忽略此版本", onIgnore)
            Spacer(Modifier.weight(1f))
            DialogButton("稍后", onDismiss)
            DialogButton("前往下载", primary = true, onClick = {
                when {
                    openInBrowser(info.releasePageUrl) -> onHide()
                    copyTextToClipboard(info.releasePageUrl) -> onMessage("无法打开浏览器，已复制发布页链接")
                    else -> onMessage("无法打开浏览器，请手动访问 GitHub 发布页")
                }
            })
        }
    ) {
        val scrollState = rememberScrollState()
        HoverScrollbarBox(scrollState, Modifier.fillMaxWidth().heightIn(max = 280.dp)) {
            Text(
                text = info.changelog.ifBlank { "暂无更新说明" },
                color = DesktopColors.TextGray,
                fontSize = 13.sp,
                lineHeight = 19.sp,
                modifier = Modifier.fillMaxWidth().verticalScroll(scrollState)
            )
        }
    }
}

internal fun openInBrowser(url: String): Boolean = try {
    if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
        Desktop.getDesktop().browse(URI(url))
        true
    } else {
        false
    }
} catch (e: Exception) {
    AppLogger.w(TAG, "打开链接失败：$url", e)
    false
}

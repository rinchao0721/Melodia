package com.lin0721.linmusic.core.download.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.PauseCircleOutline
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lin0721.linmusic.core.download.DownloadTask
import com.lin0721.linmusic.core.download.DownloadTaskStatus
import com.lin0721.linmusic.core.download.SongDownloadManager
import com.lin0721.linmusic.core.ui.components.MiniStatusBanner
import com.lin0721.linmusic.core.ui.theme.MelodiaColors
import com.lin0721.linmusic.core.ui.theme.uiEnter
import com.lin0721.linmusic.core.ui.theme.uiExit
import com.lin0721.linmusic.core.ui.theme.uiTween
import kotlinx.coroutines.delay
import org.koin.compose.koinInject

private const val SUMMARY_DISPLAY_MS = 2500L

private data class DownloadBannerContent(
    val icon: ImageVector,
    val iconTint: Color,
    val title: String,
    val subtitle: String?,
    val counts: DownloadCounts?,
    val trailingText: String?,
    val closable: Boolean,
    val sheetTab: DownloadManagerTab
)

// 下载进度横幅：按一轮下载聚合进度，点击打开下载管理面板；
// 全部成功时短暂展示汇总后收起，有失败时常驻直到用户关闭
@Composable
fun DownloadProgressBanner(modifier: Modifier = Modifier) {
    val songDownloadManager: SongDownloadManager = koinInject()
    val tasksFlow = remember(songDownloadManager) { songDownloadManager.observeTasks() }
    val tasks by tasksFlow.collectAsStateWithLifecycle(initialValue = emptyList())

    // 只收录本轮进行过的任务，排除历史已完成任务
    var sessionIds by remember { mutableStateOf(emptySet<String>()) }
    val activeIds = tasks.filter { it.isActive }.mapTo(HashSet()) { it.meta.workId }
    LaunchedEffect(activeIds) {
        if (activeIds.isNotEmpty()) sessionIds = sessionIds + activeIds
    }

    val sessionTasks = tasks.filter { it.meta.workId in sessionIds || it.meta.workId in activeIds }
    val counts = sessionTasks.counts()
    val allSucceeded = activeIds.isEmpty() && sessionTasks.isNotEmpty() && counts.failed == 0 && counts.paused == 0
    LaunchedEffect(allSucceeded) {
        if (allSucceeded) {
            delay(SUMMARY_DISPLAY_MS)
            sessionIds = emptySet()
        }
    }

    val content = sessionTasks.takeIf { it.isNotEmpty() }?.let { buildBannerContent(it, counts, MelodiaColors.Accent, MelodiaColors.TextSecondary, MelodiaColors.Success, MelodiaColors.Error) }
    var lastContent by remember { mutableStateOf<DownloadBannerContent?>(null) }
    if (content != null) lastContent = content

    var sheetTab by remember { mutableStateOf<DownloadManagerTab?>(null) }

    val animatedRunning by animateFloatAsState(
        targetValue = lastContent?.counts?.runningFraction ?: 0f,
        animationSpec = uiTween(300),
        label = "downloadProgress"
    )

    AnimatedVisibility(
        visible = content != null,
        enter = uiEnter(expandVertically() + fadeIn()),
        exit = uiExit(shrinkVertically() + fadeOut()),
        modifier = modifier
    ) {
        val shown = lastContent ?: return@AnimatedVisibility
        MiniStatusBanner(
            icon = shown.icon,
            iconTint = shown.iconTint,
            title = shown.title,
            subtitle = shown.subtitle,
            progressContent = shown.counts?.let { bannerCounts ->
                { DownloadSegmentedProgress(counts = bannerCounts.copy(runningFraction = animatedRunning), height = 4.dp) }
            },
            trailingText = shown.trailingText,
            onClick = { sheetTab = shown.sheetTab },
            onClose = if (shown.closable) ({ sessionIds = emptySet() }) else null
        )
    }

    sheetTab?.let { tab ->
        DownloadManagerSheet(onDismiss = { sheetTab = null }, initialTab = tab)
    }
}

private fun buildBannerContent(
    tasks: List<DownloadTask>,
    counts: DownloadCounts,
    accent: Color,
    secondary: Color,
    success: Color,
    error: Color
): DownloadBannerContent {
    if (counts.active == 0 && counts.paused > 0) {
        return DownloadBannerContent(
            icon = Icons.Rounded.PauseCircleOutline,
            iconTint = secondary,
            title = "下载已暂停",
            subtitle = buildString {
                append("剩余 ${counts.paused} 首")
                if (counts.failed > 0) append("，失败 ${counts.failed} 首")
                append("，点击管理")
            },
            counts = counts,
            trailingText = "${counts.settled}/${counts.total}",
            closable = true,
            sheetTab = DownloadManagerTab.ACTIVE
        )
    }
    if (counts.active == 0) {
        val hasFailure = counts.failed > 0
        return DownloadBannerContent(
            icon = if (hasFailure) Icons.Rounded.ErrorOutline else Icons.Rounded.CheckCircle,
            iconTint = if (hasFailure) error else success,
            title = if (hasFailure) "下载结束" else "下载完成",
            subtitle = when {
                hasFailure -> "成功 ${counts.succeeded} 首，失败 ${counts.failed} 首，点击查看"
                counts.total == 1 -> "已保存到 Music/Melodia"
                else -> "共 ${counts.total} 首已保存"
            },
            counts = null,
            trailingText = null,
            closable = true,
            sheetTab = if (hasFailure) DownloadManagerTab.FAILED else DownloadManagerTab.SUCCEEDED
        )
    }

    val running = tasks.firstOrNull { it.status == DownloadTaskStatus.RUNNING }
    if (counts.total == 1) {
        val task = tasks.first()
        return DownloadBannerContent(
            icon = Icons.Rounded.Download,
            iconTint = accent,
            title = if (running == null) "等待下载 ${task.meta.songName}" else "正在下载 ${task.meta.songName}",
            subtitle = task.meta.artistName.ifBlank { null },
            counts = counts,
            trailingText = "${task.progress.coerceIn(0, 100)}%",
            closable = false,
            sheetTab = DownloadManagerTab.ACTIVE
        )
    }

    val activeLabels = tasks.filter { it.isActive }.map { it.meta.batchLabel ?: "单曲" }.distinct()
    val subtitle = buildString {
        append(if (activeLabels.size == 1) activeLabels.first() else "${activeLabels.size} 个下载任务")
        if (counts.failed > 0) append(" · 失败 ${counts.failed}")
    }
    return DownloadBannerContent(
        icon = Icons.Rounded.Download,
        iconTint = accent,
        title = running?.let { "正在下载 ${it.meta.songName}" } ?: "等待下载 ${counts.active} 首歌曲",
        subtitle = subtitle,
        counts = counts,
        trailingText = "${counts.settled}/${counts.total}",
        closable = false,
        sheetTab = DownloadManagerTab.ACTIVE
    )
}

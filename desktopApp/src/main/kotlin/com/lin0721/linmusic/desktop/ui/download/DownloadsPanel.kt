package com.lin0721.linmusic.desktop.ui.download

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lin0721.linmusic.desktop.data.download.DesktopSongDownloader
import com.lin0721.linmusic.desktop.data.download.DownloadTask
import com.lin0721.linmusic.desktop.data.download.DownloadTaskStatus
import com.lin0721.linmusic.desktop.ui.components.Cover
import com.lin0721.linmusic.desktop.ui.components.DesktopTooltip
import com.lin0721.linmusic.desktop.ui.components.HoverScrollbarBox
import com.lin0721.linmusic.desktop.ui.components.OverlayPanelHeader
import com.lin0721.linmusic.desktop.ui.components.TooltipSide
import com.lin0721.linmusic.desktop.ui.theme.DesktopColors

private val CoverSize = 44.dp
private val RowButtonSize = 28.dp

private enum class DownloadFilter(val label: String) {
    ALL("全部"),
    ACTIVE("进行中"),
    FAILED("失败"),
    DONE("已完成");

    fun matches(task: DownloadTask): Boolean = when (this) {
        ALL -> true
        ACTIVE -> task.isLive
        FAILED -> task.status == DownloadTaskStatus.FAILED
        DONE -> task.status == DownloadTaskStatus.SUCCEEDED
    }
}

private fun List<DownloadTask>.ordered(): List<DownloadTask> = sortedWith(
    compareBy<DownloadTask> {
        when (it.status) {
            DownloadTaskStatus.DOWNLOADING -> 0
            DownloadTaskStatus.QUEUED -> 1
            DownloadTaskStatus.PAUSED -> 2
            DownloadTaskStatus.FAILED -> 3
            DownloadTaskStatus.SUCCEEDED -> 4
        }
    }.thenBy { it.createdAt }
)

@Composable
fun DownloadsPanel(
    downloader: DesktopSongDownloader,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    val tasks by downloader.tasks.collectAsState()
    var filter by remember { mutableStateOf(DownloadFilter.ALL) }
    val shown = remember(tasks, filter) { tasks.filter(filter::matches).ordered() }
    val finishedCount = tasks.count { it.status == DownloadTaskStatus.SUCCEEDED }
    val failedCount = tasks.count { it.status == DownloadTaskStatus.FAILED }
    val listState = rememberLazyListState()

    Column(modifier.fillMaxSize().padding(top = 16.dp)) {
        OverlayPanelHeader("下载管理", "关闭下载管理", onClose, Modifier.padding(horizontal = 16.dp))
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "${finishedCount} / ${tasks.size} 首已完成",
                color = DesktopColors.TextGray,
                fontSize = 12.sp,
                modifier = Modifier.weight(1f)
            )
            if (failedCount > 0) {
                TextButton(onClick = downloader::clearFailed) { Text("清除失败", color = DesktopColors.TextGray, fontSize = 12.sp) }
            }
            if (finishedCount > 0) {
                TextButton(onClick = downloader::clearFinished) { Text("清除已完成", color = DesktopColors.TextGray, fontSize = 12.sp) }
            }
        }
        Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            DownloadFilter.entries.forEach { entry ->
                val count = tasks.count(entry::matches)
                FilterChip(
                    selected = filter == entry,
                    onClick = { filter = entry },
                    label = { Text(if (count > 0) "${entry.label} $count" else entry.label, fontSize = 12.sp) },
                    colors = FilterChipDefaults.filterChipColors(
                        containerColor = DesktopColors.Surface,
                        labelColor = DesktopColors.TextGray,
                        selectedContainerColor = DesktopColors.TextPrimary,
                        selectedLabelColor = DesktopColors.WindowBackground
                    ),
                    border = null
                )
            }
        }
        if (shown.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    if (tasks.isEmpty()) "暂无下载任务" else "没有${filter.label}的任务",
                    color = DesktopColors.TextGray,
                    fontSize = 14.sp
                )
            }
        } else {
            HoverScrollbarBox(listState, Modifier.weight(1f).padding(top = 8.dp)) {
                LazyColumn(state = listState, modifier = Modifier.fillMaxSize().padding(horizontal = 8.dp)) {
                    items(shown, key = { it.id }) { task ->
                        DownloadTaskRow(
                            task = task,
                            onPause = { downloader.pause(task.id) },
                            onResume = { downloader.resume(task.id) },
                            onCancel = { downloader.cancel(task.id) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DownloadTaskRow(task: DownloadTask, onPause: () -> Unit, onResume: () -> Unit, onCancel: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Cover(task.coverUrl, CoverSize)
        Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
            Text(task.songName, color = DesktopColors.TextPrimary, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                listOfNotNull(task.artistName.takeIf { it.isNotBlank() }, task.batchLabel).joinToString(" · "),
                color = DesktopColors.TextGray,
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            TaskStatusLine(task)
        }
        when (task.status) {
            DownloadTaskStatus.DOWNLOADING, DownloadTaskStatus.QUEUED -> RowAction(Icons.Rounded.Pause, "暂停", onPause)
            DownloadTaskStatus.PAUSED -> RowAction(Icons.Rounded.PlayArrow, "继续", onResume)
            DownloadTaskStatus.FAILED -> RowAction(Icons.Rounded.Refresh, "重试", onResume)
            DownloadTaskStatus.SUCCEEDED -> Unit
        }
        RowAction(Icons.Rounded.Close, if (task.status == DownloadTaskStatus.SUCCEEDED) "移除" else "取消下载", onCancel)
    }
}

@Composable
private fun TaskStatusLine(task: DownloadTask) {
    when (task.status) {
        DownloadTaskStatus.DOWNLOADING -> Row(Modifier.padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            LinearProgressIndicator(
                progress = { task.progress / 100f },
                modifier = Modifier.weight(1f).height(3.dp),
                color = DesktopColors.AccentText,
                trackColor = DesktopColors.SurfaceLight
            )
            Text("${task.progress}%", color = DesktopColors.TextGray, fontSize = 11.sp, modifier = Modifier.padding(start = 8.dp))
        }
        DownloadTaskStatus.QUEUED -> StatusText("排队中", DesktopColors.TextGray)
        DownloadTaskStatus.PAUSED -> StatusText("已暂停", DesktopColors.TextGray)
        DownloadTaskStatus.FAILED -> StatusText(task.failureReason ?: "下载失败", DesktopColors.Accent)
        DownloadTaskStatus.SUCCEEDED -> StatusText(if (task.skipped) "已下载过" else "已完成", DesktopColors.TextGray)
    }
}

@Composable
private fun StatusText(text: String, color: androidx.compose.ui.graphics.Color) {
    Text(text, color = color, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 4.dp))
}

@Composable
private fun RowAction(icon: androidx.compose.ui.graphics.vector.ImageVector, description: String, onClick: () -> Unit) {
    DesktopTooltip(description, side = TooltipSide.Left) {
        IconButton(onClick = onClick, modifier = Modifier.size(RowButtonSize)) {
            Icon(icon, description, tint = DesktopColors.TextGray, modifier = Modifier.size(18.dp))
        }
    }
}

package com.lin0721.linmusic.core.download.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DragHandle
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.RadioButtonUnchecked
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.VerticalAlignTop
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.SubcomposeAsyncImage
import com.lin0721.linmusic.core.download.DownloadTask
import com.lin0721.linmusic.core.download.DownloadTaskStatus
import com.lin0721.linmusic.core.model.getQualityDisplayName
import com.lin0721.linmusic.core.ui.components.CoverPlaceholder
import com.lin0721.linmusic.core.ui.theme.MelodiaColors
import com.lin0721.linmusic.core.ui.theme.MelodiaSpacing

internal val TrackGray: Color @Composable @ReadOnlyComposable get() = MelodiaColors.SurfaceLight
internal val MutedText: Color @Composable @ReadOnlyComposable get() = MelodiaColors.TextTertiary

// 单条任务的操作集合，普通行、长按菜单与编辑栏共用
internal class TaskActions(
    val onPrioritize: (List<DownloadTask>) -> Unit,
    val onPause: (List<DownloadTask>) -> Unit,
    val onResume: (List<DownloadTask>) -> Unit,
    val onCancel: (List<DownloadTask>) -> Unit,
    val onRetry: (List<DownloadTask>) -> Unit,
    val onRemove: (List<DownloadTask>) -> Unit
)

// 普通模式的任务行：右侧为主要操作，长按弹出更多操作
@Composable
internal fun TaskRow(task: DownloadTask, queuePosition: Int?, actions: TaskActions) {
    var menuOpen by remember { mutableStateOf(false) }
    Box {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .combinedClickable(onClick = {}, onLongClick = { menuOpen = true })
                .padding(horizontal = MelodiaSpacing.sm, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TaskCover(task)
            Spacer(Modifier.width(10.dp))
            TaskInfo(task, modifier = Modifier.weight(1f))
            Spacer(Modifier.width(8.dp))
            when (task.status) {
                DownloadTaskStatus.RUNNING, DownloadTaskStatus.WAITING, DownloadTaskStatus.PAUSED -> {
                    Text(
                        text = when (task.status) {
                            DownloadTaskStatus.RUNNING -> "${task.progress}%"
                            DownloadTaskStatus.PAUSED -> "已暂停"
                            else -> queuePosition?.let { "排队第 $it" } ?: "排队中"
                        },
                        color = if (task.status == DownloadTaskStatus.RUNNING) MelodiaColors.TextPrimary else MutedText,
                        fontSize = 12.sp
                    )
                    if (task.status == DownloadTaskStatus.PAUSED) {
                        IconButton(onClick = { actions.onResume(listOf(task)) }) {
                            Icon(Icons.Rounded.PlayArrow, contentDescription = "继续下载", tint = MelodiaColors.TextPrimary, modifier = Modifier.size(20.dp))
                        }
                    } else {
                        IconButton(onClick = { actions.onPause(listOf(task)) }) {
                            Icon(Icons.Rounded.Pause, contentDescription = "暂停下载", tint = MelodiaColors.TextPrimary, modifier = Modifier.size(20.dp))
                        }
                    }
                    IconButton(onClick = { actions.onCancel(listOf(task)) }, modifier = Modifier.size(36.dp)) {
                        Icon(Icons.Rounded.Close, contentDescription = "取消下载", tint = MutedText, modifier = Modifier.size(18.dp))
                    }
                }
                DownloadTaskStatus.FAILED -> {
                    Box(
                        modifier = Modifier
                            .height(32.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .border(1.dp, MelodiaColors.Overlay.copy(alpha = 0.25f), RoundedCornerShape(16.dp))
                            .clickable { actions.onRetry(listOf(task)) }
                            .padding(horizontal = 12.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(text = "重试", color = MelodiaColors.TextPrimary, fontSize = 12.sp)
                    }
                }
                DownloadTaskStatus.SUCCEEDED -> {
                    Icon(
                        imageVector = Icons.Rounded.CheckCircle,
                        contentDescription = "已完成",
                        tint = MelodiaColors.Success,
                        modifier = Modifier.padding(end = 8.dp).size(20.dp)
                    )
                }
            }
        }
        DropdownMenu(
            expanded = menuOpen,
            onDismissRequest = { menuOpen = false },
            containerColor = MelodiaColors.Surface
        ) {
            menuItemsFor(task, actions, MelodiaColors.TextPrimary, MelodiaColors.Error).forEach { item ->
                DropdownMenuItem(
                    text = { Text(item.label, color = item.color) },
                    leadingIcon = { Icon(item.icon, contentDescription = null, tint = item.color) },
                    onClick = {
                        menuOpen = false
                        item.onClick()
                    }
                )
            }
        }
    }
}

private class MenuItem(val label: String, val icon: ImageVector, val color: Color, val onClick: () -> Unit)

private fun menuItemsFor(task: DownloadTask, actions: TaskActions, normal: Color, danger: Color): List<MenuItem> {
    val single = listOf(task)
    return when (task.status) {
        DownloadTaskStatus.RUNNING, DownloadTaskStatus.WAITING, DownloadTaskStatus.PAUSED -> listOfNotNull(
            MenuItem("优先下载", Icons.Rounded.VerticalAlignTop, normal) { actions.onPrioritize(single) }
                .takeIf { task.status != DownloadTaskStatus.RUNNING },
            if (task.status == DownloadTaskStatus.PAUSED) {
                MenuItem("继续", Icons.Rounded.PlayArrow, normal) { actions.onResume(single) }
            } else {
                MenuItem("暂停", Icons.Rounded.Pause, normal) { actions.onPause(single) }
            },
            MenuItem("取消下载", Icons.Rounded.Close, danger) { actions.onCancel(single) }
        )
        DownloadTaskStatus.FAILED -> listOf(
            MenuItem("重试", Icons.Rounded.Refresh, normal) { actions.onRetry(single) },
            MenuItem("移除记录", Icons.Outlined.DeleteOutline, danger) { actions.onRemove(single) }
        )
        DownloadTaskStatus.SUCCEEDED -> listOf(
            MenuItem("移除记录", Icons.Outlined.DeleteOutline, danger) { actions.onRemove(single) }
        )
    }
}

// 编辑模式的任务行：左侧勾选，可排序时右侧显示拖动把手
@Composable
internal fun EditableTaskRow(
    task: DownloadTask,
    selected: Boolean,
    onToggle: () -> Unit,
    dragHandle: (Modifier.() -> Modifier)?,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(if (selected) MelodiaColors.Accent.copy(alpha = 0.12f) else Color.Transparent)
            .clickable(onClick = onToggle)
            .padding(start = MelodiaSpacing.sm, end = MelodiaSpacing.xs, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = if (selected) Icons.Rounded.CheckCircle else Icons.Rounded.RadioButtonUnchecked,
            contentDescription = if (selected) "取消选择" else "选择",
            tint = if (selected) MelodiaColors.Accent else MutedText,
            modifier = Modifier.size(22.dp)
        )
        Spacer(Modifier.width(10.dp))
        TaskCover(task)
        Spacer(Modifier.width(10.dp))
        TaskInfo(task, modifier = Modifier.weight(1f), showProgress = false)
        if (dragHandle != null) {
            Box(
                modifier = Modifier.size(44.dp).clip(RoundedCornerShape(8.dp)).dragHandle(),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Rounded.DragHandle, contentDescription = "拖动排序", tint = MutedText)
            }
        }
    }
}

// 拖动把手手势，交给 DragReorderState 处理位移与换位
internal fun Modifier.reorderHandle(key: Any, onStart: () -> Unit, onDrag: (Float) -> Unit, onEnd: () -> Unit): Modifier =
    pointerInput(key) {
        detectDragGestures(
            onDragStart = { onStart() },
            onDrag = { change, amount ->
                change.consume()
                onDrag(amount.y)
            },
            onDragEnd = { onEnd() },
            onDragCancel = { onEnd() }
        )
    }

// 编辑模式底部批量操作栏
@Composable
internal fun EditActionBar(tab: DownloadManagerTab, selectedTasks: List<DownloadTask>, actions: TaskActions) {
    val enabled = selectedTasks.isNotEmpty()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MelodiaColors.Surface.copy(alpha = 0.5f))
            .padding(horizontal = MelodiaSpacing.xs, vertical = MelodiaSpacing.xs),
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        when (tab) {
            DownloadManagerTab.ACTIVE -> {
                EditAction("优先下载", Icons.Rounded.VerticalAlignTop, enabled) { actions.onPrioritize(selectedTasks) }
                EditAction("暂停", Icons.Rounded.Pause, enabled) { actions.onPause(selectedTasks) }
                EditAction("继续", Icons.Rounded.PlayArrow, enabled) { actions.onResume(selectedTasks) }
                EditAction("取消下载", Icons.Rounded.Close, enabled, MelodiaColors.Error) { actions.onCancel(selectedTasks) }
            }
            DownloadManagerTab.FAILED -> {
                EditAction("重试", Icons.Rounded.Refresh, enabled) { actions.onRetry(selectedTasks) }
                EditAction("移除记录", Icons.Outlined.DeleteOutline, enabled, MelodiaColors.Error) { actions.onRemove(selectedTasks) }
            }
            DownloadManagerTab.SUCCEEDED -> {
                EditAction("移除记录", Icons.Outlined.DeleteOutline, enabled, MelodiaColors.Error) { actions.onRemove(selectedTasks) }
            }
        }
    }
}

@Composable
private fun RowScope.EditAction(
    label: String,
    icon: ImageVector,
    enabled: Boolean,
    color: Color = MelodiaColors.TextPrimary,
    onClick: () -> Unit
) {
    val tint = if (enabled) color else MutedText.copy(alpha = 0.5f)
    Column(
        modifier = Modifier
            .weight(1f)
            .clip(RoundedCornerShape(12.dp))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(22.dp))
        Spacer(Modifier.height(4.dp))
        Text(text = label, color = tint, fontSize = 11.sp)
    }
}

@Composable
private fun TaskCover(task: DownloadTask) {
    val cover = task.meta.coverUrl?.let { url ->
        if (url.startsWith("http://") || url.startsWith("https://")) "$url?param=120y120" else url
    }
    SubcomposeAsyncImage(
        model = cover,
        contentDescription = null,
        contentScale = ContentScale.Crop,
        loading = { CoverPlaceholder() },
        error = { CoverPlaceholder() },
        modifier = Modifier.size(44.dp).clip(RoundedCornerShape(8.dp))
    )
}

@Composable
private fun TaskInfo(task: DownloadTask, modifier: Modifier = Modifier, showProgress: Boolean = true) {
    val meta = task.meta
    Column(modifier = modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = meta.songName,
                color = MelodiaColors.TextPrimary,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false)
            )
            Spacer(Modifier.width(6.dp))
            Box(
                modifier = Modifier
                    .border(1.dp, MutedText, RoundedCornerShape(4.dp))
                    .padding(horizontal = 4.dp)
            ) {
                Text(text = getQualityDisplayName(meta.level), color = MelodiaColors.TextSecondary, fontSize = 10.sp, maxLines = 1)
            }
        }
        Spacer(Modifier.height(2.dp))
        val secondary = when {
            task.status == DownloadTaskStatus.FAILED -> task.failureReason ?: "下载失败"
            task.skipped -> "${meta.artistName} · 已存在，未重复下载"
            else -> meta.artistName
        }
        Text(
            text = secondary,
            color = if (task.status == DownloadTaskStatus.FAILED) MelodiaColors.Error else MutedText,
            fontSize = 12.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        if (showProgress && task.status == DownloadTaskStatus.RUNNING) {
            Spacer(Modifier.height(6.dp))
            LinearProgressIndicator(
                progress = { task.progress.coerceIn(0, 100) / 100f },
                modifier = Modifier.fillMaxWidth().height(3.dp),
                color = MelodiaColors.TextPrimary,
                trackColor = TrackGray,
                drawStopIndicator = {}
            )
        }
    }
}

package com.lin0721.linmusic.desktop.ui.cloud

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.CloudUpload
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lin0721.linmusic.desktop.ui.components.DesktopMenu
import com.lin0721.linmusic.desktop.ui.components.DesktopTooltip
import com.lin0721.linmusic.desktop.ui.components.TooltipSide
import com.lin0721.linmusic.desktop.ui.theme.DesktopColors
import com.lin0721.linmusic.feature.cloud.upload.UploadStatus
import com.lin0721.linmusic.feature.cloud.upload.UploadTask
import kotlin.math.roundToInt

private val ButtonSize = 32.dp
private val PanelWidth = 340.dp
private val ListMaxHeight = 320.dp

private val UploadTask.isFinished: Boolean get() = !isActive

// 队列中尚未结束的任务折算出的整体进度，全部结束时为 null
internal fun uploadOverallProgress(tasks: List<UploadTask>): Float? {
    if (tasks.none { it.isActive }) return null
    val done = tasks.count { it.isFinished } + tasks.filter { it.isActive }.sumOf { it.progress.toDouble() }
    return (done / tasks.size).toFloat().coerceIn(0f, 1f)
}

// 标题栏的云盘上传入口：有上传任务时才出现，点开查看队列
@Composable
internal fun CloudUploadsButton(cloud: CloudMusicActions) {
    val tasks by cloud.uploadManager.state.collectAsState()
    if (tasks.isEmpty()) return
    var open by remember { mutableStateOf(false) }
    val progress = uploadOverallProgress(tasks)
    val hasFailed = tasks.any { it.status == UploadStatus.FAILED }
    val hoverSource = remember { MutableInteractionSource() }
    val hovered by hoverSource.collectIsHoveredAsState()
    val tint = if (hovered || open) DesktopColors.TextPrimary else DesktopColors.TextGray
    val tooltip = progress?.let { "云盘上传中 ${(it * 100).roundToInt()}%" } ?: "云盘上传"
    Box(Modifier.padding(end = 4.dp)) {
        DesktopTooltip(tooltip, side = TooltipSide.Bottom) {
            Box(contentAlignment = Alignment.Center) {
                IconButton(onClick = { open = true }, interactionSource = hoverSource, modifier = Modifier.size(ButtonSize)) {
                    Box(contentAlignment = Alignment.Center) {
                        if (progress != null) {
                            CircularProgressIndicator(
                                progress = { progress },
                                modifier = Modifier.size(26.dp),
                                color = DesktopColors.AccentText,
                                trackColor = DesktopColors.SurfaceLight,
                                strokeWidth = 2.dp
                            )
                        }
                        Icon(Icons.Rounded.CloudUpload, "云盘上传", tint = tint, modifier = Modifier.size(if (progress != null) 15.dp else 20.dp))
                    }
                }
                if (hasFailed) {
                    Box(
                        Modifier.align(Alignment.TopEnd).padding(top = 5.dp, end = 5.dp).size(8.dp)
                            .border(1.dp, DesktopColors.WindowBackground, CircleShape)
                            .padding(1.dp)
                            .clip(CircleShape)
                            .background(DesktopColors.Accent)
                    )
                }
            }
        }
        DesktopMenu(
            expanded = open,
            onDismiss = { open = false },
            offset = DpOffset(ButtonSize - PanelWidth, 4.dp),
            width = PanelWidth
        ) {
            UploadsPanel(tasks, onRetry = cloud.uploadRunner::retry, onClearFinished = cloud.uploadManager::clearFinished)
        }
    }
}

@Composable
private fun UploadsPanel(tasks: List<UploadTask>, onRetry: (String) -> Unit, onClearFinished: () -> Unit) {
    val succeeded = tasks.count { it.status == UploadStatus.SUCCESS }
    val failed = tasks.count { it.status == UploadStatus.FAILED }
    Column(Modifier.fillMaxWidth().padding(horizontal = 8.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("云盘上传", color = DesktopColors.TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                Text(
                    buildString {
                        append("$succeeded/${tasks.size} 首完成")
                        if (failed > 0) append("，$failed 首失败")
                    },
                    color = DesktopColors.TextGray,
                    fontSize = 12.sp
                )
            }
            if (succeeded > 0) {
                TextButton(onClick = onClearFinished) { Text("清除已完成", color = DesktopColors.TextGray, fontSize = 12.sp) }
            }
        }
        LazyColumn(Modifier.fillMaxWidth().heightIn(max = ListMaxHeight).padding(top = 6.dp)) {
            items(tasks, key = { it.id }) { task -> UploadTaskRow(task) { onRetry(task.id) } }
        }
    }
}

@Composable
private fun UploadTaskRow(task: UploadTask, onRetry: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        val (icon, iconTint) = when (task.status) {
            UploadStatus.SUCCESS -> Icons.Rounded.CheckCircle to DesktopColors.Accent
            UploadStatus.FAILED -> Icons.Rounded.ErrorOutline to DesktopColors.Accent
            UploadStatus.QUEUED -> Icons.Rounded.Schedule to DesktopColors.TextGray
            else -> Icons.Rounded.CloudUpload to DesktopColors.TextPrimary
        }
        Icon(icon, null, tint = iconTint, modifier = Modifier.size(18.dp))
        Column(Modifier.weight(1f).padding(horizontal = 10.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(task.fileName, color = DesktopColors.TextPrimary, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (task.status == UploadStatus.UPLOADING) {
                LinearProgressIndicator(
                    progress = { task.progress },
                    modifier = Modifier.fillMaxWidth().height(3.dp),
                    color = DesktopColors.AccentText,
                    trackColor = DesktopColors.SurfaceLight
                )
            } else {
                Text(
                    uploadStatusText(task),
                    color = if (task.status == UploadStatus.FAILED) DesktopColors.AccentText else DesktopColors.TextGray,
                    fontSize = 11.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        if (task.status == UploadStatus.FAILED) {
            DesktopTooltip("重试") {
                IconButton(onClick = onRetry, modifier = Modifier.size(28.dp)) {
                    Icon(Icons.Rounded.Refresh, "重试", tint = DesktopColors.TextGray, modifier = Modifier.size(18.dp))
                }
            }
        }
    }
}

private fun uploadStatusText(task: UploadTask): String = when (task.status) {
    UploadStatus.QUEUED -> "等待上传"
    UploadStatus.CHECKING -> "正在校验文件"
    UploadStatus.ALLOCATING_TOKEN -> "正在准备上传"
    UploadStatus.UPLOADING -> "上传中 ${(task.progress * 100).roundToInt()}%"
    UploadStatus.REGISTERING, UploadStatus.PUBLISHING -> "正在发布到云盘"
    UploadStatus.SUCCESS -> "已上传"
    UploadStatus.FAILED -> task.errorMessage ?: "上传失败"
}

internal fun uploadBatchMessage(succeeded: Int, failed: Int): String =
    if (failed == 0) "云盘上传完成，共 $succeeded 首" else "云盘上传结束，$succeeded 首成功，$failed 首失败"

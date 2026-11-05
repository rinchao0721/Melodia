package com.lin0721.linmusic.feature.downloads.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.SubcomposeAsyncImage
import com.lin0721.linmusic.core.download.DownloadRecord
import com.lin0721.linmusic.core.download.DownloadTask
import com.lin0721.linmusic.core.download.ui.DownloadManagerSheet
import com.lin0721.linmusic.core.download.ui.DownloadManagerTab
import com.lin0721.linmusic.core.download.ui.DownloadSegmentedProgress
import com.lin0721.linmusic.core.download.ui.counts
import com.lin0721.linmusic.core.model.getQualityDisplayName
import com.lin0721.linmusic.core.player.rememberQueueItemCoverUrl
import com.lin0721.linmusic.core.ui.components.CoverPlaceholder
import com.lin0721.linmusic.core.ui.components.SecondaryScreenScaffold
import com.lin0721.linmusic.core.ui.components.ToastManager
import com.lin0721.linmusic.core.ui.interaction.pressable
import com.lin0721.linmusic.core.ui.theme.MelodiaColors
import com.lin0721.linmusic.core.ui.theme.MelodiaPress
import com.lin0721.linmusic.core.ui.theme.MelodiaSpacing
import com.lin0721.linmusic.core.ui.theme.RadiusCompact
import org.koin.androidx.compose.koinViewModel

private val CoverSize = 52.dp
private val MutedText: Color @Composable @ReadOnlyComposable get() = MelodiaColors.TextTertiary

// 侧边栏「下载管理」：顶部为下载任务入口，下方列出已下载歌曲
@Composable
fun DownloadsScreen(
    onBack: () -> Unit,
    viewModel: DownloadsViewModel = koinViewModel()
) {
    val downloads by viewModel.downloads.collectAsStateWithLifecycle()
    val tasks by viewModel.tasks.collectAsStateWithLifecycle()
    var sheetTab by remember { mutableStateOf<DownloadManagerTab?>(null) }
    var deleteTarget by remember { mutableStateOf<DownloadRecord?>(null) }

    LaunchedEffect(viewModel) {
        viewModel.toastEvent.collect { ToastManager.showToast(it) }
    }

    SecondaryScreenScaffold(title = "下载管理", onBack = onBack) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 160.dp)
        ) {
            item(key = "tasks") {
                TaskOverviewCard(
                    tasks = tasks,
                    onClick = { tab -> sheetTab = tab }
                )
            }

            val records = downloads
            when {
                records == null -> item(key = "loading") {
                    Box(Modifier.fillMaxWidth().padding(top = 48.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = MelodiaColors.Accent, modifier = Modifier.size(28.dp))
                    }
                }
                records.isEmpty() -> item(key = "empty") {
                    Text(
                        text = "还没有下载过歌曲",
                        color = MutedText,
                        fontSize = 14.sp,
                        modifier = Modifier.fillMaxWidth().padding(top = 48.dp),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                }
                else -> {
                    item(key = "header") {
                        DownloadedHeader(records = records, onPlayAll = { viewModel.play() })
                    }
                    items(records, key = { it.songId }) { record ->
                        DownloadedSongRow(
                            record = record,
                            onClick = { viewModel.play(record) },
                            onDeleteClick = { deleteTarget = record }
                        )
                    }
                }
            }
        }
    }

    sheetTab?.let { tab ->
        DownloadManagerSheet(onDismiss = { sheetTab = null }, initialTab = tab)
    }

    deleteTarget?.let { record ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text("删除下载", fontWeight = FontWeight.Bold) },
            text = { Text("确定删除「${record.displayName()}」吗？本地文件将一并删除。") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.delete(record)
                    deleteTarget = null
                }) {
                    Text("删除", color = MelodiaColors.AccentText)
                }
            },
            dismissButton = {
                TextButton(onClick = { deleteTarget = null }) {
                    Text("取消", color = MelodiaColors.TextSecondary)
                }
            }
        )
    }
}

@Composable
private fun TaskOverviewCard(tasks: List<DownloadTask>, onClick: (DownloadManagerTab) -> Unit) {
    val counts = tasks.counts()
    val tab = when {
        counts.unfinished > 0 -> DownloadManagerTab.ACTIVE
        counts.failed > 0 -> DownloadManagerTab.FAILED
        else -> DownloadManagerTab.SUCCEEDED
    }
    Column(
        modifier = Modifier
            .padding(horizontal = MelodiaSpacing.md, vertical = MelodiaSpacing.sm)
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MelodiaColors.Surface.copy(alpha = 0.6f))
            .pressable(MelodiaPress.Row) { onClick(tab) }
            .padding(14.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.Download, contentDescription = null, tint = MelodiaColors.Accent, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(text = "下载任务", color = MelodiaColors.TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.Medium)
                Spacer(Modifier.height(2.dp))
                val summary = when {
                    tasks.isEmpty() -> "暂无下载任务"
                    else -> buildList {
                        if (counts.active > 0) add("进行中 ${counts.active}")
                        if (counts.paused > 0) add("已暂停 ${counts.paused}")
                        if (counts.failed > 0) add("失败 ${counts.failed}")
                        if (counts.succeeded > 0) add("已完成 ${counts.succeeded}")
                    }.joinToString(" · ")
                }
                Text(
                    text = summary,
                    color = if (counts.failed > 0 && counts.active == 0) MelodiaColors.Error else MelodiaColors.TextSecondary,
                    fontSize = 12.sp
                )
            }
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = "打开下载任务",
                tint = MutedText
            )
        }
        if (counts.active > 0) {
            Spacer(Modifier.height(10.dp))
            DownloadSegmentedProgress(counts = counts, height = 4.dp)
        }
    }
}

@Composable
private fun DownloadedHeader(records: List<DownloadRecord>, onPlayAll: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = MelodiaSpacing.md, end = MelodiaSpacing.md, top = MelodiaSpacing.md, bottom = MelodiaSpacing.xs),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = "已下载的歌曲", color = MelodiaColors.TextPrimary, fontSize = 17.sp, fontWeight = FontWeight.Bold)
            Text(
                text = listOf("${records.size} 首", formatSize(records.sumOf { it.fileSize }))
                    .filter { it.isNotBlank() }
                    .joinToString(" · "),
                color = MutedText,
                fontSize = 12.sp
            )
        }
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(18.dp))
                .background(MelodiaColors.Accent)
                .pressable(MelodiaPress.Action, onClick = onPlayAll)
                .padding(start = 10.dp, end = 14.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Rounded.PlayArrow, contentDescription = null, tint = MelodiaColors.OnAccent, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(4.dp))
            Text(text = "播放全部", color = MelodiaColors.OnAccent, fontSize = 13.sp, fontWeight = FontWeight.Medium)
        }
    }
}

@Composable
private fun DownloadedSongRow(record: DownloadRecord, onClick: () -> Unit, onDeleteClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .pressable(MelodiaPress.Row, onClick = onClick)
            .padding(horizontal = MelodiaSpacing.md, vertical = MelodiaSpacing.sm),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 下载记录不存封面地址，从文件内嵌封面提取
        val cover = rememberQueueItemCoverUrl(coverUrl = "", songId = record.songId, localUri = record.mediaStoreUri)
        SubcomposeAsyncImage(
            model = cover.ifBlank { null },
            contentDescription = null,
            contentScale = ContentScale.Crop,
            loading = { CoverPlaceholder() },
            error = { CoverPlaceholder() },
            modifier = Modifier.size(CoverSize).clip(RoundedCornerShape(RadiusCompact))
        )
        Column(modifier = Modifier.padding(start = MelodiaSpacing.sm).weight(1f)) {
            Text(
                text = record.displayName(),
                color = MelodiaColors.TextPrimary,
                fontSize = 15.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(3.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .border(1.dp, MutedText, RoundedCornerShape(4.dp))
                        .padding(horizontal = 4.dp)
                ) {
                    Text(text = getQualityDisplayName(record.quality), color = MelodiaColors.TextSecondary, fontSize = 10.sp, maxLines = 1)
                }
                Spacer(Modifier.width(6.dp))
                Text(
                    text = listOf(record.artistName, formatSize(record.fileSize))
                        .filter { it.isNotBlank() }
                        .joinToString(" · "),
                    color = MutedText,
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        IconButton(onClick = onDeleteClick) {
            Icon(Icons.Outlined.Delete, contentDescription = "删除下载", tint = MutedText, modifier = Modifier.size(20.dp))
        }
    }
}

private fun formatSize(bytes: Long): String = when {
    bytes <= 0L -> ""
    bytes >= 1024L * 1024 * 1024 -> "%.1f GB".format(bytes / (1024.0 * 1024 * 1024))
    else -> "%.1f MB".format(bytes / (1024.0 * 1024))
}

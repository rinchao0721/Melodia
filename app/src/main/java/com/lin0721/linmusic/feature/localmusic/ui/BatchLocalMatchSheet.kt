package com.lin0721.linmusic.feature.localmusic.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.lin0721.linmusic.core.model.Track
import com.lin0721.linmusic.core.ui.components.AntiFlickerCoverImage
import com.lin0721.linmusic.feature.localmusic.data.batch.LocalMatchCoverRepository
import com.lin0721.linmusic.feature.localmusic.domain.BatchLocalMatchRow
import com.lin0721.linmusic.feature.localmusic.domain.BatchLocalMatchState
import com.lin0721.linmusic.feature.localmusic.domain.BatchLocalMatchStatus
import com.lin0721.linmusic.feature.localmusic.ui.components.LocalCover
import com.lin0721.linmusic.feature.player.ui.formatTime
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import org.koin.compose.koinInject

enum class BatchMatchFilter(val title: String) {
    ALL("全部"), MATCHED("已匹配"), NO_MATCH("未命中"), NEEDS_CONFIRMATION("需确认"),
    ERROR("出错"), PENDING("未开始"), REVIEW("待处理");

    fun accepts(status: BatchLocalMatchStatus): Boolean = when (this) {
        ALL -> true
        MATCHED -> status == BatchLocalMatchStatus.MATCHED || status == BatchLocalMatchStatus.ALREADY_LINKED
        NO_MATCH -> status == BatchLocalMatchStatus.NO_MATCH
        NEEDS_CONFIRMATION -> status == BatchLocalMatchStatus.NEEDS_CONFIRMATION
        ERROR -> status == BatchLocalMatchStatus.ERROR
        PENDING -> status == BatchLocalMatchStatus.PENDING
        REVIEW -> status.isUnresolved()
    }
}

private val visibleBatchFilters = listOf(BatchMatchFilter.REVIEW, BatchMatchFilter.ALL,
    BatchMatchFilter.MATCHED, BatchMatchFilter.NEEDS_CONFIRMATION, BatchMatchFilter.NO_MATCH, BatchMatchFilter.ERROR)

/** Report and review share one non-draggable window instead of recreating a sheet per song. */
@Composable
fun BatchLocalMatchSheet(
    state: BatchLocalMatchState,
    filter: BatchMatchFilter,
    onFilterChange: (BatchMatchFilter) -> Unit,
    listState: LazyListState,
    savingUri: String?,
    openingReview: Boolean,
    onDismiss: () -> Unit,
    onStartOrResume: () -> Unit,
    onPause: () -> Unit,
    onRefresh: () -> Unit,
    onManualAssociation: (BatchLocalMatchRow) -> Unit,
    onLinkCandidate: (BatchLocalMatchRow, Track) -> Unit,
    associationState: LocalOnlineAssociationState?,
    reviewPosition: String?,
    onCloseReview: () -> Unit,
    onSkipReview: () -> Unit,
    onReviewQueryChange: (String) -> Unit,
    onReviewSearch: () -> Unit,
    onReviewLoadMore: () -> Unit,
    onReviewConfirm: (Track) -> Unit,
    onStartReview: () -> Unit
) {
    val counts = remember(state.rows) {
        BatchMatchFilter.entries.associateWith { current -> state.rows.count { current.accepts(it.status) } }
    }
    val visibleRows = remember(state.rows, filter) { state.rows.filter { filter.accepts(it.status) } }
    val matchCovers: LocalMatchCoverRepository = koinInject()
    var displayedCovers by remember { mutableStateOf<Map<Long, String>>(emptyMap()) }
    // A missing thumbnail never gates opening candidates or confirming an association.
    LaunchedEffect(listState, visibleRows, associationState != null) {
        if (associationState != null) return@LaunchedEffect
        snapshotFlow {
            listState.layoutInfo.visibleItemsInfo.mapNotNull { visibleRows.getOrNull(it.index) }
                .flatMap { row -> listOfNotNull(row.matchedTrack, row.candidates.singleOrNull()) }
                .filter { it.al.picUrl.isBlank() }.distinctBy { it.id }
        }.distinctUntilChanged().collectLatest { tracks ->
            if (tracks.isNotEmpty()) {
                val covers = matchCovers.fillCovers(tracks).mapNotNull { track ->
                    track.al.picUrl.takeIf(String::isNotBlank)?.let { track.id to it }
                }.toMap()
                if (covers.isNotEmpty()) displayedCovers = (displayedCovers + covers).entries
                    .toList().takeLast(1_024).associate { it.toPair() }
            }
        }
    }
    val unresolvedCount = counts[BatchMatchFilter.REVIEW] ?: 0
    val saving = savingUri != null || associationState?.saving == true
    Dialog(
        onDismissRequest = { if (!saving) { if (associationState != null) onCloseReview() else onDismiss() } },
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)
    ) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Column(Modifier.fillMaxSize().systemBarsPadding().imePadding()) {
                Row(Modifier.fillMaxWidth().padding(start = 8.dp, end = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (associationState != null) IconButton(onClick = onCloseReview, enabled = !saving) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "返回匹配报告")
                    }
                    Column(Modifier.weight(1f).padding(start = if (associationState == null) 12.dp else 0.dp)) {
                        Text(if (associationState != null) "连续处理" else "本地与在线匹配",
                            style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                        if (associationState != null && reviewPosition != null) Text("本轮 $reviewPosition · 选用后自动下一首",
                            style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    IconButton(onClick = onDismiss, enabled = !saving) {
                        Icon(Icons.Rounded.Close, contentDescription = "关闭匹配页面")
                    }
                }
                HorizontalDivider()
                if (associationState != null) {
                    LocalOnlineAssociationContent(state = associationState, onQueryChange = onReviewQueryChange,
                        onSearch = onReviewSearch, onLoadMore = onReviewLoadMore, onConfirm = onReviewConfirm,
                        modifier = Modifier.weight(1f))
                    HorizontalDivider()
                    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        TextButton(onClick = onCloseReview, enabled = !saving) { Text("返回报告") }
                        TextButton(onClick = onSkipReview, enabled = !saving) { Text("稍后处理 · 下一首") }
                    }
                } else {
                    Column(Modifier.padding(horizontal = 20.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("已匹配 ${counts[BatchMatchFilter.MATCHED] ?: 0} / ${state.totalCount} · 待处理 $unresolvedCount",
                            style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                        if (state.isRunning || state.isLoading) LinearProgressIndicator(
                            progress = { if (state.totalCount == 0) 0f else state.processedCount.toFloat() / state.totalCount },
                            modifier = Modifier.fillMaxWidth())
                        state.message?.takeIf(String::isNotBlank)?.let { message ->
                            Text(message, style = MaterialTheme.typography.bodySmall, maxLines = 2,
                                overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        if (state.isLoading) Text("正在读取曲库和匹配记录…", style = MaterialTheme.typography.bodySmall)
                        // Automatic creation stays discoverable at every font scale. Manual review
                        // is a separate action and never silently replaces the batch-start button.
                        Button(onClick = if (state.isRunning) onPause else onStartOrResume,
                            enabled = !saving && !openingReview && !state.isLoading &&
                                (state.isRunning || unresolvedCount > 0), modifier = Modifier.fillMaxWidth()) {
                            Text(when {
                                state.isRunning -> "暂停自动匹配"
                                state.processedCount == 0 -> "开始自动匹配"
                                else -> "继续自动匹配"
                            })
                        }
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            TextButton(onClick = onStartReview,
                                enabled = unresolvedCount > 0 && !saving && !state.isLoading && !openingReview) {
                                Text(if (openingReview) "正在准备核验…" else "人工核验 · 连续处理")
                            }
                            TextButton(onClick = onRefresh,
                                enabled = !state.isRunning && !state.isLoading && !saving && !openingReview) { Text("刷新曲库") }
                        }
                    }
                    FlowRow(Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp), maxItemsInEachRow = 3) {
                        visibleBatchFilters.forEach { item ->
                            FilterChip(selected = filter == item || (filter == BatchMatchFilter.PENDING && item == BatchMatchFilter.REVIEW),
                                onClick = { onFilterChange(item) }, label = { Text("${item.title} ${counts[item] ?: 0}") })
                        }
                    }
                    HorizontalDivider()
                    LazyColumn(state = listState, modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp)) {
                        if (visibleRows.isEmpty()) item {
                            Text(if (state.totalCount == 0) "还没有本地歌曲，请先扫描或导入。"
                                else if (filter == BatchMatchFilter.REVIEW) "待处理歌曲已全部完成。" else "此分类没有歌曲。",
                                modifier = Modifier.padding(vertical = 24.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        items(visibleRows, key = { it.local.uri.toString() }) { row ->
                            BatchLocalMatchResultRow(row, savingUri, displayedCovers, onManualAssociation, onLinkCandidate,
                                enabled = !state.isLoading && !openingReview)
                            HorizontalDivider()
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun BatchLocalMatchResultRow(row: BatchLocalMatchRow, savingUri: String?, displayedCovers: Map<Long, String>,
    onManualAssociation: (BatchLocalMatchRow) -> Unit, onLinkCandidate: (BatchLocalMatchRow, Track) -> Unit,
    enabled: Boolean) {
    val canInteract = enabled && savingUri == null
    val linked = !row.status.isUnresolved()
    val statusText = when (row.status) {
        BatchLocalMatchStatus.PENDING -> "未开始"
        BatchLocalMatchStatus.MATCHED -> "已匹配"
        BatchLocalMatchStatus.ALREADY_LINKED -> if (row.usesExistingSource) "已有优先音源" else "已有关联"
        BatchLocalMatchStatus.NO_MATCH -> "未命中"
        BatchLocalMatchStatus.NEEDS_CONFIRMATION -> "需确认"
        BatchLocalMatchStatus.ERROR -> "出错"
    }
    Column(Modifier.fillMaxWidth().padding(vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(Modifier.fillMaxWidth().clickable(enabled = canInteract,
            onClickLabel = "查看 ${row.local.title} 的匹配候选") { onManualAssociation(row) },
            horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            LocalCover(sourceUri = row.local.uri, size = 48.dp)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(row.local.title, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text("${row.local.artist} · ${formatTime(row.local.durationMs)}", style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Text(statusText, style = MaterialTheme.typography.labelMedium, color = if (linked) MaterialTheme.colorScheme.primary
                else if (row.status == BatchLocalMatchStatus.ERROR) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if ((!linked || row.usesExistingSource) && row.reason.isNotBlank()) Text(row.reason, style = MaterialTheme.typography.bodySmall,
            maxLines = 2, overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onSurfaceVariant)
        row.matchedTrack?.let { BatchOnlineTrackDetails(it, displayedCovers[it.id]) }
        val singleCandidate = row.candidates.singleOrNull()?.takeUnless { linked }
        if (singleCandidate != null) BatchOnlineTrackDetails(singleCandidate, displayedCovers[singleCandidate.id],
            onClick = { onLinkCandidate(row, singleCandidate) }, enabled = canInteract)
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = { if (singleCandidate != null) onLinkCandidate(row, singleCandidate) else onManualAssociation(row) },
                enabled = canInteract) {
                Text(when {
                    savingUri == row.local.uri.toString() -> "正在关联…"
                    singleCandidate != null -> "选用并关联"
                    linked -> "查看或更换"
                    row.candidates.isNotEmpty() -> "查看 ${row.candidates.size} 个候选"
                    else -> "搜索并关联"
                })
            }
            if (singleCandidate != null) TextButton(onClick = { onManualAssociation(row) }, enabled = canInteract) { Text("其他候选") }
            if (savingUri == row.local.uri.toString()) CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
        }
    }
}

@Composable
private fun BatchOnlineTrackDetails(track: Track, filledCoverUrl: String? = null,
    onClick: (() -> Unit)? = null, enabled: Boolean = true) {
    Row(Modifier.fillMaxWidth().then(if (onClick != null) Modifier.clickable(enabled = enabled,
        onClickLabel = "选用并关联 ${track.name}", onClick = onClick) else Modifier).padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
        AntiFlickerCoverImage(track.al.picUrl.ifBlank { filledCoverUrl.orEmpty() }, Modifier.size(40.dp).clip(MaterialTheme.shapes.small))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text("网易云：${track.name} · ${track.ar.joinToString(" / ") { it.name }}", style = MaterialTheme.typography.bodySmall,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text("${track.al.name.ifBlank { "专辑未知" }} · ${track.dt.takeIf { it > 0L }?.let(::formatTime) ?: "时长未知"}",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

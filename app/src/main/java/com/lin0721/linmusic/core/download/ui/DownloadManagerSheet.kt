package com.lin0721.linmusic.core.download.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.zIndex
import com.lin0721.linmusic.core.ui.components.rememberDragReorderState
import com.lin0721.linmusic.core.ui.theme.LocalUiMotion
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lin0721.linmusic.core.download.DownloadTask
import com.lin0721.linmusic.core.download.DownloadTaskStatus
import com.lin0721.linmusic.core.download.SongDownloadManager
import com.lin0721.linmusic.core.ui.components.MelodiaDragHandle
import com.lin0721.linmusic.core.ui.components.MelodiaTextButton
import com.lin0721.linmusic.core.ui.theme.BottomSheetShape
import com.lin0721.linmusic.core.ui.theme.MelodiaColors
import com.lin0721.linmusic.core.ui.theme.MelodiaSpacing
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

private val PendingGray: Color @Composable @ReadOnlyComposable get() = MelodiaColors.TextDisabled

// 下载管理面板的分类
enum class DownloadManagerTab(val label: String) {
    ACTIVE("进行中"),
    SUCCEEDED("已完成"),
    FAILED("失败");

    fun matches(task: DownloadTask): Boolean = when (this) {
        ACTIVE -> task.isUnfinished
        SUCCEEDED -> task.status == DownloadTaskStatus.SUCCEEDED
        FAILED -> task.status == DownloadTaskStatus.FAILED
    }
}

// 下载任务计数
internal data class DownloadCounts(
    val total: Int,
    val succeeded: Int,
    val failed: Int,
    val running: Int,
    val waiting: Int,
    val paused: Int,
    val runningFraction: Float
) {
    val active: Int get() = running + waiting
    val unfinished: Int get() = active + paused
    val settled: Int get() = succeeded + failed
}

internal fun List<DownloadTask>.counts(): DownloadCounts {
    val running = filter { it.status == DownloadTaskStatus.RUNNING }
    return DownloadCounts(
        total = size,
        succeeded = count { it.status == DownloadTaskStatus.SUCCEEDED },
        failed = count { it.status == DownloadTaskStatus.FAILED },
        running = running.size,
        waiting = count { it.status == DownloadTaskStatus.WAITING },
        paused = count { it.status == DownloadTaskStatus.PAUSED },
        runningFraction = running.sumOf { it.progress.coerceIn(0, 100) } / 100f
    )
}

// 下载管理面板：查看各任务进度，暂停、取消、重试与清理；编辑模式下批量操作与拖动排序
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DownloadManagerSheet(
    onDismiss: () -> Unit,
    initialTab: DownloadManagerTab = DownloadManagerTab.ACTIVE
) {
    val manager: SongDownloadManager = koinInject()
    val tasksFlow = remember(manager) { manager.observeTasks() }
    val tasks by tasksFlow.collectAsStateWithLifecycle(initialValue = emptyList())
    val scope = rememberCoroutineScope()
    var tab by rememberSaveable { mutableStateOf(initialTab) }
    var editing by rememberSaveable { mutableStateOf(false) }
    var selectedIds by remember { mutableStateOf(emptySet<String>()) }
    var showClearFailedDialog by remember { mutableStateOf(false) }
    val counts = tasks.counts()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    val actions = remember(manager, scope) {
        fun run(block: suspend () -> Unit) {
            scope.launch { block() }
        }
        TaskActions(
            onPrioritize = { run { manager.prioritize(it) } },
            onPause = { run { manager.pause(it) } },
            onResume = { run { manager.resume(it) } },
            onCancel = { run { manager.cancel(it) } },
            onRetry = { run { manager.retry(it) } },
            onRemove = { run { manager.dismiss(it) } }
        )
    }
    // 批量操作后清空选择，保留编辑模式便于连续操作
    val editActions = remember(actions) {
        fun after(action: (List<DownloadTask>) -> Unit): (List<DownloadTask>) -> Unit = {
            action(it)
            selectedIds = emptySet()
        }
        TaskActions(
            onPrioritize = after(actions.onPrioritize),
            onPause = after(actions.onPause),
            onResume = after(actions.onResume),
            onCancel = after(actions.onCancel),
            onRetry = after(actions.onRetry),
            onRemove = after(actions.onRemove)
        )
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MelodiaColors.Background,
        shape = BottomSheetShape,
        dragHandle = { MelodiaDragHandle() }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.85f)
                .navigationBarsPadding()
        ) {
            val visible = tasks.filter(tab::matches)

            if (editing) {
                EditHeader(
                    selectedCount = selectedIds.size,
                    allSelected = visible.isNotEmpty() && visible.all { it.meta.workId in selectedIds },
                    onToggleAll = {
                        selectedIds = if (visible.all { it.meta.workId in selectedIds }) emptySet()
                        else visible.mapTo(HashSet()) { it.meta.workId }
                    },
                    onDone = {
                        editing = false
                        selectedIds = emptySet()
                    }
                )
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(start = MelodiaSpacing.lg, end = MelodiaSpacing.sm),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "下载管理",
                        color = MelodiaColors.TextPrimary,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.ExtraBold,
                        modifier = Modifier.weight(1f)
                    )
                    if (tasks.isNotEmpty()) {
                        TextAction("编辑", MelodiaColors.TextPrimary) { editing = true }
                    }
                }
            }
            Spacer(Modifier.height(MelodiaSpacing.sm))

            if (tasks.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Text(text = "暂无下载任务", color = MutedText, fontSize = 14.sp)
                }
                return@Column
            }

            if (!editing) {
                SummaryCard(
                    counts = counts,
                    tab = tab,
                    onPauseAll = { actions.onPause(tasks) },
                    onResumeAll = { actions.onResume(tasks) },
                    onRetryFailed = { actions.onRetry(tasks) },
                    onClearSucceeded = { actions.onRemove(tasks.filter { it.status == DownloadTaskStatus.SUCCEEDED }) },
                    onClearFailed = { showClearFailedDialog = true }
                )
            }

            Row(
                modifier = Modifier.padding(horizontal = MelodiaSpacing.md, vertical = MelodiaSpacing.sm),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                DownloadManagerTab.entries.forEach { entry ->
                    val count = when (entry) {
                        DownloadManagerTab.ACTIVE -> counts.unfinished
                        DownloadManagerTab.SUCCEEDED -> counts.succeeded
                        DownloadManagerTab.FAILED -> counts.failed
                    }
                    TabChip(
                        text = "${entry.label} $count",
                        selected = tab == entry,
                        accent = if (entry == DownloadManagerTab.FAILED && count > 0) MelodiaColors.Error else null,
                        onClick = {
                            tab = entry
                            selectedIds = emptySet()
                        }
                    )
                }
            }

            when {
                visible.isEmpty() -> Box(
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Text(text = "没有${tab.label}的任务", color = MutedText, fontSize = 14.sp)
                }
                editing -> {
                    EditableTaskList(
                        tasks = visible,
                        selectedIds = selectedIds,
                        sortable = tab == DownloadManagerTab.ACTIVE,
                        onToggle = { id -> selectedIds = if (id in selectedIds) selectedIds - id else selectedIds + id },
                        onReorder = { orderedIds -> scope.launch { manager.reorder(orderedIds) } },
                        modifier = Modifier.weight(1f)
                    )
                    EditActionBar(
                        tab = tab,
                        selectedTasks = visible.filter { it.meta.workId in selectedIds },
                        actions = editActions
                    )
                }
                else -> GroupedTaskList(tasks = visible, tab = tab, actions = actions, modifier = Modifier.weight(1f))
            }
        }
    }

    if (showClearFailedDialog) {
        AlertDialog(
            onDismissRequest = { showClearFailedDialog = false },
            title = { Text("清除失败任务", color = MelodiaColors.TextPrimary, fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    text = if (counts.failed > 0) "确定清除全部 ${counts.failed} 首失败的下载任务吗？" else "确定清除全部失败的下载任务吗？",
                    color = MelodiaColors.TextSecondary,
                    fontSize = 14.sp
                )
            },
            confirmButton = {
                MelodiaTextButton(
                    onClick = {
                        actions.onRemove(tasks.filter { it.status == DownloadTaskStatus.FAILED })
                        showClearFailedDialog = false
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MelodiaColors.Error)
                ) {
                    Text("清除", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                MelodiaTextButton(onClick = { showClearFailedDialog = false }) {
                    Text("取消", color = MelodiaColors.TextPrimary)
                }
            },
            containerColor = MelodiaColors.Surface,
            shape = RoundedCornerShape(12.dp)
        )
    }
}

// 普通模式：按歌单分组展示
@Composable
private fun GroupedTaskList(
    tasks: List<DownloadTask>,
    tab: DownloadManagerTab,
    actions: TaskActions,
    modifier: Modifier = Modifier
) {
    // 等待中的任务按排队顺序编号，tasks 已按排队顺序排列
    val queuePositions = remember(tasks) {
        tasks.filter { it.status == DownloadTaskStatus.WAITING }
            .mapIndexed { index, task -> task.meta.workId to index + 1 }
            .toMap()
    }
    val groups = tasks.groupBy { it.meta.batchLabel ?: "单曲" }
    LazyColumn(
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(start = MelodiaSpacing.sm, end = MelodiaSpacing.sm, bottom = MelodiaSpacing.lg)
    ) {
        groups.forEach { (label, groupTasks) ->
            item(key = "header_$label") {
                GroupHeader(
                    label = label,
                    tasks = groupTasks,
                    tab = tab,
                    onPause = { actions.onPause(groupTasks) },
                    onResume = { actions.onResume(groupTasks) },
                    onCancel = { actions.onCancel(groupTasks) },
                    onRetry = { actions.onRetry(groupTasks) }
                )
            }
            items(groupTasks, key = { it.meta.workId }) { task ->
                TaskRow(task = task, queuePosition = queuePositions[task.meta.workId], actions = actions)
            }
        }
    }
}

// 编辑模式：平铺列表，进行中分类可拖动调整下载顺序
@Composable
private fun EditableTaskList(
    tasks: List<DownloadTask>,
    selectedIds: Set<String>,
    sortable: Boolean,
    onToggle: (String) -> Unit,
    onReorder: (List<String>) -> Unit,
    modifier: Modifier = Modifier
) {
    // 拖动期间维护本地顺序，松手后再写入；任务增减时与最新列表对齐
    var order by remember { mutableStateOf(tasks.map { it.meta.workId }) }
    val taskById = tasks.associateBy { it.meta.workId }
    val ordered = order.filter { it in taskById } + tasks.map { it.meta.workId }.filterNot { it in order }
    val listState = rememberLazyListState()
    val reorder = rememberDragReorderState(listState) { from, to ->
        order = ordered.toMutableList().apply { add(to, removeAt(from)) }
    }
    // 拖动手势只在开始时捕获回调，用最新值避免松手时按过期的任务列表写入
    val currentTaskIds by rememberUpdatedState(taskById.keys)
    val currentOnReorder by rememberUpdatedState(onReorder)

    if (sortable && ordered.size > 1) {
        Text(
            text = "拖动右侧把手调整下载顺序，正在下载的任务不受影响",
            color = MutedText,
            fontSize = 12.sp,
            modifier = Modifier.fillMaxWidth().padding(horizontal = MelodiaSpacing.md, vertical = MelodiaSpacing.xs)
        )
    }
    // 列表里只放可排序的行，拖动换位按列表下标计算
    LazyColumn(
        state = listState,
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(start = MelodiaSpacing.sm, end = MelodiaSpacing.sm, bottom = MelodiaSpacing.sm)
    ) {
        items(ordered, key = { it }) { id ->
            val task = taskById.getValue(id)
            val dragging = reorder.draggingKey == id
            EditableTaskRow(
                task = task,
                selected = id in selectedIds,
                onToggle = { onToggle(id) },
                dragHandle = if (sortable) {
                    {
                        reorderHandle(
                            key = id,
                            onStart = { reorder.start(id) },
                            onDrag = reorder::drag,
                            onEnd = {
                                reorder.end()
                                currentOnReorder(order.filter { it in currentTaskIds })
                            }
                        )
                    }
                } else {
                    null
                },
                modifier = Modifier
                    .then(if (dragging) Modifier.zIndex(1f) else if (LocalUiMotion.current.enabled) Modifier.animateItem() else Modifier)
                    .graphicsLayer { translationY = if (dragging) reorder.dragOffset else 0f }
                    .background(if (dragging) MelodiaColors.Surface else Color.Transparent, RoundedCornerShape(12.dp))
            )
        }
    }
}

@Composable
private fun EditHeader(selectedCount: Int, allSelected: Boolean, onToggleAll: () -> Unit, onDone: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = MelodiaSpacing.sm),
        verticalAlignment = Alignment.CenterVertically
    ) {
        TextAction(if (allSelected) "全不选" else "全选", MelodiaColors.TextPrimary, onToggleAll)
        Text(
            text = if (selectedCount > 0) "已选 $selectedCount 项" else "选择任务",
            color = MelodiaColors.TextPrimary,
            fontSize = 17.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            modifier = Modifier.weight(1f)
        )
        TextAction("完成", MelodiaColors.Accent, onDone)
    }
}

@Composable
private fun SummaryCard(
    counts: DownloadCounts,
    tab: DownloadManagerTab,
    onPauseAll: () -> Unit,
    onResumeAll: () -> Unit,
    onRetryFailed: () -> Unit,
    onClearSucceeded: () -> Unit,
    onClearFailed: () -> Unit
) {
    Column(
        modifier = Modifier
            .padding(horizontal = MelodiaSpacing.md)
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MelodiaColors.Surface.copy(alpha = 0.6f))
            .padding(14.dp)
    ) {
        Row(verticalAlignment = Alignment.Bottom) {
            Text(text = "${counts.settled}", color = MelodiaColors.TextPrimary, fontSize = 26.sp, fontWeight = FontWeight.Bold)
            Text(
                text = " / ${counts.total} 首",
                color = MutedText,
                fontSize = 15.sp,
                modifier = Modifier.padding(bottom = 3.dp)
            )
        }
        Spacer(Modifier.height(10.dp))
        DownloadSegmentedProgress(counts = counts, height = 6.dp)
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            LegendDot(MelodiaColors.Success, "完成 ${counts.succeeded}")
            LegendDot(MelodiaColors.TextPrimary, "下载中 ${counts.running}")
            LegendDot(PendingGray, if (counts.paused > 0) "等待 ${counts.waiting} · 暂停 ${counts.paused}" else "等待 ${counts.waiting}")
            LegendDot(MelodiaColors.Error, "失败 ${counts.failed}")
        }
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (counts.active == 0 && counts.paused > 0) {
                ActionPill("全部继续", enabled = true, onClick = onResumeAll, modifier = Modifier.weight(1f))
            } else {
                ActionPill("全部暂停", enabled = counts.active > 0, onClick = onPauseAll, modifier = Modifier.weight(1f))
            }
            ActionPill(
                text = if (counts.failed > 0) "重试失败 ${counts.failed}" else "重试失败",
                enabled = counts.failed > 0,
                color = MelodiaColors.Error,
                onClick = onRetryFailed,
                modifier = Modifier.weight(1f)
            )
            val showClearFailed = tab == DownloadManagerTab.FAILED || (counts.succeeded == 0 && counts.failed > 0)
            if (showClearFailed) {
                ActionPill(
                    text = "清除失败",
                    enabled = counts.failed > 0,
                    color = MelodiaColors.Error,
                    onClick = onClearFailed,
                    modifier = Modifier.weight(1f)
                )
            } else {
                ActionPill(
                    text = "清除已完成",
                    enabled = counts.succeeded > 0,
                    onClick = onClearSucceeded,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

// 分段进度条：绿=完成，红=失败，白=下载中，灰底=等待
@Composable
internal fun DownloadSegmentedProgress(counts: DownloadCounts, height: Dp, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .clip(RoundedCornerShape(height / 2))
            .background(TrackGray),
        horizontalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        val remaining = counts.total - counts.settled - counts.runningFraction
        if (counts.succeeded > 0) Box(Modifier.weight(counts.succeeded.toFloat()).fillMaxHeight().background(MelodiaColors.Success))
        if (counts.failed > 0) Box(Modifier.weight(counts.failed.toFloat()).fillMaxHeight().background(MelodiaColors.Error))
        if (counts.runningFraction > 0f) Box(Modifier.weight(counts.runningFraction).fillMaxHeight().background(MelodiaColors.TextPrimary))
        if (remaining > 0f) Spacer(Modifier.weight(remaining))
    }
}

@Composable
private fun LegendDot(color: Color, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(8.dp).clip(CircleShape).background(color))
        Spacer(Modifier.width(5.dp))
        Text(text = text, color = MelodiaColors.TextSecondary, fontSize = 12.sp)
    }
}

@Composable
private fun ActionPill(
    text: String,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    color: Color = MelodiaColors.TextPrimary
) {
    Box(
        modifier = modifier
            .height(36.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(MelodiaColors.Fill)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            color = if (enabled) color else MutedText.copy(alpha = 0.6f),
            fontSize = 13.sp,
            maxLines = 1
        )
    }
}

@Composable
private fun TabChip(text: String, selected: Boolean, accent: Color?, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .height(32.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(if (selected) MelodiaColors.TextPrimary else MelodiaColors.Fill)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            color = when {
                selected -> MelodiaColors.Background
                accent != null -> accent
                else -> MelodiaColors.TextSecondary
            },
            fontSize = 13.sp,
            fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal
        )
    }
}

@Composable
private fun GroupHeader(
    label: String,
    tasks: List<DownloadTask>,
    tab: DownloadManagerTab,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onCancel: () -> Unit,
    onRetry: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = MelodiaSpacing.sm, end = MelodiaSpacing.xs, top = 12.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                color = MelodiaColors.TextPrimary,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(text = "${tasks.size} 首", color = MutedText, fontSize = 12.sp)
        }
        when {
            tab == DownloadManagerTab.ACTIVE && tasks.size > 1 -> {
                if (tasks.any { it.isActive }) {
                    TextAction("暂停", MelodiaColors.TextPrimary, onPause)
                } else {
                    TextAction("继续", MelodiaColors.TextPrimary, onResume)
                }
                TextAction("取消", MutedText, onCancel)
            }
            tab == DownloadManagerTab.FAILED && tasks.size > 1 -> TextAction("全部重试", MelodiaColors.Error, onRetry)
        }
    }
}

@Composable
private fun TextAction(text: String, color: Color, onClick: () -> Unit) {
    Text(
        text = text,
        color = color,
        fontSize = 13.sp,
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 10.dp)
    )
}

package com.lin0721.linmusic.feature.localmusic.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.lin0721.linmusic.core.model.Album
import com.lin0721.linmusic.core.ui.components.AntiFlickerCoverImage
import com.lin0721.linmusic.feature.localmusic.domain.LocalAlbum
import com.lin0721.linmusic.feature.localmusic.ui.components.LocalCover

enum class AlbumBatchStatus {
    UNPROCESSED, SEARCHING, MATCHED, REVIEW, NO_MATCH, ERROR
}

enum class AlbumBatchFilter {
    ALL, MATCHED, REVIEW, UNPROCESSED, NO_MATCH, ERROR
}

data class AlbumBatchRow(
    val local: LocalAlbum,
    val status: AlbumBatchStatus = AlbumBatchStatus.UNPROCESSED,
    val candidates: List<Album> = emptyList(),
    val selected: Album? = null,
    val reason: String = "",
    val linkedSongs: Int = 0,
    val totalSongs: Int = local.tracks.size
)

data class BatchAlbumMatchState(
    val rows: List<AlbumBatchRow> = emptyList(),
    val filter: AlbumBatchFilter = AlbumBatchFilter.ALL,
    val query: String = "",
    val running: Boolean = false,
    val current: String? = null,
    val completed: Int = 0,
    val total: Int = 0,
    val message: String? = null,
    val loading: Boolean = false
)

/** One report stays in place while albums are searched, paused, or reviewed individually. */
@Composable
fun BatchAlbumMatchDialog(
    state: BatchAlbumMatchState,
    onDismiss: () -> Unit,
    onStart: () -> Unit,
    onPause: () -> Unit,
    onFilter: (AlbumBatchFilter) -> Unit,
    onQuery: (String) -> Unit,
    onOpenRow: (String) -> Unit
) {
    val listState = rememberLazyListState()
    val keyboard = LocalSoftwareKeyboardController.current
    val focus = LocalFocusManager.current
    val counts = remember(state.rows) {
        AlbumBatchFilter.entries.associateWith { filter -> state.rows.count { filter.acceptsAlbum(it.status) } }
    }
    val visibleRows = remember(state.rows, state.filter, state.query) {
        val terms = state.query.trim().split(Regex("\\s+")).filter(String::isNotBlank)
        state.rows.filter { row ->
            val text = "${row.local.name} ${row.local.artist} ${row.selected?.name.orEmpty()} " +
                row.selected?.artists.orEmpty().joinToString(" ") { it.name }
            state.filter.acceptsAlbum(row.status) && terms.all { text.contains(it, ignoreCase = true) }
        }
    }
    val hasRemaining = state.rows.any { it.status != AlbumBatchStatus.MATCHED }
    val hasStarted = state.rows.any { it.status != AlbumBatchStatus.UNPROCESSED }
    fun hideKeyboard() {
        keyboard?.hide()
        focus.clearFocus()
    }
    // Do not react to row progress here: stable row keys retain the current viewport during updates.
    LaunchedEffect(state.filter, state.query) { listState.scrollToItem(0) }
    Dialog(onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false,
            dismissOnClickOutside = false)) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Column(Modifier.fillMaxSize().systemBarsPadding().imePadding()) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "返回匹配面板")
                    }
                    Text("专辑批量匹配", modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    if (state.running) TextButton(onClick = onPause) { Text("暂停") }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Rounded.Close, contentDescription = "关闭专辑批量匹配")
                    }
                }
                if (state.loading || state.running) LinearProgressIndicator(Modifier.fillMaxWidth())
                LazyColumn(state = listState, modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    item(key = "controls") {
                        Column(Modifier.padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("逐张搜索网易云专辑，再匹配专辑内歌曲。歌曲全部关联或人工确认后进入已完成，后续批量会跳过。",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Button(onClick = {
                                hideKeyboard()
                                if (state.running) onPause() else onStart()
                            }, modifier = Modifier.fillMaxWidth(),
                                enabled = state.running || (!state.loading && hasRemaining)) {
                                Text(when {
                                    state.running -> "暂停，保留已完成结果"
                                    state.rows.isNotEmpty() && !hasRemaining -> "全部专辑已完成"
                                    hasStarted -> "继续匹配未完成专辑"
                                    else -> "开始批量匹配"
                                })
                            }
                            if (state.running || state.total > 0) {
                                Text(buildString {
                                    append("本轮进度 ${state.completed.coerceAtLeast(0)} / ${state.total.coerceAtLeast(0)}")
                                    state.current?.takeIf(String::isNotBlank)?.let { append(" · $it") }
                                }, style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 2, overflow = TextOverflow.Ellipsis)
                            }
                            state.message?.takeIf(String::isNotBlank)?.let {
                                Text(it, style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                    item(key = "counts") {
                        Text("已完成 ${counts[AlbumBatchFilter.MATCHED] ?: 0} · 待核验 ${counts[AlbumBatchFilter.REVIEW] ?: 0}" +
                            " · 未处理 ${counts[AlbumBatchFilter.UNPROCESSED] ?: 0}" +
                            " · 未命中 ${counts[AlbumBatchFilter.NO_MATCH] ?: 0} · 出错 ${counts[AlbumBatchFilter.ERROR] ?: 0}",
                            style = MaterialTheme.typography.labelLarge)
                    }
                    item(key = "filters") {
                        FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp),
                            maxItemsInEachRow = 3) {
                            AlbumBatchFilter.entries.forEach { filter ->
                                FilterChip(selected = state.filter == filter,
                                    onClick = { if (state.filter != filter) onFilter(filter) },
                                    label = { Text("${filter.albumFilterLabel()} ${counts[filter] ?: 0}") })
                            }
                        }
                    }
                    item(key = "query") {
                        OutlinedTextField(value = state.query, onValueChange = onQuery,
                            modifier = Modifier.fillMaxWidth(), singleLine = true,
                            label = { Text("筛选专辑名 / 歌手") },
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                            keyboardActions = KeyboardActions(onSearch = { hideKeyboard() }))
                    }
                    item(key = "list-hint") {
                        Text("显示 ${visibleRows.size} 张 · 点开查看候选和歌曲结果" +
                            if (state.running) "；会先暂停批量任务" else "",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    if (visibleRows.isEmpty()) item(key = "empty") {
                        Column(Modifier.padding(vertical = 24.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text(when {
                                state.loading -> "正在读取本地专辑…"
                                state.rows.isEmpty() -> "还没有本地专辑，请先到本地音乐扫描或导入音乐。"
                                state.query.isNotBlank() -> "没有符合筛选内容的专辑，可修改专辑名或歌手。"
                                else -> "此分类暂无专辑，可切换分类查看。"
                            }, style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                            if (state.query.isNotBlank()) TextButton(onClick = { onQuery("") }) { Text("清空搜索") }
                        }
                    }
                    items(visibleRows, key = { it.local.key }) { row ->
                        AlbumBatchReportRow(row = row, onClick = {
                            hideKeyboard()
                            onOpenRow(row.local.key)
                        })
                        HorizontalDivider(Modifier.padding(top = 10.dp))
                    }
                }
            }
        }
    }
}

private fun AlbumBatchFilter.acceptsAlbum(status: AlbumBatchStatus): Boolean = when (this) {
    AlbumBatchFilter.ALL -> true
    AlbumBatchFilter.MATCHED -> status == AlbumBatchStatus.MATCHED
    AlbumBatchFilter.REVIEW -> status == AlbumBatchStatus.REVIEW
    AlbumBatchFilter.UNPROCESSED -> status == AlbumBatchStatus.UNPROCESSED || status == AlbumBatchStatus.SEARCHING
    AlbumBatchFilter.NO_MATCH -> status == AlbumBatchStatus.NO_MATCH
    AlbumBatchFilter.ERROR -> status == AlbumBatchStatus.ERROR
}

private fun AlbumBatchFilter.albumFilterLabel(): String = when (this) {
    AlbumBatchFilter.ALL -> "全部"
    AlbumBatchFilter.MATCHED -> "已完成"
    AlbumBatchFilter.REVIEW -> "待核验"
    AlbumBatchFilter.UNPROCESSED -> "未处理"
    AlbumBatchFilter.NO_MATCH -> "未命中"
    AlbumBatchFilter.ERROR -> "出错"
}

private fun AlbumBatchStatus.albumStatusLabel(): String = when (this) {
    AlbumBatchStatus.UNPROCESSED -> "未处理"
    AlbumBatchStatus.SEARCHING -> "匹配中"
    AlbumBatchStatus.MATCHED -> "已完成"
    AlbumBatchStatus.REVIEW -> "待核验"
    AlbumBatchStatus.NO_MATCH -> "未命中"
    AlbumBatchStatus.ERROR -> "出错"
}

@Composable
private fun AlbumBatchReportRow(row: AlbumBatchRow, onClick: () -> Unit) {
    Column(Modifier.fillMaxWidth().clickable(onClickLabel = "查看专辑 ${row.local.name} 的匹配结果",
        onClick = onClick).padding(vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            LocalCover(row.local.coverSourceUri, size = 56.dp)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(row.local.name, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(listOfNotNull(row.local.artist, row.local.year?.toString(), "${row.local.tracks.size} 首").joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            if (row.status == AlbumBatchStatus.SEARCHING) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
            else Text(row.status.albumStatusLabel(), style = MaterialTheme.typography.labelMedium,
                color = if (row.status == AlbumBatchStatus.ERROR) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary)
        }
        val displayedOnline = row.selected ?: row.candidates.firstOrNull()
        displayedOnline?.let { album ->
            Row(Modifier.padding(start = 12.dp), horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically) {
                AntiFlickerCoverImage(album.picUrl, Modifier.size(40.dp).clip(MaterialTheme.shapes.small))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text("${if (row.selected != null) "网易云" else "推荐"}：${album.name}",
                        style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    album.artists.joinToString(" / ") { it.name }.takeIf(String::isNotBlank)?.let {
                        Text(it, style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("歌曲 ${row.linkedSongs.coerceAtLeast(0)} / ${row.totalSongs.coerceAtLeast(0)} 已关联",
                style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.weight(1f))
            Text(if (row.candidates.isNotEmpty()) "${row.candidates.size} 个候选 · 查看" else "查看", style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary)
        }
        if (row.reason.isNotBlank()) Text(row.reason, style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

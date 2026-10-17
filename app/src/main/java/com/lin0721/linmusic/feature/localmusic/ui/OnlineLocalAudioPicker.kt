package com.lin0721.linmusic.feature.localmusic.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.ime
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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.lin0721.linmusic.core.model.Track
import com.lin0721.linmusic.core.ui.components.AntiFlickerCoverImage
import com.lin0721.linmusic.feature.localmusic.domain.LocalTrack
import com.lin0721.linmusic.feature.localmusic.ui.components.LocalCover
import com.lin0721.linmusic.feature.player.ui.formatTime

enum class OnlineLocalPickerFilter(val label: String) {
    RECOMMENDED("推荐"), ARTIST("同歌手"), ALBUM("同专辑"), ALL("全部")
}

data class OnlineLocalPickerItem(val track: LocalTrack, val reasons: List<String>)

data class OnlineLocalPickerState(
    val online: Track,
    val items: List<OnlineLocalPickerItem> = emptyList(),
    val counts: Map<OnlineLocalPickerFilter, Int> = emptyMap(),
    val libraryCount: Int = 0,
    val query: String = "",
    val filter: OnlineLocalPickerFilter = OnlineLocalPickerFilter.RECOMMENDED,
    val loading: Boolean = true,
    val savingUri: String? = null,
    val currentUri: String? = null,
    val error: String? = null
)

/** The scanned library is the default; the system file picker remains a secondary escape hatch. */
@Composable
fun OnlineLocalAudioPicker(
    state: OnlineLocalPickerState,
    onDismiss: () -> Unit,
    onQueryChange: (String) -> Unit,
    onFilterChange: (OnlineLocalPickerFilter) -> Unit,
    onSelect: (LocalTrack) -> Unit,
    onChooseFile: () -> Unit
) {
    val saving = state.savingUri != null
    val keyboard = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current
    val imeVisible = WindowInsets.ime.getBottom(LocalDensity.current) > 0
    val listState = rememberLazyListState()
    fun hideKeyboard() {
        keyboard?.hide()
        focusManager.clearFocus()
    }
    fun dismiss() {
        if (!saving) {
            hideKeyboard()
            onDismiss()
        }
    }
    fun chooseFilter(filter: OnlineLocalPickerFilter) {
        if (!saving && filter != state.filter) {
            hideKeyboard()
            onFilterChange(filter)
        }
    }
    // Candidate/loading/cover updates preserve the user's position. Re-tapping a chip does too.
    LaunchedEffect(state.online.id, state.filter, state.query) { listState.scrollToItem(0) }
    Dialog(
        onDismissRequest = { dismiss() },
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false,
            dismissOnBackPress = !saving,
            dismissOnClickOutside = false
        )
    ) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Column(Modifier.fillMaxSize().systemBarsPadding().imePadding()) {
                Row(
                    Modifier.fillMaxWidth().padding(start = 8.dp, end = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = { dismiss() }, enabled = !saving) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "返回")
                    }
                    Text("从本地曲库关联", Modifier.weight(1f),
                        style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                }
                HorizontalDivider()
                Column(
                    Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = if (imeVisible) 4.dp else 12.dp),
                    verticalArrangement = Arrangement.spacedBy(if (imeVisible) 4.dp else 8.dp)
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        if (!imeVisible) AntiFlickerCoverImage(state.online.al.picUrl,
                            Modifier.size(52.dp).clip(MaterialTheme.shapes.small))
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            Text("网易云：${state.online.name}", fontWeight = FontWeight.SemiBold,
                                maxLines = if (imeVisible) 1 else 2, overflow = TextOverflow.Ellipsis,
                                style = MaterialTheme.typography.bodyMedium)
                            if (!imeVisible) {
                                Text(state.online.ar.joinToString(" / ") { it.name },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text("${state.online.al.name.ifBlank { "专辑未知" }} · ${pickerDuration(state.online.dt)}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }
                    OutlinedTextField(
                        value = state.query,
                        onValueChange = { if (!saving && it != state.query) onQueryChange(it) },
                        modifier = Modifier.fillMaxWidth(), singleLine = true, enabled = !saving,
                        label = { Text("搜索歌名、歌手、专辑或文件名") },
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = { hideKeyboard() }),
                        trailingIcon = {
                            if (state.query.isNotEmpty()) IconButton(onClick = { onQueryChange("") }, enabled = !saving) {
                                Icon(Icons.Rounded.Close, contentDescription = "清空搜索")
                            }
                        }
                    )
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), maxItemsInEachRow = 4) {
                        OnlineLocalPickerFilter.entries.forEach { filter ->
                            FilterChip(selected = state.filter == filter, enabled = !saving,
                                onClick = { chooseFilter(filter) },
                                label = { Text("${filter.label} ${state.counts[filter] ?: 0}") })
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        if (saving || state.loading) CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                        Text(
                            when {
                                saving -> "正在保存关联…"
                                state.loading && state.items.isEmpty() -> "正在整理本地曲库…"
                                state.loading -> "正在更新推荐，可先选择已有曲目…"
                                else -> "${state.items.size} 首 · 点选即关联"
                            },
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    state.error?.takeIf(String::isNotBlank)?.let { error ->
                        Text("$error\n可再次点选曲目重试。", style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error)
                    }
                }
                HorizontalDivider()
                LazyColumn(state = listState, modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp)) {
                    if (!state.loading && state.items.isEmpty()) item {
                        Column(Modifier.fillMaxWidth().padding(vertical = 20.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(
                                when {
                                    state.libraryCount == 0 -> "本地曲库还没有歌曲。请先到“本地音乐”扫描或导入，也可以从下方选择音频文件。"
                                    state.query.isNotBlank() -> "此分类没有找到对应曲目，可修改搜索条件。"
                                    state.filter == OnlineLocalPickerFilter.RECOMMENDED -> "暂未找到合适的推荐，可查看同歌手、同专辑或全部曲目。"
                                    else -> "此分类暂时没有曲目。"
                                }, style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            if (state.libraryCount > 0 && state.filter != OnlineLocalPickerFilter.ALL) {
                                TextButton(onClick = { chooseFilter(OnlineLocalPickerFilter.ALL) }, enabled = !saving) {
                                    Text("查看全部本地曲目")
                                }
                            }
                            if (state.query.isNotBlank()) TextButton(onClick = { onQueryChange("") }, enabled = !saving) {
                                Text("清空搜索条件")
                            }
                        }
                    }
                    items(state.items, key = { it.track.uri.toString() }) { item ->
                        OnlineLocalAudioPickerRow(item, state.currentUri, state.savingUri) {
                            hideKeyboard()
                            onSelect(item.track)
                        }
                        HorizontalDivider()
                    }
                }
                HorizontalDivider()
                TextButton(
                    onClick = {
                        if (!saving) {
                            hideKeyboard()
                            onChooseFile()
                        }
                    }, enabled = !saving,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)
                ) { Text("从文件选择未导入的音频") }
            }
        }
    }
}

@Composable
private fun OnlineLocalAudioPickerRow(
    item: OnlineLocalPickerItem,
    currentUri: String?,
    savingUri: String?,
    onSelect: () -> Unit
) {
    val track = item.track
    val uri = track.uri.toString()
    Row(
        Modifier.fillMaxWidth().clickable(enabled = savingUri == null,
            onClickLabel = "关联到本地曲目 ${track.title}，${track.artist}", onClick = onSelect).padding(vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically
    ) {
        LocalCover(track.uri, size = 52.dp)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(track.title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold,
                maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(track.artist.ifBlank { "歌手未知" }, style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text("${track.album?.takeIf(String::isNotBlank) ?: "专辑未知"} · ${pickerDuration(track.durationMs)}",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2, overflow = TextOverflow.Ellipsis)
            item.reasons.takeIf { it.isNotEmpty() }?.let {
                Text(it.distinct().joinToString(" · "), style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            track.path?.replace('\\', '/')?.split('/')?.filter(String::isNotBlank)?.takeLast(2)?.joinToString("/")
                ?.takeIf(String::isNotBlank)?.let { path ->
                    Text(path, style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
        }
        Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(4.dp)) {
            if (uri == currentUri) Text("当前关联", style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary)
            if (uri == savingUri) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
            else Text("选用", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
        }
    }
}

private fun pickerDuration(durationMs: Long): String = if (durationMs > 0L) formatTime(durationMs) else "时长未知"

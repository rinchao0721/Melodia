package com.lin0721.linmusic.feature.localmusic.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
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
import com.lin0721.linmusic.core.model.Album
import com.lin0721.linmusic.core.model.Track
import com.lin0721.linmusic.core.ui.components.AntiFlickerCoverImage
import com.lin0721.linmusic.feature.localmusic.domain.LocalAlbum
import com.lin0721.linmusic.feature.localmusic.domain.LocalTrack
import com.lin0721.linmusic.feature.localmusic.ui.components.LocalCover
import com.lin0721.linmusic.feature.player.ui.formatTime
import java.time.Instant
import java.time.ZoneOffset

enum class AlbumSongMatchStatus(val label: String) {
    READY("可自动"), REVIEW("待核验"), NO_MATCH("未命中"), EXISTING("已有关联"), SAVED("已关联"), ERROR("出错")
}

data class AlbumSongMatchRow(
    val local: LocalTrack,
    val status: AlbumSongMatchStatus,
    val online: Track? = null,
    val candidates: List<Track> = emptyList(),
    val reason: String = ""
)

data class LocalAlbumAssociationState(
    val local: LocalAlbum,
    val query: String = "",
    val albums: List<Album> = emptyList(),
    val selected: Album? = null,
    val onlineTracks: List<Track> = emptyList(),
    val rows: List<AlbumSongMatchRow> = emptyList(),
    val bindingSaved: Boolean = false,
    val reviewCompleted: Boolean = false,
    val loading: Boolean = false,
    val running: Boolean = false,
    val savingUri: String? = null,
    val error: String? = null,
    val message: String? = null
)

/** Album selection and per-song review share one fixed window and one scrolling list. */
@Composable
fun LocalAlbumAssociationDialog(
    state: LocalAlbumAssociationState,
    onDismiss: () -> Unit,
    onQueryChange: (String) -> Unit,
    onSearch: () -> Unit,
    onSelectAlbum: (Album) -> Unit,
    onChangeAlbum: () -> Unit,
    onRefresh: () -> Unit,
    onApply: () -> Unit,
    onStop: () -> Unit,
    onLink: (LocalTrack, Track) -> Unit,
    onForget: () -> Unit
) {
    val busy = state.running || state.savingUri != null
    val listState = rememberLazyListState()
    var pendingOnly by rememberSaveable(state.local.key) { mutableStateOf(false) }
    var expandedUri by rememberSaveable(state.local.key, state.selected?.id) { mutableStateOf<String?>(null) }
    var allOnlineForUri by rememberSaveable(state.local.key, state.selected?.id) { mutableStateOf<String?>(null) }
    val keyboard = LocalSoftwareKeyboardController.current
    val focus = LocalFocusManager.current
    val imeVisible = WindowInsets.ime.getBottom(LocalDensity.current) > 0
    val readyCount = state.rows.count { it.status == AlbumSongMatchStatus.READY }
    val pendingCount = state.rows.count { !it.status.isAlbumLinked() }
    val linkedCount = state.rows.size - pendingCount
    val visibleRows = remember(state.rows, pendingOnly) {
        if (pendingOnly) state.rows.filterNot { it.status.isAlbumLinked() } else state.rows
    }
    fun hideKeyboard() {
        keyboard?.hide()
        focus.clearFocus()
    }
    fun search() {
        if (!busy && !state.loading && state.query.isNotBlank()) {
            hideKeyboard()
            onSearch()
        }
    }
    LaunchedEffect(state.selected?.id) { listState.scrollToItem(0) }
    Dialog(
        onDismissRequest = { if (!busy) onDismiss() },
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false,
            dismissOnBackPress = !busy,
            dismissOnClickOutside = false
        )
    ) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Column(Modifier.fillMaxSize().systemBarsPadding().imePadding()) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = {
                        hideKeyboard()
                        if (state.selected == null) onDismiss() else onChangeAlbum()
                    }, enabled = !busy) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = if (state.selected == null) "返回本地专辑" else "重新选择在线专辑")
                    }
                    Text(if (state.selected == null) "专辑对应" else "专辑内歌曲匹配",
                        modifier = Modifier.weight(1f), style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold)
                    if (state.running) TextButton(onClick = onStop) { Text("暂停") }
                    else IconButton(onClick = onDismiss, enabled = !busy) {
                        Icon(Icons.Rounded.Close, contentDescription = "关闭专辑匹配")
                    }
                }
                if (state.loading || state.running) LinearProgressIndicator(Modifier.fillMaxWidth())
                LazyColumn(
                    state = listState,
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    item(key = "local-album") {
                        Row(Modifier.fillMaxWidth().padding(top = if (imeVisible) 2.dp else 10.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically) {
                            if (!imeVisible) LocalCover(state.local.coverSourceUri, size = 60.dp)
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                Text("本地：${state.local.name}", fontWeight = FontWeight.SemiBold,
                                    maxLines = if (imeVisible) 1 else 2, overflow = TextOverflow.Ellipsis)
                                if (!imeVisible) Text(
                                    listOfNotNull(state.local.artist, state.local.year?.toString(), "${state.local.tracks.size} 首").joinToString(" · "),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }
                    if (state.selected == null) {
                        item(key = "search") {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                OutlinedTextField(value = state.query, onValueChange = onQueryChange,
                                    label = { Text("专辑名 / 歌手") }, singleLine = true,
                                    enabled = !busy && !state.loading,
                                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                                    keyboardActions = KeyboardActions(onSearch = { search() }),
                                    modifier = Modifier.weight(1f))
                                Spacer(Modifier.width(8.dp))
                                Button(onClick = { search() }, enabled = !busy && !state.loading && state.query.isNotBlank()) {
                                    Text("搜索")
                                }
                            }
                        }
                        item(key = "search-help") {
                            Text("先选择同一张网易云专辑，再集中匹配专辑里的歌曲。",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    } else {
                        item(key = "online-album") {
                            AlbumCandidate(state.selected, enabled = false, compact = imeVisible,
                                prefix = "网易云：", onClick = {})
                        }
                        item(key = "album-controls") {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text(when {
                                        state.reviewCompleted -> "专辑已人工核验 · 已完成"
                                        state.bindingSaved -> "已保存专辑对应 · 尚未人工核验"
                                        else -> "确认后保存专辑对应"
                                    },
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    TextButton(onClick = {
                                        hideKeyboard()
                                        onChangeAlbum()
                                    }, enabled = !busy && !state.loading) { Text("更换专辑") }
                                }
                                Text("仅在已选专辑内匹配；歌手署名和发行版本可不同，录音版本差异仍需核对。",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text("确认专辑后移入已完成并跳过后续批量；未关联歌曲仍可单独处理。",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text("可自动 $readyCount · 待处理 ${pendingCount - readyCount} · 已关联 $linkedCount",
                                    style = MaterialTheme.typography.labelLarge)
                                if (state.running) {
                                    Button(onClick = onStop, modifier = Modifier.fillMaxWidth()) {
                                        Text("完成当前歌曲后暂停")
                                    }
                                } else {
                                    Button(onClick = {
                                        hideKeyboard()
                                        onApply()
                                    }, modifier = Modifier.fillMaxWidth(),
                                        enabled = !busy && !state.loading && state.onlineTracks.isNotEmpty() &&
                                            (!state.reviewCompleted || readyCount > 0)) {
                                        Text(when {
                                            readyCount > 0 && state.reviewCompleted -> "匹配剩余 $readyCount 首"
                                            readyCount > 0 -> "确认专辑并匹配 $readyCount 首"
                                            state.reviewCompleted -> "专辑核验已完成"
                                            else -> "确认专辑并完成核验"
                                        })
                                    }
                                }
                                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    FilterChip(selected = !pendingOnly, onClick = { pendingOnly = false },
                                        enabled = !busy, label = { Text("全部 ${state.rows.size}") })
                                    FilterChip(selected = pendingOnly, onClick = { pendingOnly = true },
                                        enabled = !busy, label = { Text("待处理 $pendingCount") })
                                    Spacer(Modifier.weight(1f))
                                    TextButton(onClick = onRefresh, enabled = !busy && !state.loading) { Text("重新检查") }
                                }
                            }
                        }
                    }
                    state.error?.let { error ->
                        item(key = "error") {
                            Text(error, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                    state.message?.let { message ->
                        item(key = "message") {
                            Text(message, color = MaterialTheme.colorScheme.onSurfaceVariant,
                                style = MaterialTheme.typography.bodySmall)
                        }
                    }
                    if (state.selected == null) {
                        items(state.albums, key = { "album-${it.id}" }) { album ->
                            AlbumCandidate(album, enabled = !busy && !state.loading, onClick = {
                                hideKeyboard()
                                onSelectAlbum(album)
                            })
                            HorizontalDivider(Modifier.padding(top = 10.dp))
                        }
                    } else {
                        if (visibleRows.isEmpty() && !state.loading) item(key = "empty-rows") {
                            Text(if (pendingOnly && state.rows.isNotEmpty()) "这张专辑的歌曲均已有关联。" else "暂无可匹配曲目。",
                                style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(vertical = 16.dp))
                        }
                        visibleRows.forEach { row ->
                            val uri = row.local.uri.toString()
                            val canChoose = !row.status.isAlbumLinked()
                            item(key = "local-$uri") {
                                AlbumSongRow(row = row, expanded = expandedUri == uri, enabled = !busy && canChoose,
                                    saving = state.savingUri == uri,
                                    onExpand = {
                                        hideKeyboard()
                                        expandedUri = if (expandedUri == uri) null else uri
                                        allOnlineForUri = null
                                    })
                            }
                            if (expandedUri == uri && canChoose) {
                                val allOnline = allOnlineForUri == uri
                                val candidates = if (allOnline) state.onlineTracks else
                                    (listOfNotNull(row.online) + row.candidates).distinctBy { it.id }
                                item(key = "candidate-controls-$uri") {
                                    Column(Modifier.padding(start = 10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                        Text(if (allOnline) "该专辑全部曲目 · 点选即关联" else "专辑内候选 · 点选即关联",
                                            style = MaterialTheme.typography.labelMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        TextButton(onClick = { allOnlineForUri = if (allOnline) null else uri },
                                            enabled = !busy) {
                                            Text(if (allOnline) "只看推荐候选" else "查看该专辑全部 ${state.onlineTracks.size} 首曲目")
                                        }
                                        if (candidates.isEmpty()) Text("未找到相近歌名，可从该专辑全部曲目中手动选择。",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                                itemsIndexed(candidates, key = { _, track -> "candidate-$uri-${track.id}" }) { index, candidate ->
                                    AlbumTrackCandidate(track = candidate,
                                        index = if (allOnline) index + 1 else null, enabled = !busy,
                                        onClick = {
                                            hideKeyboard()
                                            onLink(row.local, candidate)
                                        })
                                }
                            }
                            item(key = "divider-$uri") { HorizontalDivider() }
                        }
                        if (state.bindingSaved) item(key = "forget") {
                            Column {
                                TextButton(onClick = onForget, enabled = !busy && !state.loading) {
                                    Text("解除专辑对应")
                                }
                                Text("仅解除这两张专辑的对应，已建立的歌曲关联会保留。",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun AlbumSongMatchStatus.isAlbumLinked(): Boolean =
    this == AlbumSongMatchStatus.EXISTING || this == AlbumSongMatchStatus.SAVED

@Composable
private fun AlbumCandidate(
    album: Album,
    enabled: Boolean,
    onClick: () -> Unit,
    compact: Boolean = false,
    prefix: String = ""
) {
    val year = remember(album.publishTime) {
        album.publishTime.takeIf { it > 0 }?.let { Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).year }
    }
    Row(Modifier.fillMaxWidth().clickable(enabled = enabled,
        onClickLabel = "选择专辑 ${album.name}", onClick = onClick).padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
        if (!compact) AntiFlickerCoverImage(album.picUrl, Modifier.size(60.dp).clip(MaterialTheme.shapes.small))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("$prefix${album.name}", fontWeight = FontWeight.SemiBold,
                maxLines = if (compact) 1 else 2, overflow = TextOverflow.Ellipsis)
            if (!compact) Text(listOfNotNull(album.artists.joinToString(" / ") { it.name }.takeIf(String::isNotBlank),
                year?.toString()).joinToString(" · ").ifBlank { "歌手 / 年份未知" },
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        if (enabled) Text("选择", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
    }
}

@Composable
private fun AlbumSongRow(
    row: AlbumSongMatchRow,
    expanded: Boolean,
    enabled: Boolean,
    saving: Boolean,
    onExpand: () -> Unit
) {
    Column(Modifier.fillMaxWidth().clickable(enabled = enabled,
        onClickLabel = "查看 ${row.local.title} 的专辑内候选", onClick = onExpand).padding(vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            LocalCover(row.local.uri, size = 48.dp)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(row.local.title, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(listOfNotNull(row.local.trackNumber?.let { "第 $it 首" }, formatTime(row.local.durationMs),
                    row.local.artist.takeIf(String::isNotBlank)).joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            if (saving) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
            else Text(row.status.label, style = MaterialTheme.typography.labelMedium,
                color = if (row.status == AlbumSongMatchStatus.ERROR) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary)
        }
        row.online?.let { online ->
            Row(Modifier.padding(start = 12.dp), horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically) {
                AntiFlickerCoverImage(online.al.picUrl, Modifier.size(38.dp).clip(MaterialTheme.shapes.small))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text("→ ${online.name}", style = MaterialTheme.typography.bodyMedium,
                        maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text(listOf(online.ar.joinToString(" / ") { it.name }, formatTime(online.dt)).filter(String::isNotBlank).joinToString(" · "),
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
            }
        }
        if (row.reason.isNotBlank()) Text(row.reason,
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (!row.status.isAlbumLinked()) Text(if (expanded) "收起候选" else "点此查看候选 / 手动选择",
            style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
    }
}

@Composable
private fun AlbumTrackCandidate(track: Track, index: Int?, enabled: Boolean, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(enabled = enabled,
        onClickLabel = "将本地曲目关联到 ${track.name}", onClick = onClick).padding(start = 12.dp, top = 10.dp, bottom = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
        AntiFlickerCoverImage(track.al.picUrl, Modifier.size(42.dp).clip(MaterialTheme.shapes.small))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(if (index == null) track.name else "$index. ${track.name}",
                style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(listOf(track.ar.joinToString(" / ") { it.name }, formatTime(track.dt)).filter(String::isNotBlank).joinToString(" · "),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        Text("选用", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
    }
}

package com.lin0721.linmusic.feature.localmusic.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
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
import com.lin0721.linmusic.core.model.Track
import com.lin0721.linmusic.core.ui.components.AntiFlickerCoverImage
import com.lin0721.linmusic.core.ui.components.MelodiaDragHandle
import com.lin0721.linmusic.core.ui.theme.BottomSheetShape
import com.lin0721.linmusic.feature.localmusic.ui.components.LocalCover
import com.lin0721.linmusic.feature.player.ui.formatTime

/** Individual song menus retain their sheet; batch review embeds the same content in one fixed page. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LocalOnlineAssociationSheet(
    state: LocalOnlineAssociationState,
    onDismiss: () -> Unit,
    onQueryChange: (String) -> Unit,
    onSearch: () -> Unit,
    onLoadMore: () -> Unit,
    onConfirm: (Track) -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = { if (!state.saving) onDismiss() },
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.background,
        shape = BottomSheetShape,
        dragHandle = { MelodiaDragHandle() }
    ) {
        Column(Modifier.fillMaxWidth().fillMaxHeight(0.88f).navigationBarsPadding()) {
            Text(if (state.track.songId == null) "关联网易云歌曲" else "更换网易云关联",
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            LocalOnlineAssociationContent(state, onQueryChange, onSearch, onLoadMore, onConfirm,
                modifier = Modifier.weight(1f))
        }
    }
}

/** Cached candidates are clickable immediately. Only this candidate list scrolls; no sheet drags compete with it. */
@Composable
internal fun LocalOnlineAssociationContent(
    state: LocalOnlineAssociationState,
    onQueryChange: (String) -> Unit,
    onSearch: () -> Unit,
    onLoadMore: () -> Unit,
    onConfirm: (Track) -> Unit,
    modifier: Modifier = Modifier
) {
    var searchExpanded by rememberSaveable(state.track.uri.toString()) { mutableStateOf(state.results.isEmpty()) }
    val listState = rememberLazyListState()
    val keyboard = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current
    val imeVisible = WindowInsets.ime.getBottom(LocalDensity.current) > 0
    fun hideKeyboard() {
        keyboard?.hide()
        focusManager.clearFocus()
    }
    fun submitSearch() {
        hideKeyboard()
        onSearch()
    }
    LaunchedEffect(state.track.uri, state.searchedQuery) { listState.scrollToItem(0) }
    Column(modifier.fillMaxWidth().imePadding().padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(Modifier.padding(top = if (imeVisible) 4.dp else 12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically) {
            if (!imeVisible) LocalCover(state.track.uri, size = 56.dp)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text("本地：${state.track.title}", fontWeight = FontWeight.SemiBold,
                    style = MaterialTheme.typography.bodyMedium, maxLines = if (imeVisible) 1 else 2, overflow = TextOverflow.Ellipsis)
                if (!imeVisible) {
                    Text(state.track.artist, style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text("${state.track.album?.takeIf(String::isNotBlank) ?: "专辑未知"} · ${formatTime(state.track.durationMs)}",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically) {
            Text(if (state.saving) "正在保存关联…" else "在线候选 ${state.results.size} · 点选即关联",
                style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            TextButton(onClick = {
                if (searchExpanded) hideKeyboard()
                searchExpanded = !searchExpanded
            }, enabled = !state.saving) {
                Text(if (searchExpanded) "收起搜索" else "修改搜索")
            }
        }
        if (searchExpanded) Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(value = state.query, onValueChange = onQueryChange,
                label = { Text("歌名 / 歌手") }, singleLine = true, enabled = !state.saving,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { submitSearch() }),
                modifier = Modifier.weight(1f))
            Spacer(Modifier.width(8.dp))
            Button(onClick = { submitSearch() }, enabled = state.query.isNotBlank() && !state.saving) { Text("搜索") }
        }
        state.error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
        if (state.searching || state.saving) Row(horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically) {
            CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
            Text(if (state.saving) "正在建立关联…" else "正在搜索，可先选择已有候选…",
                style = MaterialTheme.typography.bodySmall)
        }
        LazyColumn(state = listState, modifier = Modifier.weight(1f), contentPadding = PaddingValues(bottom = 16.dp)) {
            if (!state.searching && state.searchedQuery.isNotBlank() && state.results.isEmpty() && state.error == null) item {
                Text("未找到歌曲，请修改歌名或歌手后搜索，也可以稍后处理。",
                    modifier = Modifier.padding(vertical = 20.dp), style = MaterialTheme.typography.bodyMedium)
            }
            items(state.results, key = { it.id }) { track ->
                Row(Modifier.fillMaxWidth().clickable(enabled = !state.saving,
                    onClickLabel = "关联到 ${track.name}，${track.ar.joinToString(" / ") { it.name }}") {
                    hideKeyboard()
                    onConfirm(track)
                }.padding(vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    AntiFlickerCoverImage(track.al.picUrl, Modifier.size(48.dp).clip(MaterialTheme.shapes.small))
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(track.name, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        Text(track.ar.joinToString(" / ") { it.name }, color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text("${track.al.name.ifBlank { "专辑未知" }} · ${track.dt.takeIf { it > 0L }?.let(::formatTime) ?: "时长未知"}",
                            color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall,
                            maxLines = 2, overflow = TextOverflow.Ellipsis)
                    }
                    Text("选用", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                }
                HorizontalDivider()
            }
            if (state.hasMore) item {
                TextButton(onClick = onLoadMore, enabled = !state.searching && !state.saving,
                    modifier = Modifier.fillMaxWidth()) { Text("加载更多候选") }
            }
        }
    }
}

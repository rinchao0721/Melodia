package com.lin0721.linmusic.feature.localmusic.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material3.Button
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
import com.lin0721.linmusic.core.ui.components.AntiFlickerCoverImage
import com.lin0721.linmusic.feature.localmusic.domain.LocalAlbum
import com.lin0721.linmusic.feature.localmusic.ui.components.LocalCover

data class OnlineAlbumLocalPickerItem(
    val album: LocalAlbum,
    val reasons: List<String>
)

data class OnlineAlbumLocalPickerState(
    val online: Album,
    val items: List<OnlineAlbumLocalPickerItem> = emptyList(),
    val query: String = "",
    val showAll: Boolean = false,
    val loading: Boolean = true,
    val error: String? = null
)

/** Choosing a local album enters the same preview used by the local-album entry point. */
@Composable
fun OnlineAlbumLocalPickerDialog(
    state: OnlineAlbumLocalPickerState,
    onDismiss: () -> Unit,
    onQueryChange: (String) -> Unit,
    onShowAllChange: (Boolean) -> Unit,
    onSelect: (LocalAlbum) -> Unit
) {
    val listState = rememberLazyListState()
    val keyboard = LocalSoftwareKeyboardController.current
    val focus = LocalFocusManager.current
    val imeVisible = WindowInsets.ime.getBottom(LocalDensity.current) > 0
    fun hideKeyboard() {
        keyboard?.hide()
        focus.clearFocus()
    }
    Dialog(onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false,
            dismissOnClickOutside = false)) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Column(Modifier.fillMaxSize().systemBarsPadding().imePadding()) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "返回网易云专辑")
                    }
                    Text("选择对应的本地专辑", modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Rounded.Close, contentDescription = "关闭专辑选择")
                    }
                }
                if (state.loading) LinearProgressIndicator(Modifier.fillMaxWidth())
                LazyColumn(state = listState, modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    item(key = "online") {
                        Row(Modifier.fillMaxWidth().padding(top = if (imeVisible) 2.dp else 10.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically) {
                            if (!imeVisible) AntiFlickerCoverImage(state.online.picUrl,
                                Modifier.size(60.dp).clip(MaterialTheme.shapes.small))
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text("网易云：${state.online.name}", fontWeight = FontWeight.SemiBold,
                                    maxLines = if (imeVisible) 1 else 2, overflow = TextOverflow.Ellipsis)
                                if (!imeVisible) Text(state.online.artists.joinToString(" / ") { it.name }.ifBlank { "歌手未知" },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }
                    item(key = "query") {
                        OutlinedTextField(value = state.query, onValueChange = onQueryChange,
                            modifier = Modifier.fillMaxWidth(), singleLine = true,
                            label = { Text("搜索本地专辑名 / 歌手") },
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                            keyboardActions = KeyboardActions(onSearch = { hideKeyboard() }))
                    }
                    item(key = "filters") {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            FilterChip(selected = !state.showAll, onClick = { onShowAllChange(false) },
                                label = { Text("推荐") })
                            FilterChip(selected = state.showAll, onClick = { onShowAllChange(true) },
                                label = { Text("全部本地专辑") })
                        }
                    }
                    item(key = "instructions") {
                        Text("选择后可预览歌曲匹配结果，再确认专辑对应。",
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    state.error?.let { error ->
                        item(key = "error") { Text(error, color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall) }
                    }
                    if (state.items.isEmpty()) item(key = "empty") {
                        Column(Modifier.padding(vertical = 20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text(when {
                                state.loading -> "正在读取本地专辑…"
                                state.query.isNotBlank() -> "没有符合搜索内容的本地专辑，可修改专辑名或歌手。"
                                state.showAll -> "本地曲库暂无专辑，请先到本地音乐扫描或导入音乐。"
                                else -> "暂未找到推荐专辑，可查看全部本地专辑，或修改搜索内容。"
                            }, style = MaterialTheme.typography.bodyMedium)
                            if (!state.showAll && !state.loading) Button(onClick = { onShowAllChange(true) }) {
                                Text("查看全部本地专辑")
                            }
                            if (state.query.isNotBlank()) TextButton(onClick = { onQueryChange("") }) { Text("清空搜索") }
                        }
                    }
                    items(state.items, key = { "local-${it.album.key}" }) { item ->
                        Row(Modifier.fillMaxWidth().clickable(enabled = !state.loading,
                            onClickLabel = "选择本地专辑 ${item.album.name}", onClick = {
                                hideKeyboard()
                                onSelect(item.album)
                            }).padding(vertical = 10.dp), horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically) {
                            LocalCover(item.album.coverSourceUri, size = 60.dp)
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(item.album.name, fontWeight = FontWeight.SemiBold,
                                    maxLines = 2, overflow = TextOverflow.Ellipsis)
                                Text("${item.album.artist} · ${item.album.tracks.size} 首",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                if (item.reasons.isNotEmpty()) Text(item.reasons.joinToString(" · "),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.primary, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            }
                            Text("选择", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                        }
                        HorizontalDivider()
                    }
                }
            }
        }
    }
}

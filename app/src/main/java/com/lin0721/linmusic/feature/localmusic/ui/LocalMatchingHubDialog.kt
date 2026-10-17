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

data class LocalMatchingHubState(
    val albums: List<LocalAlbum> = emptyList(),
    val query: String = "",
    val onlineMode: Boolean = false,
    val onlineAlbums: List<Album> = emptyList(),
    val loading: Boolean = false,
    val error: String? = null
)

/** Settings owns this entry point; choosing a side opens the existing album preview flow. */
@Composable
fun LocalMatchingHubDialog(
    state: LocalMatchingHubState,
    onDismiss: () -> Unit,
    onQueryChange: (String) -> Unit,
    onOnlineModeChange: (Boolean) -> Unit,
    onSearch: () -> Unit,
    onChooseLocal: (LocalAlbum) -> Unit,
    onChooseOnline: (Album) -> Unit,
    onSongs: () -> Unit,
    onAlbums: () -> Unit
) {
    val listState = rememberLazyListState()
    val keyboard = LocalSoftwareKeyboardController.current
    val focus = LocalFocusManager.current
    val imeVisible = WindowInsets.ime.getBottom(LocalDensity.current) > 0
    fun hideKeyboard() {
        keyboard?.hide()
        focus.clearFocus()
    }
    fun search() {
        hideKeyboard()
        if (state.onlineMode && state.query.isNotBlank() && !state.loading) onSearch()
    }
    Dialog(onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false,
            dismissOnClickOutside = false)) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Column(Modifier.fillMaxSize().systemBarsPadding().imePadding()) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "返回设置")
                    }
                    Text("本地与网易云匹配", modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Rounded.Close, contentDescription = "关闭匹配面板")
                    }
                }
                if (state.loading) LinearProgressIndicator(Modifier.fillMaxWidth())
                LazyColumn(state = listState, modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    item(key = "songs") {
                        Button(onClick = {
                            hideKeyboard()
                            onSongs()
                        }, modifier = Modifier.fillMaxWidth().padding(top = if (imeVisible) 2.dp else 10.dp)) {
                            Text("歌曲批量匹配与待处理")
                        }
                    }
                    item(key = "album-heading") {
                        Column(Modifier.padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text("专辑匹配", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                            Button(onClick = { hideKeyboard(); onAlbums() }, modifier = Modifier.fillMaxWidth()) {
                                Text("专辑批量匹配与核验")
                            }
                            Text("可批量查找并跳过已匹配；也可在下方从任一边手动对应专辑。",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    item(key = "mode") {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            FilterChip(selected = !state.onlineMode, onClick = {
                                hideKeyboard()
                                onOnlineModeChange(false)
                            }, label = { Text("本地专辑") })
                            FilterChip(selected = state.onlineMode, onClick = {
                                hideKeyboard()
                                onOnlineModeChange(true)
                            }, label = { Text("网易云专辑") })
                        }
                    }
                    item(key = "query") {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            OutlinedTextField(value = state.query, onValueChange = onQueryChange,
                                modifier = Modifier.weight(1f), singleLine = true,
                                label = { Text(if (state.onlineMode) "搜索网易云专辑 / 歌手" else "筛选本地专辑 / 歌手") },
                                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                                keyboardActions = KeyboardActions(onSearch = { search() }))
                            if (state.onlineMode) {
                                Spacer(Modifier.width(8.dp))
                                Button(onClick = { search() }, enabled = state.query.isNotBlank() && !state.loading) {
                                    Text("搜索")
                                }
                            }
                        }
                    }
                    state.error?.let { error ->
                        item(key = "error") {
                            Text(error, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                    if (state.onlineMode) {
                        if (state.onlineAlbums.isEmpty()) item(key = "empty-online") {
                            Text(if (state.loading) "正在搜索网易云专辑…" else "输入专辑名或歌手搜索，再选择对应的本地专辑。",
                                modifier = Modifier.padding(vertical = 20.dp),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        items(state.onlineAlbums, key = { "online-${it.id}" }) { album ->
                            Row(Modifier.fillMaxWidth().clickable(onClickLabel = "选择网易云专辑 ${album.name}") {
                                hideKeyboard()
                                onChooseOnline(album)
                            }.padding(vertical = 10.dp), horizontalArrangement = Arrangement.spacedBy(12.dp),
                                verticalAlignment = Alignment.CenterVertically) {
                                AntiFlickerCoverImage(album.picUrl, Modifier.size(60.dp).clip(MaterialTheme.shapes.small))
                                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Text(album.name, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                    Text(album.artists.joinToString(" / ") { it.name }.ifBlank { "歌手未知" },
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 2, overflow = TextOverflow.Ellipsis)
                                }
                                Text("选择", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                            }
                            HorizontalDivider()
                        }
                    } else {
                        if (state.albums.isEmpty()) item(key = "empty-local") {
                            Text(when {
                                state.loading -> "正在读取本地专辑…"
                                state.query.isNotBlank() -> "没有符合筛选内容的本地专辑，可修改专辑名或歌手。"
                                else -> "本地曲库暂无专辑，请先到本地音乐扫描或导入音乐。"
                            }, modifier = Modifier.padding(vertical = 20.dp),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        items(state.albums, key = { "local-${it.key}" }) { album ->
                            Row(Modifier.fillMaxWidth().clickable(onClickLabel = "选择本地专辑 ${album.name}") {
                                hideKeyboard()
                                onChooseLocal(album)
                            }.padding(vertical = 10.dp), horizontalArrangement = Arrangement.spacedBy(12.dp),
                                verticalAlignment = Alignment.CenterVertically) {
                                LocalCover(album.coverSourceUri, size = 60.dp)
                                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Text(album.name, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                    Text("${album.artist} · ${album.tracks.size} 首", style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 2, overflow = TextOverflow.Ellipsis)
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
}

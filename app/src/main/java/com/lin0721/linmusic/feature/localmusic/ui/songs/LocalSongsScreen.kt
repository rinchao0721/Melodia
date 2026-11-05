package com.lin0721.linmusic.feature.localmusic.ui.songs

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.PlaylistAdd
import androidx.compose.material.icons.rounded.Checklist
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.SwapVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lin0721.linmusic.LocalBottomOverlayInset
import com.lin0721.linmusic.core.ui.components.EmptyState
import com.lin0721.linmusic.core.ui.components.MelodiaIconButton
import com.lin0721.linmusic.core.ui.components.SecondaryScreenScaffold
import com.lin0721.linmusic.core.ui.interaction.pressable
import com.lin0721.linmusic.core.ui.theme.MelodiaColors
import com.lin0721.linmusic.core.ui.theme.MelodiaPress
import com.lin0721.linmusic.core.ui.theme.MelodiaSpacing
import com.lin0721.linmusic.core.ui.theme.PillRadius
import com.lin0721.linmusic.feature.localmusic.ui.LocalLibraryStateGate
import com.lin0721.linmusic.feature.localmusic.ui.LocalMusicNavigation
import com.lin0721.linmusic.feature.localmusic.ui.LocalMusicSortOrder
import com.lin0721.linmusic.feature.localmusic.ui.LocalMusicUiState
import com.lin0721.linmusic.feature.localmusic.ui.LocalMusicViewModel
import com.lin0721.linmusic.feature.localmusic.ui.LocalTrackActionsHost
import com.lin0721.linmusic.feature.localmusic.ui.components.LocalTrackRow
import org.koin.androidx.compose.koinViewModel

private val DividerColor: Color @Composable @ReadOnlyComposable get() = MelodiaColors.Divider

@Composable
fun LocalSongsScreen(
    navigation: LocalMusicNavigation,
    viewModel: LocalMusicViewModel = koinViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val playingMediaId by viewModel.playingMediaId.collectAsStateWithLifecycle()
    val isPlaying by viewModel.isPlaying.collectAsStateWithLifecycle()
    var confirmDeleteSelected by remember { mutableStateOf(false) }
    val success = uiState as? LocalMusicUiState.Success

    // 多选/搜索状态在 ViewModel 里跨页共享，返回前先退出
    BackHandler(enabled = success?.isSelectionMode == true || success?.isSearchActive == true) {
        if (success?.isSelectionMode == true) viewModel.toggleSelectionMode() else viewModel.toggleSearch()
    }

    SecondaryScreenScaffold(
        title = "全部歌曲",
        onBack = navigation.onBack,
        actions = {
            if (success == null || success.tracks.isEmpty()) return@SecondaryScreenScaffold
            if (success.isSelectionMode) {
                if (success.selectedUris.isNotEmpty()) {
                    MelodiaIconButton(onClick = {
                        viewModel.openPlaylistPicker(success.filteredTracks.filter { it.uri.toString() in success.selectedUris })
                    }) {
                        Icon(Icons.AutoMirrored.Rounded.PlaylistAdd, contentDescription = "加入本地歌单", tint = MelodiaColors.TextPrimary)
                    }
                }
                MelodiaIconButton(onClick = {
                    if (success.selectedUris.isEmpty()) viewModel.toggleSelectionMode() else confirmDeleteSelected = true
                }) {
                    if (success.selectedUris.isEmpty()) {
                        Icon(Icons.Rounded.Close, contentDescription = "退出多选", tint = MelodiaColors.TextPrimary)
                    } else {
                        Icon(Icons.Rounded.Delete, contentDescription = "删除选中", tint = MaterialTheme.colorScheme.error)
                    }
                }
            } else {
                MelodiaIconButton(onClick = { viewModel.toggleSearch() }) {
                    Icon(
                        if (success.isSearchActive) Icons.Rounded.Close else Icons.Rounded.Search,
                        contentDescription = if (success.isSearchActive) "关闭搜索" else "搜索",
                        tint = MelodiaColors.TextPrimary
                    )
                }
                MelodiaIconButton(onClick = { viewModel.toggleSelectionMode() }) {
                    Icon(Icons.Rounded.Checklist, contentDescription = "多选", tint = MelodiaColors.TextPrimary)
                }
            }
        }
    ) {
        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            LocalLibraryStateGate(viewModel) { state ->
                val list = state.filteredTracks
                Column(modifier = Modifier.fillMaxSize()) {
                    if (state.isSearchActive) {
                        LocalSearchField(query = state.searchQuery, onQueryChange = viewModel::updateSearchQuery)
                    }
                    if (list.isEmpty()) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            EmptyState(
                                icon = Icons.Rounded.LibraryMusic,
                                title = if (state.tracks.isEmpty()) "还没有本地音频文件" else "没有匹配的结果"
                            )
                        }
                        return@Column
                    }
                    LazyColumn(contentPadding = PaddingValues(bottom = LocalBottomOverlayInset.current + 16.dp)) {
                        item(key = "header") {
                            PlayAllRow(
                                count = list.size,
                                sortOrder = state.sortOrder,
                                onPlayAll = { viewModel.playTracks(list) },
                                onShuffle = { viewModel.playTracks(list, shuffle = true) },
                                onSortSelected = viewModel::setSortOrder
                            )
                            HorizontalDivider(color = DividerColor)
                        }
                        items(list, key = { it.uri.toString() }) { track ->
                            LocalTrackRow(
                                track = track,
                                subtitle = track.artist,
                                playingMediaId = playingMediaId,
                                isPlaying = isPlaying,
                                selectionMode = state.isSelectionMode,
                                selected = track.uri.toString() in state.selectedUris,
                                onClick = {
                                    if (state.isSelectionMode) viewModel.toggleSelected(track)
                                    else viewModel.playTracks(list, start = track)
                                },
                                onMoreClick = { coverUrl -> viewModel.openTrackMenu(track, list, coverUrl) }
                            )
                        }
                    }
                }
            }
        }
    }

    LocalTrackActionsHost(viewModel = viewModel, navigation = navigation)

    if (confirmDeleteSelected) {
        val count = success?.selectedUris?.size ?: 0
        AlertDialog(
            onDismissRequest = { confirmDeleteSelected = false },
            title = { Text("批量处理", fontWeight = FontWeight.Bold) },
            text = { Text("确定要处理选中的 $count 首歌曲吗？普通本地文件会被永久删除，导入的歌曲将被移出列表。") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteSelected()
                    confirmDeleteSelected = false
                }) {
                    Text("确定", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmDeleteSelected = false }) { Text("取消") }
            }
        )
    }
}

@Composable
private fun LocalSearchField(query: String, onQueryChange: (String) -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = MelodiaSpacing.md, vertical = MelodiaSpacing.sm)
            .clip(RoundedCornerShape(PillRadius))
            .background(MaterialTheme.colorScheme.surface)
            .padding(horizontal = MelodiaSpacing.md, vertical = 10.dp)
    ) {
        if (query.isEmpty()) {
            Text("按歌名/歌手搜索本地文件", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp)
        }
        BasicTextField(
            value = query,
            onValueChange = onQueryChange,
            textStyle = TextStyle(color = MaterialTheme.colorScheme.onSurface, fontSize = 14.sp),
            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun PlayAllRow(
    count: Int,
    sortOrder: LocalMusicSortOrder,
    onPlayAll: () -> Unit,
    onShuffle: () -> Unit,
    onSortSelected: (LocalMusicSortOrder) -> Unit
) {
    var showSortMenu by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = MelodiaSpacing.md, end = MelodiaSpacing.xs, top = MelodiaSpacing.xs, bottom = MelodiaSpacing.xs),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            modifier = Modifier
                .weight(1f)
                .pressable(MelodiaPress.Row) { onPlayAll() }
                .padding(vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier.size(28.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Rounded.PlayArrow, contentDescription = null, tint = MelodiaColors.OnAccent, modifier = Modifier.size(16.dp))
            }
            Spacer(Modifier.width(MelodiaSpacing.sm))
            Text("播放全部", color = MaterialTheme.colorScheme.onSurface, fontSize = 15.sp, fontWeight = FontWeight.Bold)
            Text(" ($count)", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
        }
        MelodiaIconButton(onClick = onShuffle) {
            Icon(Icons.Rounded.Shuffle, contentDescription = "随机播放", tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
        }
        Box {
            Row(
                modifier = Modifier
                    .pressable(MelodiaPress.Pill) { showSortMenu = true }
                    .padding(horizontal = MelodiaSpacing.sm, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(sortOrder.label, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.5.sp)
                Spacer(Modifier.width(4.dp))
                Icon(Icons.Rounded.SwapVert, contentDescription = "排序", tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(15.dp))
            }
            DropdownMenu(expanded = showSortMenu, onDismissRequest = { showSortMenu = false }) {
                LocalMusicSortOrder.entries.forEach { order ->
                    DropdownMenuItem(text = { Text(order.label) }, onClick = {
                        showSortMenu = false
                        onSortSelected(order)
                    })
                }
            }
        }
    }
}

package com.lin0721.linmusic.feature.listentogether.ui

import android.widget.Toast
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lin0721.linmusic.core.ui.components.MelodiaDragHandle
import com.lin0721.linmusic.core.ui.components.MelodiaIconButton
import com.lin0721.linmusic.core.ui.components.PlaceholderTextField
import com.lin0721.linmusic.core.ui.components.SongRow
import com.lin0721.linmusic.core.ui.components.SongRowData
import com.lin0721.linmusic.core.ui.theme.BottomSheetShape
import com.lin0721.linmusic.core.ui.theme.MelodiaColors
import com.lin0721.linmusic.core.ui.theme.MelodiaSpacing
import org.koin.androidx.compose.koinViewModel

// 一起听点歌面板：未输入时列出我喜欢的音乐，输入后搜索，点右侧按钮加入房间队列
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RoomSongPickerSheet(
    onDismiss: () -> Unit,
    viewModel: RoomSongPickerViewModel = koinViewModel()
) {
    val state by viewModel.state.collectAsState()
    val roomSongIds by viewModel.roomSongIds.collectAsState()
    val context = LocalContext.current

    LaunchedEffect(viewModel) { viewModel.open() }
    LaunchedEffect(viewModel) {
        viewModel.messages.collect { Toast.makeText(context, it, Toast.LENGTH_SHORT).show() }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.background,
        shape = BottomSheetShape,
        dragHandle = { MelodiaDragHandle() }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.85f)
                .navigationBarsPadding()
        ) {
            Text(
                text = "添加歌曲到一起听",
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 20.sp,
                fontWeight = FontWeight.ExtraBold,
                modifier = Modifier.padding(horizontal = MelodiaSpacing.lg)
            )
            PlaceholderTextField(
                value = state.query,
                onValueChange = viewModel::setQuery,
                placeholder = "搜索歌曲、歌手",
                containerColor = MelodiaColors.Surface,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = MelodiaSpacing.lg, vertical = MelodiaSpacing.md)
            )
            Text(
                text = state.title,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 13.sp,
                modifier = Modifier.padding(horizontal = MelodiaSpacing.lg)
            )
            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                when {
                    state.loading -> CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                    state.error != null -> Text(
                        text = state.error.orEmpty(),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.align(Alignment.Center)
                    )
                    state.tracks.isEmpty() -> Text(
                        text = if (state.query.isBlank()) "暂无喜欢的音乐，试试搜索" else "没有找到相关歌曲",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.align(Alignment.Center)
                    )
                    else -> LazyColumn(modifier = Modifier.fillMaxWidth()) {
                        items(state.tracks, key = { it.songId }) { track ->
                            val added = track.songId in state.added || track.songId in roomSongIds
                            SongRow(
                                data = SongRowData(
                                    id = track.songId,
                                    title = track.title,
                                    artist = track.artist,
                                    coverUrl = track.coverUrl,
                                    qualitySongId = track.songId
                                ),
                                compact = true,
                                onClick = { viewModel.add(track) },
                                trailingSlot = {
                                    MelodiaIconButton(onClick = { viewModel.add(track) }, enabled = !added) {
                                        Icon(
                                            imageVector = if (added) Icons.Rounded.Check else Icons.Rounded.Add,
                                            contentDescription = if (added) "已添加" else "添加到一起听队列",
                                            tint = if (added) MelodiaColors.Success else MaterialTheme.colorScheme.onSurface,
                                            modifier = Modifier.size(22.dp)
                                        )
                                    }
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

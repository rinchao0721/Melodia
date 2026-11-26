package com.lin0721.linmusic.feature.localmusic.ui.library

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lin0721.linmusic.LocalBottomOverlayInset
import com.lin0721.linmusic.core.localmusic.ui.filterDownloadTracks
import com.lin0721.linmusic.core.ui.components.EmptyState
import com.lin0721.linmusic.core.ui.icons.Download
import com.lin0721.linmusic.core.ui.icons.MelodiaIcons
import com.lin0721.linmusic.core.ui.icons.Shuffle
import com.lin0721.linmusic.core.ui.theme.MelodiaColors
import com.lin0721.linmusic.feature.localmusic.domain.toShared
import com.lin0721.linmusic.feature.localmusic.ui.LocalLibraryStateGate
import com.lin0721.linmusic.feature.localmusic.ui.LocalMusicNavigation
import com.lin0721.linmusic.feature.localmusic.ui.LocalMusicViewModel
import com.lin0721.linmusic.feature.localmusic.ui.LocalTrackActionsHost
import com.lin0721.linmusic.feature.localmusic.ui.components.LocalTrackRow
import com.lin0721.linmusic.feature.localmusic.ui.sortTracks
import org.koin.androidx.compose.koinViewModel

// 本地模式「下载」页：仅按来源筛选本地库曲目，不调用任何在线接口
@Composable
fun LocalDownloadsScreen(
    navigation: LocalMusicNavigation,
    onOpenSidebar: () -> Unit = {},
    viewModel: LocalMusicViewModel = koinViewModel()
) {
    val playingMediaId by viewModel.playingMediaId.collectAsStateWithLifecycle()
    val isPlaying by viewModel.isPlaying.collectAsStateWithLifecycle()
    val bottomInset = LocalBottomOverlayInset.current
    var showSortMenu by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MelodiaColors.PageFill)
            .statusBarsPadding()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "下载",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = MelodiaColors.TextPrimary,
                modifier = Modifier.clickable(onClick = onOpenSidebar)
            )
        }

        Box(modifier = Modifier.fillMaxSize()) {
            LocalLibraryStateGate(viewModel = viewModel) { state ->
                val downloadLocations = remember(state.tracks) {
                    filterDownloadTracks(state.tracks.map { it.toShared() }).mapTo(HashSet()) { it.location }
                }
                val downloadTracks = remember(state.tracks, downloadLocations, state.sortOrder) {
                    sortTracks(
                        state.tracks.filter { it.uri.toString() in downloadLocations },
                        state.sortOrder
                    )
                }

                if (downloadTracks.isEmpty()) {
                    EmptyState(
                        icon = MelodiaIcons.Download,
                        title = "暂无已下载歌曲",
                        subtitle = "下载的本地歌曲会自动显示在这里",
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(bottom = bottomInset + 88.dp)
                    ) {
                        item(key = "downloads_header") {
                            CountAndSortBar(
                                countText = "共 ${downloadTracks.size} 首歌曲",
                                sortOrder = state.sortOrder,
                                showSortMenu = showSortMenu,
                                onShowSortMenu = { showSortMenu = it },
                                onSortChange = viewModel::setSortOrder
                            )
                        }
                        items(downloadTracks, key = { it.uri }) { track ->
                            LocalTrackRow(
                                track = track,
                                subtitle = "${track.artist} · ${track.album ?: "未知专辑"}",
                                playingMediaId = playingMediaId,
                                isPlaying = isPlaying,
                                onClick = { viewModel.playTracks(downloadTracks, track) },
                                onMoreClick = { viewModel.openTrackMenu(track, downloadTracks) }
                            )
                        }
                    }

                    FloatingActionButton(
                        onClick = { viewModel.playTracks(downloadTracks, shuffle = true) },
                        containerColor = MelodiaColors.Accent,
                        contentColor = MelodiaColors.OnAccent,
                        shape = CircleShape,
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(end = 16.dp, bottom = bottomInset + 16.dp)
                    ) {
                        Icon(MelodiaIcons.Shuffle, contentDescription = "随机播放全部")
                    }
                }
            }
        }
    }

    LocalTrackActionsHost(viewModel = viewModel, navigation = navigation)
}

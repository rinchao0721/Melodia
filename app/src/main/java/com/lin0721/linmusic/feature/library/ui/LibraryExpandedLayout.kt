package com.lin0721.linmusic.feature.library.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.lin0721.linmusic.core.ui.components.EmptyState
import com.lin0721.linmusic.feature.playlist.ui.PlaylistScreen

// 曲库 Expanded 断点下的列表栏固定宽度。目标是"播放面板展开时联动收紧到 240dp"，
// 但播放面板常驻侧栏（Phase 3）还没落地，现状只有这一个宽度，收紧逻辑等 Phase 3
// 产出"面板是否展开"的真实信号后再接上（见平板适配设计文档 6.2/7 节）
private val LibraryListColumnWidth = 340.dp

// 曲库双栏右栏当前选中的详情项。歌手本轮不做嵌入版，点击仍走整屏跳转，故这里只有歌单/专辑一种
private sealed class LibrarySelection {
    data class Playlist(val id: Long, val isAlbum: Boolean) : LibrarySelection()
}

// Expanded 断点下的曲库：左栏是原样的 LibraryScreen（点击不再整屏跳转，改为更新选中态），
// 右栏渲染选中项的嵌入版详情（复用 PlaylistScreen，showTopBar=false）
@Composable
fun LibraryExpandedLayout(
    onArtistClick: (Long) -> Unit,
    onNavigateToProfile: (Long) -> Unit,
    onOpenSidebar: () -> Unit,
    onLoginScreenVisibilityChanged: (Boolean) -> Unit
) {
    var selection by remember { mutableStateOf<LibrarySelection?>(null) }

    // 返回键先清空选中回空态，再按一次才走外层默认返回（退出曲库/切 Tab）——已与用户确认
    BackHandler(enabled = selection != null) {
        selection = null
    }

    Row(modifier = Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .width(LibraryListColumnWidth)
                .fillMaxHeight()
        ) {
            LibraryScreen(
                onPlaylistClick = { id -> selection = LibrarySelection.Playlist(id, isAlbum = false) },
                onArtistClick = onArtistClick,
                onAlbumClick = { id -> selection = LibrarySelection.Playlist(id, isAlbum = true) },
                onBack = {},
                onOpenSidebar = onOpenSidebar,
                onLoginScreenVisibilityChanged = onLoginScreenVisibilityChanged
            )
        }

        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
        ) {
            when (val current = selection) {
                null -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        EmptyState(
                            icon = Icons.Rounded.LibraryMusic,
                            title = "选择左侧的歌单或专辑",
                            subtitle = "详情会显示在这里"
                        )
                    }
                }
                is LibrarySelection.Playlist -> {
                    PlaylistScreen(
                        playlistId = current.id,
                        isAlbum = current.isAlbum,
                        showTopBar = false,
                        onBack = { selection = null },
                        onArtistClick = onArtistClick,
                        onAlbumClick = { id -> selection = LibrarySelection.Playlist(id, isAlbum = true) },
                        onNavigateToProfile = onNavigateToProfile
                    )
                }
            }
        }
    }
}

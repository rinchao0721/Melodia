package com.lin0721.linmusic.desktop.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.PlaylistAdd
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.FileDownload
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.lin0721.linmusic.core.ui.components.PlaylistCollectItem
import com.lin0721.linmusic.core.ui.components.PlaylistCollectState
import com.lin0721.linmusic.desktop.ui.components.Cover
import com.lin0721.linmusic.desktop.ui.components.HoverReveal
import com.lin0721.linmusic.desktop.ui.components.HoverScrollbarBox
import com.lin0721.linmusic.desktop.ui.components.PlayPauseIcon
import com.lin0721.linmusic.desktop.ui.components.SimpleMenuItem
import com.lin0721.linmusic.desktop.ui.components.StateIcon
import com.lin0721.linmusic.desktop.ui.components.sizedCoverUrl
import com.lin0721.linmusic.desktop.ui.library.CreatePlaylistDialog
import com.lin0721.linmusic.desktop.ui.library.EMPTY_NAME_MESSAGE
import com.lin0721.linmusic.desktop.ui.shell.LocalDesktopNavigator
import com.lin0721.linmusic.desktop.ui.theme.DesktopColors
import com.lin0721.linmusic.desktop.ui.track.CollectToPlaylistDialog
import com.lin0721.linmusic.feature.newworks.domain.NewWorksRelease
import com.lin0721.linmusic.feature.newworks.domain.typeLabel
import com.lin0721.linmusic.feature.newworks.ui.NewWorksReleaseStatus
import com.lin0721.linmusic.feature.newworks.ui.NewWorksUiState
import com.lin0721.linmusic.feature.playlist.ui.PlaylistImportState
import com.lin0721.linmusic.desktop.ui.components.copyTextToClipboard

// 距离末尾还剩几项时提前拉下一页
private const val LOAD_MORE_THRESHOLD = 2
private const val COVER_REQUEST_PX = 640
private const val COVER_BLUR_RADIUS_DP = 10
private const val CARD_ASPECT_RATIO = 0.8f
private const val COPY_SUCCESS_MESSAGE = "已复制链接"
private const val COPY_FAILURE_MESSAGE = "复制失败"
private val EdgePadding = 24.dp
private val ReleaseCardMinWidth = 300.dp
private val CardShape = RoundedCornerShape(8.dp)
private val PlayButtonSize = 56.dp
private val MenuWidth = 240.dp

class NewWorksTabActions(
    val onAlbumClick: (id: Long, title: String) -> Unit,
    val onTogglePlay: (NewWorksRelease) -> Unit,
    val onToggleLibrary: (NewWorksRelease) -> Unit,
    val onAddToPlayNext: (NewWorksRelease) -> Unit,
    val onPrepareCollect: (songId: Long, onReady: () -> Unit) -> Unit,
    val onSaveCollection: (songId: Long, items: List<PlaylistCollectItem>) -> Unit,
    val onSaveNewCollection: (name: String, songId: Long) -> Unit,
    val onPrepareImportTargets: (onReady: () -> Unit) -> Unit,
    val onAddToPlaylist: (NewWorksRelease, playlistId: Long) -> Unit,
    val onCreatePlaylistAndAdd: (NewWorksRelease, name: String, isPrivate: Boolean) -> Unit,
    val onRetry: () -> Unit,
    val onLoadMore: () -> Unit
)

// 音乐页「最新」：「最新发布」标题 + 大卡片网格。
// 卡片点击：专辑进专辑页，单曲直接播放；操作按钮悬停才显示
@Composable
fun NewWorksTab(
    uiState: NewWorksUiState,
    gridState: LazyGridState,
    collectState: PlaylistCollectState,
    importState: PlaylistImportState,
    status: NewWorksReleaseStatus,
    actions: NewWorksTabActions
) {
    when (uiState) {
        NewWorksUiState.Loading -> HomeTabLoading()
        is NewWorksUiState.Error -> HomeTabError(uiState.message, actions.onRetry)
        is NewWorksUiState.Success -> {
            if (uiState.releases.isEmpty()) {
                Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("还没有关注歌手的新作", color = DesktopColors.TextPrimary, fontWeight = FontWeight.Bold)
                    Text(
                        "多关注几位歌手，新歌会出现在这里",
                        color = DesktopColors.TextGray,
                        fontSize = 13.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
                return
            }
            NewWorksGrid(uiState, gridState, collectState, importState, status, actions)
        }
    }
}

@Composable
private fun NewWorksGrid(
    state: NewWorksUiState.Success,
    gridState: LazyGridState,
    collectState: PlaylistCollectState,
    importState: PlaylistImportState,
    status: NewWorksReleaseStatus,
    actions: NewWorksTabActions
) {
    val shouldLoadMore by remember(state.releases.size, state.hasMore, state.isLoadingMore) {
        derivedStateOf {
            val lastVisible = gridState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            state.hasMore && !state.isLoadingMore &&
                lastVisible >= gridState.layoutInfo.totalItemsCount - LOAD_MORE_THRESHOLD
        }
    }
    LaunchedEffect(shouldLoadMore) {
        if (shouldLoadMore) actions.onLoadMore()
    }

    // 弹窗放在网格外层，避免随卡片滚出视口被回收
    var collectSongId by remember { mutableStateOf<Long?>(null) }
    var createPlaylistFor by remember { mutableStateOf<NewWorksRelease?>(null) }
    val navigator = LocalDesktopNavigator.current

    HoverScrollbarBox(gridState) {
        LazyVerticalGrid(
            columns = GridCells.Adaptive(ReleaseCardMinWidth),
            state = gridState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = EdgePadding, end = EdgePadding, top = 4.dp, bottom = 24.dp),
            horizontalArrangement = Arrangement.spacedBy(24.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            item(key = "title", span = { GridItemSpan(maxLineSpan) }) {
                Text(
                    "最新发布",
                    color = DesktopColors.TextPrimary,
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold
                )
            }
            items(state.releases, key = { "${it.isAlbum}_${it.id}" }) { release ->
                ReleaseCard(
                    release = release,
                    inLibrary = status.isInLibrary(release),
                    playing = status.isReleasePlaying(release),
                    importState = importState,
                    actions = actions,
                    onCollectSong = { collectSongId = it },
                    onCreatePlaylist = { createPlaylistFor = it }
                )
            }
            if (state.isLoadingMore) {
                item(key = "loading_more", span = { GridItemSpan(maxLineSpan) }) {
                    Box(Modifier.fillMaxWidth().padding(vertical = 8.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = DesktopColors.Accent)
                    }
                }
            }
        }
    }

    collectSongId?.let { songId ->
        CollectToPlaylistDialog(
            songId = songId,
            state = collectState,
            onSave = { items ->
                actions.onSaveCollection(songId, items)
                collectSongId = null
            },
            onCreate = { name ->
                actions.onSaveNewCollection(name, songId)
                collectSongId = null
            },
            onDismiss = { collectSongId = null }
        )
    }

    createPlaylistFor?.let { release ->
        CreatePlaylistDialog(
            onDismiss = { createPlaylistFor = null },
            onCreate = { name, isPrivate ->
                actions.onCreatePlaylistAndAdd(release, name, isPrivate)
                createPlaylistFor = null
            },
            onEmptyName = { navigator.showMessage(EMPTY_NAME_MESSAGE) }
        )
    }
}

@Composable
private fun ReleaseCard(
    release: NewWorksRelease,
    inLibrary: Boolean,
    playing: Boolean,
    importState: PlaylistImportState,
    actions: NewWorksTabActions,
    onCollectSong: (Long) -> Unit,
    onCreatePlaylist: (NewWorksRelease) -> Unit
) {
    val hoverSource = remember { MutableInteractionSource() }
    val hovered by hoverSource.collectIsHoveredAsState()
    var menuOpen by remember { mutableStateOf(false) }

    Box(
        Modifier
            .fillMaxWidth()
            .aspectRatio(CARD_ASPECT_RATIO)
            .clip(CardShape)
            .background(DesktopColors.CoverPlaceholder)
            .hoverable(hoverSource)
            .pointerHoverIcon(PointerIcon.Hand)
            .clickable { if (release.isAlbum) actions.onAlbumClick(release.id, release.title) else actions.onTogglePlay(release) }
    ) {
        AsyncImage(
            model = sizedCoverUrl(release.coverUrl, COVER_REQUEST_PX),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize().blur(COVER_BLUR_RADIUS_DP.dp, BlurredEdgeTreatment.Rectangle)
        )
        Box(
            Modifier.matchParentSize().background(
                Brush.verticalGradient(
                    0f to Color.Black.copy(alpha = 0.75f),
                    0.5f to Color.Black.copy(alpha = 0.15f),
                    1f to Color.Black.copy(alpha = 0.6f)
                )
            )
        )
        Column(Modifier.fillMaxSize().padding(16.dp)) {
            Row(verticalAlignment = Alignment.Top) {
                Cover(release.coverUrl, 72.dp, shape = RoundedCornerShape(4.dp))
                Column(Modifier.weight(1f).padding(start = 12.dp)) {
                    Text(
                        release.title,
                        color = Color.White,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        "${release.typeLabel} • ${release.artistName}",
                        color = Color.White.copy(alpha = 0.75f),
                        fontSize = 13.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }
            Spacer(Modifier.weight(1f))
            HoverReveal(revealed = hovered || menuOpen, modifier = Modifier.fillMaxWidth()) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
                    Box {
                        IconButton(onClick = { menuOpen = true }) {
                            Icon(Icons.Rounded.MoreHoriz, contentDescription = "更多", tint = Color.White)
                        }
                        ReleaseMenu(
                            expanded = menuOpen,
                            onDismiss = { menuOpen = false },
                            release = release,
                            inLibrary = inLibrary,
                            importState = importState,
                            actions = actions,
                            onCreatePlaylist = onCreatePlaylist
                        )
                    }
                    IconButton(onClick = {
                        if (release.isAlbum) {
                            actions.onToggleLibrary(release)
                        } else {
                            actions.onPrepareCollect(release.id) { onCollectSong(release.id) }
                        }
                    }) {
                        StateIcon(inLibrary) { added ->
                            Icon(
                                imageVector = if (added) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                                contentDescription = if (added) "取消收藏" else "收藏",
                                tint = if (added) DesktopColors.Accent else Color.White,
                                modifier = Modifier.size(28.dp)
                            )
                        }
                    }
                    Spacer(Modifier.width(8.dp))
                    Box(
                        Modifier.size(PlayButtonSize).clip(CircleShape).background(Color.White)
                            .pointerHoverIcon(PointerIcon.Hand).clickable { actions.onTogglePlay(release) },
                        contentAlignment = Alignment.Center
                    ) {
                        PlayPauseIcon(
                            isPlaying = playing,
                            tint = Color.Black,
                            size = 32.dp,
                            contentDescription = if (playing) "暂停" else "播放"
                        )
                    }
                }
            }
        }
    }
}

private enum class SubMenu { None, Playlist, Share }

// 「更多」菜单：添加到音乐库 / 加入播放队列 / 加入歌单 ▸ / 下载（暂不可用）/ 分享 ▸
@Composable
private fun ReleaseMenu(
    expanded: Boolean,
    onDismiss: () -> Unit,
    release: NewWorksRelease,
    inLibrary: Boolean,
    importState: PlaylistImportState,
    actions: NewWorksTabActions,
    onCreatePlaylist: (NewWorksRelease) -> Unit
) {
    var subMenu by remember { mutableStateOf(SubMenu.None) }
    val navigator = LocalDesktopNavigator.current
    val closeAll = {
        subMenu = SubMenu.None
        onDismiss()
    }

    DropdownMenu(
        expanded = expanded,
        // 子菜单展开时点击落在子菜单上也会触发父级的外部点击，此时不关闭
        onDismissRequest = { if (subMenu == SubMenu.None) onDismiss() },
        modifier = Modifier.width(MenuWidth),
        shape = RoundedCornerShape(6.dp),
        containerColor = DesktopColors.PopupSurface,
        shadowElevation = 12.dp
    ) {
        MenuEntry(
            if (inLibrary) "从音乐库移除" else "添加到音乐库",
            if (inLibrary) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder
        ) {
            closeAll()
            actions.onToggleLibrary(release)
        }
        MenuEntry("加入播放队列", Icons.AutoMirrored.Rounded.QueueMusic) {
            closeAll()
            actions.onAddToPlayNext(release)
        }
        Box {
            MenuEntry("加入歌单", Icons.AutoMirrored.Rounded.PlaylistAdd, chevron = true) {
                actions.onPrepareImportTargets { subMenu = SubMenu.Playlist }
            }
            DropdownMenu(
                expanded = subMenu == SubMenu.Playlist,
                onDismissRequest = { subMenu = SubMenu.None },
                modifier = Modifier.width(MenuWidth),
                offset = DpOffset(MenuWidth - 12.dp, 0.dp),
                shape = RoundedCornerShape(6.dp),
                containerColor = DesktopColors.PopupSurface,
                shadowElevation = 12.dp
            ) {
                MenuEntry("新建歌单", Icons.AutoMirrored.Rounded.PlaylistAdd) {
                    closeAll()
                    onCreatePlaylist(release)
                }
                when {
                    importState.isLoading -> Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = DesktopColors.Accent, modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                    }
                    importState.items.isEmpty() -> MenuEntry("暂无歌单", null, enabled = false) {}
                    else -> importState.items.forEach { playlist ->
                        SimpleMenuItem(
                            text = playlist.name,
                            modifier = Modifier.padding(horizontal = 4.dp),
                            onClick = {
                                closeAll()
                                actions.onAddToPlaylist(release, playlist.id)
                            }
                        )
                    }
                }
            }
        }
        MenuEntry("下载", Icons.Rounded.FileDownload, enabled = false) {}
        Box {
            MenuEntry("分享", Icons.Rounded.Share, chevron = true) { subMenu = SubMenu.Share }
            DropdownMenu(
                expanded = subMenu == SubMenu.Share,
                onDismissRequest = { subMenu = SubMenu.None },
                modifier = Modifier.width(MenuWidth),
                offset = DpOffset(MenuWidth - 12.dp, 0.dp),
                shape = RoundedCornerShape(6.dp),
                containerColor = DesktopColors.PopupSurface,
                shadowElevation = 12.dp
            ) {
                MenuEntry("复制链接", null) {
                    closeAll()
                    navigator.showMessage(if (copyReleaseLink(release)) COPY_SUCCESS_MESSAGE else COPY_FAILURE_MESSAGE)
                }
            }
        }
    }
}

@Composable
private fun MenuEntry(
    text: String,
    icon: ImageVector?,
    enabled: Boolean = true,
    chevron: Boolean = false,
    onClick: () -> Unit
) {
    SimpleMenuItem(
        text = text,
        icon = icon,
        enabled = enabled,
        modifier = Modifier.padding(horizontal = 4.dp),
        trailing = if (chevron) {
            { Icon(Icons.Rounded.ChevronRight, null, tint = DesktopColors.TextGray, modifier = Modifier.size(16.dp)) }
        } else {
            null
        },
        onClick = onClick
    )
}

private fun copyReleaseLink(release: NewWorksRelease): Boolean {
    val path = if (release.isAlbum) "album" else "song"
    return copyTextToClipboard("https://music.163.com/$path?id=${release.id}")
}

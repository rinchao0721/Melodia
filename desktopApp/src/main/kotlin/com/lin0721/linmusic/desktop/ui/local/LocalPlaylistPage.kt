package com.lin0721.linmusic.desktop.ui.local

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.ExperimentalFoundationApi
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.AudioFile
import androidx.compose.material.icons.rounded.CloudSync
import androidx.compose.material.icons.rounded.CloudUpload
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.CreateNewFolder
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.DriveFileRenameOutline
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.FolderOff
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.PushPin
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lin0721.linmusic.core.model.Album
import com.lin0721.linmusic.core.model.Artist
import com.lin0721.linmusic.core.model.Track
import com.lin0721.linmusic.core.player.PlayMode
import com.lin0721.linmusic.core.player.PlaybackController
import com.lin0721.linmusic.desktop.data.local.LOCAL_ALL_TRACKS_ID
import com.lin0721.linmusic.desktop.data.local.LOCAL_DOWNLOADS_ID
import com.lin0721.linmusic.desktop.data.local.LocalPlaylistSummary
import com.lin0721.linmusic.desktop.data.local.LocalScanState
import com.lin0721.linmusic.desktop.data.local.LocalTrack
import com.lin0721.linmusic.desktop.platform.revealInFileManager
import com.lin0721.linmusic.desktop.ui.cloud.CloudMusicActions
import com.lin0721.linmusic.desktop.ui.cloud.uploadFiles
import com.lin0721.linmusic.desktop.ui.components.Cover
import com.lin0721.linmusic.desktop.ui.components.DesktopMenu
import com.lin0721.linmusic.desktop.ui.components.HoverScrollbarBox
import com.lin0721.linmusic.desktop.ui.components.LocalUiMotion
import com.lin0721.linmusic.desktop.ui.components.MenuDivider
import com.lin0721.linmusic.desktop.ui.components.MenuItem
import com.lin0721.linmusic.desktop.ui.components.MenuScope
import com.lin0721.linmusic.desktop.ui.components.SimpleMenuItem
import com.lin0721.linmusic.desktop.ui.components.SubMenuItem
import com.lin0721.linmusic.desktop.ui.components.copyTextToClipboard
import com.lin0721.linmusic.desktop.ui.components.songInfoText
import com.lin0721.linmusic.desktop.ui.components.uiSpring
import com.lin0721.linmusic.desktop.ui.components.uiTween
import com.lin0721.linmusic.desktop.ui.palette.FallbackCoverPalette
import com.lin0721.linmusic.desktop.ui.palette.extractCoverPaletteFromUrl
import com.lin0721.linmusic.desktop.ui.palette.themedCoverBackground
import com.lin0721.linmusic.desktop.ui.playlist.ActionIcon
import com.lin0721.linmusic.desktop.ui.playlist.PlayCircleButton
import com.lin0721.linmusic.desktop.ui.playlist.PlaylistPill
import com.lin0721.linmusic.desktop.ui.playlist.PlaylistSearchBox
import com.lin0721.linmusic.desktop.ui.playlist.PlaylistSortMenu
import com.lin0721.linmusic.desktop.ui.playlist.PlaylistStickyBar
import com.lin0721.linmusic.desktop.ui.playlist.TrackColumnHeader
import com.lin0721.linmusic.desktop.ui.playlist.scrolledPast
import com.lin0721.linmusic.desktop.ui.shell.LocalDesktopNavigator
import com.lin0721.linmusic.desktop.ui.shell.SongActionSources
import com.lin0721.linmusic.desktop.ui.theme.DesktopColors
import com.lin0721.linmusic.desktop.ui.track.TrackRow
import com.lin0721.linmusic.feature.playlist.presentation.PlaylistSortKey
import com.lin0721.linmusic.feature.playlist.presentation.PlaylistSortOrder
import com.lin0721.linmusic.feature.playlist.presentation.filterTracks
import com.lin0721.linmusic.feature.playlist.presentation.formatTotalDuration
import com.lin0721.linmusic.feature.playlist.presentation.sortTracks
import kotlinx.coroutines.launch
import java.io.File

private const val HERO_DARKEN_FRACTION = 0.35f
private const val STICKY_BAR_ANIMATION_MS = 200
private val HeaderPadding = 24.dp
private val ActionBarVerticalPadding = 16.dp
private val PlayButtonSize = 56.dp
private val StickyBarHeight = 64.dp

// 列表展示用的歌曲行：id 恒为路径占位 id，保证同一首歌匹配到同一网易歌曲时也不重复
private class LocalRow(val display: Track, val track: LocalTrack)

private fun LocalTrack.toDisplayTrack(coverUri: String?): Track = Track(
    id = placeholderId,
    name = title,
    ar = listOf(Artist(name = artist)),
    al = Album(name = album, picUrl = coverUri.orEmpty()),
    dt = durationMs
)

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun LocalPlaylistPage(
    playlistId: Long,
    actions: LocalMusicActions,
    cloud: CloudMusicActions,
    controller: PlaybackController,
    songActions: SongActionSources,
    modifier: Modifier = Modifier
) {
    val navigator = LocalDesktopNavigator.current
    val repository = actions.repository
    val scope = rememberCoroutineScope()
    val summaries by repository.summaries.collectAsState()
    val summary = summaries.firstOrNull { it.id == playlistId }
    if (summary == null) {
        Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("歌单不存在或已被移除", color = DesktopColors.TextGray, fontSize = 14.sp)
        }
        return
    }
    val library by repository.data.collectAsState()
    val folders = remember(library, playlistId) { library.playlists.firstOrNull { it.id == playlistId }?.folders.orEmpty() }
    val tracks by remember(playlistId) { repository.tracksOf(playlistId) }
        .collectAsState(initial = remember(playlistId) { repository.currentTracks(playlistId) })
    val scanState by repository.scanState.collectAsState()
    val nowPlaying by controller.nowPlaying.collectAsState()
    val playMode by controller.playMode.collectAsState()
    val playWhenReady by controller.playWhenReady.collectAsState()
    val playContext by controller.playContext.collectAsState()
    val likedSongIds by songActions.likes.likedSongIds.collectAsState()

    var query by remember(playlistId) { mutableStateOf("") }
    var order by remember(playlistId) { mutableStateOf(PlaylistSortOrder()) }
    var renaming by remember(playlistId) { mutableStateOf(false) }
    var removing by remember(playlistId) { mutableStateOf(false) }
    var editingTrack by remember(playlistId) { mutableStateOf<LocalTrack?>(null) }
    var matchingTrack by remember(playlistId) { mutableStateOf<LocalTrack?>(null) }

    val rows = remember(tracks) { tracks.map { LocalRow(it.toDisplayTrack(repository.coverUri(it)), it) } }
    val shown = remember(rows, query, order) {
        val byId = rows.associateBy { it.display.id }
        sortTracks(filterTracks(rows.map { it.display }, query), order, emptyMap()).mapNotNull { byId[it.id] }
    }
    val isUserPlaylist = playlistId > 0L
    val isThisContext = playContext == actions.playContext(playlistId)
    val isShuffleActive = playMode == PlayMode.SHUFFLE

    val playFrom: (LocalRow) -> Unit = { row ->
        val list = shown.map { it.track }
        if (isShuffleActive) {
            actions.playTracks(playlistId, listOf(row.track) + list.filterNot { it.path == row.track.path }.shuffled(), 0)
        } else {
            actions.playTracks(playlistId, list, list.indexOfFirst { it.path == row.track.path })
        }
    }
    val onPlayClick: () -> Unit = {
        when {
            isThisContext -> controller.togglePlayPause()
            shown.isEmpty() -> navigator.showMessage("歌单里还没有歌曲")
            else -> {
                val list = shown.map { it.track }
                actions.playTracks(playlistId, if (isShuffleActive) list.shuffled() else list, 0)
            }
        }
    }
    val toggleLike: (Long) -> Unit = { id ->
        if (!navigator.isLoggedIn) {
            navigator.showMessage("请先登录账号")
        } else {
            scope.launch {
                songActions.likes.likeSong(id, id !in likedSongIds).collect { result ->
                    result.onFailure { navigator.showMessage("操作失败") }
                }
            }
        }
    }
    val addFolder: () -> Unit = { scope.launch { pickMusicFolder()?.let { repository.addFolder(playlistId, it) } } }
    val addFiles: () -> Unit = {
        scope.launch { pickAudioFiles().takeIf { it.isNotEmpty() }?.let { repository.addFiles(playlistId, it) } }
    }

    var heroBase by remember(playlistId) { mutableStateOf(FallbackCoverPalette.base) }
    LaunchedEffect(summary.coverUri) {
        summary.coverUri?.let { heroBase = extractCoverPaletteFromUrl(it).base }
    }
    val heroColor by animateColorAsState(
        themedCoverBackground(heroBase, HERO_DARKEN_FRACTION), uiSpring(), label = "localPlaylistHero"
    )

    val listState = rememberLazyListState()
    val density = LocalDensity.current
    val barHeightPx = with(density) { StickyBarHeight.roundToPx() }
    val playButtonBottomPx = with(density) { (ActionBarVerticalPadding + PlayButtonSize).roundToPx() }
    // 恢复页面现场时列表直接停在中间，大标题不会再测一次，高度要随现场保存
    var heroHeightPx by rememberSaveable(playlistId) { mutableIntStateOf(0) }
    var headerHeightPx by rememberSaveable(playlistId) { mutableIntStateOf(0) }
    val showTitleBar by remember(barHeightPx) {
        derivedStateOf { heroHeightPx > 0 && listState.scrolledPast(heroHeightPx - barHeightPx) }
    }
    val showPlayButton by remember(barHeightPx, playButtonBottomPx) {
        derivedStateOf { heroHeightPx > 0 && listState.scrolledPast(heroHeightPx + playButtonBottomPx - barHeightPx) }
    }

    HoverScrollbarBox(listState) {
        LazyColumn(state = listState, modifier = modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
            item(key = "header") {
                Column(
                    Modifier.fillMaxWidth().onSizeChanged { headerHeightPx = it.height }
                        .background(Brush.verticalGradient(listOf(heroColor, DesktopColors.PageFill)))
                ) {
                    Box(Modifier.onSizeChanged { heroHeightPx = it.height }) {
                        LocalPlaylistHero(summary, tracks, folders)
                    }
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = HeaderPadding, vertical = ActionBarVerticalPadding),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        PlayCircleButton(PlayButtonSize, 32.dp, isThisContext && playWhenReady, onPlayClick)
                        ActionIcon(
                            Icons.Rounded.Shuffle,
                            if (isShuffleActive) "随机播放已开启" else "随机播放已关闭",
                            tint = if (isShuffleActive) DesktopColors.Accent else DesktopColors.TextGray,
                            onClick = controller::toggleShuffle
                        )
                        LocalPlaylistMoreMenu(
                            summary = summary,
                            folders = folders,
                            onPlayNextAll = {
                                actions.addToPlayNext(shown.map { it.track })
                                navigator.showMessage(if (shown.isEmpty()) "歌单里还没有歌曲" else "已添加至下一首播放")
                            },
                            onUploadAll = { uploadFiles(cloud, navigator, shown.map { File(it.track.path) }) },
                            onAddFolder = addFolder,
                            onAddFiles = addFiles,
                            onRemoveFolder = { repository.removeFolder(playlistId, it) },
                            onRescan = repository::rescan,
                            onTogglePin = { repository.setPinned(playlistId, !summary.isPinned) },
                            onRename = { renaming = true },
                            onRemove = { removing = true }
                        )
                        Spacer(Modifier.weight(1f))
                        PlaylistSearchBox(query) { query = it }
                        PlaylistSortMenu(order, showAdded = false) { key ->
                            order = if (key == PlaylistSortKey.CUSTOM) PlaylistSortOrder() else PlaylistSortOrder(key, true)
                        }
                    }
                    if (isUserPlaylist) {
                        Row(
                            Modifier.padding(start = HeaderPadding, end = HeaderPadding, bottom = 12.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            PlaylistPill(Icons.Rounded.CreateNewFolder, "添加文件夹", addFolder)
                            PlaylistPill(Icons.Rounded.AudioFile, "添加歌曲", addFiles)
                        }
                    }
                    (scanState as? LocalScanState.Scanning)?.let { scanning ->
                        Row(
                            Modifier.padding(start = HeaderPadding, end = HeaderPadding, bottom = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            CircularProgressIndicator(Modifier.size(14.dp), color = DesktopColors.TextGray, strokeWidth = 2.dp)
                            Spacer(Modifier.width(8.dp))
                            Text("正在扫描本地音乐 ${scanning.done}/${scanning.total}", color = DesktopColors.TextGray, fontSize = 12.sp)
                        }
                    }
                }
            }
            stickyHeader(key = "columns") {
                Box(
                    Modifier.graphicsLayer {
                        if (headerHeightPx > 0) {
                            val naturalY = if (listState.firstVisibleItemIndex > 0) 0 else headerHeightPx - listState.firstVisibleItemScrollOffset
                            translationY = (barHeightPx - naturalY).coerceIn(0, barHeightPx).toFloat()
                        }
                    }
                ) {
                    TrackColumnHeader(order, showAdded = false) { order = order.toggled(it) }
                }
            }
            itemsIndexed(shown, key = { _, row -> row.track.path }) { index, row ->
                val songId = row.track.songId
                TrackRow(
                    index = index,
                    track = row.display,
                    isCurrent = nowPlaying?.songId == row.track.queueSongId,
                    onPlay = { playFrom(row) },
                    modifier = Modifier.padding(horizontal = 16.dp)
                        .then(if (LocalUiMotion.current.enabled) Modifier.animateItem() else Modifier),
                    menuContent = { dismiss ->
                        LocalTrackMenuItems(
                            track = row.track,
                            isLiked = songId != null && songId in likedSongIds,
                            dismiss = dismiss,
                            onPlay = { playFrom(row) },
                            onPlayNext = {
                                actions.addToPlayNext(listOf(row.track))
                                navigator.showMessage("已添加至下一首播放")
                            },
                            onToggleLike = songId?.let { id -> { toggleLike(id) } },
                            onEditTags = { editingTrack = row.track },
                            onUpload = { uploadFiles(cloud, navigator, listOf(File(row.track.path))) },
                            onMatch = if (repository.isDownloadedFile(row.track.path)) {
                                null
                            } else {
                                { matchingTrack = row.track }
                            },
                            onRemove = if (isUserPlaylist) {
                                { repository.removeTracks(playlistId, listOf(row.track.path)) }
                            } else {
                                null
                            }
                        )
                    }
                )
            }
            if (shown.isEmpty()) {
                item(key = "empty") {
                    Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                        Text(emptyMessage(playlistId, query), color = DesktopColors.TextGray, fontSize = 14.sp)
                    }
                }
            }
        }
        Box(Modifier.fillMaxWidth().clipToBounds()) {
            AnimatedVisibility(
                visible = showTitleBar,
                enter = fadeIn(uiTween(STICKY_BAR_ANIMATION_MS)) + slideInVertically(uiTween(STICKY_BAR_ANIMATION_MS)) { -it / 2 },
                exit = fadeOut(uiTween(STICKY_BAR_ANIMATION_MS)) + slideOutVertically(uiTween(STICKY_BAR_ANIMATION_MS)) { -it / 2 }
            ) {
                PlaylistStickyBar(summary.name, heroColor, showPlayButton, isThisContext && playWhenReady, listState, onPlayClick)
            }
        }
    }

    if (renaming) {
        RenameLocalPlaylistDialog(summary.name, onDismiss = { renaming = false }) { name ->
            repository.rename(playlistId, name)
            renaming = false
        }
    }
    editingTrack?.let { track ->
        LocalTagEditorDialog(track, repository) { editingTrack = null }
    }
    matchingTrack?.let { track ->
        LocalMatchDialog(track, repository) { matchingTrack = null }
    }
    if (removing) {
        RemoveLocalPlaylistDialog(summary.name, onDismiss = { removing = false }) {
            removing = false
            repository.removePlaylist(playlistId)
            navigator.goBack()
        }
    }
}

private fun emptyMessage(playlistId: Long, query: String): String = when {
    query.isNotBlank() -> "未找到匹配的歌曲"
    playlistId == LOCAL_DOWNLOADS_ID -> "还没有下载的歌曲"
    playlistId == LOCAL_ALL_TRACKS_ID -> "还没有本地歌曲，先添加一个本地歌单吧"
    else -> "还没有歌曲，添加文件夹或歌曲文件即可收录"
}

@Composable
private fun LocalPlaylistHero(summary: LocalPlaylistSummary, tracks: List<LocalTrack>, folders: List<String>) {
    val totalMs = tracks.sumOf { it.durationMs }
    val detail = buildString {
        append("${tracks.size} 首歌曲")
        if (totalMs > 0) append("，${formatTotalDuration(totalMs)}")
    }
    Row(Modifier.fillMaxWidth().padding(HeaderPadding), verticalAlignment = Alignment.Bottom) {
        Cover(summary.coverUri, 200.dp, shape = RoundedCornerShape(6.dp))
        Column(Modifier.weight(1f).padding(start = HeaderPadding)) {
            Text("本地歌单", color = DesktopColors.TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            Text(
                summary.name,
                color = DesktopColors.TextPrimary,
                fontSize = 48.sp,
                lineHeight = 56.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(vertical = 4.dp)
            )
            Text(detail, color = DesktopColors.TextGray, fontSize = 14.sp)
            if (folders.isNotEmpty()) {
                Text(
                    folders.joinToString("、"),
                    color = DesktopColors.TextGray,
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
        }
    }
}

@Composable
private fun LocalPlaylistMoreMenu(
    summary: LocalPlaylistSummary,
    folders: List<String>,
    onPlayNextAll: () -> Unit,
    onUploadAll: () -> Unit,
    onAddFolder: () -> Unit,
    onAddFiles: () -> Unit,
    onRemoveFolder: (String) -> Unit,
    onRescan: () -> Unit,
    onTogglePin: () -> Unit,
    onRename: () -> Unit,
    onRemove: () -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val isUserPlaylist = !summary.isBuiltin
    Box {
        ActionIcon(Icons.Rounded.MoreHoriz, "更多") { expanded = true }
        DesktopMenu(expanded = expanded, onDismiss = { expanded = false }) {
            MenuItem("全部加入下一首播放", icon = Icons.AutoMirrored.Rounded.QueueMusic, onClick = {
                expanded = false
                onPlayNextAll()
            })
            MenuItem("全部上传到云盘", icon = Icons.Rounded.CloudUpload, onClick = {
                expanded = false
                onUploadAll()
            })
            if (isUserPlaylist) {
                MenuItem("添加文件夹", icon = Icons.Rounded.CreateNewFolder, onClick = {
                    expanded = false
                    onAddFolder()
                })
                MenuItem("添加歌曲文件", icon = Icons.Rounded.AudioFile, onClick = {
                    expanded = false
                    onAddFiles()
                })
                if (folders.isNotEmpty()) {
                    SubMenuItem("移除文件夹", icon = Icons.Rounded.FolderOff) {
                        folders.forEach { folder ->
                            SimpleMenuItem(File(folder).name.ifBlank { folder }, onClick = {
                                expanded = false
                                onRemoveFolder(folder)
                            })
                        }
                    }
                }
                MenuItem("重命名", icon = Icons.Rounded.DriveFileRenameOutline, onClick = {
                    expanded = false
                    onRename()
                })
            }
            MenuItem(if (summary.isPinned) "取消置顶" else "置顶歌单", icon = Icons.Rounded.PushPin, onClick = {
                expanded = false
                onTogglePin()
            })
            MenuItem("重新扫描", icon = Icons.Rounded.Refresh, onClick = {
                expanded = false
                onRescan()
            })
            if (isUserPlaylist) {
                MenuDivider()
                MenuItem("移除歌单", icon = Icons.Rounded.Delete, danger = true, onClick = {
                    expanded = false
                    onRemove()
                })
            }
        }
    }
}

@Composable
private fun MenuScope.LocalTrackMenuItems(
    track: LocalTrack,
    isLiked: Boolean,
    dismiss: () -> Unit,
    onPlay: () -> Unit,
    onPlayNext: () -> Unit,
    onToggleLike: (() -> Unit)?,
    onEditTags: () -> Unit,
    onUpload: () -> Unit,
    onMatch: (() -> Unit)?,
    onRemove: (() -> Unit)?
) {
    val navigator = LocalDesktopNavigator.current
    MenuItem("播放", icon = Icons.Rounded.PlayArrow, onClick = {
        dismiss()
        onPlay()
    })
    MenuItem("下一首播放", icon = Icons.AutoMirrored.Rounded.QueueMusic, onClick = {
        dismiss()
        onPlayNext()
    })
    onToggleLike?.let { toggle ->
        MenuItem(
            if (isLiked) "取消喜欢" else "喜欢",
            icon = if (isLiked) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
            onClick = {
                dismiss()
                toggle()
            }
        )
    }
    MenuDivider()
    MenuItem("编辑歌曲信息", icon = Icons.Rounded.Edit, onClick = {
        dismiss()
        onEditTags()
    })
    onMatch?.let { match ->
        MenuItem(if (track.songId != null) "更改网易云匹配" else "匹配网易云歌曲", icon = Icons.Rounded.CloudSync, onClick = {
            dismiss()
            match()
        })
    }
    MenuItem("上传到云盘", icon = Icons.Rounded.CloudUpload, onClick = {
        dismiss()
        onUpload()
    })
    MenuItem("在文件夹中显示", icon = Icons.Rounded.FolderOpen, onClick = {
        dismiss()
        if (!revealInFileManager(File(track.path))) navigator.showMessage("文件不存在或无法打开")
    })
    MenuItem("复制歌曲信息", icon = Icons.Rounded.ContentCopy, onClick = {
        dismiss()
        val ok = copyTextToClipboard(songInfoText(track.title, track.artist))
        navigator.showMessage(if (ok) "已复制歌曲信息" else "复制失败")
    })
    onRemove?.let { remove ->
        MenuDivider()
        MenuItem("从歌单中移除", icon = Icons.Rounded.Delete, danger = true, onClick = {
            dismiss()
            remove()
        })
    }
}

package com.lin0721.linmusic.desktop.ui.cloud

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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.CloudSync
import androidx.compose.material.icons.rounded.CloudUpload
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.DriveFolderUpload
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lin0721.linmusic.core.model.Album
import com.lin0721.linmusic.core.model.Artist
import com.lin0721.linmusic.core.model.Track
import com.lin0721.linmusic.core.player.PlayMode
import com.lin0721.linmusic.core.player.PlaybackController
import com.lin0721.linmusic.desktop.data.cloud.CloudLibraryState
import com.lin0721.linmusic.desktop.ui.components.Cover
import com.lin0721.linmusic.desktop.ui.components.DesktopDialog
import com.lin0721.linmusic.desktop.ui.components.DesktopMenu
import com.lin0721.linmusic.desktop.ui.components.DialogButton
import com.lin0721.linmusic.desktop.ui.components.HoverScrollbarBox
import com.lin0721.linmusic.desktop.ui.components.LocalUiMotion
import com.lin0721.linmusic.desktop.ui.components.MenuDivider
import com.lin0721.linmusic.desktop.ui.components.MenuItem
import com.lin0721.linmusic.desktop.ui.components.uiSpring
import com.lin0721.linmusic.desktop.ui.components.uiTween
import com.lin0721.linmusic.desktop.ui.library.CLOUD_TITLE
import com.lin0721.linmusic.desktop.ui.local.SongMatchDialog
import com.lin0721.linmusic.desktop.ui.palette.FallbackCoverPalette
import com.lin0721.linmusic.desktop.ui.palette.extractCoverPaletteFromUrl
import com.lin0721.linmusic.desktop.ui.palette.headerCoverBackground
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
import com.lin0721.linmusic.desktop.ui.track.rememberTrackActions
import com.lin0721.linmusic.feature.cloud.domain.CloudSong
import com.lin0721.linmusic.feature.cloud.domain.formatFileSize
import com.lin0721.linmusic.feature.playlist.presentation.PlaylistSortKey
import com.lin0721.linmusic.feature.playlist.presentation.PlaylistSortOrder
import com.lin0721.linmusic.feature.playlist.presentation.filterTracks
import com.lin0721.linmusic.feature.playlist.presentation.sortTracks
import kotlinx.coroutines.launch
import java.util.IdentityHashMap

private const val HERO_DARKEN_FRACTION = 0.35f
private const val STICKY_BAR_ANIMATION_MS = 200
private val HeaderPadding = 24.dp
private val ActionBarVerticalPadding = 16.dp
private val PlayButtonSize = 56.dp
private val StickyBarHeight = 64.dp
private val QuotaBarWidth = 280.dp

// sortKey 以云盘 songId 为 id 用于筛选排序；display 以正版 id 为 id，喜欢、收藏、评论都认它
private class CloudRow(val sortKey: Track, val display: Track, val song: CloudSong)

private fun CloudSong.toRow(): CloudRow {
    val sortKey = toTrack(songId)
    return CloudRow(sortKey, sortKey.copy(id = playSongId), this)
}

private fun CloudSong.toTrack(id: Long): Track = Track(
    id = id,
    name = name,
    ar = artist.split(" / ").filter { it.isNotBlank() }.map { Artist(name = it) },
    al = Album(name = album, picUrl = coverUrl.orEmpty()),
    dt = durationMs
)

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun CloudPage(
    actions: CloudMusicActions,
    controller: PlaybackController,
    songActions: SongActionSources,
    modifier: Modifier = Modifier
) {
    val navigator = LocalDesktopNavigator.current
    val library = actions.library
    val state by library.state.collectAsState()
    LaunchedEffect(navigator.isLoggedIn) {
        if (navigator.isLoggedIn) library.ensureLoaded()
    }
    if (!navigator.isLoggedIn) {
        CenteredMessage("登录后即可查看和上传云盘歌曲", modifier) {
            TextButton(onClick = navigator.openLogin) { Text("登录", color = DesktopColors.TextPrimary) }
        }
        return
    }
    if (!state.loaded) {
        if (state.failed) {
            CenteredMessage("云盘加载失败", modifier) {
                TextButton(onClick = library::refresh) { Text("重试", color = DesktopColors.TextPrimary) }
            }
        } else {
            Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = DesktopColors.Accent)
            }
        }
        return
    }
    CloudSongList(state, actions, controller, songActions, modifier)
}

@Composable
private fun CenteredMessage(text: String, modifier: Modifier, action: @Composable () -> Unit) {
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(text, color = DesktopColors.TextGray, fontSize = 14.sp)
            action()
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun CloudSongList(
    state: CloudLibraryState,
    actions: CloudMusicActions,
    controller: PlaybackController,
    songActions: SongActionSources,
    modifier: Modifier
) {
    val navigator = LocalDesktopNavigator.current
    val scope = rememberCoroutineScope()
    val library = actions.library
    val nowPlaying by controller.nowPlaying.collectAsState()
    val playMode by controller.playMode.collectAsState()
    val playWhenReady by controller.playWhenReady.collectAsState()
    val playContext by controller.playContext.collectAsState()
    val likedSongIds by songActions.likes.likedSongIds.collectAsState()
    val collectState by songActions.collect.state.collectAsState()

    var query by remember { mutableStateOf("") }
    var order by remember { mutableStateOf(PlaylistSortOrder()) }
    var deleting by remember { mutableStateOf<CloudSong?>(null) }
    var matching by remember { mutableStateOf<CloudSong?>(null) }

    val rows = remember(state.songs) { state.songs.map { it.toRow() } }
    val addedAt = remember(state.songs) { state.songs.associate { it.songId to it.addedAtMs } }
    val shown = remember(rows, query, order) {
        val byId = rows.associateBy { it.sortKey.id }
        sortTracks(filterTracks(rows.map { it.sortKey }, query), order, addedAt).mapNotNull { byId[it.id] }
    }
    // 同一首正版歌可能对应多条云盘记录，菜单回调按行内 Track 实例找回对应记录
    val songsByTrack = remember(rows) { IdentityHashMap<Track, CloudSong>().apply { rows.forEach { put(it.display, it.song) } } }
    val isThisContext = playContext == CloudMusicActions.PLAY_CONTEXT
    val isShuffleActive = playMode == PlayMode.SHUFFLE

    val playFrom: (CloudSong) -> Unit = { song ->
        val list = shown.map { it.song }
        if (isShuffleActive) {
            actions.play(listOf(song) + list.filterNot { it.songId == song.songId }.shuffled(), 0)
        } else {
            actions.play(list, list.indexOfFirst { it.songId == song.songId })
        }
    }
    val onPlayClick: () -> Unit = {
        when {
            isThisContext -> controller.togglePlayPause()
            shown.isEmpty() -> navigator.showMessage("云盘里还没有歌曲")
            else -> {
                val list = shown.map { it.song }
                actions.play(if (isShuffleActive) list.shuffled() else list, 0)
            }
        }
    }
    val trackActions = rememberTrackActions(
        likedSongIds = likedSongIds,
        collectState = collectState,
        onToggleLike = { songId, like ->
            scope.launch {
                songActions.likes.likeSong(songId, like).collect { result ->
                    result.onFailure { navigator.showMessage("操作失败") }
                }
            }
        },
        onPlayNext = { track ->
            songsByTrack[track]?.let { actions.addToPlayNext(listOf(it)) }
            navigator.showMessage("已添加至下一首播放")
        },
        onPrepareCollect = { songId ->
            scope.launch {
                songActions.collect.prepare(songId, songActions.likes.likedSongIds.value) { navigator.showMessage(it) }
            }
        },
        onSaveCollect = { songId, items ->
            scope.launch {
                songActions.collect.save(
                    songId = songId,
                    items = items,
                    likedSongIds = songActions.likes.likedSongIds.value,
                    onToast = { navigator.showMessage(it) },
                    onLikedChanged = { songActions.likes.syncLikedSongIds(it) }
                )
            }
        },
        onCreateAndAdd = { name, songId ->
            scope.launch {
                songActions.collect.createAndAdd(name, songId, songActions.likes.likedSongIds.value) { navigator.showMessage(it) }
            }
        },
        extraMenuItems = { track, dismiss ->
            val song = songsByTrack[track]
            if (song != null) {
                MenuDivider()
                MenuItem(if (song.isUnmatched) "匹配网易云歌曲" else "纠正匹配", icon = Icons.Rounded.CloudSync, onClick = {
                    dismiss()
                    matching = song
                })
                MenuItem("从云盘删除", icon = Icons.Rounded.Delete, danger = true, onClick = {
                    dismiss()
                    deleting = song
                })
            }
        }
    )

    val heroCover = remember(state.songs) { state.songs.firstNotNullOfOrNull { it.coverUrl } }
    var heroBase by remember { mutableStateOf(FallbackCoverPalette.base) }
    LaunchedEffect(heroCover) {
        heroCover?.let { heroBase = extractCoverPaletteFromUrl(it).base }
    }
    val heroColor by animateColorAsState(headerCoverBackground(heroBase, HERO_DARKEN_FRACTION), uiSpring(), label = "cloudHero")

    val listState = rememberLazyListState()
    val density = LocalDensity.current
    val barHeightPx = with(density) { StickyBarHeight.roundToPx() }
    val playButtonBottomPx = with(density) { (ActionBarVerticalPadding + PlayButtonSize).roundToPx() }
    // 恢复页面现场时列表直接停在中间，大标题不会再测一次，高度要随现场保存
    var heroHeightPx by rememberSaveable { mutableIntStateOf(0) }
    var headerHeightPx by rememberSaveable { mutableIntStateOf(0) }
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
                        CloudHero(state, heroCover)
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
                        CloudMoreMenu(
                            onPlayNextAll = {
                                actions.addToPlayNext(shown.map { it.song })
                                navigator.showMessage(if (shown.isEmpty()) "云盘里还没有歌曲" else "已添加至下一首播放")
                            },
                            onRefresh = library::refresh
                        )
                        Spacer(Modifier.weight(1f))
                        PlaylistSearchBox(query) { query = it }
                        PlaylistSortMenu(order, showAdded = true) { key ->
                            order = if (key == PlaylistSortKey.CUSTOM) PlaylistSortOrder() else PlaylistSortOrder(key, true)
                        }
                    }
                    Row(
                        Modifier.padding(start = HeaderPadding, end = HeaderPadding, bottom = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        PlaylistPill(Icons.Rounded.CloudUpload, "上传歌曲") { uploadFilesFromPicker(scope, actions, navigator) }
                        PlaylistPill(Icons.Rounded.DriveFolderUpload, "上传文件夹") { uploadFolderFromPicker(scope, actions, navigator) }
                        if (state.loading) {
                            Spacer(Modifier.width(4.dp))
                            CircularProgressIndicator(Modifier.size(14.dp), color = DesktopColors.TextGray, strokeWidth = 2.dp)
                            Text("正在加载全部歌曲", color = DesktopColors.TextGray, fontSize = 12.sp)
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
                    TrackColumnHeader(order, showAdded = true) { order = order.toggled(it) }
                }
            }
            itemsIndexed(shown, key = { _, row -> row.song.songId }) { index, row ->
                TrackRow(
                    index = index,
                    track = row.display,
                    isCurrent = nowPlaying?.songId == row.song.playSongId,
                    onPlay = { playFrom(row.song) },
                    modifier = Modifier.padding(horizontal = 16.dp)
                        .then(if (LocalUiMotion.current.enabled) Modifier.animateItem() else Modifier),
                    actions = trackActions,
                    addedAtText = row.song.addedAtText
                )
            }
            if (shown.isEmpty()) {
                item(key = "empty") {
                    Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                        Text(
                            if (query.isNotBlank()) "未找到匹配的歌曲" else "云盘里还没有歌曲，上传本地音乐即可随处收听",
                            color = DesktopColors.TextGray,
                            fontSize = 14.sp
                        )
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
                PlaylistStickyBar(CLOUD_TITLE, heroColor, showPlayButton, isThisContext && playWhenReady, listState, onPlayClick)
            }
        }
    }

    deleting?.let { song ->
        var busy by remember(song.songId) { mutableStateOf(false) }
        DesktopDialog(
            title = "从云盘删除",
            onDismiss = { if (!busy) deleting = null },
            width = 360.dp,
            actions = {
                DialogButton("取消", { deleting = null }, enabled = !busy)
                DialogButton("删除", {
                    busy = true
                    scope.launch {
                        val result = library.delete(song)
                        navigator.showMessage(if (result.isSuccess) "已从云盘删除" else "删除失败")
                        deleting = null
                    }
                }, danger = true, loading = busy)
            }
        ) {
            Text("确定要删除「${song.name}」吗？删除后无法恢复。", color = DesktopColors.TextGray, fontSize = 13.sp)
        }
    }
    matching?.let { song ->
        var busy by remember(song.songId) { mutableStateOf(false) }
        SongMatchDialog(
            key = song.songId,
            title = if (song.isUnmatched) "匹配网易云歌曲" else "纠正匹配",
            subtitle = "${song.name} · ${song.artist}",
            initialKeyword = "${song.name} ${song.artist}".trim(),
            currentSongId = song.playSongId.takeUnless { song.isUnmatched },
            search = actions::searchCandidates,
            onPick = { target ->
                if (!busy) {
                    busy = true
                    scope.launch {
                        val result = library.match(song, target)
                        navigator.showMessage(if (result.isSuccess) "匹配成功" else "匹配失败")
                        matching = null
                    }
                }
            },
            onDetach = null,
            onDismiss = { if (!busy) matching = null },
            busy = busy
        )
    }
}

@Composable
private fun CloudHero(state: CloudLibraryState, cover: String?) {
    val quota = state.quota
    Row(Modifier.fillMaxWidth().padding(HeaderPadding), verticalAlignment = Alignment.Bottom) {
        Cover(cover, 200.dp, shape = RoundedCornerShape(6.dp))
        Column(Modifier.weight(1f).padding(start = HeaderPadding)) {
            Text("云盘", color = DesktopColors.TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            Text(
                CLOUD_TITLE,
                color = DesktopColors.TextPrimary,
                fontSize = 48.sp,
                lineHeight = 56.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(vertical = 4.dp)
            )
            if (quota != null) {
                Text(
                    "${quota.totalCount} 首歌曲，已用 ${formatFileSize(quota.usedBytes)} / ${formatFileSize(quota.maxBytes)}",
                    color = DesktopColors.TextGray,
                    fontSize = 14.sp
                )
                LinearProgressIndicator(
                    progress = { quota.usedRatio },
                    modifier = Modifier.padding(top = 8.dp).widthIn(max = QuotaBarWidth).fillMaxWidth().height(4.dp),
                    color = DesktopColors.AccentText,
                    trackColor = DesktopColors.SurfaceLight
                )
            }
        }
    }
}

@Composable
private fun CloudMoreMenu(onPlayNextAll: () -> Unit, onRefresh: () -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        ActionIcon(Icons.Rounded.MoreHoriz, "更多") { expanded = true }
        DesktopMenu(expanded = expanded, onDismiss = { expanded = false }) {
            MenuItem("全部加入下一首播放", icon = Icons.AutoMirrored.Rounded.QueueMusic, onClick = {
                expanded = false
                onPlayNextAll()
            })
            MenuItem("刷新", icon = Icons.Rounded.Refresh, onClick = {
                expanded = false
                onRefresh()
            })
        }
    }
}

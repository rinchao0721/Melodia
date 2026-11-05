package com.lin0721.linmusic.desktop.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
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
import com.lin0721.linmusic.core.model.Track
import com.lin0721.linmusic.core.player.PlaybackController
import com.lin0721.linmusic.desktop.ui.components.Cover
import com.lin0721.linmusic.desktop.ui.components.DesktopMenu
import com.lin0721.linmusic.desktop.ui.components.HoverScrollbarBox
import com.lin0721.linmusic.desktop.ui.components.SimpleMenuItem
import com.lin0721.linmusic.desktop.ui.components.onDoubleClick
import com.lin0721.linmusic.desktop.ui.components.sizedCoverUrl
import com.lin0721.linmusic.desktop.ui.shell.LocalDesktopNavigator
import com.lin0721.linmusic.desktop.ui.theme.DesktopColors
import com.lin0721.linmusic.desktop.ui.track.TrackRow
import com.lin0721.linmusic.feature.music.domain.StyleArtistItem
import com.lin0721.linmusic.feature.music.domain.StyleHead
import com.lin0721.linmusic.feature.music.domain.StyleSort
import com.lin0721.linmusic.feature.music.ui.StyleDetailData
import com.lin0721.linmusic.feature.music.ui.StyleDetailUiState
import com.lin0721.linmusic.feature.music.ui.StyleDetailViewModel
import java.time.Instant
import java.time.ZoneId

private val EdgePadding = 24.dp
private val HeroHeight = 280.dp
private val ArtistAvatarSize = 140.dp
private val SectionGap = 28.dp

// 距离列表底部还剩几项时加载下一页曲目
private const val SONG_LOAD_MORE_THRESHOLD = 10

// 曲风详情页：色块头图 → 播放与二级标签 → 歌单 / 专辑 / 歌手货架 → 可翻页曲目表
@Composable
fun StyleDetailPage(
    tagId: Long,
    name: String,
    viewModel: StyleDetailViewModel,
    controller: PlaybackController,
    modifier: Modifier = Modifier
) {
    LaunchedEffect(tagId) {
        viewModel.load(tagId)
    }
    val uiState by viewModel.uiState.collectAsState()
    val nowPlaying by controller.nowPlaying.collectAsState()

    when (val state = uiState) {
        StyleDetailUiState.Loading -> HomeTabLoading(modifier.fillMaxSize())
        is StyleDetailUiState.Error -> HomeTabError(state.message, viewModel::retry, modifier.fillMaxSize())
        is StyleDetailUiState.Success -> {
            // 单例 ViewModel 在切换曲风的瞬间仍持有上一个曲风，避免闪现旧内容
            if (state.data.tagId != tagId) {
                HomeTabLoading(modifier.fillMaxSize())
                return
            }
            StyleDetailContent(
                data = state.data,
                fallbackName = name,
                nowPlayingSongId = nowPlaying?.songId,
                viewModel = viewModel,
                modifier = modifier
            )
        }
    }
}

@Composable
private fun StyleDetailContent(
    data: StyleDetailData,
    fallbackName: String,
    nowPlayingSongId: Long?,
    viewModel: StyleDetailViewModel,
    modifier: Modifier
) {
    val navigator = LocalDesktopNavigator.current
    val accent = data.head?.colorHex.toStyleColor()
    val listState = rememberLazyListState()
    val shouldLoadMore by remember(data) {
        derivedStateOf {
            val lastVisible = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            data.hasMoreSongs && !data.isLoadingMore && !data.isContentLoading && !data.isSongsLoading &&
                lastVisible >= listState.layoutInfo.totalItemsCount - SONG_LOAD_MORE_THRESHOLD
        }
    }
    LaunchedEffect(shouldLoadMore) {
        if (shouldLoadMore) viewModel.loadMoreSongs()
    }

    HoverScrollbarBox(listState) {
        LazyColumn(state = listState, modifier = modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
            item(key = "hero") { StyleHero(data.head, fallbackName, accent) }
            item(key = "actions") {
                Column(
                    Modifier.fillMaxWidth().background(Brush.verticalGradient(listOf(accent.copy(alpha = 0.32f), Color.Transparent)))
                        .padding(top = 24.dp, bottom = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(20.dp)
                ) {
                    ActionRow(
                        favourite = data.head?.favouriteSong,
                        canPlay = data.songs.isNotEmpty(),
                        onPlayAll = { viewModel.playSongAt(0) },
                        onPlayFavourite = viewModel::playFavourite
                    )
                    if (data.children.isNotEmpty()) {
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = EdgePadding),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            item(key = "all") { OutlineChip("全部", data.selectedChildId == null) { viewModel.selectChild(null) } }
                            items(data.children, key = { it.id }) { child ->
                                OutlineChip(child.name, data.selectedChildId == child.id) { viewModel.selectChild(child.id) }
                            }
                        }
                    }
                }
            }

            if (data.isContentLoading) {
                item(key = "content_loading") { HomeTabLoading(Modifier.fillMaxWidth().height(200.dp)) }
                return@LazyColumn
            }

            data.playlists.takeIf { it.isNotEmpty() }?.let { playlists ->
                item(key = "playlists") {
                    Box(Modifier.padding(top = SectionGap)) {
                        ShelfRow("热门歌单", playlists) { playlist ->
                            CardTile(playlist.coverUrl, playlist.name, playlist.playCount.toPlayCountText()) {
                                navigator.openPlaylist(playlist.id, playlist.name)
                            }
                        }
                    }
                }
            }
            data.albums.takeIf { it.isNotEmpty() }?.let { albums ->
                item(key = "albums") {
                    Box(Modifier.padding(top = SectionGap)) {
                        ShelfRow("热门专辑", albums) { album ->
                            val year = album.publishTime.takeIf { it > 0 }
                                ?.let { Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()).year.toString() }
                            CardTile(album.coverUrl, album.name, listOfNotNull(year, album.artistName.ifBlank { null }).joinToString(" · ")) {
                                navigator.openAlbum(album.id, album.name)
                            }
                        }
                    }
                }
            }
            data.artists.takeIf { it.isNotEmpty() }?.let { artists ->
                item(key = "artists") {
                    Box(Modifier.padding(top = SectionGap)) { ArtistShelf(artists, navigator.openArtist) }
                }
            }

            item(key = "songs_title") {
                Column(Modifier.padding(top = SectionGap)) {
                    Row(
                        Modifier.fillMaxWidth().padding(end = EdgePadding),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(Modifier.weight(1f)) { SectionTitle("歌曲") }
                        SortMenu(data.sort, viewModel::selectSort)
                    }
                    SongTableHeader()
                }
            }
            if (data.isSongsLoading) {
                item(key = "songs_loading") { HomeTabLoading(Modifier.fillMaxWidth().height(200.dp)) }
            } else {
                itemsIndexed(data.songs, key = { index, track -> "${track.id}_$index" }) { index, track ->
                    TrackRow(
                        index = index,
                        track = track,
                        isCurrent = nowPlayingSongId == track.id,
                        onPlay = { viewModel.playSongAt(index) },
                        modifier = Modifier.padding(horizontal = 16.dp)
                    )
                }
                if (data.isLoadingMore) {
                    item(key = "loading_more") {
                        Box(Modifier.fillMaxWidth().padding(12.dp), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(color = DesktopColors.Accent)
                        }
                    }
                }
            }
        }
    }
}

// 曲风色块铺底，封面淡化叠在上面；英文名作水印
@Composable
private fun StyleHero(head: StyleHead?, fallbackName: String, accent: Color) {
    Box(
        Modifier.fillMaxWidth().height(HeroHeight)
            .background(Brush.verticalGradient(listOf(accent, accent.copy(alpha = 0.55f))))
    ) {
        sizedCoverUrl(head?.coverUrl, 1000)?.let {
            AsyncImage(
                model = it,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                alpha = 0.35f,
                modifier = Modifier.fillMaxSize()
            )
        }
        head?.enName?.takeIf { it.isNotBlank() }?.let { enName ->
            Text(
                enName,
                color = Color.White.copy(alpha = 0.15f),
                fontSize = 48.sp,
                fontWeight = FontWeight.ExtraBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.align(Alignment.TopEnd).padding(24.dp)
            )
        }
        Column(Modifier.align(Alignment.BottomStart).padding(EdgePadding).fillMaxWidth(0.85f)) {
            Text("曲风", color = Color.White.copy(alpha = 0.85f), fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            Text(
                head?.name ?: fallbackName,
                color = Color.White,
                fontSize = 56.sp,
                lineHeight = 64.sp,
                fontWeight = FontWeight.ExtraBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            val subtitle = head?.desc?.takeIf { it.isNotBlank() } ?: head?.realStatsOrEmpty().orEmpty()
            if (subtitle.isNotBlank()) {
                Text(
                    subtitle,
                    color = Color.White.copy(alpha = 0.85f),
                    fontSize = 14.sp,
                    lineHeight = 20.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
        }
    }
}

// 带 + 的是服务端封顶值（999999+ / 1000+），各曲风都一样，展示了等于没说
private fun StyleHead.realStatsOrEmpty(): String = listOfNotNull(
    songNum.takeIf { it.isNotBlank() && !it.endsWith("+") }?.let { "$it 首歌" },
    artistNum.takeIf { it.isNotBlank() && !it.endsWith("+") }?.let { "$it 位歌手" }
).joinToString(" · ")

@Composable
private fun ActionRow(favourite: Track?, canPlay: Boolean, onPlayAll: () -> Unit, onPlayFavourite: () -> Unit) {
    Row(
        Modifier.padding(horizontal = EdgePadding).fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(20.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier.size(56.dp).clip(CircleShape)
                .background(if (canPlay) DesktopColors.Accent else DesktopColors.SurfaceLight)
                .pointerHoverIcon(PointerIcon.Hand).clickable(enabled = canPlay, onClick = onPlayAll),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Rounded.PlayArrow, "播放全部", tint = DesktopColors.TextPrimary, modifier = Modifier.size(32.dp))
        }
        favourite?.let { FavouriteChip(it, onPlayFavourite) }
    }
}

// 「你在此曲风最爱」，未登录时没有；双击起播
@Composable
private fun FavouriteChip(track: Track, onPlay: () -> Unit) {
    Row(
        Modifier.clip(RoundedCornerShape(8.dp)).background(DesktopColors.Fill)
            .onDoubleClick(onPlay).padding(8.dp).padding(end = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Cover(track.al.picUrl, 40.dp)
        Column(Modifier.padding(start = 10.dp).width(220.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.Favorite, null, tint = DesktopColors.Accent, modifier = Modifier.size(12.dp))
                Text(
                    "你在此曲风最爱",
                    color = DesktopColors.AccentText,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(start = 4.dp)
                )
            }
            Text(
                "${track.name} · ${track.ar.joinToString("/") { it.name }}",
                color = DesktopColors.TextPrimary,
                fontSize = 13.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun SortMenu(sort: StyleSort, onSelect: (StyleSort) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        Row(
            Modifier.height(32.dp).clip(RoundedCornerShape(16.dp)).pointerHoverIcon(PointerIcon.Hand)
                .clickable { expanded = true }.padding(horizontal = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(sort.label(), color = DesktopColors.TextGray, fontSize = 13.sp)
            Icon(Icons.Rounded.KeyboardArrowDown, "排序方式", tint = DesktopColors.TextGray, modifier = Modifier.size(18.dp))
        }
        DesktopMenu(expanded = expanded, onDismiss = { expanded = false }, offset = DpOffset(0.dp, 4.dp)) {
            StyleSort.entries.forEach { option ->
                SimpleMenuItem(
                    text = option.label(),
                    selected = option == sort,
                    trailing = if (option == sort) {
                        { Icon(Icons.Rounded.Check, null, tint = DesktopColors.Accent, modifier = Modifier.size(16.dp)) }
                    } else {
                        null
                    },
                    onClick = {
                        expanded = false
                        onSelect(option)
                    }
                )
            }
        }
    }
}

private fun StyleSort.label(): String = when (this) {
    StyleSort.Hot -> "热门"
    StyleSort.Latest -> "最新"
}

// 列宽与 TrackRow 一一对应：序号 32 + 封面 16+40 + 标题 0.45 + 专辑 0.35 + 时长 48
@Composable
private fun SongTableHeader() {
    Column(Modifier.padding(horizontal = 16.dp).padding(top = 12.dp)) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("#", color = DesktopColors.TextGray, fontSize = 13.sp, textAlign = TextAlign.End, modifier = Modifier.width(32.dp))
            Spacer(Modifier.width(56.dp))
            Text("标题", color = DesktopColors.TextGray, fontSize = 13.sp, modifier = Modifier.weight(0.45f).padding(start = 12.dp))
            Text("专辑", color = DesktopColors.TextGray, fontSize = 13.sp, modifier = Modifier.weight(0.35f).padding(horizontal = 12.dp))
            Box(Modifier.width(48.dp), contentAlignment = Alignment.CenterEnd) {
                Icon(Icons.Rounded.Schedule, "时长", tint = DesktopColors.TextGray, modifier = Modifier.size(16.dp))
            }
        }
        HorizontalDivider(color = DesktopColors.SurfaceLight, modifier = Modifier.padding(bottom = 8.dp))
    }
}

// 代表歌手：圆形头像，点击进歌手页
@Composable
private fun ArtistShelf(artists: List<StyleArtistItem>, onClick: (id: Long, name: String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SectionTitle("代表歌手")
        LazyRow(
            contentPadding = PaddingValues(horizontal = EdgePadding),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            items(artists, key = { it.id }) { artist ->
                Column(
                    Modifier.width(ArtistAvatarSize).clip(RoundedCornerShape(6.dp)).clickable { onClick(artist.id, artist.name) },
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Cover(artist.picUrl, ArtistAvatarSize, shape = CircleShape)
                    Text(
                        artist.name,
                        color = DesktopColors.TextPrimary,
                        fontSize = 14.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                    if (artist.musicSize > 0) {
                        Text("${artist.musicSize} 首", color = DesktopColors.TextGray, fontSize = 12.sp, maxLines = 1)
                    }
                }
            }
        }
    }
}

private fun Long.toPlayCountText(): String = when {
    this >= 100_000_000 -> "${this / 100_000_000} 亿次播放"
    this >= 10_000 -> "${this / 10_000} 万次播放"
    this <= 0 -> ""
    else -> "$this 次播放"
}

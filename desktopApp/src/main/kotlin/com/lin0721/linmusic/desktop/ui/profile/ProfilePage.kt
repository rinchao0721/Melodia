package com.lin0721.linmusic.desktop.ui.profile

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lin0721.linmusic.core.format.formatCompactCount
import com.lin0721.linmusic.core.player.PlaybackController
import com.lin0721.linmusic.core.player.QueueItem
import com.lin0721.linmusic.core.ui.theme.paneBackdrop
import com.lin0721.linmusic.desktop.ui.artist.TabBar
import com.lin0721.linmusic.desktop.ui.components.Cover
import com.lin0721.linmusic.desktop.ui.components.HoverScrollbarBox
import com.lin0721.linmusic.desktop.ui.components.onDoubleClick
import com.lin0721.linmusic.desktop.ui.components.uiSpring
import com.lin0721.linmusic.desktop.ui.palette.FallbackCoverPalette
import com.lin0721.linmusic.desktop.ui.palette.extractCoverPaletteFromUrl
import com.lin0721.linmusic.desktop.ui.palette.themedCoverBackground
import com.lin0721.linmusic.desktop.ui.shell.LocalDesktopNavigator
import com.lin0721.linmusic.desktop.ui.theme.DesktopColors
import com.lin0721.linmusic.feature.profile.domain.ProfileListenRankItem
import com.lin0721.linmusic.feature.profile.domain.ProfilePlaylistInfo
import com.lin0721.linmusic.feature.profile.ui.FollowListMode
import com.lin0721.linmusic.feature.profile.ui.ProfileUiState
import com.lin0721.linmusic.feature.profile.ui.ProfileViewModel

private const val HERO_DARKEN_FRACTION = 0.35f

// 与 ProfileViewModel.selectedTab 的下标一致
private const val TAB_PLAYLISTS = 0
private const val TAB_RANK = 1
private val ContentPadding = 24.dp
private val PlaylistCardWidth = 168.dp
private val AvatarSize = 160.dp
private val RankPlayCountWidth = 80.dp

@Composable
fun ProfilePage(
    uid: Long,
    viewModel: ProfileViewModel,
    controller: PlaybackController,
    modifier: Modifier = Modifier
) {
    LaunchedEffect(uid) { viewModel.loadIfNeeded(uid) }
    val uiState by viewModel.uiState.collectAsState()

    when (val state = uiState) {
        ProfileUiState.Loading -> Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = DesktopColors.Accent)
        }
        is ProfileUiState.Error -> Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(state.message, color = DesktopColors.TextGray)
                TextButton(onClick = viewModel::retry) { Text("重试", color = DesktopColors.TextPrimary) }
            }
        }
        is ProfileUiState.Success -> ProfileContent(state, viewModel, controller, modifier)
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ProfileContent(
    state: ProfileUiState.Success,
    viewModel: ProfileViewModel,
    controller: PlaybackController,
    modifier: Modifier
) {
    val navigator = LocalDesktopNavigator.current
    val info = state.userInfo
    val nowPlaying by controller.nowPlaying.collectAsState()
    val listState = rememberLazyListState()

    var heroBase by remember(info.uid) { mutableStateOf(FallbackCoverPalette.base) }
    LaunchedEffect(info.avatarUrl) {
        if (info.avatarUrl.isNotBlank()) heroBase = extractCoverPaletteFromUrl(info.avatarUrl).base
    }
    val heroColor by animateColorAsState(themedCoverBackground(heroBase, HERO_DARKEN_FRACTION), uiSpring(), label = "profileHero")

    val playRank: (Int) -> Unit = { index ->
        val items = state.rankItems.map { QueueItem(it.songId, it.songName, it.artistName, it.albumCoverUrl) }
        controller.playQueue(items, index, "${info.nickname}的听歌排行")
    }

    BoxWithConstraints(modifier.fillMaxSize()) {
        val columns = ((maxWidth - ContentPadding * 2) / (PlaylistCardWidth + 16.dp)).toInt().coerceAtLeast(2)
        HoverScrollbarBox(listState) {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 24.dp)
            ) {
                item(key = "header") {
                    Column(Modifier.fillMaxWidth().background(Brush.verticalGradient(listOf(heroColor, DesktopColors.PageFill)))) {
                        ProfileHero(
                            state = state,
                            onFollowsClick = { navigator.openFollowList(info.uid, FollowListMode.FOLLOWS) },
                            onFollowedsClick = { navigator.openFollowList(info.uid, FollowListMode.FOLLOWEDS) }
                        )
                        if (!state.isSelf) {
                            FollowButton(
                                followed = info.isFollowedByMe,
                                modifier = Modifier.padding(start = ContentPadding, end = ContentPadding, bottom = 20.dp)
                            ) {
                                if (navigator.isLoggedIn) viewModel.toggleFollow() else navigator.showMessage("请先登录账号")
                            }
                        } else {
                            Box(Modifier.height(20.dp))
                        }
                    }
                }

                // 标签吸顶，歌单再多也能随时切到听歌排行
                stickyHeader(key = "tabs") {
                    TabBar(
                        tabs = listOf(TAB_PLAYLISTS, TAB_RANK),
                        selected = state.selectedTab,
                        label = { if (it == TAB_PLAYLISTS) "歌单" else "听歌排行" },
                        onSelect = viewModel::selectTab,
                        modifier = Modifier.fillMaxWidth().paneBackdrop(DesktopColors.Pane)
                            .padding(horizontal = ContentPadding, vertical = 12.dp)
                    )
                }

                if (state.selectedTab == TAB_PLAYLISTS) {
                    val rows = state.playlists.chunked(columns)
                    items(rows.size, key = { "playlist_row_$it" }) { rowIndex ->
                        Row(
                            Modifier.fillMaxWidth().padding(horizontal = ContentPadding, vertical = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            rows[rowIndex].forEach { playlist ->
                                PlaylistCard(playlist) { navigator.openPlaylist(playlist.id, playlist.name) }
                            }
                        }
                        if (rowIndex == rows.lastIndex && state.playlistsHasMore) {
                            LaunchedEffect(rows.size) { viewModel.loadMorePlaylists() }
                        }
                    }
                    if (state.playlistsLoadingMore) item(key = "playlists_loading") { LoadingRow() }
                    if (state.playlistsLoaded && state.playlists.isEmpty()) {
                        item(key = "playlists_empty") { EmptyRow("暂无歌单") }
                    }
                } else {
                    item(key = "rank_range") {
                        TabBar(
                            tabs = listOf(0, 1),
                            selected = state.rankSubTab,
                            label = { if (it == 0) "所有时间" else "最近一周" },
                            onSelect = viewModel::selectRankSubTab,
                            modifier = Modifier.padding(horizontal = ContentPadding, vertical = 8.dp),
                            small = true
                        )
                    }
                    if (state.rankLoading) {
                        item(key = "rank_loading") { LoadingRow() }
                    } else if (state.rankLoaded && state.rankItems.isEmpty()) {
                        item(key = "rank_empty") { EmptyRow("暂无听歌排行，对方可能没有公开") }
                    } else {
                        items(state.rankItems.size, key = { "rank_${state.rankItems[it].songId}_$it" }) { index ->
                            val item = state.rankItems[index]
                            RankRow(
                                index = index,
                                item = item,
                                isCurrent = nowPlaying?.songId == item.songId,
                                onPlay = { playRank(index) },
                                modifier = Modifier.padding(horizontal = 16.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ProfileHero(state: ProfileUiState.Success, onFollowsClick: () -> Unit, onFollowedsClick: () -> Unit) {
    val info = state.userInfo
    Row(Modifier.fillMaxWidth().padding(ContentPadding), verticalAlignment = Alignment.Bottom) {
        Cover(info.avatarUrl, AvatarSize, shape = CircleShape)
        Column(Modifier.padding(start = ContentPadding).weight(1f)) {
            Text("用户", color = DesktopColors.TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            Text(
                info.nickname,
                color = DesktopColors.TextPrimary,
                fontSize = 48.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(vertical = 4.dp)
            )
            if (info.signature.isNotBlank()) {
                Text(
                    info.signature,
                    color = DesktopColors.TextGray,
                    fontSize = 14.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
                StatText(info.followsCount.toLong(), "关注", onFollowsClick)
                StatText(info.followedsCount.toLong(), "粉丝", onFollowedsClick)
                StatText(info.playlistCount.toLong(), "歌单", null)
            }
        }
    }
}

@Composable
private fun StatText(count: Long, label: String, onClick: (() -> Unit)?) {
    Row(
        Modifier.clip(RoundedCornerShape(4.dp))
            .then(if (onClick != null) Modifier.pointerHoverIcon(PointerIcon.Hand).clickable(onClick = onClick) else Modifier),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(formatCompactCount(count), color = DesktopColors.TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.Bold)
        Text(" $label", color = DesktopColors.TextGray, fontSize = 14.sp)
    }
}

@Composable
internal fun FollowButton(followed: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(
        modifier.clip(RoundedCornerShape(16.dp))
            .border(1.dp, if (followed) DesktopColors.TextGray else DesktopColors.TextPrimary, RoundedCornerShape(16.dp))
            .pointerHoverIcon(PointerIcon.Hand).clickable(onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            if (followed) "已关注" else "关注",
            color = DesktopColors.TextPrimary,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun PlaylistCard(playlist: ProfilePlaylistInfo, onClick: () -> Unit) {
    Column(
        Modifier.width(PlaylistCardWidth).clip(RoundedCornerShape(6.dp)).pointerHoverIcon(PointerIcon.Hand)
            .clickable(onClick = onClick).padding(8.dp)
    ) {
        Cover(playlist.coverImgUrl, PlaylistCardWidth - 16.dp, shape = RoundedCornerShape(4.dp))
        Text(
            playlist.name,
            color = DesktopColors.TextPrimary,
            fontSize = 14.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 8.dp)
        )
        Text(
            "${playlist.trackCount} 首 · ${formatCompactCount(playlist.playCount)} 次播放",
            color = DesktopColors.TextGray,
            fontSize = 12.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun RankRow(index: Int, item: ProfileListenRankItem, isCurrent: Boolean, onPlay: () -> Unit, modifier: Modifier = Modifier) {
    var hovered by remember { mutableStateOf(false) }
    Row(
        modifier.fillMaxWidth().clip(RoundedCornerShape(4.dp))
            .background(if (hovered) DesktopColors.Hover else Color.Transparent)
            .onPointerEvent(PointerEventType.Enter) { hovered = true }
            .onPointerEvent(PointerEventType.Exit) { hovered = false }
            .onDoubleClick(onPlay)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.width(32.dp).height(32.dp), contentAlignment = Alignment.CenterEnd) {
            if (hovered) {
                Box(
                    Modifier.fillMaxSize().pointerHoverIcon(PointerIcon.Hand).clickable(onClick = onPlay),
                    contentAlignment = Alignment.CenterEnd
                ) {
                    Icon(Icons.Rounded.PlayArrow, "播放${item.songName}", tint = DesktopColors.TextPrimary, modifier = Modifier.size(22.dp))
                }
            } else {
                Text(
                    "${index + 1}",
                    color = if (isCurrent) DesktopColors.AccentText else DesktopColors.TextGray,
                    fontSize = 14.sp,
                    textAlign = TextAlign.End
                )
            }
        }
        Cover(item.albumCoverUrl, 40.dp, modifier = Modifier.padding(start = 16.dp))
        Column(Modifier.weight(1f).padding(start = 12.dp)) {
            Text(
                item.songName,
                color = if (isCurrent) DesktopColors.AccentText else DesktopColors.TextPrimary,
                fontSize = 15.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(item.artistName, color = DesktopColors.TextGray, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Text(
            "${item.playCount} 次",
            color = DesktopColors.TextGray,
            fontSize = 13.sp,
            textAlign = TextAlign.End,
            modifier = Modifier.width(RankPlayCountWidth)
        )
    }
}

@Composable
private fun LoadingRow() {
    Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(color = DesktopColors.Accent, modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
    }
}

@Composable
private fun EmptyRow(text: String) {
    Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
        Text(text, color = DesktopColors.TextGray, fontSize = 14.sp)
    }
}

package com.lin0721.linmusic.desktop.ui.nowplaying

import kotlinx.coroutines.flow.flowOf
import com.lin0721.linmusic.core.preferences.PlayerCardEntries
import com.lin0721.linmusic.core.plugin.PluginCardView
import com.lin0721.linmusic.core.plugin.LocalPluginCards
import com.lin0721.linmusic.core.plugin.LayoutNodeType
import com.lin0721.linmusic.core.plugin.LocalLayoutCoverColors
import com.lin0721.linmusic.core.plugin.LocalLayoutEnvironment
import com.lin0721.linmusic.core.plugin.PlayerBindingState
import com.lin0721.linmusic.core.plugin.LocalPlayerLayouts
import androidx.compose.runtime.CompositionLocalProvider
import com.lin0721.linmusic.core.plugin.PlayerLayoutArea
import com.lin0721.linmusic.core.plugin.PlayerLayoutNode
import com.lin0721.linmusic.core.plugin.PlayerLayoutSpec
import com.lin0721.linmusic.core.plugin.PlayerLayoutView
import com.lin0721.linmusic.core.plugin.layoutShape
import com.lin0721.linmusic.desktop.ui.player.PlayerBarParts
import com.lin0721.linmusic.desktop.ui.player.rememberPlaybackParts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import com.lin0721.linmusic.desktop.ui.listentogether.ListenTogetherBadge
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lin0721.linmusic.core.kugou.KugouArtistLink
import com.lin0721.linmusic.core.model.Artist
import com.lin0721.linmusic.core.platform.ArtistId
import com.lin0721.linmusic.core.platform.toDeterministicLegacyId
import com.lin0721.linmusic.core.player.NowPlaying
import com.lin0721.linmusic.core.player.PlaySource
import com.lin0721.linmusic.core.player.PlaybackController
import com.lin0721.linmusic.core.player.formatSleepTimerRemaining
import com.lin0721.linmusic.core.preferences.FullPlayerCard
import com.lin0721.linmusic.desktop.ui.components.Cover
import com.lin0721.linmusic.desktop.ui.components.DesktopTooltip
import com.lin0721.linmusic.desktop.ui.components.HoverReveal
import com.lin0721.linmusic.desktop.ui.components.HoverScrollbarBox
import com.lin0721.linmusic.desktop.ui.player.PersonalFmModePill
import com.lin0721.linmusic.desktop.ui.player.PersonalFmModePopup
import com.lin0721.linmusic.desktop.ui.shell.LocalDesktopNavigator
import com.lin0721.linmusic.desktop.ui.theme.DesktopColors
import com.lin0721.linmusic.desktop.ui.theme.DesktopDimens
import com.lin0721.linmusic.desktop.ui.track.CollectToPlaylistPopup
import com.lin0721.linmusic.desktop.ui.track.artistLinks
import com.lin0721.linmusic.feature.player.presentation.visibleInfoCards
import com.lin0721.linmusic.feature.player.ui.PlayerViewModel
import com.lin0721.linmusic.core.ui.icons.MelodiaIcons
import com.lin0721.linmusic.core.ui.icons.AddCircle
import com.lin0721.linmusic.core.ui.icons.CheckCircleFilled
import com.lin0721.linmusic.core.ui.icons.PanelCollapse
import com.lin0721.linmusic.core.ui.icons.ChevronRight
import com.lin0721.linmusic.core.ui.icons.Devices
import com.lin0721.linmusic.core.ui.icons.MoreHoriz
import com.lin0721.linmusic.core.ui.icons.Moon
import com.lin0721.linmusic.core.ui.icons.Phone

private val HeaderButtonSize = 32.dp
private val HeaderButtonOffset = 4.dp

@Composable
fun NowPlayingPanel(
    controller: PlaybackController,
    playerViewModel: PlayerViewModel,
    hovered: Boolean,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    onOpenComments: () -> Unit = {},
    onOpenLyricsView: () -> Unit = {},
    onOpenLyricsFullscreen: () -> Unit = {},
    onToggleDevices: (() -> Unit)? = null,
    devicesOpen: Boolean = false,
    remoteDeviceName: String? = null
) {
    val nowPlaying by controller.nowPlaying.collectAsState()
    val track = nowPlaying ?: return
    val detailState by playerViewModel.songDetailState.collectAsState()
    val currentLyricIndex by playerViewModel.currentLyricIndex.collectAsState()
    val isPlaying by controller.isPlaying.collectAsState()
    val podcast by playerViewModel.podcastState.collectAsState()

    val navigator = LocalDesktopNavigator.current
    val cardLayout by playerViewModel.fullPlayerCardLayout.collectAsState()
    val commentsState by playerViewModel.commentsState.collectAsState()
    val base = rememberCoverBase(track.artworkUri)

    val scrollState = rememberScrollState()
    HoverScrollbarBox(scrollState) {
        Column(modifier.fillMaxSize().verticalScroll(scrollState).padding(16.dp)) {
            PanelHeader(
                track = track,
                controller = controller,
                playerViewModel = playerViewModel,
                hovered = hovered,
                onClose = onClose,
                onToggleDevices = onToggleDevices,
                devicesOpen = devicesOpen,
                remoteDeviceName = remoteDeviceName
            )
            val pluginLayout = LocalPlayerLayouts.current[PlayerLayoutArea.DESKTOP_NOW_PLAYING]
            if (pluginLayout != null) {
                PluginNowPlayingHeader(pluginLayout, track, controller, playerViewModel)
            } else {
                BoxWithConstraints(Modifier.fillMaxWidth().padding(top = 8.dp)) {
                    Cover(track.artworkUri, maxWidth, shape = RoundedCornerShape(8.dp), requestSize = DesktopDimens.NowPlayingMaxWidth)
                }
                NowPlayingSongTitle(
                    track,
                    playerViewModel,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    modifier = Modifier.padding(top = 16.dp)
                )
                NowPlayingArtists(track, playerViewModel, 14.sp)
            }
            if (!podcast.isPodcast) {
                val cards = remember(detailState, cardLayout, commentsState) {
                    visibleInfoCards(detailState, cardLayout, SupportedInfoCards, commentsState)
                }
                val cardSource = LocalPluginCards.current?.source
                val cardEntries by (cardSource?.entries ?: remember { flowOf(emptyList()) }).collectAsState(emptyList())
                val ordered = PlayerCardEntries.ordered(
                    cardEntries.ifEmpty { PlayerCardEntries.merge(null, emptyList()) },
                    cards,
                    allSettled = true
                )
                for (entry in ordered) {
                    key(entry.key) {
                        InfoCardEnter(Modifier.padding(top = 16.dp)) {
                            when (entry.card) {
                                null -> PluginCardView(entry.key)
                                FullPlayerCard.LYRICS -> LyricsCard(
                                    lines = detailState.lyrics,
                                    currentIndex = currentLyricIndex,
                                    base = base,
                                    isPlaying = isPlaying,
                                    onSeek = playerViewModel::seekToTime,
                                    onOpenFullscreen = onOpenLyricsFullscreen,
                                    onOpenLyricsView = onOpenLyricsView,
                                    sourceLabel = when (detailState.lyricsSource) {
                                        com.lin0721.linmusic.core.player.LyricsSource.AMLL -> "TTML"
                                        com.lin0721.linmusic.core.player.LyricsSource.QQ ->
                                            "QRC".takeIf { detailState.lyrics.any { it.words.isNotEmpty() } }
                                        com.lin0721.linmusic.core.player.LyricsSource.KUGOU ->
                                            "KRC".takeIf { detailState.lyrics.any { it.words.isNotEmpty() } }
                                        else -> null
                                    }
                                )
                                FullPlayerCard.COMMENTS_PREVIEW -> CommentsPreviewCard(
                                    state = commentsState,
                                    onOpen = onOpenComments,
                                    onRetry = playerViewModel::retryComments
                                )
                                FullPlayerCard.SONG_DETAIL -> detailState.songWiki?.let { SongDetailCard(it, detailState.songDetail) }
                                FullPlayerCard.MUSIC_MEMORY -> detailState.songWiki?.musicMemory?.let { MusicMemoryCard(it) }
                                FullPlayerCard.ARTIST_ALBUMS -> ArtistAlbumsCard(
                                    albums = detailState.artistAlbums,
                                    artistName = detailState.currentArtistItem?.artistName ?: detailState.artistDetail?.name,
                                    kugou = detailState.kugouLinks != null
                                )
                                FullPlayerCard.SIMILAR_ARTISTS -> SimilarArtistsCard(detailState.similarArtists)
                                FullPlayerCard.ABOUT_ARTIST -> AboutArtistCard(
                                    artists = detailState.artists,
                                    selectedIndex = detailState.selectedArtistIndex,
                                    onSelectArtist = playerViewModel::selectArtist,
                                    onToggleFollow = { artistId ->
                                        val kugouTrack = detailState.kugouLinks != null
                                        val loggedIn = if (kugouTrack) navigator.isKugouLoggedIn else navigator.isLoggedIn
                                        if (loggedIn) playerViewModel.toggleArtistFollow(artistId) else navigator.showMessage("请先登录账号")
                                    },
                                    kugou = detailState.kugouLinks != null
                                )
                                else -> Unit
                            }
                        }
                    }
                }
            }
        }
    }
}

// 歌曲详情到达后歌手名可点击跳转，之前先展示队列里的纯文本
// 插件布局替换封面与标题区；封面默认占满面板宽度，maxSize 限制最大边长
@Composable
private fun PluginNowPlayingHeader(
    spec: PlayerLayoutSpec,
    track: NowPlaying,
    controller: PlaybackController,
    playerViewModel: PlayerViewModel
) {
    val parts = rememberPlaybackParts(controller, playerViewModel)
    val coverColors = rememberLayoutCoverColors(track.artworkUri)
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val binding = parts.bindingState()
        CompositionLocalProvider(
            LocalLayoutCoverColors provides coverColors,
            LocalLayoutEnvironment provides parts.environment(spec.pluginId, binding)
        ) {
            PluginNowPlayingTree(spec.select(maxWidth.value, landscape = true), track, parts, binding)
        }
    }
}

@Composable
private fun PluginNowPlayingTree(
    root: PlayerLayoutNode,
    track: NowPlaying,
    parts: PlayerBarParts,
    binding: PlayerBindingState
) {
    val playerViewModel = parts.playerViewModel
    PlayerLayoutView(root, Modifier.fillMaxWidth().padding(top = 8.dp), binding::prepare) { node, modifier ->
        val scale = node.size.scale
        when (node.type) {
            LayoutNodeType.COVER -> BoxWithConstraints(modifier) {
                val size = node.maxSize?.let { minOf(maxWidth, it.dp) } ?: maxWidth
                Cover(track.artworkUri, size, shape = node.layoutShape(), requestSize = DesktopDimens.NowPlayingMaxWidth)
            }
            LayoutNodeType.SONG_TITLE -> NowPlayingSongTitle(
                track, playerViewModel, fontSize = (22 * scale).sp, fontWeight = FontWeight.Bold, maxLines = node.maxLines, modifier = modifier
            )
            LayoutNodeType.ARTIST -> Box(modifier) { NowPlayingArtists(track, playerViewModel, (14 * scale).sp) }
            LayoutNodeType.SONG_INFO -> Column(modifier) {
                NowPlayingSongTitle(track, playerViewModel, fontSize = (22 * scale).sp, fontWeight = FontWeight.Bold, maxLines = node.maxLines)
                NowPlayingArtists(track, playerViewModel, (14 * scale).sp)
            }
            else -> parts.Component(node, modifier)
        }
    }
}

@Composable
internal fun NowPlayingArtists(track: NowPlaying, playerViewModel: PlayerViewModel, fontSize: TextUnit) {
    val navigator = LocalDesktopNavigator.current
    val detailState by playerViewModel.songDetailState.collectAsState()
    val kugouArtists = detailState.kugouLinks?.artists.orEmpty()
    if (detailState.kugouLinks != null && kugouArtists.isNotEmpty()) {
        // 酷狗歌手用规范 id 导航，Artist.id 只作列表内的点击标识
        val entityIds = remember(kugouArtists) {
            kugouArtists.associate { it.legacyId() to it.entityId }
        }
        Text(
            artistLinks(
                artists = kugouArtists.map { Artist(id = it.legacyId(), name = it.name) },
                onClick = { id, name -> entityIds[id]?.let { navigator.openArtistEntity(it, name) } },
                clickableIds = entityIds.keys
            ),
            color = DesktopColors.TextGray,
            fontSize = fontSize,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        return
    }
    val artists = detailState.artists
        .takeIf { detailState.songDetail?.id == track.songId }
        ?.map { Artist(id = it.artistId, name = it.artistName) }
        .orEmpty()
    if (artists.isEmpty()) {
        Text(track.artist, color = DesktopColors.TextGray, fontSize = fontSize, maxLines = 1, overflow = TextOverflow.Ellipsis)
    } else {
        Text(
            artistLinks(artists, navigator.openArtist),
            color = DesktopColors.TextGray,
            fontSize = fontSize,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

private fun KugouArtistLink.legacyId(): Long = ArtistId.kugou(id).toDeterministicLegacyId()

@Composable
private fun PanelHeader(
    track: NowPlaying,
    controller: PlaybackController,
    playerViewModel: PlayerViewModel,
    hovered: Boolean,
    onClose: () -> Unit,
    onToggleDevices: (() -> Unit)? = null,
    devicesOpen: Boolean = false,
    remoteDeviceName: String? = null
) {
    val sleepRemaining by controller.sleepTimerRemaining.collectAsState()
    val podcast by playerViewModel.podcastState.collectAsState()
    var menuOpen by remember { mutableStateOf(false) }
    val sleepActive = sleepRemaining > 0L

    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        // 按钮尺寸与偏移沿用移动端播放页顶栏，使箭头与封面左缘对齐
        HoverReveal(revealed = hovered, modifier = Modifier.offset(x = (-HeaderButtonOffset)), reserveSpace = false) {
            IconButton(onClick = onClose, modifier = Modifier.size(HeaderButtonSize)) {
                Icon(
                    MelodiaIcons.PanelCollapse,
                    "关闭正在播放",
                    tint = DesktopColors.TextGray,
                    modifier = Modifier.size(22.dp)
                )
            }
        }
        if (podcast.isPodcast) {
            PodcastPanelTitle(podcast.radioId, podcast.radioName, Modifier.weight(1f))
        } else {
            PanelTitle(track, controller, playerViewModel, Modifier.weight(1f))
        }
        ListenTogetherBadge(Modifier.padding(end = 6.dp))
        if (sleepActive) {
            Icon(MelodiaIcons.Moon, "睡眠定时", tint = DesktopColors.Accent, modifier = Modifier.size(16.dp))
            Text(
                formatSleepTimerRemaining(sleepRemaining),
                color = DesktopColors.TextGray,
                fontSize = 12.sp,
                modifier = Modifier.padding(start = 4.dp)
            )
        }
        if (onToggleDevices != null) {
            val deviceActive = remoteDeviceName != null || devicesOpen
            HoverReveal(revealed = hovered || deviceActive) {
                DesktopTooltip(remoteDeviceName?.let { "在 $it 上播放" } ?: "播放设备") {
                    IconButton(onClick = onToggleDevices, modifier = Modifier.size(HeaderButtonSize)) {
                        Icon(
                            if (remoteDeviceName != null) MelodiaIcons.Phone else MelodiaIcons.Devices,
                            remoteDeviceName?.let { "在 $it 上播放" } ?: "切换播放设备",
                            tint = if (deviceActive) DesktopColors.Accent else DesktopColors.TextGray,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        }
        HoverReveal(revealed = hovered || menuOpen, modifier = Modifier.offset(x = HeaderButtonOffset)) {
            Box {
                IconButton(onClick = { menuOpen = true }, modifier = Modifier.size(HeaderButtonSize)) {
                    Icon(MelodiaIcons.MoreHoriz, "更多有关《${track.title}》的选项", tint = DesktopColors.TextGray)
                }
                NowPlayingMoreMenu(track, controller, playerViewModel, expanded = menuOpen, onDismiss = { menuOpen = false })
            }
        }
    }
}

// 播客节目的面板标题：电台名，点击进入电台详情
@Composable
private fun PodcastPanelTitle(radioId: Long, radioName: String, modifier: Modifier) {
    val navigator = LocalDesktopNavigator.current
    Text(
        radioName.ifBlank { "播客" },
        color = DesktopColors.TextPrimary,
        fontWeight = FontWeight.Bold,
        fontSize = 15.sp,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = if (radioId > 0) {
            modifier.clip(RoundedCornerShape(4.dp)).pointerHoverIcon(PointerIcon.Hand).clickable { navigator.openRadio(radioId) }
        } else {
            modifier
        }
    )
}

private class TitleTarget(val isAlbum: Boolean, val id: Long, val name: String)

// 优先显示私人FM；其次播放来源的歌单/专辑；来源缺失时退回当前歌曲所属专辑，都没有则只显示“正在播放”
@Composable
private fun PanelTitle(track: NowPlaying, controller: PlaybackController, playerViewModel: PlayerViewModel, modifier: Modifier) {
    val playContext by controller.playContext.collectAsState()
    if (playContext == PlaybackController.CONTEXT_PERSONAL_FM) {
        PersonalFmPanelTitle(playerViewModel, modifier)
        return
    }
    val navigator = LocalDesktopNavigator.current
    val source by controller.playSource.collectAsState()
    val detailState by playerViewModel.songDetailState.collectAsState()
    val album = detailState.songDetail?.takeIf { it.id == track.songId }?.al
    val target = source?.let { TitleTarget(it.kind == PlaySource.Kind.ALBUM, it.id, it.name) }
        ?: album?.takeIf { it.id > 0 && it.name.isNotBlank() }?.let { TitleTarget(true, it.id, it.name) }

    if (target == null) {
        Text("正在播放", color = DesktopColors.TextPrimary, fontWeight = FontWeight.Bold, fontSize = 15.sp, modifier = modifier)
        return
    }
    Text(
        target.name,
        color = DesktopColors.TextPrimary,
        fontWeight = FontWeight.Bold,
        fontSize = 15.sp,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier.clip(RoundedCornerShape(4.dp)).pointerHoverIcon(PointerIcon.Hand).clickable {
            if (target.isAlbum) navigator.openAlbum(target.id, target.name) else navigator.openPlaylist(target.id, target.name)
        }
    )
}

@Composable
private fun PersonalFmPanelTitle(playerViewModel: PlayerViewModel, modifier: Modifier) {
    val personalFmMode by playerViewModel.personalFmMode.collectAsState()
    var modeMenuOpen by remember { mutableStateOf(false) }

    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text(
            "播放自 私人FM",
            color = DesktopColors.TextPrimary,
            fontWeight = FontWeight.Bold,
            fontSize = 15.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false)
        )
        Box {
            PersonalFmModePill(
                mode = personalFmMode,
                onClick = { modeMenuOpen = true }
            )
            PersonalFmModePopup(
                expanded = modeMenuOpen,
                onDismiss = { modeMenuOpen = false },
                currentMode = personalFmMode,
                onSelectMode = { mode ->
                    if (mode != personalFmMode) playerViewModel.switchPersonalFmMode(mode)
                }
            )
        }
    }
}

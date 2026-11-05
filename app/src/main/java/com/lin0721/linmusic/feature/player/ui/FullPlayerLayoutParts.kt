package com.lin0721.linmusic.feature.player.ui

import android.media.AudioDeviceInfo
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lin0721.linmusic.core.player.LyricsSource
import com.lin0721.linmusic.core.player.PlayMode
import com.lin0721.linmusic.core.player.playSourceText
import com.lin0721.linmusic.core.plugin.LayoutHeader
import com.lin0721.linmusic.core.plugin.LayoutNodeType
import com.lin0721.linmusic.core.plugin.HostAction
import com.lin0721.linmusic.core.plugin.LayoutAction
import com.lin0721.linmusic.core.plugin.LayoutCoverColors
import com.lin0721.linmusic.core.plugin.LayoutEnvironment
import com.lin0721.linmusic.core.plugin.LocalLayoutEnvironment
import com.lin0721.linmusic.core.plugin.rememberLayoutScriptRunner
import com.lin0721.linmusic.core.ui.theme.LocalUiMotion
import com.lin0721.linmusic.core.plugin.LayoutTimeText
import com.lin0721.linmusic.core.plugin.LocalLayoutCoverColors
import com.lin0721.linmusic.core.plugin.LocalInLayoutPager
import com.lin0721.linmusic.core.plugin.PlayerBindingState
import com.lin0721.linmusic.core.plugin.PlayerLayoutNode
import com.lin0721.linmusic.core.plugin.PlayerLayoutSpec
import com.lin0721.linmusic.core.plugin.PlayerLayoutView
import com.lin0721.linmusic.core.plugin.layoutShape
import com.lin0721.linmusic.core.ui.components.SwipeToSkipCover
import com.lin0721.linmusic.core.ui.theme.ImmersiveTheme
import com.lin0721.linmusic.core.ui.theme.MelodiaSpacing
import com.lin0721.linmusic.core.ui.theme.PlayerBackdropPalette
import com.lin0721.linmusic.feature.podcast.ui.PodcastPlayerState

private const val SEEK_STEP_MS = 10_000L

// 全屏与宽屏播放页的插件布局：把布局节点映射到已有组件，状态与回调都来自 FullPlayerScreen
class FullPlayerLayoutParts(
    val title: String,
    val artist: String,
    val songState: PlayerSongDetailState,
    val podcast: PodcastPlayerState,
    val colors: PlayerBackdropPalette,
    val coverUrl: String,
    val previousCoverUrl: String?,
    val nextCoverUrl: String?,
    val currentKey: Any,
    val previousKey: Any?,
    val nextKey: Any?,
    val currentLyricIndex: Int,
    val isPlaying: Boolean,
    val playWhenReady: Boolean,
    val currentPositionProvider: () -> Long,
    val duration: Long,
    val playMode: PlayMode,
    val animateLyricsMesh: Boolean,
    val connectedDevice: AudioDeviceInfo?,
    val qualityLabel: String?,
    val onToggleLike: () -> Unit,
    val onToggleSubscribe: () -> Unit,
    val onArtistClick: () -> Unit,
    val onAlbumClick: (() -> Unit)?,
    val onMiniLyricClick: () -> Unit,
    val onSeek: (Long) -> Unit,
    val onTogglePlay: () -> Unit,
    val onPlayNext: () -> Unit,
    val onPlayPrevious: () -> Unit,
    val onCancelSkip: () -> Boolean,
    val onToggleShuffle: () -> Unit,
    val onToggleRepeat: () -> Unit,
    val onOutputDeviceClick: () -> Unit,
    val onQueueClick: () -> Unit,
    val onShareClick: () -> Unit,
    val onMoreClick: () -> Unit,
    val onOpenFullScreenLyrics: () -> Unit,
    val onClose: () -> Unit,
    val onToggleSidebarFullscreen: (() -> Unit)?,
    val isSidebarFullscreen: Boolean,
    val playContext: String?,
    val podcastRadioName: String,
    val onPlaylistClick: (() -> Unit)?,
    val activeLyricIndices: Set<Int>,
    val lyricPositionProvider: () -> Long,
    val lyricsHighlightColor: Color,
    val sleepTimerActive: Boolean,
    val onSleepTimerClick: () -> Unit,
    val onCommentsClick: () -> Unit
) {
    // $player 绑定的取值；本对象每次重组都重建，算一次即可
    val binding: PlayerBindingState by lazy {
        PlayerBindingState(
            title = title,
            artist = artist,
            album = songState.songDetail?.al?.name.orEmpty(),
            source = playSourceText(playContext, podcastRadioName).second.orEmpty(),
            quality = qualityLabel.orEmpty(),
            playing = playWhenReady,
            liked = if (podcast.isPodcast) podcast.subscribed else songState.isLiked,
            isPodcast = podcast.isPodcast,
            hasLyrics = songState.lyrics.isNotEmpty() && !(songState.lyrics.size == 1 && songState.lyrics[0].text == "纯音乐"),
            playMode = playMode
        )
    }

    val coverColors: LayoutCoverColors get() = LayoutCoverColors(colors.base, colors.textHighlight)

    // 手势里的宿主动作；本区域不支持的动作在解析时已去掉
    private fun handle(action: HostAction) {
        when (action) {
            HostAction.TOGGLE_PLAY -> onTogglePlay()
            HostAction.NEXT -> onPlayNext()
            HostAction.PREVIOUS -> onPlayPrevious()
            HostAction.SEEK_FORWARD -> onSeek((currentPositionProvider() + SEEK_STEP_MS).coerceAtMost(displayDuration))
            HostAction.SEEK_BACK -> onSeek((currentPositionProvider() - SEEK_STEP_MS).coerceAtLeast(0L))
            HostAction.TOGGLE_LIKE -> if (podcast.isPodcast) onToggleSubscribe() else onToggleLike()
            HostAction.CYCLE_PLAY_MODE -> if (playMode == PlayMode.LIST_LOOP) onToggleRepeat() else onToggleShuffle()
            HostAction.OPEN_LYRICS -> onOpenFullScreenLyrics()
            HostAction.OPEN_QUEUE -> onQueueClick()
            HostAction.OPEN_COMMENTS -> onCommentsClick()
            HostAction.OPEN_SLEEP_TIMER -> onSleepTimerClick()
            HostAction.OPEN_MORE -> onMoreClick()
            HostAction.COLLAPSE -> onClose()
            HostAction.PAGER_NEXT, HostAction.PAGER_PREVIOUS, HostAction.TOGGLE_NOW_PLAYING, HostAction.TOGGLE_LYRICS_VIEW,
            HostAction.CLOSE_LYRICS, HostAction.OPEN_LYRICS_SETTINGS -> Unit
        }
    }

    // 回调读最新状态但自身不变，环境的其他值没变时不让整棵布局树跟着重组
    @Composable
    fun environment(pluginId: String): LayoutEnvironment {
        val runScript = rememberLayoutScriptRunner(pluginId, binding)
        val latest by rememberUpdatedState(this)
        val onAction = remember(runScript) {
            { action: LayoutAction ->
                when (action) {
                    is LayoutAction.Host -> latest.handle(action.action)
                    is LayoutAction.Script -> runScript(action.name)
                }
            }
        }
        return LayoutEnvironment(
            playing = playWhenReady,
            songKey = currentKey,
            motionEnabled = LocalUiMotion.current.enabled,
            onAction = onAction
        )
    }

    private val displayDuration: Long get() = if (duration > 0L) duration else (songState.songDetail?.dt ?: 0L)

    @Composable
    fun Component(node: PlayerLayoutNode, modifier: Modifier) {
        val scale = node.size.scale
        when (node.type) {
            LayoutNodeType.COVER -> {
                // 占比分配高度时以高度为准定宽，否则以宽度为准定高
                val byHeight = node.weight != null
                val limited = node.maxSize?.let { modifier.widthIn(max = it.dp) } ?: modifier
                // 分页器里左右滑动用于翻页，不提供相邻封面即关闭滑动切歌
                val swipeEnabled = !LocalInLayoutPager.current
                SwipeToSkipCover(
                    coverUrl = coverUrl,
                    previousCoverUrl = previousCoverUrl.takeIf { swipeEnabled },
                    nextCoverUrl = nextCoverUrl.takeIf { swipeEnabled },
                    onConfirmPrevious = onPlayPrevious,
                    onConfirmNext = onPlayNext,
                    currentKey = currentKey,
                    previousKey = previousKey.takeIf { swipeEnabled },
                    nextKey = nextKey.takeIf { swipeEnabled },
                    contentScale = ContentScale.Crop,
                    shape = node.layoutShape(),
                    modifier = (if (byHeight) limited.fillMaxHeight() else limited.fillMaxWidth())
                        .aspectRatio(node.aspect, matchHeightConstraintsFirst = byHeight),
                    onCancelPending = onCancelSkip
                )
            }
            LayoutNodeType.SONG_TITLE -> SongTitleText(title, onAlbumClick, modifier, (22 * scale).sp, node.maxLines)
            LayoutNodeType.ARTIST -> SongArtistText(artist, onArtistClick, modifier, (15 * scale).sp)
            LayoutNodeType.SONG_INFO -> Column(modifier) {
                SongTitleText(title, onAlbumClick, Modifier, (22 * scale).sp, node.maxLines)
                SongArtistText(artist, onArtistClick, Modifier, (15 * scale).sp)
            }
            LayoutNodeType.LIKE_BUTTON -> SongLikeButton(songState.isLiked, onToggleLike, podcast, onToggleSubscribe, modifier)
            LayoutNodeType.MINI_LYRIC -> Box(modifier) {
                MiniLyricLine(
                    lyrics = songState.lyrics,
                    currentLyricIndex = currentLyricIndex,
                    isPlaying = isPlaying,
                    onClick = onMiniLyricClick,
                    horizontalPadding = 0.dp,
                    textAlign = node.textAlign,
                    marquee = node.marquee
                )
            }
            LayoutNodeType.PROGRESS -> Box(modifier) {
                ProgressSection(
                    currentPositionProvider = currentPositionProvider,
                    duration = displayDuration,
                    onSeek = onSeek,
                    chorusStartMs = songState.chorusStartMs,
                    showTime = node.showTime,
                    horizontalPadding = 0.dp
                )
            }
            LayoutNodeType.CONTROLS -> PlaybackControlRow(
                buttons = node.buttons,
                isPlaying = playWhenReady,
                onTogglePlay = onTogglePlay,
                onPlayNext = onPlayNext,
                onPlayPrevious = onPlayPrevious,
                onToggleShuffle = onToggleShuffle,
                onToggleRepeat = onToggleRepeat,
                playMode = playMode,
                showModeButtons = !podcast.isPodcast,
                modifier = modifier,
                scale = scale,
                variant = node.variant
            )
            LayoutNodeType.ACTIONS -> PlayerActionRow(
                buttons = node.buttons,
                onOutputDeviceClick = onOutputDeviceClick,
                onQueueClick = onQueueClick,
                onShareClick = onShareClick,
                onMoreClick = onMoreClick,
                connectedDevice = connectedDevice,
                qualityLabel = qualityLabel,
                modifier = modifier,
                onSleepTimerClick = onSleepTimerClick,
                sleepTimerActive = sleepTimerActive,
                onCommentsClick = onCommentsClick,
                onLyricsClick = onOpenFullScreenLyrics,
                onLyricsSettingsClick = LocalLyricsPageSlots.current?.onOpenSettings ?: {}
            )
            // 歌词卡在浅色页面里保持有色底白字
            LayoutNodeType.LYRICS_CARD -> ImmersiveTheme(colors.base) {
                Box(modifier) {
                    LyricsCard(
                        lyrics = songState.lyrics,
                        currentIndex = currentLyricIndex,
                        isLoading = false,
                        base = colors.base,
                        animateMesh = animateLyricsMesh,
                        isAmll = songState.lyricsSource == LyricsSource.AMLL,
                        isQrc = songState.lyricsSource == LyricsSource.QQ && songState.lyrics.any { it.words.isNotEmpty() },
                        onOpenFullScreen = onOpenFullScreenLyrics
                    )
                }
            }
            LayoutNodeType.TIME -> LayoutTimeText(node, currentPositionProvider, displayDuration, modifier)
            LayoutNodeType.LYRICS_VIEW -> LocalLyricsPageSlots.current?.lyricsList?.invoke(modifier) ?: EmbeddedLyricsView(
                lyrics = songState.lyrics,
                currentIndex = currentLyricIndex,
                activeIndices = activeLyricIndices,
                isLoading = songState.isLyricsLoading,
                highlightColor = lyricsHighlightColor,
                currentPositionProvider = lyricPositionProvider,
                isPlaying = isPlaying,
                onSeek = onSeek,
                modifier = modifier
            )
            // 歌词页里收起按钮关闭歌词页
            LayoutNodeType.COLLAPSE_BUTTON -> LocalLyricsPageSlots.current?.let { slots ->
                FullPlayerCollapseButtons(slots.onClose, null, false, modifier)
            } ?: FullPlayerCollapseButtons(onClose, onToggleSidebarFullscreen, isSidebarFullscreen, modifier)
            LayoutNodeType.MORE_BUTTON -> FullPlayerMoreButton(onMoreClick, modifier)
            LayoutNodeType.SOURCE_LABEL -> FullPlayerSourceLabel(playContext, podcastRadioName, onPlaylistClick, modifier)
            else -> Unit
        }
    }
}

// 插件布局的首屏：整体作为一个视口高度的列表项，key 沿用 cover，滚动指标与背景位移照常工作
fun LazyListScope.fullPlayerPluginSection(
    spec: PlayerLayoutSpec,
    parts: FullPlayerLayoutParts,
    isLandscape: Boolean,
    sourceBar: @Composable (Modifier) -> Unit
) {
    item(key = "cover") {
        FullPlayerItemEnterAnimation {
            BoxWithConstraints(Modifier.fillParentMaxHeight()) {
                val root = spec.select(maxWidth.value, isLandscape)
                val hostHeader = spec.frame.header == LayoutHeader.HOST
                Column(Modifier.fillMaxSize()) {
                    if (hostHeader) sourceBar(Modifier.fillMaxWidth())
                    CompositionLocalProvider(
                        LocalLayoutCoverColors provides parts.coverColors,
                        LocalLayoutEnvironment provides parts.environment(spec.pluginId)
                    ) {
                        PlayerLayoutView(root, Modifier.weight(1f).fillMaxWidth(), parts.binding::prepare) { node, modifier ->
                            parts.Component(node, modifier)
                        }
                    }
                }
                // 去掉来源栏而布局里没有一定看得见的收起按钮时由宿主补上，保证随时能收起
                if (!hostHeader && !root.reliablyShows { it.type == LayoutNodeType.COLLAPSE_BUTTON }) {
                    FullPlayerCollapseButtons(
                        onClose = parts.onClose,
                        onToggleSidebarFullscreen = parts.onToggleSidebarFullscreen,
                        isSidebarFullscreen = parts.isSidebarFullscreen,
                        modifier = Modifier.align(Alignment.TopStart).padding(horizontal = MelodiaSpacing.lg)
                    )
                }
            }
        }
    }
}

// 歌词页里渲染插件布局时提供，lyricsView、收起按钮与歌词设置按钮改用歌词页的实现
val LocalLyricsPageSlots = staticCompositionLocalOf<LyricsPageSlots?> { null }

// 全屏歌词页的插件布局；关闭与歌词设置动作交给歌词页，其余照常
@Composable
fun LyricsPagePluginContent(spec: PlayerLayoutSpec, parts: FullPlayerLayoutParts, isLandscape: Boolean, slots: LyricsPageSlots) {
    val base = parts.environment(spec.pluginId)
    val latestSlots by rememberUpdatedState(slots)
    val onAction = remember(base.onAction) {
        { action: LayoutAction ->
            when (action) {
                LayoutAction.Host(HostAction.CLOSE_LYRICS) -> latestSlots.onClose()
                LayoutAction.Host(HostAction.OPEN_LYRICS_SETTINGS) -> latestSlots.onOpenSettings()
                else -> base.onAction(action)
            }
        }
    }
    val environment = base.copy(controlsVisible = slots.controlsVisible, onAction = onAction)
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val root = spec.select(maxWidth.value, isLandscape)
        CompositionLocalProvider(
            LocalLayoutCoverColors provides parts.coverColors,
            LocalLayoutEnvironment provides environment,
            LocalLyricsPageSlots provides slots
        ) {
            PlayerLayoutView(root, Modifier.fillMaxSize(), parts.binding::prepare) { node, modifier ->
                parts.Component(node, modifier)
            }
        }
        // 布局里没有一定看得见的收起按钮时由宿主补上，保证随时能关闭歌词页
        if (!root.reliablyShows { it.type == LayoutNodeType.COLLAPSE_BUTTON }) {
            FullPlayerCollapseButtons(
                onClose = slots.onClose,
                onToggleSidebarFullscreen = null,
                isSidebarFullscreen = false,
                modifier = Modifier.align(Alignment.TopStart).padding(horizontal = MelodiaSpacing.lg)
            )
        }
    }
}

package com.lin0721.linmusic.desktop.ui.player

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import kotlinx.coroutines.delay
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.TextUnit
import com.lin0721.linmusic.core.player.NowPlaying
import com.lin0721.linmusic.core.player.PersonalFmMode
import com.lin0721.linmusic.core.plugin.CONTROL_BUTTONS
import com.lin0721.linmusic.core.plugin.CONTROL_PLAY_PAUSE
import com.lin0721.linmusic.core.plugin.CONTROL_PLAY_MODE
import com.lin0721.linmusic.core.plugin.ControlVariant
import com.lin0721.linmusic.core.plugin.FrameBackground
import com.lin0721.linmusic.core.plugin.HostAction
import com.lin0721.linmusic.core.plugin.LayoutAction
import com.lin0721.linmusic.core.plugin.LayoutEnvironment
import com.lin0721.linmusic.core.plugin.LayoutTimeText
import com.lin0721.linmusic.core.plugin.LocalLayoutEnvironment
import com.lin0721.linmusic.core.plugin.rememberLayoutScriptRunner
import com.lin0721.linmusic.core.plugin.LocalLayoutCoverColors
import com.lin0721.linmusic.core.plugin.PlayerBindingState
import com.lin0721.linmusic.core.plugin.layoutFill
import com.lin0721.linmusic.core.model.playbackQualityLabel
import com.lin0721.linmusic.core.player.playSourceText
import com.lin0721.linmusic.desktop.ui.nowplaying.rememberLayoutCoverColors
import androidx.compose.runtime.CompositionLocalProvider
import com.lin0721.linmusic.core.plugin.LayoutNodeType
import com.lin0721.linmusic.core.plugin.LocalPlayerLayouts
import com.lin0721.linmusic.core.plugin.PlayerLayoutArea
import com.lin0721.linmusic.core.plugin.PlayerLayoutNode
import com.lin0721.linmusic.core.plugin.PlayerLayoutSpec
import com.lin0721.linmusic.core.plugin.PlayerLayoutView
import com.lin0721.linmusic.core.plugin.layoutShape
import com.lin0721.linmusic.core.format.formatDuration
import com.lin0721.linmusic.core.player.PlayMode
import com.lin0721.linmusic.core.player.PlaybackController
import com.lin0721.linmusic.core.ui.theme.outlinedControls
import com.lin0721.linmusic.core.ui.theme.styledButtonShape
import com.lin0721.linmusic.desktop.player.AudioDevice
import com.lin0721.linmusic.desktop.data.local.LocalLibraryRepository
import com.lin0721.linmusic.desktop.ui.components.Cover
import com.lin0721.linmusic.desktop.ui.components.DesktopTooltip
import com.lin0721.linmusic.desktop.ui.components.LocalUiMotion
import com.lin0721.linmusic.desktop.ui.components.PlayPauseIcon
import com.lin0721.linmusic.desktop.ui.components.TooltipLabel
import com.lin0721.linmusic.desktop.ui.components.uiTween
import com.lin0721.linmusic.desktop.ui.icons.PlayerBarIcons
import com.lin0721.linmusic.desktop.ui.nowplaying.NowPlayingArtists
import com.lin0721.linmusic.desktop.ui.nowplaying.NowPlayingSongTitle
import com.lin0721.linmusic.desktop.ui.shell.LocalDesktopNavigator
import com.lin0721.linmusic.desktop.ui.theme.DesktopColors
import com.lin0721.linmusic.desktop.ui.theme.numericTextStyle
import com.lin0721.linmusic.desktop.ui.theme.DesktopDimens
import com.lin0721.linmusic.desktop.ui.track.CollectToPlaylistPopup
import com.lin0721.linmusic.feature.player.ui.PlayerViewModel
import kotlin.math.roundToInt
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import com.lin0721.linmusic.core.ui.icons.MelodiaIcons
import com.lin0721.linmusic.core.ui.icons.CheckCircleFilled
import com.lin0721.linmusic.core.ui.icons.Devices
import com.lin0721.linmusic.core.ui.icons.Favorite
import com.lin0721.linmusic.core.ui.icons.FavoriteFilled
import com.lin0721.linmusic.core.ui.icons.Fullscreen
import com.lin0721.linmusic.core.ui.icons.FullscreenExit
import com.lin0721.linmusic.core.ui.icons.MiniPlayer
import com.lin0721.linmusic.core.ui.icons.Phone
import com.lin0721.linmusic.core.ui.icons.Queue
import com.lin0721.linmusic.core.ui.icons.Repeat
import com.lin0721.linmusic.core.ui.icons.RepeatOne
import com.lin0721.linmusic.core.ui.icons.Shuffle
import com.lin0721.linmusic.core.ui.icons.SkipNext
import com.lin0721.linmusic.core.ui.icons.SkipPrevious
import com.lin0721.linmusic.core.ui.icons.VolumeDown
import com.lin0721.linmusic.core.ui.icons.VolumeOff
import com.lin0721.linmusic.core.ui.icons.VolumeUp
import com.lin0721.linmusic.core.ui.icons.AddCircle
import com.lin0721.linmusic.core.ui.icons.CheckCircle
import com.lin0721.linmusic.core.ui.icons.Delete
import com.lin0721.linmusic.core.ui.icons.Tune

private const val HOVER_SCALE = 1.1f
private const val HOVER_SCALE_MS = 150
private val TransportButtonSize = 36.dp
private val SideButtonSize = 32.dp

// 自绘图标字形占满格子，切歌与右侧统一 20dp 才与圆形播放键比例协调
private val SideIconSize = 20.dp
private val PlayIconSize = 18.dp
private val VolumeSliderWidth = 120.dp
private val DeviceNameMaxWidth = 160.dp
private const val DEVICE_ANIM_MS = 220
private val VolumeTooltipGap = 8.dp
// mpv 每秒上报几十次播放位置，进度条与秒级时间无需这么密，按此粒度取整去重
private const val POSITION_STEP_MS = 250L

@Composable
fun PlayerBar(
    controller: PlaybackController,
    playerViewModel: PlayerViewModel,
    volume: Int?,
    lastAudibleVolume: Int,
    onVolumeChange: (Int) -> Unit,
    nowPlayingOpen: Boolean,
    onToggleNowPlaying: () -> Unit,
    queueOpen: Boolean,
    onToggleQueue: () -> Unit,
    devicesOpen: Boolean,
    onToggleDevices: (() -> Unit)?,
    outputDevice: AudioDevice?,
    isFullscreen: Boolean,
    onToggleFullscreen: () -> Unit,
    lyricVisible: Boolean,
    onToggleLyric: () -> Unit,
    lyricsViewOpen: Boolean,
    onToggleLyricsView: () -> Unit,
    onEnterMiniPlayer: () -> Unit,
    modifier: Modifier = Modifier,
    remoteDeviceName: String? = null,
    localLibrary: LocalLibraryRepository? = null
) {
    val nowPlaying by controller.nowPlaying.collectAsState()
    val isPlaying by controller.playWhenReady.collectAsState()
    val playMode by controller.playMode.collectAsState()
    val quantizedPosition = remember(controller) {
        controller.currentPosition.map { it / POSITION_STEP_MS * POSITION_STEP_MS }.distinctUntilChanged()
    }
    val position by quantizedPosition.collectAsState(controller.currentPosition.value / POSITION_STEP_MS * POSITION_STEP_MS)
    val duration by controller.duration.collectAsState()
    val podcast by playerViewModel.podcastState.collectAsState()
    val playContext by controller.playContext.collectAsState()
    val personalFmMode by playerViewModel.personalFmMode.collectAsState()
    val parts = PlayerBarParts(
        controller = controller,
        playerViewModel = playerViewModel,
        nowPlaying = nowPlaying,
        isPlaying = isPlaying,
        playMode = playMode,
        position = position,
        duration = duration,
        isPodcast = podcast.isPodcast,
        isPersonalFm = playContext == PlaybackController.CONTEXT_PERSONAL_FM,
        personalFmMode = personalFmMode,
        volume = volume,
        lastAudibleVolume = lastAudibleVolume,
        onVolumeChange = onVolumeChange,
        nowPlayingOpen = nowPlayingOpen,
        onToggleNowPlaying = onToggleNowPlaying,
        queueOpen = queueOpen,
        onToggleQueue = onToggleQueue,
        devicesOpen = devicesOpen,
        onToggleDevices = onToggleDevices,
        outputDevice = outputDevice,
        remoteDeviceName = remoteDeviceName,
        isFullscreen = isFullscreen,
        onToggleFullscreen = onToggleFullscreen,
        lyricVisible = lyricVisible,
        onToggleLyric = onToggleLyric,
        lyricsViewOpen = lyricsViewOpen,
        onToggleLyricsView = onToggleLyricsView,
        onEnterMiniPlayer = onEnterMiniPlayer,
        localLibrary = localLibrary
    )
    val pluginLayout = LocalPlayerLayouts.current[PlayerLayoutArea.DESKTOP_PLAYER_BAR]
    Column(modifier.fillMaxWidth()) {
        if (pluginLayout != null) {
            PluginPlayerBar(pluginLayout, parts, Modifier)
        } else {
            Row(
                Modifier.fillMaxWidth().height(DesktopDimens.PlayerBarHeight).padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(Modifier.weight(0.27f), verticalAlignment = Alignment.CenterVertically) {
                    if (nowPlaying != null) {
                        parts.CoverButton(56.dp)
                        Column(Modifier.weight(1f, fill = false).padding(start = 12.dp)) {
                            parts.Title(14.sp)
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                parts.QualityBadge()
                                Box(Modifier.weight(1f, fill = false)) {
                                    parts.Artists(12.sp)
                                }
                            }
                        }
                        parts.LikeButton(Modifier.padding(start = 4.dp))
                    }
                }
                // 进度条位置不变，播放组上移，使栏顶与进度轨道到播放键的留白相等
                Column(
                    Modifier.weight(0.4f).fillMaxHeight().padding(top = 10.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    parts.Controls(CONTROL_BUTTONS)
                    parts.Progress(showTime = true)
                }
                parts.Actions(DesktopBarActions, Modifier.weight(0.33f))
            }
        }
        // 断开后保留上次设备名，让收起动画期间文字不突变为空
        var lastRemoteName by remember { mutableStateOf("") }
        if (!remoteDeviceName.isNullOrBlank()) lastRemoteName = remoteDeviceName
        val ease = CubicBezierEasing(0.2f, 0.8f, 0.2f, 1f)
        AnimatedVisibility(
            visible = !remoteDeviceName.isNullOrBlank(),
            enter = expandVertically(tween(240, easing = ease), expandFrom = Alignment.Top) +
                slideInVertically(tween(240, easing = ease)) { it / 2 } +
                fadeIn(tween(200)),
            exit = shrinkVertically(tween(180, easing = FastOutLinearInEasing), shrinkTowards = Alignment.Top) +
                slideOutVertically(tween(180, easing = FastOutLinearInEasing)) { it / 2 } +
                fadeOut(tween(140))
        ) {
            RemotePlaybackStrip(deviceName = lastRemoteName, onClick = onToggleDevices)
        }
    }
}

// 播放设备为对端时的悬浮提示块；只有右侧图标与文字可点，打开设备面板
@Composable
private fun RemotePlaybackStrip(deviceName: String, onClick: (() -> Unit)?) {
    val shape = RoundedCornerShape(8.dp)
    Row(
        Modifier
            .fillMaxWidth()
            .padding(start = 8.dp, end = 8.dp, bottom = 8.dp)
            .shadow(6.dp, shape)
            .background(DesktopColors.Accent, shape)
            .height(28.dp)
            .padding(horizontal = 3.dp),
        horizontalArrangement = Arrangement.End,
        verticalAlignment = Alignment.CenterVertically
    ) {
        val label = @Composable { RemotePlaybackLabel(deviceName, onClick) }
        if (onClick != null) DesktopTooltip("打开设备面板") { label() } else label()
    }
}

@Composable
private fun RemotePlaybackLabel(deviceName: String, onClick: (() -> Unit)?) {
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val hoverBg by animateColorAsState(
        if (hovered && onClick != null) Color.Black.copy(alpha = 0.18f) else Color.Transparent,
        tween(150)
    )
    Row(
        Modifier
            .height(22.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(hoverBg)
            .hoverable(interaction)
            .then(
                if (onClick != null) {
                    Modifier
                        .pointerHoverIcon(PointerIcon.Hand)
                        .clickable(interactionSource = interaction, indication = null, onClick = onClick)
                } else {
                    Modifier
                }
            )
            .padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            MelodiaIcons.VolumeUp,
            contentDescription = null,
            tint = DesktopColors.OnAccent,
            modifier = Modifier.size(15.dp)
        )
        Text(
            "在 $deviceName 上播放",
            color = DesktopColors.OnAccent,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(start = 7.dp)
        )
    }
}

private val DesktopBarActions = PlayerLayoutArea.DESKTOP_PLAYER_BAR.actionButtons
private const val MIN_PLUGIN_BAR_HEIGHT = 64f
private const val SEEK_STEP_MS = 10_000L
private const val MAX_PLUGIN_BAR_HEIGHT = 160f

// 插件布局：按窗口宽度选变体，根节点高度即播放栏高度，限制在合理范围内；没有歌曲时封面、标题等组件留空
@Composable
private fun PluginPlayerBar(spec: PlayerLayoutSpec, parts: PlayerBarParts, modifier: Modifier) {
    BoxWithConstraints(modifier.fillMaxWidth()) {
        val root = spec.select(maxWidth.value, landscape = true)
        val height = (root.height ?: DesktopDimens.PlayerBarHeight.value).coerceIn(MIN_PLUGIN_BAR_HEIGHT, MAX_PLUGIN_BAR_HEIGHT).dp
        // 播放栏本身没有底色，只有插件指定色板项时才铺底
        val coverColors = rememberLayoutCoverColors(parts.nowPlaying?.artworkUri)
        val binding = parts.bindingState()
        CompositionLocalProvider(
            LocalLayoutCoverColors provides coverColors,
            LocalLayoutEnvironment provides parts.environment(spec.pluginId, binding)
        ) {
            val fill = (spec.frame.background as? FrameBackground.Fill)?.fill
            val barModifier = Modifier.fillMaxWidth().height(height).then(fill?.let { Modifier.layoutFill(it) } ?: Modifier)
            PlayerLayoutView(root.copy(height = null), barModifier, binding::prepare) { node, nodeModifier ->
                parts.Component(node, nodeModifier)
            }
        }
    }
}

// 播放栏各部件：默认布局与插件布局共用同一套实现
internal class PlayerBarParts(
    val controller: PlaybackController,
    val playerViewModel: PlayerViewModel,
    val nowPlaying: NowPlaying?,
    val isPlaying: Boolean,
    val playMode: PlayMode,
    val position: Long,
    val duration: Long,
    val isPodcast: Boolean,
    val isPersonalFm: Boolean,
    val personalFmMode: PersonalFmMode,
    val volume: Int?,
    val lastAudibleVolume: Int,
    val onVolumeChange: (Int) -> Unit,
    val nowPlayingOpen: Boolean,
    val onToggleNowPlaying: () -> Unit,
    val queueOpen: Boolean,
    val onToggleQueue: () -> Unit,
    val devicesOpen: Boolean,
    val onToggleDevices: (() -> Unit)?,
    val outputDevice: AudioDevice?,
    val remoteDeviceName: String?,
    val isFullscreen: Boolean,
    val onToggleFullscreen: () -> Unit,
    val lyricVisible: Boolean,
    val onToggleLyric: () -> Unit,
    val lyricsViewOpen: Boolean,
    val onToggleLyricsView: () -> Unit,
    val onEnterMiniPlayer: () -> Unit,
    val localLibrary: LocalLibraryRepository? = null
) {
    private val hasTrack: Boolean get() = nowPlaying != null

    private fun currentLocalPath(): String? {
        val queue = controller.queue.value
        val index = controller.currentIndex.value
        return queue.getOrNull(index)?.localUri?.takeIf { it.isNotBlank() }
    }

    // 插件布局节点到部件的映射；校验已保证只会出现本区域支持的组件
    @Composable
    fun Component(node: PlayerLayoutNode, modifier: Modifier) {
        val scale = node.size.scale
        when (node.type) {
            LayoutNodeType.COVER -> Box(modifier) {
                CoverButton(((node.maxSize ?: PLUGIN_COVER_SIZE) * scale).dp, node.layoutShape())
            }
            LayoutNodeType.SONG_TITLE -> Box(modifier) { Title((14 * scale).sp, node.maxLines) }
            LayoutNodeType.ARTIST -> Box(modifier) { Artists((12 * scale).sp) }
            LayoutNodeType.SONG_INFO -> Column(modifier) {
                Title((14 * scale).sp, node.maxLines)
                Artists((12 * scale).sp)
            }
            LayoutNodeType.LIKE_BUTTON -> LikeButton(modifier)
            LayoutNodeType.CONTROLS -> Controls(node.buttons, modifier, scale, node.variant)
            LayoutNodeType.PROGRESS -> Box(modifier) { Progress(node.showTime) }
            LayoutNodeType.ACTIONS -> Actions(node.buttons, modifier)
            LayoutNodeType.TIME -> LayoutTimeText(node, { position }, duration, modifier)
            else -> Unit
        }
    }

    // 手势里的宿主动作；本区域不支持的动作在解析时已去掉
    private fun handle(action: HostAction) {
        when (action) {
            HostAction.TOGGLE_PLAY -> controller.togglePlayPause()
            HostAction.NEXT -> controller.playNext()
            HostAction.PREVIOUS -> if (!isPersonalFm) controller.skipToPrevious()
            HostAction.SEEK_FORWARD -> controller.seekTo((position + SEEK_STEP_MS).coerceAtMost(duration.coerceAtLeast(0L)))
            HostAction.SEEK_BACK -> controller.seekTo((position - SEEK_STEP_MS).coerceAtLeast(0L))
            HostAction.TOGGLE_LIKE -> {
                val localPath = currentLocalPath()
                if (localPath != null && localLibrary != null) {
                    localLibrary.toggleLiked(localPath)
                } else if (isPodcast) {
                    playerViewModel.toggleSubscribe()
                } else {
                    playerViewModel.toggleLike()
                }
            }
            HostAction.CYCLE_PLAY_MODE -> if (!isPersonalFm) {
                if (playMode == PlayMode.LIST_LOOP) controller.toggleRepeat() else controller.toggleShuffle()
            }
            HostAction.TOGGLE_NOW_PLAYING -> onToggleNowPlaying()
            HostAction.TOGGLE_LYRICS_VIEW -> onToggleLyricsView()
            else -> Unit
        }
    }

    // 回调读最新状态但自身不变，环境的其他值没变时不让整棵布局树跟着重组
    @Composable
    fun environment(pluginId: String, binding: PlayerBindingState): LayoutEnvironment {
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
            playing = isPlaying,
            songKey = nowPlaying?.mediaId,
            motionEnabled = LocalUiMotion.current.enabled,
            onAction = onAction
        )
    }

    // $player 绑定的取值
    @Composable
    fun bindingState(): PlayerBindingState {
        val songDetail by playerViewModel.songDetailState.collectAsState()
        val podcast by playerViewModel.podcastState.collectAsState()
        val playContext by controller.playContext.collectAsState()
        val showQuality by playerViewModel.showPlaybackQuality.collectAsState()
        val playbackQuality by controller.playbackQuality.collectAsState()
        val track = nowPlaying
        val lyrics = songDetail.lyrics
        val quality = playbackQuality
            ?.takeIf { showQuality && !podcast.isPodcast && it.mediaId == track?.mediaId }
            ?.let(::playbackQualityLabel)
        return PlayerBindingState(
            title = track?.title.orEmpty(),
            artist = track?.artist.orEmpty(),
            album = songDetail.songDetail?.al?.name.orEmpty(),
            source = playSourceText(playContext, podcast.radioName).second.orEmpty(),
            quality = quality.orEmpty(),
            playing = isPlaying,
            liked = if (podcast.isPodcast) podcast.subscribed else songDetail.isLiked,
            isPodcast = podcast.isPodcast,
            hasLyrics = lyrics.isNotEmpty() && !(lyrics.size == 1 && lyrics[0].text == "纯音乐"),
            playMode = playMode
        )
    }

    @Composable
    fun CoverButton(size: Dp, shape: Shape? = null) {
        val track = nowPlaying ?: return
        DesktopTooltip(if (nowPlayingOpen) "隐藏“正在播放”" else "显示“正在播放”") {
            Box(Modifier.pointerHoverIcon(PointerIcon.Hand).clickable(onClick = onToggleNowPlaying)) {
                if (shape == null) Cover(track.artworkUri, size) else Cover(track.artworkUri, size, shape = shape)
            }
        }
    }

    @Composable
    fun Title(fontSize: TextUnit, maxLines: Int = 1) {
        val track = nowPlaying ?: return
        NowPlayingSongTitle(track, playerViewModel, fontSize = fontSize, maxLines = maxLines)
    }

    @Composable
    fun QualityBadge(modifier: Modifier = Modifier) {
        val showQuality by playerViewModel.showPlaybackQuality.collectAsState()
        val playbackQuality by controller.playbackQuality.collectAsState()
        val track = nowPlaying
        val quality = playbackQuality
            ?.takeIf { track != null && showQuality && !isPodcast && it.mediaId == track.mediaId }
        PlaybackQualityBadge(quality = quality, modifier = modifier)
    }

    @Composable
    fun Artists(fontSize: TextUnit) {
        val track = nowPlaying ?: return
        NowPlayingArtists(track, playerViewModel, fontSize)
    }

    // 歌曲为收藏到歌单，播客为订阅所属电台
    @Composable
    fun LikeButton(modifier: Modifier = Modifier) {
        val track = nowPlaying ?: return
        val navigator = LocalDesktopNavigator.current
        val queue by controller.queue.collectAsState()
        val currentIndex by controller.currentIndex.collectAsState()
        val localPath = queue.getOrNull(currentIndex)?.localUri?.takeIf { it.isNotBlank() }
        val localLikedLocations = localLibrary?.likedLocations?.collectAsState()?.value ?: emptySet()
        val isLocalLiked = localPath != null && localLibrary?.isLiked(localPath, localLikedLocations) == true
        val songDetail by playerViewModel.songDetailState.collectAsState()
        val podcast by playerViewModel.podcastState.collectAsState()
        if (localPath != null && localLibrary != null) {
            BarIconButton(
                icon = if (isLocalLiked) MelodiaIcons.CheckCircleFilled else MelodiaIcons.AddCircle,
                description = if (isLocalLiked) "取消喜欢" else "添加到“我喜欢的音乐”",
                active = isLocalLiked,
                size = SideButtonSize, iconSize = SideIconSize,
                modifier = modifier,
                onClick = { localLibrary.toggleLiked(localPath) }
            )
            return
        }
        if (podcast.isPodcast) {
            if (podcast.canSubscribe) {
                BarIconButton(
                    icon = if (podcast.subscribed) MelodiaIcons.FavoriteFilled else MelodiaIcons.Favorite,
                    description = if (podcast.subscribed) "取消订阅电台" else "订阅电台",
                    active = podcast.subscribed,
                    size = SideButtonSize, iconSize = SideIconSize,
                    modifier = modifier,
                    onClick = playerViewModel::toggleSubscribe
                )
            }
            return
        }
        val collectState by playerViewModel.collectState.collectAsState()
        var showCollectPopup by remember(track.songId) { mutableStateOf(false) }

        Box {
            BarIconButton(
                icon = if (songDetail.isLiked) MelodiaIcons.CheckCircleFilled else MelodiaIcons.AddCircle,
                description = if (songDetail.isLiked) "收藏到歌单" else "添加到“已点赞的歌曲”",
                active = songDetail.isLiked,
                size = SideButtonSize, iconSize = SideIconSize,
                modifier = modifier,
                onClick = {
                    if (!navigator.hasCloudAccount) {
                        navigator.showMessage("请先登录账号")
                    } else {
                        track.songId?.let { songId ->
                            if (songDetail.isLiked) {
                                playerViewModel.prepareCollectDialog(songId)
                                showCollectPopup = true
                            } else {
                                playerViewModel.quickLike(songId)
                            }
                        }
                    }
                }
            )

            if (showCollectPopup && track.songId != null) {
                val songId = track.songId!!
                CollectToPlaylistPopup(
                    songId = songId,
                    state = collectState,
                    onSave = { items ->
                        playerViewModel.savePlaylistCollection(songId, items)
                        showCollectPopup = false
                    },
                    onCreate = { name ->
                        playerViewModel.createPlaylistAndAddSong(name, songId)
                        showCollectPopup = false
                    },
                    onDismiss = { showCollectPopup = false }
                )
            }
        }
    }

    // 播客没有随机与循环，对应按钮不显示；私人FM 禁用上一首、将循环替换为不喜欢、将随机替换为 FM 模式菜单
    @Composable
    fun Controls(buttons: List<String>, modifier: Modifier = Modifier, scale: Float = 1f, variant: ControlVariant = ControlVariant.DEFAULT) {
        val transportSize = TransportButtonSize * scale
        Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp * scale)) {
            val outlined = variant == ControlVariant.OUTLINED || (variant == ControlVariant.DEFAULT && outlinedControls)
            val transportOutline = if (outlined) Modifier.border(1.dp, DesktopColors.Outline, CircleShape) else Modifier
            buttons.forEach { button ->
                when (button) {
                    "shuffle" -> if (isPersonalFm) {
                        FmModeButton(size = transportSize)
                    } else if (!isPodcast) {
                        BarIconButton(
                            MelodiaIcons.Shuffle,
                            "随机播放",
                            enabled = hasTrack,
                            active = playMode == PlayMode.SHUFFLE,
                            showDot = true,
                            size = transportSize,
                            onClick = controller::toggleShuffle
                        )
                    }
                    "previous" -> BarIconButton(
                        MelodiaIcons.SkipPrevious, "上一首", enabled = hasTrack && !isPersonalFm, size = transportSize,
                        onClick = controller::skipToPrevious, modifier = transportOutline
                    )
                    CONTROL_PLAY_PAUSE -> if (variant == ControlVariant.PLAIN) {
                        Box(Modifier.size(transportSize), contentAlignment = Alignment.Center) {
                            IconButton(onClick = controller::togglePlayPause, enabled = hasTrack) {
                                PlayPauseIcon(isPlaying, DesktopColors.TextPrimary, contentDescription = if (isPlaying) "暂停" else "播放")
                            }
                        }
                    } else {
                        PlayPauseButton(transportSize, outlined)
                    }
                    // 列表循环 → 单曲循环 → 随机 → 列表循环
                    CONTROL_PLAY_MODE -> if (isPersonalFm) {
                        BarIconButton(
                            MelodiaIcons.Delete,
                            "不喜欢",
                            enabled = hasTrack,
                            size = transportSize,
                            onClick = playerViewModel::trashCurrentFmSong
                        )
                    } else if (!isPodcast) {
                        BarIconButton(
                            when (playMode) {
                                PlayMode.LIST_LOOP -> MelodiaIcons.Repeat
                                PlayMode.SINGLE_LOOP -> MelodiaIcons.RepeatOne
                                PlayMode.SHUFFLE -> MelodiaIcons.Shuffle
                            },
                            when (playMode) {
                                PlayMode.LIST_LOOP -> "列表循环"
                                PlayMode.SINGLE_LOOP -> "单曲循环"
                                PlayMode.SHUFFLE -> "随机播放"
                            },
                            enabled = hasTrack,
                            size = transportSize,
                            onClick = if (playMode == PlayMode.LIST_LOOP) controller::toggleRepeat else controller::toggleShuffle
                        )
                    }
                    "next" -> BarIconButton(
                        MelodiaIcons.SkipNext, "下一首", enabled = hasTrack, size = transportSize,
                        onClick = controller::playNext, modifier = transportOutline
                    )
                    "repeat" -> if (isPersonalFm) {
                        BarIconButton(
                            MelodiaIcons.Delete,
                            "不喜欢",
                            enabled = hasTrack,
                            size = transportSize,
                            onClick = playerViewModel::trashCurrentFmSong
                        )
                    } else if (!isPodcast) {
                        BarIconButton(
                            if (playMode == PlayMode.SINGLE_LOOP) MelodiaIcons.RepeatOne else MelodiaIcons.Repeat,
                            "循环模式",
                            enabled = hasTrack,
                            active = playMode == PlayMode.SINGLE_LOOP,
                            showDot = true,
                            size = transportSize,
                            onClick = controller::toggleRepeat
                        )
                    }
                }
            }
        }
    }

    @Composable
    private fun FmModeButton(size: Dp) {
        var menuOpen by remember { mutableStateOf(false) }
        Box {
            BarIconButton(
                MelodiaIcons.Tune,
                "私人FM模式：${personalFmMode.displayName}",
                enabled = hasTrack,
                highlighted = true,
                size = size,
                onClick = { menuOpen = true }
            )
            PersonalFmModePopup(
                expanded = menuOpen,
                onDismiss = { menuOpen = false },
                currentMode = personalFmMode,
                onSelectMode = { mode ->
                    if (mode != personalFmMode) playerViewModel.switchPersonalFmMode(mode)
                }
            )
        }
    }

    // 描边风格：不填充，描边与图标用前景色
    @Composable
    private fun PlayPauseButton(size: Dp, outlined: Boolean) {
        val playHover = remember { MutableInteractionSource() }
        val playHovered by playHover.collectIsHoveredAsState()
        val playScale by hoverScale(playHovered && hasTrack)
        val playShape = styledButtonShape(CircleShape)
        val playActive = if (hasTrack) DesktopColors.TextPrimary else DesktopColors.SurfaceLight
        Box(
            Modifier.size(size).graphicsLayer { scaleX = playScale; scaleY = playScale }.clip(playShape)
                .then(if (outlined) Modifier.border(1.5.dp, playActive, playShape) else Modifier.background(playActive))
                .hoverable(playHover),
            contentAlignment = Alignment.Center
        ) {
            IconButton(onClick = controller::togglePlayPause, enabled = hasTrack) {
                PlayPauseIcon(isPlaying, if (outlined) playActive else DesktopColors.Pane, size = PlayIconSize, contentDescription = if (isPlaying) "暂停" else "播放")
            }
        }
    }

    @Composable
    fun Progress(showTime: Boolean) {
        ProgressRow(position, duration, enabled = hasTrack && duration > 0, showTime = showTime, onSeek = controller::seekTo)
    }

    @Composable
    fun Actions(buttons: List<String>, modifier: Modifier = Modifier) {
        Row(modifier, horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
            buttons.forEach { button ->
                when (button) {
                    "desktopLyric" -> if (!isPodcast) BarIconButton(
                        PlayerBarIcons.DesktopLyric,
                        if (lyricVisible) "关闭桌面歌词" else "开启桌面歌词",
                        active = lyricVisible,
                        showDot = true,
                        size = SideButtonSize, iconSize = SideIconSize,
                        onClick = onToggleLyric
                    )
                    "lyricsView" -> if (!isPodcast) BarIconButton(
                        PlayerBarIcons.Lyrics,
                        if (lyricsViewOpen) "收起全屏歌词" else "全屏歌词",
                        enabled = hasTrack,
                        active = lyricsViewOpen,
                        showDot = true,
                        size = SideButtonSize, iconSize = SideIconSize,
                        onClick = onToggleLyricsView
                    )
                    "queue" -> BarIconButton(
                        MelodiaIcons.Queue,
                        "播放队列",
                        enabled = hasTrack,
                        active = queueOpen,
                        showDot = true,
                        size = SideButtonSize, iconSize = SideIconSize,
                        onClick = onToggleQueue
                    )
                    // 设备名最后测量，只占其余按钮排完后的剩余宽度，窗口窄时自身截断而不挤掉音量等按钮
                    "device" -> if (onToggleDevices != null) {
                        OutputDeviceButton(
                            device = outputDevice,
                            remoteDeviceName = remoteDeviceName,
                            open = devicesOpen,
                            onClick = onToggleDevices,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                    }
                    // 占位播放器没有音量能力时不显示
                    "volume" -> if (volume != null) VolumeControl(volume, lastAudibleVolume, onVolumeChange)
                    "miniPlayer" -> BarIconButton(
                        MelodiaIcons.MiniPlayer, "迷你播放器", size = SideButtonSize, iconSize = SideIconSize,
                        onClick = onEnterMiniPlayer
                    )
                    "fullscreen" -> BarIconButton(
                        if (isFullscreen) MelodiaIcons.FullscreenExit else MelodiaIcons.Fullscreen,
                        if (isFullscreen) "退出全屏" else "全屏",
                        size = SideButtonSize, iconSize = SideIconSize,
                        onClick = onToggleFullscreen
                    )
                }
            }
        }
    }

    private companion object {
        const val PLUGIN_COVER_SIZE = 56f
    }
}

private const val VOLUME_THROTTLE_MS = 100L
private const val VOLUME_HOLD_MS = 450L

@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun VolumeControl(volume: Int, lastAudible: Int, onVolumeChange: (Int) -> Unit) {
    var scrollDeltaAccumulator by remember { mutableStateOf(0f) }
    var isScrollingVolume by remember { mutableStateOf(false) }
    var lastScrollPosition by remember { mutableStateOf<Offset?>(null) }
    var controlWidthPx by remember { mutableStateOf(0) }

    var dragVolume by remember { mutableStateOf<Int?>(null) }
    var isDragging by remember { mutableStateOf(false) }
    var lastSentAtMs by remember { mutableStateOf(0L) }
    var pendingEmitVolume by remember { mutableStateOf<Int?>(null) }
    var holdGeneration by remember { mutableStateOf(0) }

    val displayVolume = dragVolume ?: volume
    val currentDisplayVolume by rememberUpdatedState(displayVolume)
    val currentLastAudible by rememberUpdatedState(lastAudible)
    val currentOnVolumeChange by rememberUpdatedState(onVolumeChange)

    LaunchedEffect(isDragging, volume, dragVolume) {
        if (!isDragging && dragVolume != null && volume == dragVolume) {
            dragVolume = null
        }
    }

    LaunchedEffect(holdGeneration, isDragging) {
        if (!isDragging && dragVolume != null) {
            delay(VOLUME_HOLD_MS)
            dragVolume = null
        }
    }

    LaunchedEffect(pendingEmitVolume, isDragging) {
        val pending = pendingEmitVolume ?: return@LaunchedEffect
        if (!isDragging) return@LaunchedEffect
        val waitMs = (VOLUME_THROTTLE_MS - (System.currentTimeMillis() - lastSentAtMs)).coerceAtLeast(0L)
        if (waitMs > 0L) delay(waitMs)
        if (isDragging && pendingEmitVolume != null) {
            val latest = pendingEmitVolume!!
            pendingEmitVolume = null
            lastSentAtMs = System.currentTimeMillis()
            currentOnVolumeChange(latest)
        }
    }

    Box(
        modifier = Modifier
            .onSizeChanged { controlWidthPx = it.width }
            .onPointerEvent(PointerEventType.Scroll) { event ->
                val deltaY = event.changes.firstOrNull()?.scrollDelta?.y ?: return@onPointerEvent
                if (deltaY == 0f) return@onPointerEvent
                event.changes.forEach { it.consume() }

                isScrollingVolume = true
                lastScrollPosition = event.changes.firstOrNull()?.position

                scrollDeltaAccumulator += deltaY
                val steps = scrollDeltaAccumulator.toInt()
                if (steps != 0) {
                    scrollDeltaAccumulator -= steps
                    val currentVol = currentDisplayVolume
                    val audible = currentLastAudible
                    val newVolume = if (steps < 0) {
                        if (currentVol == 0) {
                            val base = if (audible > 0) audible else 0
                            (base + (-steps - 1) * 5 + if (base == 0) 5 else 0).coerceIn(0, 100)
                        } else {
                            (currentVol - steps * 5).coerceIn(0, 100)
                        }
                    } else {
                        (currentVol - steps * 5).coerceIn(0, 100)
                    }
                    if (newVolume != currentVol) {
                        isDragging = false
                        pendingEmitVolume = null
                        dragVolume = newVolume
                        holdGeneration++
                        lastSentAtMs = System.currentTimeMillis()
                        currentOnVolumeChange(newVolume)
                    }
                }
            }
            .onPointerEvent(PointerEventType.Move) { event ->
                val currentPos = event.changes.firstOrNull()?.position
                val anchor = lastScrollPosition
                if (anchor != null && currentPos != null && (currentPos - anchor).getDistance() > 3f) {
                    isScrollingVolume = false
                    lastScrollPosition = null
                }
            }
            .onPointerEvent(PointerEventType.Exit) {
                isScrollingVolume = false
                lastScrollPosition = null
            },
        contentAlignment = Alignment.CenterStart
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            BarIconButton(
                icon = when {
                    displayVolume == 0 -> MelodiaIcons.VolumeOff
                    displayVolume < 50 -> MelodiaIcons.VolumeDown
                    else -> MelodiaIcons.VolumeUp
                },
                description = if (displayVolume == 0) "取消静音" else "静音",
                size = SideButtonSize, iconSize = SideIconSize,
                onClick = {
                    val newVol = if (displayVolume > 0) 0 else lastAudible
                    isDragging = false
                    pendingEmitVolume = null
                    dragVolume = newVol
                    holdGeneration++
                    lastSentAtMs = System.currentTimeMillis()
                    currentOnVolumeChange(newVol)
                }
            )
            PlayerSlider(
                value = displayVolume / 100f,
                onValueChange = { fraction ->
                    val target = (fraction * 100).roundToInt().coerceIn(0, 100)
                    isDragging = true
                    dragVolume = target
                    val now = System.currentTimeMillis()
                    if (now - lastSentAtMs >= VOLUME_THROTTLE_MS) {
                        lastSentAtMs = now
                        pendingEmitVolume = null
                        currentOnVolumeChange(target)
                    } else {
                        pendingEmitVolume = target
                    }
                },
                onValueChangeFinished = {
                    val finalVol = pendingEmitVolume ?: dragVolume
                    pendingEmitVolume = null
                    isDragging = false
                    holdGeneration++
                    if (finalVol != null) {
                        lastSentAtMs = System.currentTimeMillis()
                        currentOnVolumeChange(finalVol)
                    }
                },
                modifier = Modifier.width(VolumeSliderWidth),
                previewLabel = { "${(it * 100).roundToInt()}%" },
                externalActive = isScrollingVolume
            )
        }

        if (isScrollingVolume && controlWidthPx > 0) {
            val scrollX = lastScrollPosition?.x ?: (controlWidthPx / 2f)
            Box(
                Modifier.align(Alignment.TopStart).layout { measurable, _ ->
                    val placeable = measurable.measure(Constraints())
                    val minX = 0
                    val maxX = (controlWidthPx - placeable.width).coerceAtLeast(0)
                    val left = (scrollX.roundToInt() - placeable.width / 2).coerceIn(minX, maxX)
                    layout(0, 0) {
                        placeable.place(left, -placeable.height - VolumeTooltipGap.roundToPx())
                    }
                }
            ) {
                TooltipLabel("$displayVolume%")
            }
        }
    }
}

// 正在远端设备播放时显示手机图标与对端名并常亮；本机手动指定输出设备时显示对应图标与型号；跟随系统默认时保持通用图标
@Composable
private fun OutputDeviceButton(
    device: AudioDevice?,
    remoteDeviceName: String?,
    open: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val motion = LocalUiMotion.current
    val isRemote = remoteDeviceName != null
    val icon = when {
        isRemote -> MelodiaIcons.Phone
        device != null -> device.icon()
        else -> MelodiaIcons.Devices
    }
    val tooltip = when {
        isRemote -> "在 $remoteDeviceName 上播放"
        device != null -> "输出设备：${device.description}"
        else -> "输出设备"
    }
    // 多台设备主标题都叫“扬声器”，优先显示括号里的型号
    val name = remoteDeviceName ?: device?.let { it.subtitle ?: it.title }
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        BarIconButton(
            icon,
            tooltip,
            active = open,
            highlighted = isRemote || device != null,
            showDot = true,
            animateIcon = true,
            size = SideButtonSize, iconSize = SideIconSize,
            onClick = onClick
        )
        DesktopTooltip(tooltip, modifier = Modifier.weight(1f, fill = false)) {
            // 名称为 null 时内容为空，出现、消失与换设备都由宽度过渡带动
            AnimatedContent(
                targetState = name,
                transitionSpec = {
                    motion.transform(
                        (fadeIn(tween(DEVICE_ANIM_MS)) togetherWith fadeOut(tween(DEVICE_ANIM_MS)))
                            .using(SizeTransform(clip = true) { _, _ -> tween(DEVICE_ANIM_MS) })
                    )
                },
                label = "outputDeviceName"
            ) { target ->
                if (target != null) {
                    Text(
                        target,
                        color = DesktopColors.AccentText,
                        fontSize = 12.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.widthIn(max = DeviceNameMaxWidth).pointerHoverIcon(PointerIcon.Hand)
                            .clickable(onClick = onClick).padding(end = 4.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun ProgressRow(position: Long, duration: Long, enabled: Boolean, showTime: Boolean, onSeek: (Long) -> Unit) {
    // 拖动期间以本地值为准，松手才提交，避免进度回推造成跳动
    var dragValue by remember { mutableStateOf<Float?>(null) }
    val fraction = if (duration > 0) (position.toFloat() / duration).coerceIn(0f, 1f) else 0f
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        if (showTime) TimeLabel(if (dragValue != null) (dragValue!! * duration).toLong() else position, TextAlign.End)
        PlayerSlider(
            value = dragValue ?: fraction,
            onValueChange = { dragValue = it },
            onValueChangeFinished = {
                dragValue?.let { onSeek((it * duration).toLong()) }
                dragValue = null
            },
            enabled = enabled,
            modifier = Modifier.weight(1f).padding(horizontal = if (showTime) 8.dp else 0.dp),
            previewLabel = { formatDuration((it * duration).toLong()) }
        )
        if (showTime) TimeLabel(duration, TextAlign.Start)
    }
}

@Composable
private fun TimeLabel(ms: Long, align: TextAlign) {
    // 时间贴着轨道一侧对齐：已播放右对齐，总时长左对齐
    Text(
        formatDuration(ms),
        color = DesktopColors.TextGray,
        fontSize = 11.sp,
        textAlign = align,
        maxLines = 1,
        style = numericTextStyle(),
        modifier = Modifier.width(40.dp)
    )
}

@Composable
private fun hoverScale(hovered: Boolean) =
    animateFloatAsState(if (hovered) HOVER_SCALE else 1f, uiTween(HOVER_SCALE_MS), label = "hoverScale")

// 底栏统一的图标按钮：悬停时变亮并放大；showDot 为真且处于激活态时，图标下方加指示点；highlighted 只着色不加点；
// animateIcon 为真时图标与颜色渐变切换
@Composable
private fun BarIconButton(
    icon: ImageVector,
    description: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    active: Boolean = false,
    highlighted: Boolean = false,
    showDot: Boolean = false,
    animateIcon: Boolean = false,
    size: Dp = TransportButtonSize,
    iconSize: Dp = SideIconSize
) {
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val scale by hoverScale(hovered && enabled)
    val tint by animateColorAsState(
        when {
            !enabled -> DesktopColors.SurfaceLight
            active || highlighted -> DesktopColors.Accent
            hovered -> DesktopColors.TextPrimary
            else -> DesktopColors.TextGray
        },
        if (animateIcon) uiTween(DEVICE_ANIM_MS) else snap(),
        label = "barIconTint"
    )
    DesktopTooltip(description) {
        Box(modifier.size(size), contentAlignment = Alignment.Center) {
            IconButton(
                onClick = onClick,
                enabled = enabled,
                interactionSource = interaction,
                modifier = Modifier.size(size).graphicsLayer { scaleX = scale; scaleY = scale }
            ) {
                if (animateIcon) {
                    Crossfade(icon, animationSpec = uiTween(DEVICE_ANIM_MS), label = "barIcon") { current ->
                        Icon(current, description, modifier = Modifier.size(iconSize), tint = tint)
                    }
                } else {
                    Icon(icon, description, modifier = Modifier.size(iconSize), tint = tint)
                }
            }
            if (showDot && active && enabled) {
                Box(Modifier.align(Alignment.BottomCenter).size(4.dp).clip(CircleShape).background(DesktopColors.Accent))
            }
        }
    }
}

// 播放栏之外的区域（如「正在播放」面板）只用到封面、标题、收藏、播放控制与进度，其余回调留空
@Composable
internal fun rememberPlaybackParts(controller: PlaybackController, playerViewModel: PlayerViewModel): PlayerBarParts {
    val nowPlaying by controller.nowPlaying.collectAsState()
    val isPlaying by controller.playWhenReady.collectAsState()
    val playMode by controller.playMode.collectAsState()
    val quantizedPosition = remember(controller) {
        controller.currentPosition.map { it / POSITION_STEP_MS * POSITION_STEP_MS }.distinctUntilChanged()
    }
    val position by quantizedPosition.collectAsState(controller.currentPosition.value / POSITION_STEP_MS * POSITION_STEP_MS)
    val duration by controller.duration.collectAsState()
    val podcast by playerViewModel.podcastState.collectAsState()
    val playContext by controller.playContext.collectAsState()
    val personalFmMode by playerViewModel.personalFmMode.collectAsState()
    return PlayerBarParts(
        controller = controller,
        playerViewModel = playerViewModel,
        nowPlaying = nowPlaying,
        isPlaying = isPlaying,
        playMode = playMode,
        position = position,
        duration = duration,
        isPodcast = podcast.isPodcast,
        isPersonalFm = playContext == PlaybackController.CONTEXT_PERSONAL_FM,
        personalFmMode = personalFmMode,
        volume = null,
        lastAudibleVolume = 0,
        onVolumeChange = {},
        nowPlayingOpen = true,
        onToggleNowPlaying = {},
        queueOpen = false,
        onToggleQueue = {},
        devicesOpen = false,
        onToggleDevices = null,
        outputDevice = null,
        remoteDeviceName = null,
        isFullscreen = false,
        onToggleFullscreen = {},
        lyricVisible = false,
        onToggleLyric = {},
        lyricsViewOpen = false,
        onToggleLyricsView = {},
        onEnterMiniPlayer = {}
    )
}

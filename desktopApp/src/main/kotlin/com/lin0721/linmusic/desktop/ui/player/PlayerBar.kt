package com.lin0721.linmusic.desktop.ui.player

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.Crossfade
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
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.VolumeDown
import androidx.compose.material.icons.automirrored.rounded.VolumeOff
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.AddCircleOutline
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.Fullscreen
import androidx.compose.material.icons.rounded.FullscreenExit
import androidx.compose.material.icons.rounded.PictureInPictureAlt
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.RepeatOne
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.TextUnit
import com.lin0721.linmusic.core.player.NowPlaying
import com.lin0721.linmusic.core.plugin.CONTROL_BUTTONS
import com.lin0721.linmusic.core.plugin.CONTROL_PLAY_PAUSE
import com.lin0721.linmusic.core.plugin.LayoutNodeType
import com.lin0721.linmusic.core.plugin.LocalPlayerLayouts
import com.lin0721.linmusic.core.plugin.PlayerLayoutArea
import com.lin0721.linmusic.core.plugin.PlayerLayoutNode
import com.lin0721.linmusic.core.plugin.PlayerLayoutView
import com.lin0721.linmusic.core.plugin.layoutShape
import com.lin0721.linmusic.core.format.formatDuration
import com.lin0721.linmusic.core.player.PlayMode
import com.lin0721.linmusic.core.player.PlaybackController
import com.lin0721.linmusic.core.ui.theme.outlinedControls
import com.lin0721.linmusic.core.ui.theme.styledButtonShape
import com.lin0721.linmusic.desktop.player.AudioDevice
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
import com.lin0721.linmusic.desktop.ui.theme.DesktopDimens
import com.lin0721.linmusic.desktop.ui.track.CollectToPlaylistPopup
import com.lin0721.linmusic.feature.player.ui.PlayerViewModel
import kotlin.math.roundToInt
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

private const val HOVER_SCALE = 1.1f
private const val HOVER_SCALE_MS = 150
private val TransportButtonSize = 36.dp
private val SideButtonSize = 32.dp

// 右侧与红心按钮的图标比切歌按钮的字形占格更满，缩小图标才能看起来一样大
private val SideIconSize = 20.dp
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
    modifier: Modifier = Modifier
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
    val parts = PlayerBarParts(
        controller = controller,
        playerViewModel = playerViewModel,
        nowPlaying = nowPlaying,
        isPlaying = isPlaying,
        playMode = playMode,
        position = position,
        duration = duration,
        isPodcast = podcast.isPodcast,
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
        isFullscreen = isFullscreen,
        onToggleFullscreen = onToggleFullscreen,
        lyricVisible = lyricVisible,
        onToggleLyric = onToggleLyric,
        lyricsViewOpen = lyricsViewOpen,
        onToggleLyricsView = onToggleLyricsView,
        onEnterMiniPlayer = onEnterMiniPlayer
    )
    val pluginLayout = LocalPlayerLayouts.current[PlayerLayoutArea.DESKTOP_PLAYER_BAR]
    if (pluginLayout != null) {
        PluginPlayerBar(pluginLayout, parts, modifier)
        return
    }

    Row(
        modifier.fillMaxWidth().height(DesktopDimens.PlayerBarHeight).padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(Modifier.weight(0.27f), verticalAlignment = Alignment.CenterVertically) {
            if (nowPlaying != null) {
                parts.CoverButton(56.dp)
                Column(Modifier.weight(1f, fill = false).padding(start = 12.dp)) {
                    parts.Title(14.sp)
                    parts.Artists(12.sp)
                }
                parts.LikeButton(Modifier.padding(start = 4.dp))
            }
        }
        Column(Modifier.weight(0.4f), horizontalAlignment = Alignment.CenterHorizontally) {
            parts.Controls(CONTROL_BUTTONS)
            parts.Progress(showTime = true)
        }
        parts.Actions(DesktopBarActions, Modifier.weight(0.33f))
    }
}

private val DesktopBarActions = PlayerLayoutArea.DESKTOP_PLAYER_BAR.actionButtons
private const val MIN_PLUGIN_BAR_HEIGHT = 64f
private const val MAX_PLUGIN_BAR_HEIGHT = 160f

// 插件布局：根节点高度即播放栏高度，限制在合理范围内；没有歌曲时封面、标题等组件留空
@Composable
private fun PluginPlayerBar(root: PlayerLayoutNode, parts: PlayerBarParts, modifier: Modifier) {
    val height = (root.height ?: DesktopDimens.PlayerBarHeight.value).coerceIn(MIN_PLUGIN_BAR_HEIGHT, MAX_PLUGIN_BAR_HEIGHT).dp
    PlayerLayoutView(root.copy(height = null), modifier.fillMaxWidth().height(height)) { node, nodeModifier ->
        parts.Component(node, nodeModifier)
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
    val isFullscreen: Boolean,
    val onToggleFullscreen: () -> Unit,
    val lyricVisible: Boolean,
    val onToggleLyric: () -> Unit,
    val lyricsViewOpen: Boolean,
    val onToggleLyricsView: () -> Unit,
    val onEnterMiniPlayer: () -> Unit
) {
    private val hasTrack: Boolean get() = nowPlaying != null

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
            LayoutNodeType.CONTROLS -> Controls(node.buttons, modifier, scale)
            LayoutNodeType.PROGRESS -> Box(modifier) { Progress(node.showTime) }
            LayoutNodeType.ACTIONS -> Actions(node.buttons, modifier)
            else -> Unit
        }
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
    fun Artists(fontSize: TextUnit) {
        val track = nowPlaying ?: return
        NowPlayingArtists(track, playerViewModel, fontSize)
    }

    // 歌曲为收藏到歌单，播客为订阅所属电台
    @Composable
    fun LikeButton(modifier: Modifier = Modifier) {
        val track = nowPlaying ?: return
        val navigator = LocalDesktopNavigator.current
        val songDetail by playerViewModel.songDetailState.collectAsState()
        val podcast by playerViewModel.podcastState.collectAsState()
        if (podcast.isPodcast) {
            if (podcast.canSubscribe) {
                BarIconButton(
                    icon = if (podcast.subscribed) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
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
                icon = if (songDetail.isLiked) Icons.Rounded.CheckCircle else Icons.Rounded.AddCircleOutline,
                description = "收藏到歌单",
                active = songDetail.isLiked,
                size = SideButtonSize, iconSize = SideIconSize,
                modifier = modifier,
                onClick = {
                    if (!navigator.isLoggedIn) {
                        navigator.showMessage("请先登录账号")
                    } else {
                        track.songId?.let { songId ->
                            playerViewModel.prepareCollectDialog(songId)
                            showCollectPopup = true
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

    // 播客没有随机与循环，对应按钮不显示
    @Composable
    fun Controls(buttons: List<String>, modifier: Modifier = Modifier, scale: Float = 1f) {
        val transportSize = TransportButtonSize * scale
        Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp * scale)) {
            val outlined = outlinedControls
            val transportOutline = if (outlined) Modifier.border(1.dp, DesktopColors.Outline, CircleShape) else Modifier
            buttons.forEach { button ->
                when (button) {
                    "shuffle" -> if (!isPodcast) BarIconButton(
                        Icons.Rounded.Shuffle,
                        "随机播放",
                        enabled = hasTrack,
                        active = playMode == PlayMode.SHUFFLE,
                        showDot = true,
                        size = transportSize,
                        onClick = controller::toggleShuffle
                    )
                    "previous" -> BarIconButton(
                        Icons.Rounded.SkipPrevious, "上一首", enabled = hasTrack, size = transportSize,
                        onClick = controller::skipToPrevious, modifier = transportOutline
                    )
                    CONTROL_PLAY_PAUSE -> PlayPauseButton(transportSize, outlined)
                    "next" -> BarIconButton(
                        Icons.Rounded.SkipNext, "下一首", enabled = hasTrack, size = transportSize,
                        onClick = controller::playNext, modifier = transportOutline
                    )
                    "repeat" -> if (!isPodcast) BarIconButton(
                        if (playMode == PlayMode.SINGLE_LOOP) Icons.Rounded.RepeatOne else Icons.Rounded.Repeat,
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
                PlayPauseIcon(isPlaying, if (outlined) playActive else DesktopColors.Pane, contentDescription = if (isPlaying) "暂停" else "播放")
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
                        PlayerBarIcons.Queue,
                        "播放队列",
                        enabled = hasTrack,
                        active = queueOpen,
                        showDot = true,
                        size = SideButtonSize, iconSize = SideIconSize,
                        onClick = onToggleQueue
                    )
                    // 设备名最后测量，只占其余按钮排完后的剩余宽度，窗口窄时自身截断而不挤掉音量等按钮
                    "device" -> if (onToggleDevices != null) {
                        OutputDeviceButton(outputDevice, devicesOpen, onToggleDevices, Modifier.weight(1f, fill = false))
                    }
                    // 占位播放器没有音量能力时不显示
                    "volume" -> if (volume != null) VolumeControl(volume, lastAudibleVolume, onVolumeChange)
                    "miniPlayer" -> BarIconButton(
                        Icons.Rounded.PictureInPictureAlt, "迷你播放器", size = SideButtonSize, iconSize = SideIconSize,
                        onClick = onEnterMiniPlayer
                    )
                    "fullscreen" -> BarIconButton(
                        if (isFullscreen) Icons.Rounded.FullscreenExit else Icons.Rounded.Fullscreen,
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

@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun VolumeControl(volume: Int, lastAudible: Int, onVolumeChange: (Int) -> Unit) {
    var scrollDeltaAccumulator by remember { mutableStateOf(0f) }
    var isScrollingVolume by remember { mutableStateOf(false) }
    var lastScrollPosition by remember { mutableStateOf<Offset?>(null) }
    var controlWidthPx by remember { mutableStateOf(0) }

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
                    val newVolume = if (steps < 0) {
                        if (volume == 0) {
                            val base = if (lastAudible > 0) lastAudible else 0
                            (base + (-steps - 1) * 5 + if (base == 0) 5 else 0).coerceIn(0, 100)
                        } else {
                            (volume - steps * 5).coerceIn(0, 100)
                        }
                    } else {
                        (volume - steps * 5).coerceIn(0, 100)
                    }
                    if (newVolume != volume) {
                        onVolumeChange(newVolume)
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
                    volume == 0 -> Icons.AutoMirrored.Rounded.VolumeOff
                    volume < 50 -> Icons.AutoMirrored.Rounded.VolumeDown
                    else -> Icons.AutoMirrored.Rounded.VolumeUp
                },
                description = if (volume == 0) "取消静音" else "静音",
                size = SideButtonSize, iconSize = SideIconSize,
                onClick = {
                    if (volume > 0) {
                        onVolumeChange(0)
                    } else {
                        onVolumeChange(lastAudible)
                    }
                }
            )
            PlayerSlider(
                value = volume / 100f,
                onValueChange = { onVolumeChange((it * 100).roundToInt()) },
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
                TooltipLabel("$volume%")
            }
        }
    }
}

// 手动指定了输出设备时换成对应类型图标并常亮，右侧跟设备名；跟随系统默认时保持通用图标
@Composable
private fun OutputDeviceButton(device: AudioDevice?, open: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val motion = LocalUiMotion.current
    val tooltip = device?.let { "输出设备：${it.description}" } ?: "输出设备"
    // 多台设备主标题都叫“扬声器”，优先显示括号里的型号
    val name = device?.let { it.subtitle ?: it.title }
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        BarIconButton(
            device?.icon() ?: PlayerBarIcons.Devices,
            tooltip,
            active = open,
            highlighted = device != null,
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
    iconSize: Dp = 24.dp
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
    return PlayerBarParts(
        controller = controller,
        playerViewModel = playerViewModel,
        nowPlaying = nowPlaying,
        isPlaying = isPlaying,
        playMode = playMode,
        position = position,
        duration = duration,
        isPodcast = podcast.isPodcast,
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
        isFullscreen = false,
        onToggleFullscreen = {},
        lyricVisible = false,
        onToggleLyric = {},
        lyricsViewOpen = false,
        onToggleLyricsView = {},
        onEnterMiniPlayer = {}
    )
}

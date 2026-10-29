package com.lin0721.linmusic.desktop.ui.miniplayer

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.OpenInFull
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.RepeatOne
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.awt.awtEventOrNull
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.WindowScope
import com.lin0721.linmusic.core.format.formatDuration
import com.lin0721.linmusic.core.player.NowPlaying
import com.lin0721.linmusic.core.player.PlayMode
import com.lin0721.linmusic.core.player.PlaybackController
import com.lin0721.linmusic.core.player.floatinglyric.FloatingLyricAlign
import com.lin0721.linmusic.core.player.floatinglyric.FloatingLyricSecondary
import com.lin0721.linmusic.core.player.domain.LyricLine
import com.lin0721.linmusic.core.ui.skin.CoverGroup
import com.lin0721.linmusic.core.ui.skin.coverBase
import com.lin0721.linmusic.desktop.data.MiniLyricSettings
import com.lin0721.linmusic.desktop.data.MiniPlayerStyle
import com.lin0721.linmusic.desktop.ui.components.Cover
import com.lin0721.linmusic.desktop.ui.components.HoverReveal
import com.lin0721.linmusic.desktop.ui.components.LocalUiMotion
import com.lin0721.linmusic.desktop.ui.components.LocalWindowEffects
import com.lin0721.linmusic.desktop.ui.components.PlayPauseIcon
import com.lin0721.linmusic.desktop.ui.components.PointerTravel
import com.lin0721.linmusic.desktop.ui.components.uiTween
import com.lin0721.linmusic.desktop.ui.lyricsview.KaraokeText
import com.lin0721.linmusic.desktop.ui.lyricsview.rememberLyricClock
import com.lin0721.linmusic.desktop.ui.palette.FallbackCoverPalette
import com.lin0721.linmusic.desktop.ui.palette.ImmersiveSurface
import com.lin0721.linmusic.desktop.ui.palette.LocalImmersiveColors
import com.lin0721.linmusic.desktop.ui.palette.extractCoverPaletteFromUrl
import com.lin0721.linmusic.desktop.ui.palette.rememberImmersiveColors
import com.lin0721.linmusic.desktop.ui.palette.themedCoverBackground
import com.lin0721.linmusic.desktop.ui.player.PlayerSlider
import com.lin0721.linmusic.desktop.ui.shell.WindowDragArea
import com.lin0721.linmusic.desktop.ui.theme.DesktopColors
import com.lin0721.linmusic.feature.player.ui.PlayerViewModel
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import java.awt.Cursor
import kotlin.math.roundToInt

// mpv 每秒上报几十次播放位置，迷你窗无需这么密
private const val POSITION_STEP_MS = 250L
private const val BACKGROUND_DARKEN_FRACTION = 0.55f
private const val BACKGROUND_ANIM_MS = 600
private const val OVERLAY_ANIM_MS = 150
private const val LYRIC_SCROLL_MS = 450
private const val LYRIC_LINE_HEIGHT_SCALE = 1.375f
private const val LYRIC_ANCHOR_FRACTION = 0.3f
private const val LYRIC_FADE_FRACTION = 0.14f
private const val INACTIVE_LYRIC_ALPHA = 0.32f
private const val KARAOKE_UNSUNG_ALPHA = 0.45f
private const val SECONDARY_LYRIC_SCALE = 0.75f
private const val ACTIVE_LYRIC_SCALE = 1.1f
private const val MIN_LYRIC_FONT_FACTOR = 0.85f
private const val MAX_LYRIC_FONT_FACTOR = 1.8f
private const val PORTRAIT_RATIO = 1.3f
private const val PORTRAIT_COVER_MAX_FRACTION = 0.5f

private val ButtonSize = 28.dp
private val EdgeZone = 6.dp
private val CornerZone = 12.dp
private val BarNarrowWidth = 320.dp
private val BarWideWidth = 480.dp
private val LyricLineGap = 10.dp
// 横版默认窗口下歌词区的宽度
private val LyricBaseWidth = 332.dp
private val PortraitPadding = 14.dp
// 大封面显示尺寸随拖动连续变化，固定请求尺寸，否则每帧换缩略图地址、拖动中封面一直在重新加载
private val LargeCoverRequest = 320.dp
private val WindowShape = RoundedCornerShape(14.dp)
private val ChromeShape = RoundedCornerShape(8.dp)
private val ChromeBackground: Color @Composable @ReadOnlyComposable get() = LocalImmersiveColors.current.scrim.copy(alpha = 0.35f)
private val CardScrim: Color @Composable @ReadOnlyComposable get() = LocalImmersiveColors.current.scrim.copy(alpha = 0.63f)
private val WindowBorder: Color @Composable @ReadOnlyComposable get() = DesktopColors.Divider
private val ContentColor: Color @Composable @ReadOnlyComposable get() = LocalImmersiveColors.current.content
private val SubtleText: Color @Composable @ReadOnlyComposable get() = ContentColor.copy(alpha = 0.62f)
private val SecondaryLyricText: Color @Composable @ReadOnlyComposable get() = ContentColor.copy(alpha = 0.6f)
private val IdleIcon: Color @Composable @ReadOnlyComposable get() = ContentColor.copy(alpha = 0.8f)
private val DisabledIcon: Color @Composable @ReadOnlyComposable get() = ContentColor.copy(alpha = 0.3f)

class MiniResizeCallbacks(
    val onStart: (MiniResizeHandle) -> Unit,
    // 相对按下位置的累计屏幕位移（dp）
    val onDrag: (MiniResizeHandle, Float, Float) -> Unit,
    val onEnd: (MiniResizeHandle) -> Unit
)

class MiniPlayerActions(
    val onPrevious: () -> Unit,
    val onTogglePlay: () -> Unit,
    val onNext: () -> Unit,
    val onToggleRepeat: () -> Unit,
    val onToggleLike: () -> Unit,
    val onSeek: (Long) -> Unit,
    val onSwitchStyle: () -> Unit,
    val onOpenLyricSettings: () -> Unit,
    val onRestore: () -> Unit
)

private class MiniTrackInfo(
    val track: NowPlaying?,
    val isPlaying: Boolean,
    val playMode: PlayMode,
    val position: Long,
    val duration: Long,
    val liked: Boolean,
    val isPodcast: Boolean
) {
    val hasTrack: Boolean get() = track != null
    val showLike: Boolean get() = hasTrack && !isPodcast
    val showRepeat: Boolean get() = hasTrack && !isPodcast
    val fraction: Float get() = if (duration > 0) (position.toFloat() / duration).coerceIn(0f, 1f) else 0f
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun WindowScope.MiniPlayerContent(
    style: MiniPlayerStyle,
    lyricSettings: MiniLyricSettings,
    controller: PlaybackController,
    playerViewModel: PlayerViewModel,
    actions: MiniPlayerActions,
    resize: MiniResizeCallbacks,
    resizeDensity: Density
) {
    val nowPlaying by controller.nowPlaying.collectAsState()
    val isPlaying by controller.playWhenReady.collectAsState()
    val playMode by controller.playMode.collectAsState()
    val quantizedPosition = remember(controller) {
        controller.currentPosition.map { it / POSITION_STEP_MS * POSITION_STEP_MS }.distinctUntilChanged()
    }
    val position by quantizedPosition.collectAsState(controller.currentPosition.value / POSITION_STEP_MS * POSITION_STEP_MS)
    val duration by controller.duration.collectAsState()
    val detail by playerViewModel.songDetailState.collectAsState()
    val podcast by playerViewModel.podcastState.collectAsState()
    val info = MiniTrackInfo(nowPlaying, isPlaying, playMode, position, duration, detail.isLiked, podcast.isPodcast)

    // 鼠标在窗口内即视为悬停，能点到按钮时按钮必然已显示
    var hovered by remember { mutableStateOf(false) }

    val artwork = nowPlaying?.artworkUri
    var coverBase by remember { mutableStateOf(FallbackCoverPalette.base) }
    LaunchedEffect(artwork) {
        coverBase = if (artwork.isNullOrBlank()) FallbackCoverPalette.base else extractCoverPaletteFromUrl(artwork).base
    }
    val tintBase = coverBase(CoverGroup.PLAYER, coverBase) ?: coverBase
    val immersive = rememberImmersiveColors(tintBase)
    val background by animateColorAsState(
        if (DesktopColors.IsDark) themedCoverBackground(tintBase, BACKGROUND_DARKEN_FRACTION) else immersive.fill,
        uiTween(BACKGROUND_ANIM_MS),
        label = "miniPlayerBackground"
    )

    ImmersiveSurface(immersive) {
        Box(
            Modifier.fillMaxSize()
                .clip(WindowShape)
                .background(background)
                .border(0.5.dp, WindowBorder, WindowShape)
                .onPointerEvent(PointerEventType.Enter) { hovered = true }
                .onPointerEvent(PointerEventType.Exit) { hovered = false }
        ) {
            WindowDragArea(Modifier.fillMaxSize()) {
                Box(Modifier.fillMaxSize().restoreOnDoubleClick(actions.onRestore)) {
                    when (style) {
                        MiniPlayerStyle.BAR -> BarLayout(info, hovered, actions)
                        MiniPlayerStyle.CARD -> CardLayout(info, hovered, actions)
                        MiniPlayerStyle.LYRIC -> {
                            val lyricIndex by playerViewModel.currentLyricIndex.collectAsState()
                            val fallback = when {
                                !info.hasTrack -> "未在播放"
                                podcast.isPodcast -> podcast.radioName.ifBlank { "播客" }
                                detail.isLyricsLoading -> "歌词加载中"
                                else -> "暂无歌词"
                            }
                            LyricLayout(info, hovered, actions) {
                                LyricLines(
                                    lyrics = if (podcast.isPodcast) emptyList() else detail.lyrics,
                                    index = lyricIndex,
                                    fallback = fallback,
                                    settings = lyricSettings,
                                    isPlaying = isPlaying,
                                    positionProvider = rememberLyricClock(controller),
                                    modifier = Modifier.weight(1f).fillMaxWidth()
                                )
                            }
                        }
                    }
                }
            }
            // 等比缩放会改内容密度，而密度一变 pointerInput 就会重置、拖动当场中断；感应区固定用不随缩放变的密度
            CompositionLocalProvider(LocalDensity provides resizeDensity) {
                Box(Modifier.matchParentSize()) { ResizeZones(style, resize) }
            }
        }
    }
}

// 感应区不可见，只改光标；盖在内容之上，贴边的按钮让出这几 dp
@Composable
private fun BoxScope.ResizeZones(style: MiniPlayerStyle, resize: MiniResizeCallbacks) {
    if (style.allows(MiniResizeHandle.LEFT)) {
        Box(Modifier.align(Alignment.CenterStart).width(EdgeZone).fillMaxHeight().resizeDrag(MiniResizeHandle.LEFT, Cursor.W_RESIZE_CURSOR, resize))
    }
    if (style.allows(MiniResizeHandle.RIGHT)) {
        Box(Modifier.align(Alignment.CenterEnd).width(EdgeZone).fillMaxHeight().resizeDrag(MiniResizeHandle.RIGHT, Cursor.E_RESIZE_CURSOR, resize))
    }
    if (style.allows(MiniResizeHandle.TOP)) {
        Box(Modifier.align(Alignment.TopCenter).fillMaxWidth().height(EdgeZone).resizeDrag(MiniResizeHandle.TOP, Cursor.N_RESIZE_CURSOR, resize))
    }
    if (style.allows(MiniResizeHandle.BOTTOM)) {
        Box(Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(EdgeZone).resizeDrag(MiniResizeHandle.BOTTOM, Cursor.S_RESIZE_CURSOR, resize))
    }
    Box(Modifier.align(Alignment.BottomEnd).size(CornerZone).resizeDrag(MiniResizeHandle.CORNER, Cursor.SE_RESIZE_CURSOR, resize))
}

// 拖左/上边时窗口原点跟着移动，局部坐标会漂；改用屏幕坐标算累计位移
@Composable
private fun Modifier.resizeDrag(handle: MiniResizeHandle, cursor: Int, resize: MiniResizeCallbacks): Modifier {
    val callbacks by rememberUpdatedState(resize)
    val effects = LocalWindowEffects.current
    return pointerHoverIcon(PointerIcon(Cursor(cursor))).pointerInput(handle, effects) {
        awaitEachGesture {
            awaitFirstDown(requireUnconsumed = false).consume()
            val travel = PointerTravel.start(effects) ?: return@awaitEachGesture
            callbacks.onStart(handle)
            try {
                while (true) {
                    val event = awaitPointerEvent()
                    event.changes.forEach { it.consume() }
                    if (event.changes.none { it.pressed }) break
                    val delta = travel.delta() ?: continue
                    callbacks.onDrag(handle, delta.x, delta.y)
                }
            } finally {
                callbacks.onEnd(handle)
            }
        }
    }
}

// 按钮已消费的按下不算，连点按钮时不会误触还原
@OptIn(ExperimentalComposeUiApi::class)
private fun Modifier.restoreOnDoubleClick(action: () -> Unit): Modifier =
    onPointerEvent(PointerEventType.Press) { event ->
        if (event.awtEventOrNull?.clickCount == 2 && event.changes.none { it.isConsumed }) action()
    }

// 窄时收起歌手名；宽时进度条两侧显示时间，喜欢与循环常驻
@Composable
private fun BarLayout(info: MiniTrackInfo, hovered: Boolean, actions: MiniPlayerActions) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val wide = maxWidth >= BarWideWidth
        val narrow = maxWidth < BarNarrowWidth
        Row(Modifier.fillMaxSize().padding(start = 12.dp, end = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Cover(info.track?.artworkUri, 48.dp, shape = RoundedCornerShape(8.dp))
            Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                TrackTitle(info, 13.sp)
                if (!narrow) TrackArtist(info)
                if (wide) {
                    Row(Modifier.fillMaxWidth().padding(top = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                        TimeText(info.position)
                        MiniProgress(info, actions, Modifier.weight(1f).padding(horizontal = 8.dp))
                        TimeText(info.duration)
                    }
                } else {
                    MiniProgress(info, actions, Modifier.fillMaxWidth().padding(top = 2.dp))
                }
            }
            HoverReveal(revealed = (hovered || wide) && info.showLike, reserveSpace = false) {
                LikeButton(info, actions)
            }
            TransportControls(info, actions, playSize = 32.dp, spacing = 2.dp)
            if (wide && info.showRepeat) RepeatButton(info, actions)
            HoverReveal(revealed = hovered, reserveSpace = false) {
                WindowButtons(actions, vertical = true, modifier = Modifier.padding(start = 6.dp))
            }
        }
    }
}

@Composable
private fun TimeText(ms: Long) {
    Text(formatDuration(ms), color = SubtleText, fontSize = 11.sp, maxLines = 1)
}

@Composable
private fun CardLayout(info: MiniTrackInfo, hovered: Boolean, actions: MiniPlayerActions) {
    val overlayAlpha by animateFloatAsState(if (hovered) 1f else 0f, uiTween(OVERLAY_ANIM_MS), label = "miniCardOverlay")
    Box(Modifier.fillMaxSize()) {
        Cover(info.track?.artworkUri, MiniPlayerStyle.CARD.width.dp, shape = RectangleShape, requestSize = LargeCoverRequest)
        Box(
            Modifier.align(Alignment.BottomStart).fillMaxWidth().height(3.dp)
                .graphicsLayer { alpha = 1f - overlayAlpha }
                .background(LocalImmersiveColors.current.scrim.copy(alpha = 0.25f))
        ) {
            Box(Modifier.fillMaxWidth(info.fraction).fillMaxHeight().background(ContentColor))
        }
        Column(
            Modifier.fillMaxSize()
                .graphicsLayer { alpha = overlayAlpha }
                .background(CardScrim)
                .padding(14.dp),
            verticalArrangement = Arrangement.Bottom
        ) {
            TrackTitle(info, 15.sp)
            TrackArtist(info)
            Row(
                Modifier.fillMaxWidth().padding(top = 10.dp, bottom = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (info.showLike) LikeButton(info, actions) else Spacer(Modifier.size(ButtonSize))
                TransportControls(info, actions, playSize = 40.dp, spacing = 6.dp)
                if (info.showRepeat) RepeatButton(info, actions) else Spacer(Modifier.size(ButtonSize))
            }
            MiniProgress(info, actions, Modifier.fillMaxWidth())
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                TimeText(info.position)
                TimeText(info.duration)
            }
        }
        HoverReveal(revealed = hovered, modifier = Modifier.align(Alignment.TopEnd).padding(8.dp)) {
            WindowButtons(actions)
        }
    }
}

// 高明显大于宽时改竖版：大封面在上，歌词铺满剩余高度
@Composable
private fun LyricLayout(
    info: MiniTrackInfo,
    hovered: Boolean,
    actions: MiniPlayerActions,
    lyrics: @Composable ColumnScope.() -> Unit
) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        if (maxHeight > maxWidth * PORTRAIT_RATIO) {
            val coverSize = minOf(maxWidth - PortraitPadding * 2, maxHeight * PORTRAIT_COVER_MAX_FRACTION)
            Column(Modifier.fillMaxSize().padding(PortraitPadding)) {
                Box(Modifier.align(Alignment.CenterHorizontally)) {
                    Cover(info.track?.artworkUri, coverSize, shape = RoundedCornerShape(10.dp), requestSize = LargeCoverRequest)
                    HoverReveal(revealed = hovered, modifier = Modifier.align(Alignment.TopEnd).padding(6.dp)) {
                        WindowButtons(actions, onOpenLyricSettings = actions.onOpenLyricSettings)
                    }
                }
                Column(Modifier.fillMaxWidth().padding(top = 12.dp)) {
                    TrackTitle(info, 15.sp)
                    TrackArtist(info)
                }
                lyrics()
                MiniProgress(info, actions, Modifier.fillMaxWidth())
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    TimeText(info.position)
                    TimeText(info.duration)
                }
                Row(
                    Modifier.fillMaxWidth().padding(top = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (info.showLike) LikeButton(info, actions) else Spacer(Modifier.size(ButtonSize))
                    TransportControls(info, actions, playSize = 40.dp, spacing = 8.dp)
                    if (info.showRepeat) RepeatButton(info, actions) else Spacer(Modifier.size(ButtonSize))
                }
            }
        } else {
            Column(Modifier.fillMaxSize().padding(horizontal = 14.dp, vertical = 12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Cover(info.track?.artworkUri, 44.dp, shape = RoundedCornerShape(8.dp))
                    Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                        TrackTitle(info, 14.sp)
                        TrackArtist(info)
                    }
                    HoverReveal(revealed = hovered) {
                        WindowButtons(actions, onOpenLyricSettings = actions.onOpenLyricSettings)
                    }
                }
                lyrics()
                MiniProgress(info, actions, Modifier.fillMaxWidth())
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterHorizontally),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (info.showLike) LikeButton(info, actions) else Spacer(Modifier.size(ButtonSize))
                    TransportControls(info, actions, playSize = 32.dp, spacing = 8.dp)
                    if (info.showRepeat) RepeatButton(info, actions) else Spacer(Modifier.size(ButtonSize))
                }
            }
        }
    }
}

// 整首歌词同字号排成一列，当前行停在约三分之一高处，其余行压暗，上下边缘渐隐；换行时整列平滑滚动
@Composable
private fun LyricLines(
    lyrics: List<LyricLine>,
    index: Int,
    fallback: String,
    settings: MiniLyricSettings,
    isPlaying: Boolean,
    positionProvider: () -> Long,
    modifier: Modifier
) {
    if (lyrics.isEmpty()) {
        Box(modifier, contentAlignment = Alignment.Center) {
            Text(fallback, color = SubtleText, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        return
    }
    val motion = LocalUiMotion.current
    val textAlign = settings.align.toTextAlign()
    val horizontal = settings.align.toHorizontal()
    val lineTops = remember(lyrics) { mutableStateMapOf<Int, Int>() }
    val scroll = remember(lyrics) { Animatable(0f) }
    var positioned by remember(lyrics) { mutableStateOf(false) }
    var viewportHeight by remember { mutableStateOf(0) }
    // 前奏阶段 index 为 -1，先对准第一行
    val focus = index.coerceIn(0, lyrics.lastIndex)
    val focusTop = lineTops[focus]
    LaunchedEffect(focus, focusTop, viewportHeight) {
        val top = focusTop ?: return@LaunchedEffect
        if (viewportHeight == 0) return@LaunchedEffect
        val target = viewportHeight * LYRIC_ANCHOR_FRACTION - top
        if (positioned && motion.enabled) {
            scroll.animateTo(target, tween(LYRIC_SCROLL_MS))
        } else {
            scroll.snapTo(target)
            positioned = true
        }
    }
    BoxWithConstraints(modifier.clipToBounds().onSizeChanged { viewportHeight = it.height }.fadeVerticalEdges()) {
        // 字号随歌词区宽度放大，以横版默认宽度为基准
        val fontSp = settings.fontSize.sp * (maxWidth / LyricBaseWidth).coerceIn(MIN_LYRIC_FONT_FACTOR, MAX_LYRIC_FONT_FACTOR)
        val origin = settings.align.toTransformOrigin()
        Column(
            Modifier.fillMaxWidth()
                .wrapContentHeight(align = Alignment.Top, unbounded = true)
                .offset { IntOffset(0, scroll.value.roundToInt()) },
            verticalArrangement = Arrangement.spacedBy(LyricLineGap),
            horizontalAlignment = horizontal
        ) {
            lyrics.forEachIndexed { lineIndex, line ->
                key(lineIndex) {
                    val active = lineIndex == index
                    val alpha by animateFloatAsState(
                        when {
                            active -> 1f
                            settings.showSideLines -> INACTIVE_LYRIC_ALPHA
                            else -> 0f
                        },
                        uiTween(LYRIC_SCROLL_MS),
                        label = "miniLyricAlpha"
                    )
                    val scale by animateFloatAsState(
                        if (active) ACTIVE_LYRIC_SCALE else 1f,
                        uiTween(LYRIC_SCROLL_MS),
                        label = "miniLyricScale"
                    )
                    // 行宽预留放大余量，当前行放大后不会被裁掉
                    Column(
                        Modifier.fillMaxWidth(1f / ACTIVE_LYRIC_SCALE)
                            .onPlaced { lineTops[lineIndex] = it.positionInParent().y.roundToInt() }
                            .graphicsLayer {
                                this.alpha = alpha
                                scaleX = scale
                                scaleY = scale
                                transformOrigin = origin
                            },
                        horizontalAlignment = horizontal
                    ) {
                        if (active && settings.karaoke && line.words.isNotEmpty()) {
                            KaraokeText(
                                line = line,
                                positionProvider = positionProvider,
                                inactiveColor = ContentColor.copy(alpha = KARAOKE_UNSUNG_ALPHA),
                                activeColor = ContentColor,
                                fontSize = fontSp.sp,
                                lineHeight = (fontSp * LYRIC_LINE_HEIGHT_SCALE).sp,
                                textAlign = textAlign,
                                isPlaying = isPlaying,
                                fontWeight = FontWeight.Bold
                            )
                        } else {
                            Text(
                                line.text,
                                color = ContentColor,
                                fontSize = fontSp.sp,
                                lineHeight = (fontSp * LYRIC_LINE_HEIGHT_SCALE).sp,
                                fontWeight = FontWeight.Bold,
                                textAlign = textAlign,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                        val secondary = when (settings.secondary) {
                            FloatingLyricSecondary.TRANSLATION -> line.translation
                            FloatingLyricSecondary.ROMA -> line.roma
                            else -> null
                        }
                        if (!secondary.isNullOrBlank()) {
                            Text(
                                secondary,
                                color = SecondaryLyricText,
                                fontSize = (fontSp * SECONDARY_LYRIC_SCALE).sp,
                                fontWeight = FontWeight.Medium,
                                textAlign = textAlign,
                                modifier = Modifier.fillMaxWidth().padding(top = 2.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun FloatingLyricAlign.toTextAlign(): TextAlign = when (this) {
    FloatingLyricAlign.START -> TextAlign.Start
    FloatingLyricAlign.CENTER -> TextAlign.Center
    FloatingLyricAlign.END -> TextAlign.End
}

// 放大的基准点跟随对齐方式
private fun FloatingLyricAlign.toTransformOrigin(): TransformOrigin = when (this) {
    FloatingLyricAlign.START -> TransformOrigin(0f, 0.5f)
    FloatingLyricAlign.CENTER -> TransformOrigin(0.5f, 0.5f)
    FloatingLyricAlign.END -> TransformOrigin(1f, 0.5f)
}

private fun FloatingLyricAlign.toHorizontal(): Alignment.Horizontal = when (this) {
    FloatingLyricAlign.START -> Alignment.Start
    FloatingLyricAlign.CENTER -> Alignment.CenterHorizontally
    FloatingLyricAlign.END -> Alignment.End
}

// 离屏合成后用渐变做遮罩，上下边缘的歌词自然淡出
private fun Modifier.fadeVerticalEdges(): Modifier =
    graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
        .drawWithContent {
            drawContent()
            drawRect(
                Brush.verticalGradient(
                    0f to Color.Transparent,
                    LYRIC_FADE_FRACTION to Color.Black,
                    1f - LYRIC_FADE_FRACTION to Color.Black,
                    1f to Color.Transparent
                ),
                blendMode = BlendMode.DstIn
            )
        }

@Composable
private fun TrackTitle(info: MiniTrackInfo, fontSize: TextUnit) {
    Text(
        info.track?.title ?: "未在播放",
        color = ContentColor,
        fontSize = fontSize,
        fontWeight = FontWeight.Medium,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis
    )
}

@Composable
private fun TrackArtist(info: MiniTrackInfo) {
    val artist = info.track?.artist
    if (artist.isNullOrBlank()) return
    Text(artist, color = SubtleText, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
}

@Composable
private fun MiniProgress(info: MiniTrackInfo, actions: MiniPlayerActions, modifier: Modifier) {
    // 拖动期间以本地值为准，松手才提交
    var dragValue by remember { mutableStateOf<Float?>(null) }
    val duration = info.duration
    PlayerSlider(
        value = dragValue ?: info.fraction,
        onValueChange = { dragValue = it },
        onValueChangeFinished = {
            dragValue?.let { actions.onSeek((it * duration).toLong()) }
            dragValue = null
        },
        enabled = info.hasTrack && duration > 0,
        modifier = modifier
    )
}

@Composable
private fun TransportControls(info: MiniTrackInfo, actions: MiniPlayerActions, playSize: Dp, spacing: Dp) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(spacing)) {
        MiniIconButton(Icons.Rounded.SkipPrevious, "上一首", actions.onPrevious, enabled = info.hasTrack)
        PlayButton(info, playSize, actions.onTogglePlay)
        MiniIconButton(Icons.Rounded.SkipNext, "下一首", actions.onNext, enabled = info.hasTrack)
    }
}

@Composable
private fun PlayButton(info: MiniTrackInfo, size: Dp, onClick: () -> Unit) {
    Box(
        Modifier.size(size)
            .clip(CircleShape)
            .background(if (info.hasTrack) ContentColor else DisabledIcon)
            .pointerHoverIcon(PointerIcon.Hand)
            .clickable(enabled = info.hasTrack, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        PlayPauseIcon(info.isPlaying, DesktopColors.Pane, size = size * 0.6f, contentDescription = if (info.isPlaying) "暂停" else "播放")
    }
}

@Composable
private fun LikeButton(info: MiniTrackInfo, actions: MiniPlayerActions) {
    MiniIconButton(
        if (info.liked) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
        if (info.liked) "取消喜欢" else "喜欢",
        actions.onToggleLike,
        tint = if (info.liked) DesktopColors.Accent else null
    )
}

@Composable
private fun RepeatButton(info: MiniTrackInfo, actions: MiniPlayerActions) {
    val singleLoop = info.playMode == PlayMode.SINGLE_LOOP
    MiniIconButton(
        if (singleLoop) Icons.Rounded.RepeatOne else Icons.Rounded.Repeat,
        "循环模式",
        actions.onToggleRepeat,
        iconSize = 18.dp,
        tint = if (singleLoop) DesktopColors.Accent else null
    )
}

// 切换样式 / 还原主窗口 / 关闭，关闭同样回到主窗口；歌词卡另带歌词设置入口
@Composable
private fun WindowButtons(
    actions: MiniPlayerActions,
    modifier: Modifier = Modifier,
    vertical: Boolean = false,
    onOpenLyricSettings: (() -> Unit)? = null
) {
    Box(modifier.clip(ChromeShape).background(ChromeBackground).padding(2.dp)) {
        val buttons = @Composable {
            if (onOpenLyricSettings != null) {
                MiniIconButton(Icons.Rounded.Tune, "歌词设置", onOpenLyricSettings, size = 20.dp, iconSize = 14.dp)
            }
            MiniIconButton(Icons.Rounded.SwapHoriz, "切换样式", actions.onSwitchStyle, size = 20.dp, iconSize = 14.dp)
            MiniIconButton(Icons.Rounded.OpenInFull, "还原主窗口", actions.onRestore, size = 20.dp, iconSize = 12.dp)
            MiniIconButton(Icons.Rounded.Close, "关闭迷你播放器", actions.onRestore, size = 20.dp, iconSize = 14.dp)
        }
        if (vertical) Column { buttons() } else Row { buttons() }
    }
}

@Composable
private fun MiniIconButton(
    icon: ImageVector,
    description: String,
    onClick: () -> Unit,
    enabled: Boolean = true,
    size: Dp = ButtonSize,
    iconSize: Dp = 20.dp,
    tint: Color? = null
) {
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    IconButton(onClick = onClick, enabled = enabled, interactionSource = interaction, modifier = Modifier.size(size)) {
        Icon(
            icon,
            description,
            modifier = Modifier.size(iconSize),
            tint = when {
                !enabled -> DisabledIcon
                tint != null -> tint
                hovered -> ContentColor
                else -> IdleIcon
            }
        )
    }
}

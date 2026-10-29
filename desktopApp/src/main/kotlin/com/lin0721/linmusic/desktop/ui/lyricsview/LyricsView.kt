package com.lin0721.linmusic.desktop.ui.lyricsview

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CloseFullscreen
import androidx.compose.material.icons.rounded.Fullscreen
import androidx.compose.material.icons.rounded.FullscreenExit
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import com.lin0721.linmusic.core.model.Album
import com.lin0721.linmusic.core.model.Artist
import com.lin0721.linmusic.core.player.NowPlaying
import com.lin0721.linmusic.core.player.PlaybackController
import com.lin0721.linmusic.core.player.domain.LyricLine
import com.lin0721.linmusic.core.share.ShareCardKind
import com.lin0721.linmusic.core.player.domain.lyricLineKey
import com.lin0721.linmusic.core.preferences.SettingsPreferences
import com.lin0721.linmusic.core.ui.theme.canvasBackdrop
import com.lin0721.linmusic.core.ui.theme.playerBackdrop
import com.lin0721.linmusic.desktop.data.DesktopPreferences
import com.lin0721.linmusic.desktop.ui.components.DesktopTooltip
import com.lin0721.linmusic.desktop.ui.components.uiEnter
import com.lin0721.linmusic.desktop.ui.components.uiExit
import com.lin0721.linmusic.desktop.ui.components.uiTween
import com.lin0721.linmusic.desktop.ui.nowplaying.rememberCoverBase
import com.lin0721.linmusic.desktop.ui.nowplaying.verticalEdgeFade
import com.lin0721.linmusic.desktop.ui.palette.ImmersiveColors
import com.lin0721.linmusic.desktop.ui.palette.ImmersiveSurface
import com.lin0721.linmusic.desktop.ui.palette.LocalImmersiveColors
import com.lin0721.linmusic.desktop.ui.palette.rememberLyricsViewColors
import com.lin0721.linmusic.desktop.ui.shell.LocalDesktopNavigator
import com.lin0721.linmusic.desktop.ui.theme.DesktopColors
import com.lin0721.linmusic.desktop.ui.theme.DesktopDimens
import com.lin0721.linmusic.desktop.ui.track.artistLinks
import com.lin0721.linmusic.feature.player.presentation.LyricsPlaceholder
import com.lin0721.linmusic.feature.player.presentation.isActiveLine
import com.lin0721.linmusic.feature.player.presentation.lyricsPlaceholder
import com.lin0721.linmusic.feature.player.presentation.secondaryText
import com.lin0721.linmusic.feature.player.ui.PlayerViewModel
import kotlinx.coroutines.delay

private const val FADE_MS = 150
private const val OPEN_MS = 220
private const val OPEN_SCALE = 0.98f
private const val RESUME_FOLLOW_MS = 3_000L
private const val AUTO_HIDE_MS = 5_000L
private const val HEADER_FADE_MS = 200
internal const val CHROME_ANIM_MS = 250
private const val MOVE_THRESHOLD_PX = 2f

// 当前行落在视口自上而下的这个比例处
private const val ANCHOR_FRACTION = 0.38f

// 歌词列占整体宽度的比例，居中
private const val COLUMN_WIDTH_FRACTION = 0.72f
private val EdgeFade = 48.dp
private val LightTopTintHeight = 360.dp
private val HeaderButtonSize = 32.dp
private val HeaderTopPadding = 16.dp
// 歌名行底边：阴影到此为止，歌词区也从这里起算（与顶栏下半部分重叠）
private val HeaderTitleRowBottom = HeaderTopPadding + HeaderButtonSize
private val InactiveColor: Color @Composable @ReadOnlyComposable get() = LocalImmersiveColors.current.let { if (it.onLight) it.inactive else it.content.copy(alpha = 0.55f) }

// 覆盖在标题栏与播放栏之间整个工作区的全屏歌词界面；不透明底并吞掉点击，收起后原样露出下层
@Composable
fun LyricsViewOverlay(
    state: LyricsViewState,
    controller: PlaybackController,
    playerViewModel: PlayerViewModel,
    settingsPreferences: SettingsPreferences,
    desktopPreferences: DesktopPreferences,
    isFullscreen: Boolean,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = state.isOpen,
        modifier = modifier,
        enter = uiEnter(fadeIn(tween(OPEN_MS)) + scaleIn(tween(OPEN_MS, easing = FastOutSlowInEasing), initialScale = OPEN_SCALE)),
        exit = uiExit(fadeOut(tween(FADE_MS)) + scaleOut(tween(FADE_MS), targetScale = OPEN_SCALE))
    ) {
        val nowPlaying by controller.nowPlaying.collectAsState()
        val track = nowPlaying ?: return@AnimatedVisibility
        val detail by playerViewModel.songDetailState.collectAsState()
        val primaryIndex by playerViewModel.primaryLyricIndex.collectAsState()
        val activeIndices by playerViewModel.activeLyricIndices.collectAsState()
        val isPlaying by controller.isPlaying.collectAsState()
        val clock = rememberLyricClock(controller)
        // 详情属于当前曲目时才可跳转，切歌瞬间的旧详情不能指向上一首的专辑
        val ownDetail = detail.songDetail?.takeIf { it.id == track.songId }
        val album = ownDetail?.al?.takeIf { it.id > 0 && it.name.isNotBlank() }
        val artists = if (ownDetail != null) detail.artists.map { Artist(id = it.artistId, name = it.artistName) } else emptyList()
        val (settings, actions) = rememberLyricsViewSettings(settingsPreferences, desktopPreferences)
        LyricsViewContent(
            track = track,
            lines = detail.lyrics,
            isLoading = detail.isLyricsLoading,
            primaryIndex = primaryIndex,
            activeIndices = activeIndices,
            settings = settings,
            actions = actions,
            isPlaying = isPlaying,
            clock = clock,
            isFullscreen = isFullscreen,
            controlsHidden = state.controlsHidden,
            onControlsHiddenChange = { state.controlsHidden = it },
            pointerInWindow = state.pointerInWindow,
            album = album,
            artists = artists,
            onSeek = playerViewModel::seekToTime,
            onToggleFullscreen = state::toggleFullscreen,
            onClose = onClose
        )
    }
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun LyricsViewContent(
    track: NowPlaying,
    lines: List<LyricLine>,
    isLoading: Boolean,
    primaryIndex: Int,
    activeIndices: Set<Int>,
    settings: LyricsViewSettings,
    actions: LyricsViewSettingsActions,
    isPlaying: Boolean,
    clock: () -> Long,
    isFullscreen: Boolean,
    controlsHidden: Boolean,
    onControlsHiddenChange: (Boolean) -> Unit,
    pointerInWindow: Boolean,
    album: Album?,
    artists: List<Artist>,
    onSeek: (Long) -> Unit,
    onToggleFullscreen: () -> Unit,
    onClose: () -> Unit
) {
    // 深色与移动端全屏歌词同一配方；浅色下封面浅调铺底配深字
    val base = rememberCoverBase(track.artworkUri)
    val immersive = rememberLyricsViewColors(base)
    val fill = immersive.fill
    val hasTranslation = remember(lines) { lines.any { it.translation != null } }
    val hasRoma = remember(lines) { lines.any { it.roma != null } }

    var settingsOpen by remember { mutableStateOf(false) }
    var moveTick by remember { mutableIntStateOf(0) }
    // 点击空白处手动收起后，鼠标移动不再唤出，只能再点一次
    var manuallyHidden by remember { mutableStateOf(false) }
    // 鼠标移回窗口时无条件恢复
    LaunchedEffect(pointerInWindow) { if (pointerInWindow) manuallyHidden = false }
    // 开启自动隐藏后，鼠标 5 秒没有动作或移出窗口就收起全部控件；设置面板打开期间保持显示
    LaunchedEffect(settings.autoHideControls, settingsOpen, moveTick, manuallyHidden, pointerInWindow) {
        if (!settings.autoHideControls) manuallyHidden = false
        if (settings.autoHideControls && !settingsOpen && (!pointerInWindow || manuallyHidden)) {
            onControlsHiddenChange(true)
            return@LaunchedEffect
        }
        onControlsHiddenChange(false)
        if (settings.autoHideControls && !settingsOpen) {
            delay(AUTO_HIDE_MS)
            onControlsHiddenChange(true)
        }
    }
    // 沉浸态没有标题栏与播放栏的间隙，上下补一圈与左右一致的边
    val verticalGap by animateDpAsState(if (controlsHidden) DesktopDimens.PaneGap else 0.dp, uiTween(CHROME_ANIM_MS), label = "lyricsVerticalGap")
    val headerInset by animateDpAsState(if (controlsHidden) 0.dp else HeaderTitleRowBottom, uiTween(CHROME_ANIM_MS), label = "lyricsHeaderInset")
    // 布局变化会合成同位置的移动事件，按窗口坐标过滤掉没有真实位移的移动
    var boxOrigin by remember { mutableStateOf(Offset.Zero) }
    var lastPointer by remember { mutableStateOf(Offset.Unspecified) }

    val windowBackground = DesktopColors.WindowBackground
    ImmersiveSurface(immersive) {
        Box(
            // 先铺窗口底色再留边，边里不会露出下层的侧栏和面板
            Modifier.fillMaxSize().canvasBackdrop(windowBackground)
                .padding(horizontal = DesktopDimens.PaneGap, vertical = verticalGap)
                .clip(RoundedCornerShape(DesktopDimens.PaneRadius))
                .then(if (immersive.backdrop) Modifier.playerBackdrop(fill) else Modifier.background(fill))
                .then(if (immersive.onLight && !immersive.backdrop) Modifier.drawBehind { drawLightTopTint(immersive) } else Modifier)
                .onGloballyPositioned { boxOrigin = it.positionInWindow() }
                .onPointerEvent(PointerEventType.Scroll) { if (!manuallyHidden) moveTick++ }
                .onPointerEvent(PointerEventType.Move) { event ->
                    val change = event.changes.firstOrNull() ?: return@onPointerEvent
                    val position = boxOrigin + change.position
                    if (lastPointer == Offset.Unspecified || (position - lastPointer).getDistance() > MOVE_THRESHOLD_PX) {
                        lastPointer = position
                        if (!manuallyHidden) moveTick++
                    }
                }
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
                    if (settings.autoHideControls) {
                        manuallyHidden = !controlsHidden
                        moveTick++
                    }
                }
        ) {
            // 让位高度与自动隐藏开关无关，切换开关不会让歌词移位
            Box(Modifier.fillMaxSize().padding(top = headerInset)) {
                when (lyricsPlaceholder(lines, isLoading)) {
                    LyricsPlaceholder.Loading -> CircularProgressIndicator(
                        color = LocalImmersiveColors.current.content,
                        modifier = Modifier.align(Alignment.Center).size(28.dp),
                        strokeWidth = 2.dp
                    )
                    LyricsPlaceholder.Empty -> Placeholder("暂无歌词")
                    LyricsPlaceholder.PureMusic -> Placeholder("纯音乐，请欣赏")
                    null -> LyricsList(lines, primaryIndex, activeIndices, settings, isPlaying, clock, onSeek)
                }
            }
            // 退出组合后不再拦截点击
            AnimatedVisibility(
                visible = !controlsHidden,
                modifier = Modifier.align(Alignment.TopCenter),
                enter = fadeIn(uiTween(HEADER_FADE_MS)),
                exit = fadeOut(uiTween(HEADER_FADE_MS))
            ) {
                Header(
                    track = track,
                    isFullscreen = isFullscreen,
                    settingsOpen = settingsOpen,
                    onSettingsOpenChange = { settingsOpen = it },
                    settingsContent = { LyricsSettingsPanel(settings, actions, hasTranslation, hasRoma) },
                    album = album,
                    artists = artists,
                    onToggleFullscreen = onToggleFullscreen,
                    onClose = onClose,
                    modifier = Modifier
                        .drawBehind {
                            // 阴影从顶边压到歌名行底部，越靠近顶边越深
                            drawRect(
                                Brush.verticalGradient(
                                    0f to immersive.scrim.copy(alpha = 0.75f),
                                    0.5f to immersive.scrim.copy(alpha = 0.32f),
                                    1f to Color.Transparent,
                                    endY = HeaderTitleRowBottom.toPx()
                                ),
                                size = Size(size.width, HeaderTitleRowBottom.toPx())
                            )
                        }
                )
            }
        }
    }
}

// 浅色顶部封面浅调，向下渐隐到整页淡底
private fun DrawScope.drawLightTopTint(colors: ImmersiveColors) {
    val height = LightTopTintHeight.toPx().coerceAtMost(size.height)
    drawRect(Brush.verticalGradient(0f to colors.blob, 1f to colors.fill, endY = height), size = Size(size.width, height))
}

@Composable
private fun Header(
    track: NowPlaying,
    isFullscreen: Boolean,
    settingsOpen: Boolean,
    onSettingsOpenChange: (Boolean) -> Unit,
    settingsContent: @Composable () -> Unit,
    album: Album?,
    artists: List<Artist>,
    onToggleFullscreen: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    val navigator = LocalDesktopNavigator.current
    val density = LocalDensity.current
    val popupOffset = IntOffset(0, with(density) { (HeaderButtonSize + 8.dp).roundToPx() })
    Row(
        modifier.fillMaxWidth().padding(start = 32.dp, end = 16.dp, top = HeaderTopPadding, bottom = 8.dp),
        verticalAlignment = Alignment.Top
    ) {
        // 按钮顶对齐，与首行歌名垂直居中对齐；跳转后歌词界面收起，露出目标页
        Column(Modifier.weight(1f).padding(end = 16.dp)) {
            Box(Modifier.height(HeaderButtonSize), contentAlignment = Alignment.CenterStart) {
                TitleLink(track.title, album) {
                    navigator.openAlbum(it.id, it.name)
                    onClose()
                }
            }
            if (artists.isEmpty()) {
                Text(track.artist, color = LocalImmersiveColors.current.content.copy(alpha = 0.7f), fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            } else {
                Text(
                    artistLinks(artists) { id, name ->
                        navigator.openArtist(id, name)
                        onClose()
                    },
                    color = LocalImmersiveColors.current.content.copy(alpha = 0.7f),
                    fontSize = 13.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        HeaderButton(Icons.Rounded.Share, "分享歌词") { navigator.openShareCard(ShareCardKind.LYRIC) }
        Box {
            HeaderButton(Icons.Rounded.Tune, "歌词设置") { onSettingsOpenChange(!settingsOpen) }
            if (settingsOpen) {
                Popup(
                    alignment = Alignment.TopEnd,
                    offset = popupOffset,
                    onDismissRequest = { onSettingsOpenChange(false) },
                    properties = PopupProperties(focusable = true)
                ) { settingsContent() }
            }
        }
        HeaderButton(
            if (isFullscreen) Icons.Rounded.FullscreenExit else Icons.Rounded.Fullscreen,
            if (isFullscreen) "退出全屏" else "全屏",
            onToggleFullscreen
        )
        HeaderButton(Icons.Rounded.CloseFullscreen, "收起", onClose)
    }
}

// 歌名：有所属专辑时可点跳转，悬停加下划线
@Composable
private fun TitleLink(title: String, album: Album?, onOpenAlbum: (Album) -> Unit) {
    val hoverSource = remember { MutableInteractionSource() }
    val hovered by hoverSource.collectIsHoveredAsState()
    Text(
        title,
        color = LocalImmersiveColors.current.content,
        fontWeight = FontWeight.Bold,
        fontSize = 16.sp,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        textDecoration = if (album != null && hovered) TextDecoration.Underline else TextDecoration.None,
        modifier = if (album != null) {
            Modifier.clip(RoundedCornerShape(4.dp)).hoverable(hoverSource).pointerHoverIcon(PointerIcon.Hand)
                .clickable { onOpenAlbum(album) }
        } else {
            Modifier
        }
    )
}

@Composable
private fun HeaderButton(icon: ImageVector, description: String, onClick: () -> Unit) {
    DesktopTooltip(description) {
        IconButton(onClick = onClick, modifier = Modifier.size(HeaderButtonSize)) {
            Icon(icon, description, tint = LocalImmersiveColors.current.content.copy(alpha = 0.85f), modifier = Modifier.size(22.dp))
        }
    }
}

@Composable
private fun BoxScope.Placeholder(text: String) {
    Text(text, color = LocalImmersiveColors.current.content.copy(alpha = 0.7f), fontSize = 20.sp, modifier = Modifier.align(Alignment.Center))
}

// 可滚轮浏览：滚动后暂停跟随，停止操作 3 秒或点击歌词后回到当前行
@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun LyricsList(
    lines: List<LyricLine>,
    primaryIndex: Int,
    activeIndices: Set<Int>,
    settings: LyricsViewSettings,
    isPlaying: Boolean,
    clock: () -> Long,
    onSeek: (Long) -> Unit
) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val listState = rememberLazyListState()
        var following by remember { mutableStateOf(true) }
        var interactionTick by remember { mutableIntStateOf(0) }
        val sidePadding = maxWidth * ((1f - COLUMN_WIDTH_FRACTION) / 2f)
        val topPadding = maxHeight * ANCHOR_FRACTION
        val bottomPadding = maxHeight * (1f - ANCHOR_FRACTION)

        LaunchedEffect(interactionTick) {
            if (interactionTick > 0) {
                delay(RESUME_FOLLOW_MS)
                following = true
            }
        }
        // 内容区顶部留出锚点高度，滚到某行即让它落在锚点处
        LaunchedEffect(primaryIndex, following, lines) {
            if (!following) return@LaunchedEffect
            // 新歌前奏或歌词重载时当前行尚未开始：回到顶部，否则停在上一首的位置
            if (primaryIndex < 0) {
                if (lines.isNotEmpty()) listState.animateScrollToItem(0)
            } else if (primaryIndex in lines.indices) {
                listState.animateScrollToItem(primaryIndex)
            }
        }

        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize()
                .verticalEdgeFade(top = EdgeFade, bottom = EdgeFade)
                .onPointerEvent(PointerEventType.Scroll) {
                    following = false
                    interactionTick++
                },
            verticalArrangement = Arrangement.spacedBy(settings.lineSpacing.dp),
            contentPadding = PaddingValues(start = sidePadding, end = sidePadding, top = topPadding, bottom = bottomPadding)
        ) {
            itemsIndexed(lines, key = { index, line -> lyricLineKey(index, line) }) { index, line ->
                // 纯音乐段的空白占位行不占用位置
                if (line.text.isBlank()) return@itemsIndexed
                LyricsRow(
                    line = line,
                    active = isActiveLine(index, primaryIndex, activeIndices),
                    secondary = secondaryText(line, settings.secondaryMode),
                    settings = settings,
                    isPlaying = isPlaying,
                    clock = clock,
                    onClick = {
                        following = true
                        onSeek(line.timeMs)
                    }
                )
            }
        }
    }
}

@Composable
private fun LyricsRow(
    line: LyricLine,
    active: Boolean,
    secondary: String?,
    settings: LyricsViewSettings,
    isPlaying: Boolean,
    clock: () -> Long,
    onClick: () -> Unit
) {
    val hoverSource = remember { MutableInteractionSource() }
    val hovered by hoverSource.collectIsHoveredAsState()
    val color by animateColorAsState(
        when {
            active -> LocalImmersiveColors.current.highlight
            hovered -> LocalImmersiveColors.current.content.copy(alpha = 0.85f)
            else -> InactiveColor
        },
        tween(300),
        label = "lyricsRowColor"
    )
    val alignment = effectiveAlignment(settings.alignment, line.alignment)
    val fontSize = settings.fontSize.sp

    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).hoverable(hoverSource).pointerHoverIcon(PointerIcon.Hand)
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClick),
        horizontalAlignment = alignment.horizontal
    ) {
        if (active && line.words.isNotEmpty()) {
            KaraokeText(
                line = line,
                positionProvider = clock,
                inactiveColor = InactiveColor,
                activeColor = LocalImmersiveColors.current.highlight,
                fontSize = fontSize,
                lineHeight = fontSize * 1.3f,
                textAlign = alignment.textAlign,
                isPlaying = isPlaying,
                advancedEffect = settings.advancedEffect,
                // 白色加亮微光只适合深底
                glowEffect = settings.glowEffect && !LocalImmersiveColors.current.onLight
            )
        } else {
            Text(
                line.text,
                color = color,
                fontSize = fontSize,
                lineHeight = fontSize * 1.3f,
                fontWeight = FontWeight.ExtraBold,
                textAlign = alignment.textAlign,
                modifier = Modifier.fillMaxWidth()
            )
        }
        if (secondary != null) {
            Text(
                secondary,
                color = color.copy(alpha = color.alpha * 0.8f),
                fontSize = settings.secondaryFontSize,
                lineHeight = settings.secondaryFontSize * 1.35f,
                textAlign = alignment.textAlign,
                modifier = Modifier.fillMaxWidth().padding(top = settings.secondarySpacing.dp)
            )
        }
        line.backgroundLine?.let { background ->
            BackgroundVocal(background, active, alignment, settings, isPlaying, clock)
        }
    }
}

// AMLL 的背景和声：字号更小、颜色更淡，缩进到 90% 宽并随主声部对齐，避免和主声部抢视觉重心
@Composable
private fun BackgroundVocal(
    line: LyricLine,
    active: Boolean,
    alignment: RowAlignment,
    settings: LyricsViewSettings,
    isPlaying: Boolean,
    clock: () -> Long
) {
    // 未激活与本行激活但和声尚未开唱时用同一底色，避免状态切换变色
    val inactive = LocalImmersiveColors.current.content.copy(alpha = 0.22f)
    val activeColor = LocalImmersiveColors.current.content.copy(alpha = 0.82f)
    val fontSize = settings.backgroundFontSize
    Column(Modifier.fillMaxWidth(0.9f).padding(top = 8.dp), horizontalAlignment = alignment.horizontal) {
        if (active && line.words.isNotEmpty()) {
            KaraokeText(
                line = line,
                positionProvider = clock,
                inactiveColor = inactive,
                activeColor = activeColor,
                fontSize = fontSize,
                lineHeight = fontSize * 1.4f,
                textAlign = alignment.textAlign,
                isPlaying = isPlaying,
                advancedEffect = settings.advancedEffect,
                glowEffect = settings.glowEffect && !LocalImmersiveColors.current.onLight,
                fontWeight = FontWeight.Bold
            )
        } else {
            Text(
                line.text,
                color = if (active) activeColor else inactive,
                fontSize = fontSize,
                lineHeight = fontSize * 1.4f,
                fontWeight = FontWeight.Bold,
                textAlign = alignment.textAlign,
                modifier = Modifier.fillMaxWidth()
            )
        }
        val tint = if (active) activeColor else inactive
        val secondarySize = settings.backgroundSecondaryFontSize
        line.translation?.let {
            Text(it, color = tint, fontSize = secondarySize, textAlign = alignment.textAlign, modifier = Modifier.fillMaxWidth().padding(top = 3.dp))
        }
        line.roma?.let {
            Text(it, color = tint, fontSize = secondarySize, textAlign = alignment.textAlign, modifier = Modifier.fillMaxWidth().padding(top = 2.dp))
        }
    }
}

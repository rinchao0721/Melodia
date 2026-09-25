package com.lin0721.linmusic.feature.player.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.gestures.ScrollableDefaults
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitVerticalTouchSlopOrCancellation
import androidx.compose.foundation.gestures.verticalDrag
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.input.pointer.util.addPointerInputChange
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lin0721.linmusic.core.player.domain.LyricLine
import com.lin0721.linmusic.core.preferences.SettingsPreferences
import com.lin0721.linmusic.core.ui.interaction.pressable
import com.lin0721.linmusic.core.ui.theme.MelodiaPress
import com.lin0721.linmusic.core.ui.theme.MelodiaSpacing
import com.lin0721.linmusic.core.ui.theme.PillRadius
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

private val LeftColumnMaxWidth = 420.dp
private val LeftColumnStartPadding = 48.dp
private val ColumnGap = 56.dp
private val RightColumnEndPadding = 24.dp
private val NoLyricsHeight = 56.dp

// 手动滚动歌词松手后停留多久再回到当前行，与全屏歌词页一致
private const val LYRICS_RESUME_DELAY_MS = 5000L

private const val WIDE_LYRICS_KEY = "wide_lyrics"
private const val WIDE_BOTTOM_SPACER_KEY = "wide_bottom_spacer"

// 平板横屏全屏播放器：左栏封面与播放控件固定不动；右栏可整体滚动，第一屏是歌词，往下接信息卡片网格。
// 按下点落在某行歌词文字上再拖动时滚动歌词，其余位置拖动滚动整栏
@Composable
fun FullPlayerWideLayout(
    sourceBar: @Composable (Modifier) -> Unit,
    cover: @Composable (Modifier) -> Unit,
    playbackControls: @Composable ColumnScope.() -> Unit,
    lyrics: List<LyricLine>,
    isLyricsLoading: Boolean,
    currentLyricIndex: Int,
    highlightColor: Color,
    currentPositionProvider: () -> Long,
    isPlaying: Boolean,
    onLyricClick: (LyricLine) -> Unit,
    onLyricsVisibleChange: (Boolean) -> Unit,
    infoCards: LazyListScope.() -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxSize()) {
        sourceBar(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = MelodiaSpacing.lg)
                .padding(top = MelodiaSpacing.md)
        )
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = MelodiaSpacing.sm, bottom = MelodiaSpacing.md)
        ) {
            Box(
                modifier = Modifier
                    .weight(0.4f)
                    .fillMaxHeight()
                    .padding(start = LeftColumnStartPadding),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    modifier = Modifier
                        .widthIn(max = LeftColumnMaxWidth)
                        .fillMaxSize(),
                    verticalArrangement = Arrangement.Center
                ) {
                    // 封面取栏宽与扣掉控件后剩余高度中较小者，控件区始终完整可见
                    Box(
                        modifier = Modifier
                            .weight(1f, fill = false)
                            .fillMaxWidth(),
                        contentAlignment = Alignment.Center
                    ) {
                        cover(Modifier.aspectRatio(1f, matchHeightConstraintsFirst = true))
                    }
                    Spacer(modifier = Modifier.height(MelodiaSpacing.md))
                    playbackControls()
                }
            }
            Spacer(modifier = Modifier.width(ColumnGap))
            WideRightColumn(
                lyrics = lyrics,
                isLyricsLoading = isLyricsLoading,
                currentLyricIndex = currentLyricIndex,
                highlightColor = highlightColor,
                currentPositionProvider = currentPositionProvider,
                isPlaying = isPlaying,
                onLyricClick = onLyricClick,
                onLyricsVisibleChange = onLyricsVisibleChange,
                infoCards = infoCards,
                modifier = Modifier
                    .weight(0.6f)
                    .fillMaxHeight()
                    .padding(end = RightColumnEndPadding)
            )
        }
    }
}

@Composable
private fun WideRightColumn(
    lyrics: List<LyricLine>,
    isLyricsLoading: Boolean,
    currentLyricIndex: Int,
    highlightColor: Color,
    currentPositionProvider: () -> Long,
    isPlaying: Boolean,
    onLyricClick: (LyricLine) -> Unit,
    onLyricsVisibleChange: (Boolean) -> Unit,
    infoCards: LazyListScope.() -> Unit,
    modifier: Modifier = Modifier
) {
    val settingsPreferences: SettingsPreferences = koinInject()
    val lyricTextSize by settingsPreferences.fullScreenLyricTextSize.collectAsStateWithLifecycle(initialValue = 22)
    val lyricAlignment by settingsPreferences.fullScreenLyricAlignment.collectAsStateWithLifecycle(initialValue = "left")
    val lyricSecondaryMode by settingsPreferences.fullScreenLyricSecondaryMode.collectAsStateWithLifecycle(initialValue = "translation")
    val karaokeAdvancedEffect by settingsPreferences.fullScreenKaraokeAdvancedEffect.collectAsStateWithLifecycle(initialValue = true)

    val scope = rememberCoroutineScope()
    val columnState = rememberLazyListState()
    val lyricsListState = rememberLazyListState()
    val lyricsFling = ScrollableDefaults.flingBehavior()
    var lyricsViewportPx by remember { mutableFloatStateOf(0f) }
    var isUserScrollingLyrics by remember { mutableStateOf(false) }
    var resumeJob by remember { mutableStateOf<Job?>(null) }
    val isPlayingState = rememberUpdatedState(isPlaying)

    // 每行歌词文字的实际范围（根坐标），按下时据此判断拖动交给歌词还是整栏
    val lineTextBounds = remember { HashMap<Int, Rect>() }
    var lyricsOriginInRoot by remember { mutableStateOf(Offset.Zero) }

    val isPureMusic = lyrics.size == 1 && lyrics[0].text == "纯音乐"
    val hasLyrics = lyrics.isNotEmpty() && !isPureMusic

    LaunchedEffect(isPlaying) {
        if (isPlaying && isUserScrollingLyrics) isUserScrollingLyrics = false
        if (!isPlaying) resumeJob?.cancel()
    }

    // 整栏滚到顶后剩余的下拉量在此吃掉，不再冒泡给播放器宿主触发下拉收起；收起走顶部下拉箭头
    val blockDragToClose = remember {
        object : NestedScrollConnection {
            override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset = available
            override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity = available
        }
    }

    val showBackToLyrics by remember(hasLyrics) {
        derivedStateOf { hasLyrics && columnState.firstVisibleItemIndex > 0 }
    }

    Box(modifier = modifier.nestedScroll(blockDragToClose)) {
        // 不用 contentPadding 留底：fillParentMaxHeight 会扣掉内边距，歌词区变矮后首张卡片会从底部露出一截
        LazyColumn(
            state = columnState,
            modifier = Modifier.fillMaxSize()
        ) {
            item(key = WIDE_LYRICS_KEY) {
                DisposableEffect(Unit) {
                    onLyricsVisibleChange(true)
                    onDispose { onLyricsVisibleChange(false) }
                }
                when {
                    isLyricsLoading || hasLyrics -> Column(
                        modifier = Modifier
                            .fillParentMaxHeight()
                            .fillMaxWidth()
                            .onGloballyPositioned { lyricsOriginInRoot = it.positionInRoot() }
                            .pointerInput(lyricsListState) {
                                awaitEachGesture {
                                    val down = awaitFirstDown(requireUnconsumed = false)
                                    val downInRoot = down.position + lyricsOriginInRoot
                                    if (lineTextBounds.values.none { it.contains(downInRoot) }) return@awaitEachGesture

                                    val velocityTracker = VelocityTracker()
                                    velocityTracker.addPointerInputChange(down)
                                    var slopOvershoot = 0f
                                    val drag = awaitVerticalTouchSlopOrCancellation(down.id) { change, overSlop ->
                                        change.consume()
                                        slopOvershoot = overSlop
                                    } ?: return@awaitEachGesture

                                    resumeJob?.cancel()
                                    isUserScrollingLyrics = true
                                    lyricsListState.dispatchRawDelta(-slopOvershoot)
                                    verticalDrag(drag.id) { change ->
                                        velocityTracker.addPointerInputChange(change)
                                        lyricsListState.dispatchRawDelta(-change.positionChange().y)
                                        change.consume()
                                    }
                                    val velocityY = velocityTracker.calculateVelocity().y
                                    scope.launch {
                                        lyricsListState.scroll {
                                            with(lyricsFling) { performFling(-velocityY) }
                                        }
                                    }
                                    if (isPlayingState.value) {
                                        resumeJob = scope.launch {
                                            delay(LYRICS_RESUME_DELAY_MS)
                                            isUserScrollingLyrics = false
                                        }
                                    }
                                }
                            }
                    ) {
                        FullScreenLyricsList(
                            lyrics = lyrics,
                            currentIndex = currentLyricIndex,
                            isLoading = isLyricsLoading,
                            isUserScrolling = isUserScrollingLyrics,
                            highlightColor = highlightColor,
                            currentPositionProvider = currentPositionProvider,
                            lazyListState = lyricsListState,
                            viewportHeightPx = lyricsViewportPx,
                            onViewportHeightChange = { lyricsViewportPx = it },
                            gestureModifier = Modifier,
                            fontSize = lyricTextSize,
                            alignment = lyricAlignment,
                            secondaryMode = lyricSecondaryMode,
                            advancedKaraokeEffect = karaokeAdvancedEffect,
                            isPlaying = isPlaying,
                            showSeekGuide = false,
                            userScrollEnabled = false,
                            onLineTextBounds = { index, bounds -> lineTextBounds[index] = bounds },
                            onSeek = {},
                            onLyricClick = { line ->
                                resumeJob?.cancel()
                                isUserScrollingLyrics = false
                                onLyricClick(line)
                            }
                        )
                    }
                    else -> Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(NoLyricsHeight)
                            .padding(horizontal = MelodiaSpacing.md),
                        contentAlignment = Alignment.CenterStart
                    ) {
                        Text(
                            text = if (isPureMusic) "纯音乐，请欣赏" else "暂无歌词",
                            color = highlightColor,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
            infoCards()
            item(key = WIDE_BOTTOM_SPACER_KEY) {
                Spacer(modifier = Modifier.height(MelodiaSpacing.lg))
            }
        }

        AnimatedVisibility(
            visible = showBackToLyrics,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = MelodiaSpacing.sm)
        ) {
            Surface(
                color = Color.White.copy(alpha = 0.16f),
                shape = RoundedCornerShape(PillRadius),
                modifier = Modifier.pressable(MelodiaPress.Pill) {
                    scope.launch { columnState.animateScrollToItem(0) }
                }
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = MelodiaSpacing.md, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Rounded.KeyboardArrowUp,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(MelodiaSpacing.xs))
                    Text(text = "回到歌词", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                }
            }
        }
    }
}

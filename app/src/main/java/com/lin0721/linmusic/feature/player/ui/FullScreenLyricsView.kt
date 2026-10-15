package com.lin0721.linmusic.feature.player.ui

import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import android.widget.Toast
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Alignment
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.lin0721.linmusic.core.player.PlayMode
import com.lin0721.linmusic.core.player.domain.LyricLine
import dev.chrisbanes.haze.HazeState
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import android.content.Intent
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.AnnotatedString
import com.lin0721.linmusic.core.ui.theme.LocalMelodiaWindowSizeClass
import com.lin0721.linmusic.core.ui.theme.MelodiaWindowSizeClass
import com.lin0721.linmusic.core.ui.theme.darken
import com.lin0721.linmusic.core.ui.theme.saturateIfChromatic
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lin0721.linmusic.core.preferences.SettingsPreferences
import org.koin.compose.koinInject

// ────────────────────────────────────────────────────────────────────────────
// 全屏歌词页：承载下拉关闭手势与滚动跟随状态，装配顶栏、歌词列表与播放控制
// ────────────────────────────────────────────────────────────────────────────
@Composable
fun FullScreenLyricsView(
    lyrics: List<LyricLine>,
    currentIndex: Int,
    // 同时需要高亮的行（对唱/背景和声的重叠区间）；留空时退回只高亮 currentIndex
    activeIndices: Set<Int> = emptySet(),
    isLoading: Boolean,
    title: String,
    artist: String,
    album: String? = null,
    coverUrl: String? = null,
    songId: Long? = null,
    base: Color,
    highlightColor: Color,
    onSeek: (Long) -> Unit,
    hazeState: HazeState,
    onClose: () -> Unit,
    onDragClose: () -> Unit = onClose,
    isPlaying: Boolean,
    currentPositionProvider: () -> Long,
    lyricPositionProvider: () -> Long = currentPositionProvider,
    duration: Long,
    onTogglePlay: () -> Unit,
    onPlayNext: () -> Unit,
    onPlayPrevious: () -> Unit,
    playMode: PlayMode,
    onToggleShuffle: () -> Unit,
    onToggleRepeat: () -> Unit,
    onMoreClick: () -> Unit,
    onControlsVisibilityChange: (Boolean) -> Unit = {}
) {
    val lazyListState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    var timerJob by remember { mutableStateOf<Job?>(null) }

    val settingsPreferences: SettingsPreferences = koinInject()
    val fullScreenLyricTextSize by settingsPreferences.fullScreenLyricTextSize.collectAsStateWithLifecycle(initialValue = 22)
    val isExpanded = LocalMelodiaWindowSizeClass.current == MelodiaWindowSizeClass.Expanded
    val effectiveLyricTextSize = if (isExpanded) fullScreenLyricTextSize.coerceIn(20, 40) else fullScreenLyricTextSize
    val fullScreenLyricAlignment by settingsPreferences.fullScreenLyricAlignment.collectAsStateWithLifecycle(initialValue = "left")
    val fullScreenLyricSecondaryMode by settingsPreferences.fullScreenLyricSecondaryMode.collectAsStateWithLifecycle(initialValue = "translation")
    val fullScreenKaraokeAdvancedEffect by settingsPreferences.fullScreenKaraokeAdvancedEffect.collectAsStateWithLifecycle(initialValue = true)
    val fullScreenKaraokeGlowEffect by settingsPreferences.fullScreenKaraokeGlowEffect.collectAsStateWithLifecycle(initialValue = false)
    val amllLyricsEnabled by settingsPreferences.amllLyricsEnabled.collectAsStateWithLifecycle(initialValue = true)
    val fullScreenLyricLineSpacing by settingsPreferences.fullScreenLyricLineSpacing.collectAsStateWithLifecycle(initialValue = 24)
    val fullScreenLyricSecondarySpacing by settingsPreferences.fullScreenLyricSecondarySpacing.collectAsStateWithLifecycle(initialValue = 6)
    val fullScreenLyricAutoHideControls by settingsPreferences.fullScreenLyricAutoHideControls.collectAsStateWithLifecycle(initialValue = false)
    var areControlsVisible by remember { mutableStateOf(true) }
    var autoHideJob by remember { mutableStateOf<Job?>(null) }

    val hasTranslation = remember(lyrics) { lyrics.any { it.translation != null } }
    val hasRoma = remember(lyrics) { lyrics.any { it.roma != null } }
    var showSettingsSheet by remember { mutableStateOf(false) }

    val scheduleAutoHide: () -> Unit = {
        autoHideJob?.cancel()
        if (fullScreenLyricAutoHideControls && !showSettingsSheet) {
            autoHideJob = scope.launch {
                delay(5000L)
                areControlsVisible = false
            }
        }
    }

    val revealControls: () -> Unit = {
        areControlsVisible = true
        scheduleAutoHide()
    }

    val toggleControls: () -> Unit = {
        if (fullScreenLyricAutoHideControls) {
            if (areControlsVisible) {
                autoHideJob?.cancel()
                areControlsVisible = false
            } else {
                revealControls()
            }
        }
    }

    var isShareMode by remember { mutableStateOf(false) }
    var wasControlsVisibleOnShareEnter by remember { mutableStateOf(true) }
    var selectedLyricKeys by remember { mutableStateOf(setOf<String>()) }
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    val haptic = LocalHapticFeedback.current

    val enterShareMode: (String) -> Unit = { key ->
        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        wasControlsVisibleOnShareEnter = areControlsVisible
        autoHideJob?.cancel()
        isShareMode = true
        selectedLyricKeys = setOf(key)
    }

    val exitShareMode: () -> Unit = {
        isShareMode = false
        scope.launch {
            delay(260L)
            if (!isShareMode) {
                selectedLyricKeys = emptySet()
            }
        }
        if (wasControlsVisibleOnShareEnter) {
            revealControls()
        } else {
            autoHideJob?.cancel()
            areControlsVisible = false
        }
    }

    BackHandler(enabled = isShareMode) {
        exitShareMode()
    }

    LaunchedEffect(isShareMode) {
        if (isShareMode) {
            autoHideJob?.cancel()
        }
    }

    val targetBackdropBase = remember(base, isShareMode) {
        if (isShareMode) {
            base.darken(0.12f)
        } else {
            base
        }
    }
    val animatedBackdropBase by animateColorAsState(
        targetValue = targetBackdropBase,
        animationSpec = tween(280),
        label = "fs_share_backdrop_base"
    )

    val vivid = remember(animatedBackdropBase) { animatedBackdropBase.saturateIfChromatic(0.6f) }
    val scrimColor = remember(vivid) { vivid.darken(0.35f) }

    var topContainerHeightPx by remember { mutableFloatStateOf(0f) }
    var controlsContainerHeightPx by remember { mutableFloatStateOf(0f) }
    var shareContainerHeightPx by remember { mutableFloatStateOf(0f) }

    val handleCopyLyrics: () -> Unit = {
        if (selectedLyricKeys.isEmpty()) {
            Toast.makeText(context, "请先选择要复制的歌词", Toast.LENGTH_SHORT).show()
        } else {
            val copiedLines = mutableListOf<String>()
            lyrics.forEachIndexed { index, line ->
                val mainKey = "${index}_main"
                val secKey = "${index}_sec"
                val bgKey = "${index}_bg"
                if (mainKey in selectedLyricKeys && line.text.isNotBlank()) {
                    copiedLines.add(line.text)
                }
                val secondaryText = when (fullScreenLyricSecondaryMode) {
                    "translation" -> line.translation
                    "roma" -> line.roma
                    else -> null
                }
                if (secKey in selectedLyricKeys && !secondaryText.isNullOrBlank()) {
                    copiedLines.add(secondaryText)
                }
                if (bgKey in selectedLyricKeys && !line.backgroundLine?.text.isNullOrBlank()) {
                    copiedLines.add(line.backgroundLine!!.text)
                }
            }
            val textToCopy = copiedLines.joinToString("\n")
            if (textToCopy.isNotBlank()) {
                clipboardManager.setText(AnnotatedString(textToCopy))
                Toast.makeText(context, "已复制 ${copiedLines.size} 行歌词", Toast.LENGTH_SHORT).show()
                exitShareMode()
            } else {
                Toast.makeText(context, "选中的歌词为空", Toast.LENGTH_SHORT).show()
            }
        }
    }

    LaunchedEffect(fullScreenLyricAutoHideControls, showSettingsSheet) {
        if (!fullScreenLyricAutoHideControls || showSettingsSheet) {
            autoHideJob?.cancel()
            areControlsVisible = true
        } else {
            scheduleAutoHide()
        }
    }

    LaunchedEffect(areControlsVisible, isShareMode) {
        if (isShareMode) {
            onControlsVisibilityChange(true)
        } else {
            onControlsVisibilityChange(areControlsVisible)
        }
    }

    val window = (context as? ComponentActivity)?.window
    val insetsController = remember(window) {
        window?.let { WindowCompat.getInsetsController(it, it.decorView) }
    }

    DisposableEffect(areControlsVisible, isShareMode, insetsController) {
        if (!areControlsVisible && !isShareMode) {
            insetsController?.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            insetsController?.hide(WindowInsetsCompat.Type.systemBars())
        } else {
            insetsController?.show(WindowInsetsCompat.Type.systemBars())
        }
        onDispose {
            insetsController?.show(WindowInsetsCompat.Type.systemBars())
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            onControlsVisibilityChange(true)
        }
    }

    val handleShareLyrics: () -> Unit = {
        val currentLyricText = lyrics.getOrNull(currentIndex)?.text?.takeIf { it.isNotBlank() }
        val shareText = if (currentLyricText != null) {
            "「$currentLyricText」\n——《$title》$artist"
        } else {
            "《$title》- $artist"
        }
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, shareText)
        }
        context.startActivity(Intent.createChooser(intent, "分享歌词"))
    }

    val handleToggleSecondaryMode: () -> Unit = {
        val nextMode = when {
            hasTranslation && hasRoma -> when (fullScreenLyricSecondaryMode) {
                "translation" -> "roma"
                "roma" -> "none"
                else -> "translation"
            }
            hasTranslation -> when (fullScreenLyricSecondaryMode) {
                "translation" -> "none"
                else -> "translation"
            }
            hasRoma -> when (fullScreenLyricSecondaryMode) {
                "roma" -> "none"
                else -> "roma"
            }
            else -> "none"
        }
        scope.launch {
            settingsPreferences.saveFullScreenLyricSecondaryMode(nextMode)
        }
    }

    val configuration = LocalConfiguration.current
    val density = LocalDensity.current
    val screenHeightPx = remember(configuration.screenHeightDp, density) {
        with(density) { configuration.screenHeightDp.dp.toPx() }
    }


    val dragState = rememberFullScreenLyricsDragState(
        lazyListState = lazyListState,
        onClose = onClose,
        onDragClose = onDragClose,
        screenHeightPx = screenHeightPx
    )

    LaunchedEffect(Unit) {
        dragState.reset()
    }

    DisposableEffect(Unit) {
        onDispose {
            dragState.reset()
        }
    }

    // 歌词页手势拖动的纯 UI 交互态，不涉及业务数据，只在本组件内部使用
    var isUserScrolling by remember { mutableStateOf(false) }

    val isPlayingState = rememberUpdatedState(isPlaying)

    // 拖动/点击跳转播放进度后，同时结束用户滚动态，恢复自动跟随当前歌词行
    val handleSeek: (Long) -> Unit = { timeMs ->
        isUserScrolling = false
        onSeek(timeMs)
    }

    LaunchedEffect(isPlaying) {
        if (isPlaying) {
            if (isUserScrolling) {
                isUserScrolling = false
            }
        } else {
            timerJob?.cancel()
        }
    }

    // 仅在真实发生拖动位移（超过 touchSlop）时维护用户滚动状态，常规点按（如显隐控件）不阻塞自动跟随
    val gestureModifier = Modifier.pointerInput(Unit) {
        val touchSlop = viewConfiguration.touchSlop
        awaitEachGesture {
            awaitFirstDown(requireUnconsumed = false)
            var hasDragged = false
            var totalDragDistance = 0f
            do {
                val event = awaitPointerEvent()
                for (change in event.changes) {
                    val distance = (change.position - change.previousPosition).getDistance()
                    totalDragDistance += distance
                    if (totalDragDistance > touchSlop) {
                        hasDragged = true
                        isUserScrolling = true
                        timerJob?.cancel()
                    }
                }
            } while (event.changes.any { it.pressed })

            if (hasDragged) {
                timerJob?.cancel()
                timerJob = scope.launch {
                    delay(2000L)
                    isUserScrolling = false
                }
            } else {
                isUserScrolling = false
            }
            if (fullScreenLyricAutoHideControls && areControlsVisible) {
                scheduleAutoHide()
            }
        }
    }

    val topCornerRadius by remember {
        derivedStateOf {
            if (dragState.offsetY > 0f) 24.dp else 0.dp
        }
    }

    PlayerBackdrop(
        base = animatedBackdropBase,
        mode = BackdropMode.Immersive,
        modifier = Modifier
            .fillMaxSize()
            .onSizeChanged { dragState.onScreenHeightChange(it.height.toFloat()) }
            .then(
                if (!isShareMode) Modifier.nestedScroll(dragState.nestedScrollConnection)
                else Modifier
            )
            .graphicsLayer {
                val offset = if (isShareMode) 0f else dragState.offsetY
                translationY = offset
                if (topCornerRadius > 0.dp) {
                    shape = RoundedCornerShape(topStart = topCornerRadius, topEnd = topCornerRadius)
                    clip = true
                } else {
                    clip = false
                }
            }
            .clip(RoundedCornerShape(topStart = topCornerRadius, topEnd = topCornerRadius))
            // 拦截全屏歌词页空白处点击，轻触切换控制组件显隐
            .pointerInput(fullScreenLyricAutoHideControls, isShareMode) {
                if (fullScreenLyricAutoHideControls && !isShareMode) {
                    detectTapGestures {
                        toggleControls()
                    }
                }
            }
    ) {
        Box(
            modifier = Modifier.fillMaxSize()
        ) {
            // 1. 歌词列表直接铺满全屏，上下边缘根据控件显隐状态自适应羽化
            val featherDp = 38.dp
            val featherPx = with(density) { featherDp.toPx() }
            val statusBarHeightPx = with(density) { WindowInsets.statusBars.asPaddingValues().calculateTopPadding().toPx() }
            val headerContentHeightDp = 56.dp
            val headerContentHeightPx = with(density) { headerContentHeightDp.toPx() }
            val totalTopSolidHeightPx = statusBarHeightPx + headerContentHeightPx
            val defaultControlsContainerHeightPx = with(density) { 220.dp.toPx() }
            val defaultShareContainerHeightPx = with(density) { 70.dp.toPx() }

            val effectiveTopCutoffPx = totalTopSolidHeightPx
            val effectiveControlsSolidHeightPx = if (controlsContainerHeightPx > featherPx) controlsContainerHeightPx - featherPx else defaultControlsContainerHeightPx
            val effectiveShareSolidHeightPx = if (shareContainerHeightPx > featherPx) shareContainerHeightPx - featherPx else defaultShareContainerHeightPx

            val isControlsShowing = areControlsVisible || isShareMode
            val targetTopCutoffPx = if (isControlsShowing) effectiveTopCutoffPx else 0f
            val targetTopFeatherPx = if (isControlsShowing) featherPx else with(density) { 60.dp.toPx() }

            val targetBottomControlsHeightPx = when {
                isShareMode -> effectiveShareSolidHeightPx
                areControlsVisible -> effectiveControlsSolidHeightPx
                else -> 0f
            }
            val targetBottomFeatherPx = if (isControlsShowing) featherPx else with(density) { 80.dp.toPx() }

            val animatedTopCutoffPx by animateFloatAsState(
                targetValue = targetTopCutoffPx,
                animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = 300f),
                label = "fs_top_cutoff"
            )
            val animatedTopFeatherPx by animateFloatAsState(
                targetValue = targetTopFeatherPx,
                animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = 300f),
                label = "fs_top_feather"
            )
            val animatedBottomControlsHeightPx by animateFloatAsState(
                targetValue = targetBottomControlsHeightPx,
                animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = 300f),
                label = "fs_bottom_controls_height"
            )
            val animatedBottomFeatherPx by animateFloatAsState(
                targetValue = targetBottomFeatherPx,
                animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = 300f),
                label = "fs_bottom_feather"
            )

            FullScreenLyricsList(
                lyrics = lyrics,
                currentIndex = currentIndex,
                activeIndices = activeIndices,
                isLoading = isLoading,
                isUserScrolling = isUserScrolling,
                highlightColor = highlightColor,
                currentPositionProvider = lyricPositionProvider,
                lazyListState = lazyListState,
                viewportHeightPx = dragState.viewportHeightPx,
                onViewportHeightChange = { height -> dragState.onViewportHeightChange(height) },
                gestureModifier = gestureModifier,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                    .drawWithContent {
                        drawContent()
                        if (size.height > 0f) {
                            val topCutoffRatio = (animatedTopCutoffPx / size.height).coerceIn(0f, 0.4f)
                            val topFeatherEndRatio = ((animatedTopCutoffPx + animatedTopFeatherPx) / size.height).coerceIn(topCutoffRatio, 0.5f)

                            val bottomCutoffRatio = ((size.height - animatedBottomControlsHeightPx) / size.height).coerceIn(topFeatherEndRatio, 1f)
                            val bottomFeatherStartRatio = ((size.height - animatedBottomControlsHeightPx - animatedBottomFeatherPx) / size.height).coerceIn(topFeatherEndRatio, bottomCutoffRatio)

                            val topSpan = topFeatherEndRatio - topCutoffRatio
                            val bottomSpan = bottomCutoffRatio - bottomFeatherStartRatio

                            val stops = mutableListOf<Pair<Float, Color>>()
                            stops.add(0.0f to Color.Transparent)
                            if (topCutoffRatio > 0.001f) {
                                stops.add(topCutoffRatio to Color.Transparent)
                            }
                            if (topSpan > 0.001f) {
                                stops.add((topCutoffRatio + 0.12f * topSpan) to Color.Black.copy(alpha = 0.04f))
                                stops.add((topCutoffRatio + 0.28f * topSpan) to Color.Black.copy(alpha = 0.18f))
                                stops.add((topCutoffRatio + 0.46f * topSpan) to Color.Black.copy(alpha = 0.44f))
                                stops.add((topCutoffRatio + 0.64f * topSpan) to Color.Black.copy(alpha = 0.70f))
                                stops.add((topCutoffRatio + 0.80f * topSpan) to Color.Black.copy(alpha = 0.89f))
                                stops.add((topCutoffRatio + 0.92f * topSpan) to Color.Black.copy(alpha = 0.97f))
                            }
                            stops.add(topFeatherEndRatio to Color.Black)
                            stops.add(bottomFeatherStartRatio to Color.Black)
                            if (bottomSpan > 0.001f) {
                                stops.add((bottomFeatherStartRatio + 0.08f * bottomSpan) to Color.Black.copy(alpha = 0.97f))
                                stops.add((bottomFeatherStartRatio + 0.20f * bottomSpan) to Color.Black.copy(alpha = 0.89f))
                                stops.add((bottomFeatherStartRatio + 0.36f * bottomSpan) to Color.Black.copy(alpha = 0.70f))
                                stops.add((bottomFeatherStartRatio + 0.54f * bottomSpan) to Color.Black.copy(alpha = 0.44f))
                                stops.add((bottomFeatherStartRatio + 0.72f * bottomSpan) to Color.Black.copy(alpha = 0.18f))
                                stops.add((bottomFeatherStartRatio + 0.88f * bottomSpan) to Color.Black.copy(alpha = 0.04f))
                            }
                            if (bottomCutoffRatio < 0.999f) {
                                stops.add(bottomCutoffRatio to Color.Transparent)
                            }
                            stops.add(1.0f to Color.Transparent)

                            drawRect(
                                brush = Brush.verticalGradient(*stops.toTypedArray()),
                                blendMode = BlendMode.DstIn
                            )
                        }
                    },
                    fontSize = effectiveLyricTextSize,
                    alignment = fullScreenLyricAlignment,
                    secondaryMode = fullScreenLyricSecondaryMode,
                    lineSpacing = fullScreenLyricLineSpacing,
                    secondarySpacing = fullScreenLyricSecondarySpacing,
                    advancedKaraokeEffect = fullScreenKaraokeAdvancedEffect,
                    karaokeGlowEffect = fullScreenKaraokeGlowEffect,
                    isPlaying = isPlaying,
                    isShareMode = isShareMode,
                    selectedLyricKeys = selectedLyricKeys,
                    onToggleSelect = { key ->
                        selectedLyricKeys = if (key in selectedLyricKeys) {
                            selectedLyricKeys - key
                        } else {
                            selectedLyricKeys + key
                        }
                    },
                    onLyricLongPress = { key ->
                        enterShareMode(key)
                    },
                    onSeek = handleSeek,
                    onLyricClick = { line ->
                        if (!isShareMode) {
                            val isCurrentLine = currentIndex in lyrics.indices && lyrics[currentIndex] == line
                            if (isCurrentLine) {
                                // 单击当前播放到的那一行展示/隐藏控件
                                if (fullScreenLyricAutoHideControls) {
                                    if (areControlsVisible) {
                                        autoHideJob?.cancel()
                                        areControlsVisible = false
                                    } else {
                                        revealControls()
                                    }
                                } else {
                                    if (areControlsVisible) {
                                        areControlsVisible = false
                                    } else {
                                        revealControls()
                                    }
                                }
                            } else {
                                if (fullScreenLyricAutoHideControls && areControlsVisible) {
                                    // 当控制栏显示时，轻触屏幕非当前行歌词行立即隐藏控制栏，且不误触跳转进度
                                    autoHideJob?.cancel()
                                    areControlsVisible = false
                                } else {
                                    // 控制栏处于隐藏状态（或未开启自动隐藏）时，点击其他歌词行正常跳转播放进度
                                    timerJob?.cancel()
                                    isUserScrolling = false
                                    handleSeek(line.timeMs)
                                }
                            }
                        }
                    }
                )

            // 2. 顶部栏：悬浮在屏幕顶部，包含状态栏和标题/分享栏；底层配有覆盖状态栏及标题栏的实色遮罩与下方非线性渐变羽化
            androidx.compose.animation.AnimatedVisibility(
                visible = areControlsVisible || isShareMode,
                enter = fadeIn(tween(220)) + slideInVertically(tween(220)) { -it },
                exit = fadeOut(tween(180)) + slideOutVertically(tween(180)) { -it },
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.TopCenter)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .onSizeChanged { size ->
                            if (size.height > 0) topContainerHeightPx = size.height.toFloat()
                        }
                ) {
                    // 底层遮罩：文字及状态栏背后实色全覆盖，文字下方羽化过渡渐隐至透明
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(topStart = topCornerRadius, topEnd = topCornerRadius))
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(scrimColor)
                                .statusBarsPadding()
                                .height(headerContentHeightDp)
                        )
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(featherDp)
                                .background(
                                    Brush.verticalGradient(
                                        0.00f to scrimColor,
                                        0.12f to scrimColor.copy(alpha = 0.96f),
                                        0.28f to scrimColor.copy(alpha = 0.84f),
                                        0.46f to scrimColor.copy(alpha = 0.65f),
                                        0.64f to scrimColor.copy(alpha = 0.42f),
                                        0.80f to scrimColor.copy(alpha = 0.20f),
                                        0.92f to scrimColor.copy(alpha = 0.06f),
                                        1.00f to Color.Transparent
                                    )
                                )
                        )
                    }

                    // 顶层标题栏内容：状态栏内边距 + 56dp 标题栏
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .statusBarsPadding()
                            .height(headerContentHeightDp)
                    ) {
                        Crossfade(
                            targetState = isShareMode,
                            animationSpec = tween(200),
                            label = "fs_header_crossfade"
                        ) { share ->
                            if (share) {
                                FullScreenShareHeader(
                                    onBack = exitShareMode
                                )
                            } else {
                                FullScreenLyricsHeader(
                                    title = title,
                                    artist = artist,
                                    onClose = onClose,
                                    onMoreClick = onMoreClick,
                                    onDragDelta = { delta -> dragState.onHeaderDrag(delta) },
                                    onDragStart = { dragState.onHeaderDragStart() },
                                    onDragRelease = { velocity -> dragState.handleDragRelease(velocity = velocity) }
                                )
                            }
                        }
                    }
                }
            }

            // 3. 底部播放控制条：悬浮于歌词区底部，底层配有上方非线性渐变羽化+控件区实色遮罩
            androidx.compose.animation.AnimatedVisibility(
                visible = areControlsVisible && !isShareMode,
                enter = fadeIn(tween(240)) + slideInVertically(tween(240)) { it },
                exit = fadeOut(tween(200)) + slideOutVertically(tween(200)) { it },
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter)
                    .pointerInput(fullScreenLyricAutoHideControls) {
                        if (fullScreenLyricAutoHideControls) {
                            awaitPointerEventScope {
                                while (true) {
                                    awaitPointerEvent()
                                    scheduleAutoHide()
                                }
                            }
                        }
                    }
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .onSizeChanged { size ->
                            if (size.height > 0) controlsContainerHeightPx = size.height.toFloat()
                        }
                ) {
                    // 底层遮罩背景：顶部非线性羽化渐变，下方控件区域实色全覆盖杜绝透字
                    Column(modifier = Modifier.matchParentSize()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(featherDp)
                                .background(
                                    Brush.verticalGradient(
                                        0.00f to Color.Transparent,
                                        0.08f to scrimColor.copy(alpha = 0.06f),
                                        0.20f to scrimColor.copy(alpha = 0.20f),
                                        0.36f to scrimColor.copy(alpha = 0.42f),
                                        0.54f to scrimColor.copy(alpha = 0.65f),
                                        0.72f to scrimColor.copy(alpha = 0.84f),
                                        0.88f to scrimColor.copy(alpha = 0.96f),
                                        1.00f to scrimColor
                                    )
                                )
                        )
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f)
                                .background(scrimColor)
                        )
                    }

                    // 顶层播放控制控件：预留顶部 featherDp 与遮罩羽化过渡区贴合
                    FullScreenControls(
                        isPlaying = isPlaying,
                        currentPositionProvider = currentPositionProvider,
                        duration = duration,
                        onSeek = handleSeek,
                        onTogglePlay = onTogglePlay,
                        onPlayNext = onPlayNext,
                        onPlayPrevious = onPlayPrevious,
                        playMode = playMode,
                        onToggleShuffle = onToggleShuffle,
                        onToggleRepeat = onToggleRepeat,
                        secondaryMode = fullScreenLyricSecondaryMode,
                        hasTranslation = hasTranslation,
                        hasRoma = hasRoma,
                        onToggleSecondaryMode = handleToggleSecondaryMode,
                        onShareLyrics = handleShareLyrics,
                        onLyricsSettingsClick = { showSettingsSheet = true },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = featherDp)
                    )
                }
            }

            // 4. 分享模式专属独立悬浮底栏：贴紧屏幕最底部边缘滑入，底层配有羽化+实色遮罩，顶层为复制按钮
            androidx.compose.animation.AnimatedVisibility(
                visible = isShareMode,
                enter = fadeIn(tween(200)) + slideInVertically(tween(240)) { it },
                exit = fadeOut(tween(180)) + slideOutVertically(tween(200)) { it },
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .onSizeChanged { size ->
                            if (size.height > 0) shareContainerHeightPx = size.height.toFloat()
                        }
                ) {
                    // 底层遮罩背景：顶部非线性羽化渐变，下方实色全覆盖保护复制按钮
                    Column(modifier = Modifier.matchParentSize()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(featherDp)
                                .background(
                                    Brush.verticalGradient(
                                        0.00f to Color.Transparent,
                                        0.08f to scrimColor.copy(alpha = 0.06f),
                                        0.20f to scrimColor.copy(alpha = 0.20f),
                                        0.36f to scrimColor.copy(alpha = 0.42f),
                                        0.54f to scrimColor.copy(alpha = 0.65f),
                                        0.72f to scrimColor.copy(alpha = 0.84f),
                                        0.88f to scrimColor.copy(alpha = 0.96f),
                                        1.00f to scrimColor
                                    )
                                )
                        )
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f)
                                .background(scrimColor)
                        )
                    }

                    // 顶层复制按键操作栏：预留顶部 featherDp 与遮罩羽化过渡区贴合
                    FullScreenShareBottomBar(
                        onCopy = handleCopyLyrics,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = featherDp)
                    )
                }
            }
        }

        if (showSettingsSheet) {
            FullScreenLyricsSettingsSheet(
                fontSize = fullScreenLyricTextSize,
                onFontSizeChange = { size ->
                    scope.launch { settingsPreferences.saveFullScreenLyricTextSize(size) }
                },
                lineSpacing = fullScreenLyricLineSpacing,
                onLineSpacingChange = { spacing ->
                    scope.launch { settingsPreferences.saveFullScreenLyricLineSpacing(spacing) }
                },
                secondarySpacing = fullScreenLyricSecondarySpacing,
                onSecondarySpacingChange = { spacing ->
                    scope.launch { settingsPreferences.saveFullScreenLyricSecondarySpacing(spacing) }
                },
                alignment = fullScreenLyricAlignment,
                onAlignmentChange = { align ->
                    scope.launch { settingsPreferences.saveFullScreenLyricAlignment(align) }
                },
                secondaryMode = fullScreenLyricSecondaryMode,
                onSecondaryModeChange = { mode ->
                    scope.launch { settingsPreferences.saveFullScreenLyricSecondaryMode(mode) }
                },
                hasTranslation = hasTranslation,
                hasRoma = hasRoma,
                advancedKaraokeEffect = fullScreenKaraokeAdvancedEffect,
                onAdvancedKaraokeEffectChange = { enabled ->
                    scope.launch { settingsPreferences.saveFullScreenKaraokeAdvancedEffect(enabled) }
                },
                karaokeGlowEffect = fullScreenKaraokeGlowEffect,
                onKaraokeGlowEffectChange = { enabled ->
                    scope.launch { settingsPreferences.saveFullScreenKaraokeGlowEffect(enabled) }
                },
                amllLyricsEnabled = amllLyricsEnabled,
                onAmllLyricsEnabledChange = { enabled ->
                    scope.launch { settingsPreferences.saveAmllLyricsEnabled(enabled) }
                },
                autoHideControls = fullScreenLyricAutoHideControls,
                onAutoHideControlsChange = { enabled ->
                    scope.launch { settingsPreferences.saveFullScreenLyricAutoHideControls(enabled) }
                },
                onDismiss = { showSettingsSheet = false }
            )
        }
    }
}

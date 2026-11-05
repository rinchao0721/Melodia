package com.lin0721.linmusic.feature.player.ui

import com.lin0721.linmusic.core.ui.components.PlayPauseIcon
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.VisibilityThreshold
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.ui.unit.IntSize
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SecondaryTabRow
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material3.SheetValue
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.Velocity
import com.lin0721.linmusic.core.ui.components.MelodiaDragHandle
import com.lin0721.linmusic.core.ui.components.MelodiaIconButton
import com.lin0721.linmusic.core.player.PlayMode
import com.lin0721.linmusic.core.ui.interaction.pressable
import com.lin0721.linmusic.core.ui.theme.BottomSheetShape
import com.lin0721.linmusic.core.ui.theme.LocalMelodiaWindowSizeClass
import com.lin0721.linmusic.core.ui.theme.LocalUiMotion
import com.lin0721.linmusic.core.ui.theme.MelodiaColors
import com.lin0721.linmusic.core.ui.theme.MelodiaPress
import com.lin0721.linmusic.core.ui.theme.MelodiaSpacing
import com.lin0721.linmusic.core.ui.theme.MelodiaWindowSizeClass
import kotlin.math.abs
import kotlin.math.roundToInt

// ────────────────────────────────────────────────────────────────────────────
// 全屏歌词操作工具栏（翻译/罗马音切换、分享、快捷歌词设置）
// ────────────────────────────────────────────────────────────────────────────
@Composable
fun FullScreenLyricsToolbar(
    secondaryMode: String,
    hasTranslation: Boolean,
    hasRoma: Boolean,
    onToggleSecondaryMode: () -> Unit,
    onShareClick: () -> Unit,
    onSettingsClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 左侧：翻译/罗马音多态切换按钮（文A / 音A）
        val hasAnySecondary = hasTranslation || hasRoma
        val (badgeText, badgeSub) = when (secondaryMode) {
            "roma" -> "音" to "A"
            else -> "文" to "A"
        }
        val badgeTint = when {
            !hasAnySecondary -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f)
            secondaryMode == "none" -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.65f)
            else -> MelodiaColors.TextPrimary
        }
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .pressable(if (hasAnySecondary) MelodiaPress.Icon else MelodiaPress.None) {
                    if (hasAnySecondary) onToggleSecondaryMode()
                },
            contentAlignment = Alignment.CenterStart
        ) {
            Row(
                verticalAlignment = Alignment.Bottom
            ) {
                Text(
                    text = badgeText,
                    color = badgeTint,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = badgeSub,
                    color = badgeTint,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.ExtraBold,
                    modifier = Modifier.padding(start = 1.dp, bottom = 1.dp)
                )
            }
        }

        Row(
            modifier = Modifier.offset(x = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(MelodiaSpacing.xs),
            verticalAlignment = Alignment.CenterVertically
        ) {
            MelodiaIconButton(onClick = onShareClick) {
                Icon(
                    imageVector = Icons.Rounded.Share,
                    contentDescription = "分享歌词",
                    tint = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.size(24.dp)
                )
            }

            MelodiaIconButton(onClick = onSettingsClick) {
                Icon(
                    imageVector = Icons.Default.MoreVert,
                    contentDescription = "歌词设置",
                    tint = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.size(24.dp)
                )
            }
        }
    }
}

// ────────────────────────────────────────────────────────────────────────────
// iOS 胶囊风格歌词字号滑块组件（平滑填充、触感良好、无生硬 steps 点）
// ────────────────────────────────────────────────────────────────────────────
@Composable
fun LyricCapsuleSlider(
    value: Int,
    onValueChange: (Int) -> Unit,
    valueRange: ClosedFloatingPointRange<Float> = 16f..32f,
    modifier: Modifier = Modifier,
    startLabel: String = "A",
    endLabel: String = "A",
    onValueChangeFinished: (() -> Unit)? = null
) {
    var componentWidthPx by remember { mutableFloatStateOf(1f) }
    val currentOnValueChangeFinished by rememberUpdatedState(onValueChangeFinished)
    val progressFraction = remember(value, valueRange) {
        ((value.toFloat() - valueRange.start) / (valueRange.endInclusive - valueRange.start)).coerceIn(0f, 1f)
    }

    val updateValueFromPosition = rememberUpdatedState { xPx: Float ->
        if (componentWidthPx > 0) {
            val fraction = (xPx / componentWidthPx).coerceIn(0f, 1f)
            val rawValue = valueRange.start + fraction * (valueRange.endInclusive - valueRange.start)
            onValueChange(rawValue.roundToInt())
        }
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(38.dp)
            .onSizeChanged { componentWidthPx = it.width.toFloat() }
            .clip(CircleShape)
            .background(MelodiaColors.Selected)
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val downX = down.position.x
                    val downY = down.position.y
                    val touchSlop = viewConfiguration.touchSlop
                    var isHorizontalDrag = false

                    while (true) {
                        val event = awaitPointerEvent()
                        val pointerChange = event.changes.firstOrNull { it.id == down.id } ?: break

                        if (!pointerChange.pressed) {
                            if (!isHorizontalDrag && !pointerChange.isConsumed) {
                                updateValueFromPosition.value(pointerChange.position.x)
                            }
                            break
                        }

                        if (!isHorizontalDrag && pointerChange.isConsumed) {
                            break
                        }

                        val dx = pointerChange.position.x - downX
                        val dy = pointerChange.position.y - downY

                        if (!isHorizontalDrag) {
                            if (abs(dy) > touchSlop && abs(dy) > abs(dx)) {
                                break
                            }
                            if (abs(dx) > touchSlop && abs(dx) >= abs(dy)) {
                                isHorizontalDrag = true
                                pointerChange.consume()
                                updateValueFromPosition.value(pointerChange.position.x)
                            }
                        } else {
                            pointerChange.consume()
                            updateValueFromPosition.value(pointerChange.position.x)
                        }
                    }
                    currentOnValueChangeFinished?.invoke()
                }
            }
    ) {
        // 激活填充进度
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .fillMaxWidth(progressFraction)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary)
        )

        // 胶囊两端视觉指示符
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = startLabel,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = MelodiaColors.OnAccent.copy(alpha = 0.85f)
            )
            Text(
                text = endLabel,
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold,
                color = MelodiaColors.OnAccent.copy(alpha = 0.85f)
            )
        }
    }
}

@Composable
private fun LyricSliderSetting(
    label: String,
    valueText: String,
    value: Int,
    onValueChange: (Int) -> Unit,
    valueRange: ClosedFloatingPointRange<Float>,
    startLabel: String = "A",
    endLabel: String = "A"
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(label, color = MaterialTheme.colorScheme.onSurface, fontSize = 14.sp)
            Text(
                text = valueText,
                color = MaterialTheme.colorScheme.primary,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold
            )
        }
        Spacer(modifier = Modifier.height(6.dp))
        LyricCapsuleSlider(
            value = value,
            onValueChange = onValueChange,
            valueRange = valueRange,
            startLabel = startLabel,
            endLabel = endLabel
        )
    }
}

@Composable
private fun LyricCompactSwitchRow(
    title: String,
    subtitle: String? = null,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .clickable { onCheckedChange(!checked) }
            .padding(vertical = 3.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 14.sp
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 11.sp
                )
            }
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            modifier = Modifier.graphicsLayer(scaleX = 0.85f, scaleY = 0.85f),
            colors = SwitchDefaults.colors(
                checkedThumbColor = MaterialTheme.colorScheme.primary,
                checkedTrackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
            )
        )
    }
}

// ────────────────────────────────────────────────────────────────────────────
// 全屏歌词快捷设置弹窗（字号、行间距、对齐方式、副文本展示）
// ────────────────────────────────────────────────────────────────────────────
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FullScreenLyricsSettingsSheet(
    fontSize: Int,
    onFontSizeChange: (Int) -> Unit,
    lineSpacing: Int,
    onLineSpacingChange: (Int) -> Unit,
    secondarySpacing: Int,
    onSecondarySpacingChange: (Int) -> Unit,
    alignment: String,
    onAlignmentChange: (String) -> Unit,
    secondaryMode: String,
    onSecondaryModeChange: (String) -> Unit,
    hasTranslation: Boolean,
    hasRoma: Boolean,
    advancedKaraokeEffect: Boolean = true,
    onAdvancedKaraokeEffectChange: (Boolean) -> Unit = {},
    karaokeGlowEffect: Boolean = false,
    onKaraokeGlowEffectChange: (Boolean) -> Unit = {},
    neteaseLyricsEnabled: Boolean = true,
    onNeteaseLyricsEnabledChange: (Boolean) -> Unit = {},
    qqLyricsEnabled: Boolean = true,
    onQqLyricsEnabledChange: (Boolean) -> Unit = {},
    amllLyricsEnabled: Boolean = true,
    onAmllLyricsEnabledChange: (Boolean) -> Unit = {},
    autoHideControls: Boolean = false,
    onAutoHideControlsChange: (Boolean) -> Unit = {},
    centerFirstLine: Boolean = false,
    onCenterFirstLineChange: (Boolean) -> Unit = {},
    onDismiss: () -> Unit
) {
    val motion = LocalUiMotion.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.background,
        shape = BottomSheetShape,
        dragHandle = { MelodiaDragHandle() }
    ) {
        val nestedScrollConnection = remember(sheetState) {
            object : NestedScrollConnection {
                override suspend fun onPreFling(available: Velocity): Velocity {
                    return if (available.y < 0 && sheetState.targetValue == SheetValue.Expanded) {
                        available
                    } else {
                        Velocity.Zero
                    }
                }
            }
        }

        val segmentedColors = SegmentedButtonDefaults.colors(
            activeContainerColor = MaterialTheme.colorScheme.primary,
            activeContentColor = MaterialTheme.colorScheme.onPrimary,
            activeBorderColor = Color.Transparent,
            inactiveContainerColor = MelodiaColors.Overlay.copy(alpha = 0.08f),
            inactiveContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
            inactiveBorderColor = Color.Transparent,
            disabledActiveContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.38f),
            disabledActiveContentColor = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.38f),
            disabledActiveBorderColor = Color.Transparent,
            disabledInactiveContainerColor = MelodiaColors.Overlay.copy(alpha = 0.04f),
            disabledInactiveContentColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f),
            disabledInactiveBorderColor = Color.Transparent
        )

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .nestedScroll(nestedScrollConnection)
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(horizontal = MelodiaSpacing.lg)
                .padding(bottom = MelodiaSpacing.md)
        ) {
            Text(
                text = "全屏歌词设置",
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 18.sp,
                fontWeight = FontWeight.ExtraBold,
                modifier = Modifier.padding(bottom = 6.dp)
            )

            val isExpanded = LocalMelodiaWindowSizeClass.current == MelodiaWindowSizeClass.Expanded
            val fontSizeRange = if (isExpanded) 20f..40f else 16f..32f
            val displayFontSize = fontSize.coerceIn(
                fontSizeRange.start.roundToInt(),
                fontSizeRange.endInclusive.roundToInt()
            )

            LaunchedEffect(fontSizeRange) {
                if (fontSize !in fontSizeRange.start.roundToInt()..fontSizeRange.endInclusive.roundToInt()) {
                    onFontSizeChange(displayFontSize)
                }
            }

            var selectedTab by rememberSaveable { mutableIntStateOf(0) }
            val tabTitles = listOf("排版", "效果", "界面")
            SecondaryTabRow(
                selectedTabIndex = selectedTab,
                containerColor = Color.Transparent,
                contentColor = MaterialTheme.colorScheme.primary
            ) {
                tabTitles.forEachIndexed { index, title ->
                    Tab(
                        selected = selectedTab == index,
                        onClick = { selectedTab = index },
                        text = {
                            Text(
                                text = title,
                                fontSize = 14.sp,
                                fontWeight = if (selectedTab == index) FontWeight.Bold else FontWeight.Normal
                            )
                        },
                        selectedContentColor = MaterialTheme.colorScheme.primary,
                        unselectedContentColor = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // 页签内容横向滑动淡入淡出，弹窗高度随内容平滑伸缩
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp)
            ) {
                AnimatedContent(
                    targetState = selectedTab,
                    transitionSpec = {
                        val direction = if (targetState > initialState) 1 else -1
                        (fadeIn(motion.tween(220, delayMillis = 60)) +
                            slideInHorizontally(motion.tween(260, easing = FastOutSlowInEasing)) { it / 6 * direction }) togetherWith
                            (fadeOut(motion.tween(120)) +
                                slideOutHorizontally(motion.tween(260, easing = FastOutSlowInEasing)) { -it / 6 * direction }) using
                            SizeTransform(clip = true) { _, _ ->
                                motion.spring(stiffness = Spring.StiffnessMediumLow, visibilityThreshold = IntSize.VisibilityThreshold)
                            }
                    },
                    label = "lyric_settings_tab"
                ) { tab ->
                  Column(modifier = Modifier.fillMaxWidth()) {
                    when (tab) {
                        0 -> {
                        LyricSliderSetting(
                            label = "字号",
                            valueText = "${displayFontSize} sp",
                            value = displayFontSize,
                            onValueChange = onFontSizeChange,
                            valueRange = fontSizeRange
                        )

                        LyricSliderSetting(
                            label = "行间距",
                            valueText = "${lineSpacing} dp",
                            value = lineSpacing,
                            onValueChange = onLineSpacingChange,
                            valueRange = 8f..48f,
                            startLabel = "窄",
                            endLabel = "宽"
                        )

                        LyricSliderSetting(
                            label = "翻译间距",
                            valueText = "${secondarySpacing} dp",
                            value = secondarySpacing,
                            onValueChange = onSecondarySpacingChange,
                            valueRange = 0f..24f,
                            startLabel = "窄",
                            endLabel = "宽"
                        )

                        Spacer(modifier = Modifier.height(4.dp))

                        // 歌词对齐方式分段选择器
                        Text(
                            text = "对齐方式",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 13.sp,
                            modifier = Modifier.padding(top = 4.dp, bottom = 4.dp)
                        )
                        val alignments = listOf(
                            "left" to "左对齐",
                            "center" to "居中对齐",
                            "right" to "右对齐"
                        )
                        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                            alignments.forEachIndexed { index, (key, label) ->
                                val isSelected = alignment == key
                                SegmentedButton(
                                    selected = isSelected,
                                    onClick = { onAlignmentChange(key) },
                                    shape = SegmentedButtonDefaults.itemShape(index = index, count = alignments.size),
                                    colors = segmentedColors,
                                    icon = {}
                                ) {
                                    Text(
                                        text = label,
                                        fontSize = 13.sp,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(4.dp))

                        // 歌词副文本分段选择器
                        Text(
                            text = "副文本",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 13.sp,
                            modifier = Modifier.padding(top = 4.dp, bottom = 4.dp)
                        )
                        val secondaryOptions = listOf(
                            "translation" to ("翻译" to hasTranslation),
                            "roma" to ("罗马音" to hasRoma),
                            "none" to ("仅原词" to true)
                        )
                        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                            secondaryOptions.forEachIndexed { index, (key, pair) ->
                                val (label, isAvailable) = pair
                                val isSelected = secondaryMode == key
                                SegmentedButton(
                                    selected = isSelected,
                                    onClick = { if (isAvailable) onSecondaryModeChange(key) },
                                    enabled = isAvailable,
                                    shape = SegmentedButtonDefaults.itemShape(index = index, count = secondaryOptions.size),
                                    colors = segmentedColors,
                                    icon = {}
                                ) {
                                    Text(
                                        text = if (isAvailable) label else "$label(无)",
                                        fontSize = 13.sp,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                    )
                                }
                            }
                        }
                        }
                        1 -> {
                            LyricCompactSwitchRow(
                                title = "启用网易云歌词源",
                                subtitle = "提供歌词、翻译与逐字歌词",
                                checked = neteaseLyricsEnabled,
                                onCheckedChange = onNeteaseLyricsEnabledChange
                            )

                            LyricCompactSwitchRow(
                                title = "启用QQ音乐歌词源",
                                subtitle = "提供更多逐字歌词",
                                checked = qqLyricsEnabled,
                                onCheckedChange = onQqLyricsEnabledChange
                            )

                            LyricCompactSwitchRow(
                                title = "启用 AMLL 歌词源",
                                subtitle = "支持对唱分边与背景和声",
                                checked = amllLyricsEnabled,
                                onCheckedChange = onAmllLyricsEnabledChange
                            )

                            LyricCompactSwitchRow(
                                title = "逐字歌词流光动效",
                                subtitle = "开启柔和渐变推进边缘",
                                checked = advancedKaraokeEffect,
                                onCheckedChange = onAdvancedKaraokeEffectChange
                            )

                            LyricCompactSwitchRow(
                                title = "字词呼吸光晕动效",
                                subtitle = "演唱字词叠加呼吸高亮微光",
                                checked = karaokeGlowEffect,
                                onCheckedChange = onKaraokeGlowEffectChange
                            )
                        }
                        else -> {
                            LyricCompactSwitchRow(
                                title = "自动隐藏控制组件",
                                subtitle = "无操作 5 秒后自动隐藏顶栏与播放控制，轻触屏幕重新呼出",
                                checked = autoHideControls,
                                onCheckedChange = onAutoHideControlsChange
                            )

                            LyricCompactSwitchRow(
                                title = "歌词首行居中",
                                subtitle = "首行下沉到屏幕中央，不再吸顶",
                                checked = centerFirstLine,
                                onCheckedChange = onCenterFirstLineChange
                            )

                        }
                    }
                  }
                }
            }
        }
    }
}

// ────────────────────────────────────────────────────────────────────────────
// 全屏歌词页的播放控制区（工具栏 + 进度条 + 五键播放控制）
// ────────────────────────────────────────────────────────────────────────────
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FullScreenControls(
    isPlaying: Boolean,
    currentPositionProvider: () -> Long,
    duration: Long,
    onSeek: (Long) -> Unit,
    onTogglePlay: () -> Unit,
    onPlayNext: () -> Unit,
    onPlayPrevious: () -> Unit,
    playMode: PlayMode,
    onToggleShuffle: () -> Unit,
    onToggleRepeat: () -> Unit,
    secondaryMode: String,
    hasTranslation: Boolean,
    hasRoma: Boolean,
    onToggleSecondaryMode: () -> Unit,
    onShareLyrics: () -> Unit,
    onLyricsSettingsClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val motion = LocalUiMotion.current
    var isSeeking by remember { mutableStateOf(false) }
    var seekPosition by remember { mutableFloatStateOf(0f) }

    val currentPosition = currentPositionProvider()
    val progress = if (duration > 0) {
        if (isSeeking) seekPosition else currentPosition.toFloat() / duration
    } else 0f

    Column(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = MelodiaSpacing.lg)
            .padding(bottom = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 720.dp)
        ) {
            // 1. 顶部插入操作工具栏（翻译/罗马音多态切换、分享、歌词设置）
        FullScreenLyricsToolbar(
            secondaryMode = secondaryMode,
            hasTranslation = hasTranslation,
            hasRoma = hasRoma,
            onToggleSecondaryMode = onToggleSecondaryMode,
            onShareClick = onShareLyrics,
            onSettingsClick = onLyricsSettingsClick
        )

        Spacer(modifier = Modifier.height(MelodiaSpacing.xs))

        // 2. 进度条
        var trackWidthPx by remember { mutableFloatStateOf(0f) }
        val density = LocalDensity.current

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(24.dp)
                .onSizeChanged { trackWidthPx = it.width.toFloat() }
                .pointerInput(duration) {
                    detectTapGestures(
                        onPress = { offset ->
                            if (trackWidthPx > 0f && duration > 0L) {
                                isSeeking = true
                                val newProgress = (offset.x / trackWidthPx).coerceIn(0f, 1f)
                                seekPosition = newProgress
                                val released = tryAwaitRelease()
                                if (released) {
                                    isSeeking = false
                                    onSeek((seekPosition * duration).toLong())
                                } else {
                                    isSeeking = false
                                }
                            }
                        }
                    )
                }
                .draggable(
                    orientation = Orientation.Horizontal,
                    state = rememberDraggableState { delta ->
                        if (trackWidthPx > 0f && duration > 0L) {
                            isSeeking = true
                            seekPosition = (seekPosition + delta / trackWidthPx).coerceIn(0f, 1f)
                        }
                    },
                    onDragStarted = { isSeeking = true },
                    onDragStopped = {
                        isSeeking = false
                        onSeek((seekPosition * duration).toLong())
                    }
                ),
            contentAlignment = Alignment.CenterStart
        ) {
            // 背景底轨
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(3.dp)
                    .clip(RoundedCornerShape(1.5.dp))
                    .background(MelodiaColors.Overlay.copy(alpha = 0.2f))
            )
            // 播放高亮条
            Box(
                modifier = Modifier
                    .fillMaxWidth(progress.coerceIn(0f, 1f))
                    .height(3.dp)
                    .clip(RoundedCornerShape(1.5.dp))
                    .background(MelodiaColors.TextPrimary)
            )
            // 进度滑块 Thumb
            val thumbSize = if (isSeeking) 14.dp else 8.dp
            val thumbRadiusPx = with(density) { (thumbSize / 2).toPx() }
            val thumbOffsetPx = (progress.coerceIn(0f, 1f) * trackWidthPx - thumbRadiusPx)
                .coerceIn(0f, (trackWidthPx - thumbRadiusPx * 2).coerceAtLeast(0f))
            val thumbOffsetDp = with(density) { thumbOffsetPx.toDp() }
            Box(
                modifier = Modifier
                    .offset(x = thumbOffsetDp)
                    .size(thumbSize)
                    .background(MelodiaColors.TextPrimary, CircleShape)
            )
        }

        // 3. 当前播放时间与总时长（两端完全对齐进度条轨道与工具栏两端）
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 2.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            val displayPosition = if (isSeeking) (seekPosition * duration).toLong() else currentPosition
            Text(formatTime(displayPosition), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
            Text(formatTime(duration), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
        }

        Spacer(modifier = Modifier.height(MelodiaSpacing.sm))

        // 4. 底部播放控制五键（两端严格对齐）
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = MelodiaSpacing.xs),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .pressable(MelodiaPress.Icon) { onToggleShuffle() },
                contentAlignment = Alignment.CenterStart
            ) {
                Icon(
                    imageVector = Icons.Default.Shuffle,
                    contentDescription = null,
                    tint = if (playMode == PlayMode.SHUFFLE) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(26.dp)
                )
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(20.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                MelodiaIconButton(onClick = onPlayPrevious, style = MelodiaPress.Transport) {
                    Icon(
                        imageVector = Icons.Rounded.SkipPrevious,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.size(46.dp)
                    )
                }

                val playInteraction = remember { MutableInteractionSource() }
                val isPressed by playInteraction.collectIsPressedAsState()
                val buttonAlpha = remember { Animatable(1f) }
                var clickTrigger by remember { mutableIntStateOf(0) }

                LaunchedEffect(isPressed) {
                    if (isPressed) {
                        buttonAlpha.animateTo(0.70f, motion.tween(durationMillis = 80))
                    } else if (clickTrigger == 0) {
                        buttonAlpha.animateTo(1f, motion.tween(durationMillis = 200, easing = FastOutSlowInEasing))
                    }
                }

                LaunchedEffect(clickTrigger) {
                    if (clickTrigger > 0) {
                        buttonAlpha.snapTo(0.70f)
                        buttonAlpha.animateTo(1f, motion.tween(durationMillis = 220, easing = FastOutSlowInEasing))
                    }
                }

                Box(
                    modifier = Modifier
                        .size(72.dp)
                        .graphicsLayer {
                            alpha = buttonAlpha.value
                        }
                        .background(MelodiaColors.TextPrimary, CircleShape)
                        .clip(CircleShape)
                        .clickable(
                            interactionSource = playInteraction,
                            indication = null,
                            onClick = {
                                clickTrigger++
                                onTogglePlay()
                            }
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    PlayPauseIcon(isPlaying, MelodiaColors.InverseText, size = 42.dp)
                }

                MelodiaIconButton(onClick = onPlayNext, style = MelodiaPress.Transport) {
                    Icon(
                        imageVector = Icons.Rounded.SkipNext,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.size(46.dp)
                    )
                }
            }

            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .pressable(MelodiaPress.Icon) { onToggleRepeat() },
                contentAlignment = Alignment.CenterEnd
            ) {
                Icon(
                    imageVector = if (playMode == PlayMode.SINGLE_LOOP) Icons.Default.RepeatOne else Icons.Default.Repeat,
                    contentDescription = null,
                    tint = if (playMode == PlayMode.SINGLE_LOOP) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(26.dp)
                )
            }
        }
    }
}
}


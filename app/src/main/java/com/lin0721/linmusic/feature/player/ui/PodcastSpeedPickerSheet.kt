package com.lin0721.linmusic.feature.player.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lin0721.linmusic.core.player.PodcastSpeedPolicy
import com.lin0721.linmusic.core.ui.components.MelodiaDragHandle
import com.lin0721.linmusic.core.ui.interaction.pressable
import com.lin0721.linmusic.core.ui.theme.BottomSheetShape
import com.lin0721.linmusic.core.ui.theme.LocalUiMotion
import com.lin0721.linmusic.core.ui.theme.MelodiaColors
import com.lin0721.linmusic.core.ui.theme.MelodiaPress
import com.lin0721.linmusic.core.ui.theme.MelodiaSpacing
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos

private val TickSlotWidth = 20.dp
private val RulerHeight = 82.dp

/**
 * 播客倍速滑动选择器（0.5x ~ 3.0x，步进 0.1x）。
 *
 * 采用横向磁性阻尼刻度尺方案：
 * - SnapFlingBehavior 自动吸附到最近的 0.1x 刻度，彻底避免手抖偏移；
 * - 刻度高度与颜色按到中心准星的实时像素距离做余弦插值高亮；
 * - 每跨过 0.1x 触发触觉反馈并实时调节音频倍速；
 * - 底部提供常用快捷胶囊一键平滑滚动定位。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PodcastSpeedPickerSheet(
    currentSpeed: Float,
    onSpeedSelected: (Float) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val motion = LocalUiMotion.current
    val haptic = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()

    val normalizedInitial = remember { PodcastSpeedPolicy.normalize(currentSpeed) }
    var selectedTenth by remember(normalizedInitial) {
        mutableIntStateOf(PodcastSpeedPolicy.toTenth(normalizedInitial))
    }
    val initialIndex = remember { PodcastSpeedPolicy.indexOf(normalizedInitial) }
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = initialIndex)
    val snapFlingBehavior = rememberSnapFlingBehavior(lazyListState = listState)

    // 实时计算当前距离视口中心最近的刻度下标
    val centeredIndex by remember {
        derivedStateOf {
            val layoutInfo = listState.layoutInfo
            val visible = layoutInfo.visibleItemsInfo
            if (visible.isEmpty()) return@derivedStateOf initialIndex
            val center = (layoutInfo.viewportStartOffset + layoutInfo.viewportEndOffset) / 2f
            visible.minByOrNull { info ->
                abs((info.offset + info.size / 2f) - center)
            }?.index ?: initialIndex
        }
    }

    // 手指拖动/惯性滑动期间，每经过一格立刻触发震动并实时更新倍速
    LaunchedEffect(listState) {
        snapshotFlow { centeredIndex to listState.isScrollInProgress }
            .distinctUntilChanged()
            .collect { (idx, scrolling) ->
                val tenth = PodcastSpeedPolicy.MIN_TENTH + idx.coerceIn(0, PodcastSpeedPolicy.STEP_COUNT - 1)
                if (tenth != selectedTenth) {
                    selectedTenth = tenth
                    if (scrolling) {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    }
                    onSpeedSelected(PodcastSpeedPolicy.fromTenth(tenth))
                }
            }
    }

    val activeSpeed = PodcastSpeedPolicy.fromTenth(selectedTenth)
    val accentColor = MaterialTheme.colorScheme.primary

    fun jumpToSpeed(target: Float) {
        val targetTenth = PodcastSpeedPolicy.toTenth(target)
        val idx = targetTenth - PodcastSpeedPolicy.MIN_TENTH
        if (targetTenth != selectedTenth) {
            selectedTenth = targetTenth
            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            onSpeedSelected(PodcastSpeedPolicy.fromTenth(targetTenth))
        }
        scope.launch {
            if (motion.enabled) {
                listState.animateScrollToItem(idx)
            } else {
                listState.scrollToItem(idx)
            }
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MelodiaColors.Background,
        shape = BottomSheetShape,
        dragHandle = { MelodiaDragHandle() }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(bottom = MelodiaSpacing.lg),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // 标题与重置按钮
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = MelodiaSpacing.lg),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(
                        text = "播客倍速",
                        color = MelodiaColors.TextPrimary,
                        fontSize = 19.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                    Text(
                        text = "仅对播客生效 · 自动记忆当前倍速",
                        color = MelodiaColors.TextSecondary,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }
                val isDefault = selectedTenth == PodcastSpeedPolicy.DEFAULT_TENTH
                Box(
                    modifier = Modifier
                        .clip(CircleShape)
                        .pressable(MelodiaPress.Pill, enabled = !isDefault) {
                            jumpToSpeed(PodcastSpeedPolicy.DEFAULT_SPEED)
                        }
                        .background(if (isDefault) MelodiaColors.Surface else accentColor.copy(alpha = 0.16f))
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = "恢复 1.0x",
                        color = if (isDefault) MelodiaColors.TextTertiary else accentColor,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            Spacer(Modifier.height(16.dp))

            // 顶部大号数字滚动反馈 + 速度描述
            AnimatedContent(
                targetState = selectedTenth,
                transitionSpec = {
                    val forward = targetState > initialState
                    motion.transform(
                        (slideInVertically(tween(140)) { h -> if (forward) h / 3 else -h / 3 } + fadeIn(tween(140)))
                            .togetherWith(
                                slideOutVertically(tween(110)) { h -> if (forward) -h / 3 else h / 3 } + fadeOut(tween(110))
                            )
                            .using(SizeTransform(clip = false))
                    )
                },
                label = "podcast_speed_value"
            ) { tenth ->
                Text(
                    text = PodcastSpeedPolicy.format(PodcastSpeedPolicy.fromTenth(tenth)),
                    color = if (tenth == PodcastSpeedPolicy.DEFAULT_TENTH) MelodiaColors.TextPrimary else accentColor,
                    fontSize = 36.sp,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = (-0.5).sp
                )
            }
            Text(
                text = speedDescription(activeSpeed),
                color = MelodiaColors.TextSecondary,
                fontSize = 12.sp,
                modifier = Modifier.padding(top = 4.dp)
            )

            Spacer(Modifier.height(14.dp))

            // 阻尼磁性刻度尺滑动区
            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(RulerHeight),
                contentAlignment = Alignment.Center
            ) {
                val sidePadding = (maxWidth - TickSlotWidth) / 2
                val bgColor = MelodiaColors.Background
                val mutedTickColor = MelodiaColors.TextSecondary.copy(alpha = 0.42f)

                LazyRow(
                    state = listState,
                    flingBehavior = snapFlingBehavior,
                    contentPadding = PaddingValues(horizontal = sidePadding.coerceAtLeast(0.dp)),
                    modifier = Modifier.fillMaxSize(),
                    verticalAlignment = Alignment.Top
                ) {
                    items(
                        count = PodcastSpeedPolicy.STEP_COUNT,
                        key = { idx -> PodcastSpeedPolicy.MIN_TENTH + idx }
                    ) { idx ->
                        val tenth = PodcastSpeedPolicy.MIN_TENTH + idx
                        val isMajor = tenth % 5 == 0
                        val itemProximity by remember {
                            derivedStateOf {
                                val layoutInfo = listState.layoutInfo
                                val info = layoutInfo.visibleItemsInfo.firstOrNull { it.index == idx }
                                    ?: return@derivedStateOf if (idx == centeredIndex) 1f else 0f
                                val center = (layoutInfo.viewportStartOffset + layoutInfo.viewportEndOffset) / 2f
                                val distance = abs((info.offset + info.size / 2f) - center)
                                val span = info.size * 3.2f
                                val linear = (1f - (distance / span)).coerceIn(0f, 1f)
                                ((1f - cos(linear * PI.toFloat())) / 2f)
                            }
                        }

                        Column(
                            modifier = Modifier
                                .width(TickSlotWidth)
                                .height(RulerHeight)
                                .clickable { jumpToSpeed(PodcastSpeedPolicy.fromTenth(tenth)) },
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Box(
                                modifier = Modifier
                                    .width(TickSlotWidth)
                                    .height(50.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Canvas(modifier = Modifier.fillMaxSize()) {
                                    val baseH = if (isMajor) 24.dp.toPx() else 13.dp.toPx()
                                    val maxH = if (isMajor) 36.dp.toPx() else 25.dp.toPx()
                                    val tickH = baseH + (maxH - baseH) * itemProximity
                                    val tickW = (if (isMajor) 2.dp else 1.4.dp).toPx() + 1.2.dp.toPx() * itemProximity
                                    val tickColor = lerp(mutedTickColor, accentColor, itemProximity)
                                    drawRoundRect(
                                        color = tickColor,
                                        topLeft = Offset((size.width - tickW) / 2f, size.height - tickH),
                                        size = Size(tickW, tickH),
                                        cornerRadius = CornerRadius(tickW / 2f, tickW / 2f)
                                    )
                                }
                            }
                            if (isMajor) {
                                Text(
                                    text = "${tenth / 10}.${tenth % 10}",
                                    color = lerp(MelodiaColors.TextSecondary, accentColor, itemProximity),
                                    fontSize = 11.sp,
                                    fontWeight = if (itemProximity > 0.5f) FontWeight.Bold else FontWeight.Medium,
                                    textAlign = TextAlign.Center,
                                    maxLines = 1,
                                    modifier = Modifier.padding(top = 6.dp)
                                )
                            }
                        }
                    }
                }

                // 中心固定准星指示线
                Box(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 4.dp)
                        .width(3.dp)
                        .height(46.dp)
                        .clip(CircleShape)
                        .background(accentColor)
                )

                // 左右边缘渐隐遮罩，提升刻度尺景深感
                Box(
                    modifier = Modifier
                        .align(Alignment.CenterStart)
                        .width(44.dp)
                        .height(RulerHeight)
                        .background(Brush.horizontalGradient(listOf(bgColor, Color.Transparent)))
                )
                Box(
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .width(44.dp)
                        .height(RulerHeight)
                        .background(Brush.horizontalGradient(listOf(Color.Transparent, bgColor)))
                )
            }

            Spacer(Modifier.height(16.dp))

            // 常用快捷倍率胶囊
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = MelodiaSpacing.md),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                PodcastSpeedPolicy.PRESETS.forEach { preset ->
                    val presetTenth = PodcastSpeedPolicy.toTenth(preset)
                    val isSelected = selectedTenth == presetTenth
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .pressable(MelodiaPress.Pill, shape = CircleShape) {
                                jumpToSpeed(preset)
                            }
                            .clip(CircleShape)
                            .then(
                                if (isSelected) {
                                    Modifier.background(accentColor)
                                } else {
                                    Modifier
                                        .background(MelodiaColors.Surface)
                                        .border(0.5.dp, MelodiaColors.Divider, CircleShape)
                                }
                            )
                            .padding(vertical = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = PodcastSpeedPolicy.format(preset),
                            color = if (isSelected) MelodiaColors.OnAccent else MelodiaColors.TextPrimary,
                            fontSize = 11.5.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                            maxLines = 1
                        )
                    }
                }
            }
        }
    }
}

private fun speedDescription(speed: Float): String {
    val tenth = PodcastSpeedPolicy.toTenth(speed)
    return when {
        tenth < 10 -> "慢速细听"
        tenth == 10 -> "原速播放"
        tenth <= 13 -> "轻度加速"
        tenth <= 18 -> "常用高效"
        tenth <= 23 -> "快速收听"
        else -> "极速浏览"
    }
}

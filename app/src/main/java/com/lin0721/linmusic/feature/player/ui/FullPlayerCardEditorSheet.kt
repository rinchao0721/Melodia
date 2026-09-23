package com.lin0721.linmusic.feature.player.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.lin0721.linmusic.core.preferences.FullPlayerCard
import com.lin0721.linmusic.core.preferences.FullPlayerCardLayout
import com.lin0721.linmusic.core.preferences.FullPlayerCardSetting
import com.lin0721.linmusic.core.ui.components.MelodiaDragHandle
import com.lin0721.linmusic.core.ui.components.MelodiaSwitch
import com.lin0721.linmusic.core.ui.theme.BottomSheetShape
import com.lin0721.linmusic.core.ui.theme.MelodiaSpacing

// 全屏播放页信息卡片编辑弹窗：长按整行拖动排序，开关控制显隐
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FullPlayerCardEditorSheet(
    layout: List<FullPlayerCardSetting>,
    onLayoutChange: (List<FullPlayerCardSetting>) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val haptic = LocalHapticFeedback.current
    val currentOnLayoutChange by rememberUpdatedState(onLayoutChange)

    // 拖动期间以本地列表为准，松手才落盘，避免每次换位都写 DataStore
    var items by remember { mutableStateOf(layout) }
    var draggedCard by remember { mutableStateOf<FullPlayerCard?>(null) }
    var dragOffset by remember { mutableFloatStateOf(0f) }
    val rowHeights = remember { mutableStateMapOf<FullPlayerCard, Int>() }

    LaunchedEffect(layout) {
        if (draggedCard == null) items = layout
    }

    fun commit(newItems: List<FullPlayerCardSetting>) {
        items = newItems
        currentOnLayoutChange(newItems)
    }

    // 越过相邻行整行高度即换位，并扣掉该行高度，保证拖动中的行始终跟手
    fun onDrag(deltaY: Float) {
        val card = draggedCard ?: return
        dragOffset += deltaY
        var index = items.indexOfFirst { it.card == card }
        if (index < 0) return
        var swapped = false
        while (index < items.lastIndex) {
            val nextHeight = rowHeights[items[index + 1].card]?.toFloat() ?: break
            if (dragOffset <= nextHeight) break
            items = items.toMutableList().apply { add(index + 1, removeAt(index)) }
            dragOffset -= nextHeight
            index += 1
            swapped = true
        }
        while (index > 0) {
            val prevHeight = rowHeights[items[index - 1].card]?.toFloat() ?: break
            if (dragOffset >= -prevHeight) break
            items = items.toMutableList().apply { add(index - 1, removeAt(index)) }
            dragOffset += prevHeight
            index -= 1
            swapped = true
        }
        if (index == 0) dragOffset = dragOffset.coerceAtLeast(0f)
        if (index == items.lastIndex) dragOffset = dragOffset.coerceAtMost(0f)
        if (swapped) haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
    }

    fun onDragEnd() {
        if (draggedCard == null) return
        draggedCard = null
        dragOffset = 0f
        if (items != layout) currentOnLayoutChange(items)
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.background,
        shape = BottomSheetShape,
        dragHandle = { MelodiaDragHandle() }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = MelodiaSpacing.lg)
                .padding(bottom = MelodiaSpacing.lg)
        ) {
            Text(
                text = "编辑卡片",
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 20.sp,
                fontWeight = FontWeight.ExtraBold
            )
            Text(
                text = "长按拖动调整顺序",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 13.sp,
                modifier = Modifier.padding(top = 4.dp, bottom = MelodiaSpacing.sm)
            )

            items.forEach { setting ->
                key(setting.card) {
                    CardEditorRow(
                        setting = setting,
                        isDragging = draggedCard == setting.card,
                        dragOffsetY = if (draggedCard == setting.card) dragOffset else 0f,
                        onHeightMeasured = { rowHeights[setting.card] = it },
                        onVisibleChange = { visible ->
                            commit(items.map { if (it.card == setting.card) it.copy(visible = visible) else it })
                        },
                        onDragStart = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            draggedCard = setting.card
                            dragOffset = 0f
                        },
                        onDrag = ::onDrag,
                        onDragEnd = ::onDragEnd
                    )
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = MelodiaSpacing.sm),
                horizontalArrangement = Arrangement.Center
            ) {
                PlayerCapsuleButton(
                    text = "恢复默认",
                    onClick = { commit(FullPlayerCardLayout.DEFAULT) },
                    enabled = draggedCard == null && items != FullPlayerCardLayout.DEFAULT
                )
            }
        }
    }
}

@Composable
private fun CardEditorRow(
    setting: FullPlayerCardSetting,
    isDragging: Boolean,
    dragOffsetY: Float,
    onHeightMeasured: (Int) -> Unit,
    onVisibleChange: (Boolean) -> Unit,
    onDragStart: () -> Unit,
    onDrag: (Float) -> Unit,
    onDragEnd: () -> Unit
) {
    val liftSpec = remember { spring<Dp>(stiffness = Spring.StiffnessMediumLow) }
    val elevation by animateDpAsState(
        targetValue = if (isDragging) 8.dp else 0.dp,
        animationSpec = liftSpec,
        label = "card_editor_elev"
    )
    val bgColor by animateColorAsState(
        targetValue = if (isDragging) MaterialTheme.colorScheme.surfaceVariant else Color.Transparent,
        label = "card_editor_bg"
    )
    val currentOnDragStart by rememberUpdatedState(onDragStart)
    val currentOnDrag by rememberUpdatedState(onDrag)
    val currentOnDragEnd by rememberUpdatedState(onDragEnd)
    val rowShape = RoundedCornerShape(8.dp)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .onSizeChanged { onHeightMeasured(it.height) }
            .then(
                if (isDragging) Modifier
                    .zIndex(1f)
                    .graphicsLayer { translationY = dragOffsetY }
                    .shadow(elevation, rowShape)
                else Modifier
            )
            .clip(rowShape)
            .background(bgColor)
            .pointerInput(Unit) {
                detectDragGesturesAfterLongPress(
                    onDragStart = { currentOnDragStart() },
                    onDrag = { change, offset ->
                        change.consume()
                        currentOnDrag(offset.y)
                    },
                    onDragEnd = { currentOnDragEnd() },
                    onDragCancel = { currentOnDragEnd() }
                )
            }
            .padding(vertical = MelodiaSpacing.xs, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = Icons.Default.Menu,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(20.dp)
        )
        Spacer(modifier = Modifier.width(MelodiaSpacing.sm))
        Text(
            text = setting.card.title,
            color = if (setting.visible) MaterialTheme.colorScheme.onSurface
            else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
            fontSize = 15.sp,
            modifier = Modifier.weight(1f)
        )
        MelodiaSwitch(
            checked = setting.visible,
            onCheckedChange = onVisibleChange
        )
    }
}

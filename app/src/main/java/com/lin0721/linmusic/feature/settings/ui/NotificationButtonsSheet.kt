package com.lin0721.linmusic.feature.settings.ui

import androidx.annotation.DrawableRes
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.lin0721.linmusic.R
import com.lin0721.linmusic.core.preferences.NotificationButton
import com.lin0721.linmusic.core.preferences.NotificationButtonLayout
import com.lin0721.linmusic.core.preferences.NotificationButtonSetting
import com.lin0721.linmusic.core.ui.components.MelodiaButton
import com.lin0721.linmusic.core.ui.components.MelodiaDragHandle
import com.lin0721.linmusic.core.ui.components.MelodiaSwitch
import com.lin0721.linmusic.core.ui.theme.BottomSheetShape
import com.lin0721.linmusic.core.ui.theme.MelodiaColors
import com.lin0721.linmusic.core.ui.theme.MelodiaSpacing
import com.lin0721.linmusic.core.ui.theme.themedShadow
import com.lin0721.linmusic.core.ui.theme.uiSpring
import com.lin0721.linmusic.core.ui.icons.MelodiaIcons
import com.lin0721.linmusic.core.ui.icons.Play
import com.lin0721.linmusic.core.ui.icons.SkipNext
import com.lin0721.linmusic.core.ui.icons.SkipPrevious

// 通知栏按钮自定义弹层：固定核心播控展示 + 可配置项显隐开关与长按拖动排序
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotificationButtonsSheet(
    layout: List<NotificationButtonSetting>,
    onLayoutChange: (List<NotificationButtonSetting>) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val haptic = LocalHapticFeedback.current
    val currentOnLayoutChange by rememberUpdatedState(onLayoutChange)

    var items by remember { mutableStateOf(layout) }
    var draggedItem by remember { mutableStateOf<NotificationButton?>(null) }
    var dragOffset by remember { mutableFloatStateOf(0f) }
    val rowHeights = remember { mutableStateMapOf<NotificationButton, Int>() }

    LaunchedEffect(layout) {
        if (draggedItem == null) items = layout
    }

    fun commit(newItems: List<NotificationButtonSetting>) {
        items = newItems
        currentOnLayoutChange(newItems)
    }

    fun onDrag(deltaY: Float) {
        val item = draggedItem ?: return
        dragOffset += deltaY
        var index = items.indexOfFirst { it.item == item }
        if (index < 0) return
        var swapped = false
        while (index < items.lastIndex) {
            val nextHeight = rowHeights[items[index + 1].item]?.toFloat() ?: break
            if (dragOffset <= nextHeight) break
            items = items.toMutableList().apply { add(index + 1, removeAt(index)) }
            dragOffset -= nextHeight
            index += 1
            swapped = true
        }
        while (index > 0) {
            val prevHeight = rowHeights[items[index - 1].item]?.toFloat() ?: break
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
        if (draggedItem == null) return
        draggedItem = null
        dragOffset = 0f
        if (items != layout) currentOnLayoutChange(items)
    }

    // 找出第 2 个已启用项的位置，用于在静止状态下绘制优先槽位分隔线
    val visibleItems = remember(items) {
        items.filter { it.visible }.map { it.item }
    }
    val priorityCutoffItem = visibleItems.getOrNull(NotificationButtonLayout.PRIORITY_SLOT_COUNT - 1)
    val showCutoffDivider = draggedItem == null &&
        priorityCutoffItem != null &&
        items.indexOfFirst { it.item == priorityCutoffItem } < items.lastIndex

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.background,
        shape = BottomSheetShape,
        dragHandle = { MelodiaDragHandle() }
    ) {
        // 弹层贴到状态栏时 M3 会追加顶部 inset，内容高度随之变化引发上下抽动，需限高避免触顶
        val maxSheetContentHeight = (LocalConfiguration.current.screenHeightDp * 0.82f).dp
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = maxSheetContentHeight)
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(horizontal = MelodiaSpacing.lg)
                .padding(bottom = MelodiaSpacing.lg)
        ) {
            Text(
                text = "通知栏按钮",
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 20.sp,
                fontWeight = FontWeight.ExtraBold
            )

            Text(
                text = "长按拖动调整顺序；仅特定场景可用的按钮在其他模式下自动隐藏",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 13.sp,
                modifier = Modifier.padding(top = 4.dp, bottom = MelodiaSpacing.sm)
            )

            // 固定置顶的核心播控按钮，仅作示意且不可关闭或拖动
            Text(
                text = "固定控制按钮",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.padding(bottom = 4.dp)
            )
            FixedNotificationButtonRow(
                icon = MelodiaIcons.SkipPrevious,
                title = "上一首",
                hint = "私人 FM 自动隐藏"
            )
            FixedNotificationButtonRow(
                icon = MelodiaIcons.Play,
                title = "播放 / 暂停"
            )
            FixedNotificationButtonRow(
                icon = MelodiaIcons.SkipNext,
                title = "下一首"
            )

            LabeledSectionDivider(
                label = "系统媒体卡片优先显示（前 ${NotificationButtonLayout.PRIORITY_SLOT_COUNT} 个）"
            )

            items.forEach { setting ->
                val isPriorityItem = setting.visible &&
                    visibleItems.indexOf(setting.item) in 0 until NotificationButtonLayout.PRIORITY_SLOT_COUNT
                key(setting.item) {
                    NotificationButtonEditorRow(
                        setting = setting,
                        isPriority = isPriorityItem,
                        isDragging = draggedItem == setting.item,
                        dragOffsetY = if (draggedItem == setting.item) dragOffset else 0f,
                        onHeightMeasured = { rowHeights[setting.item] = it },
                        onVisibleChange = { visible ->
                            commit(items.map { if (it.item == setting.item) it.copy(visible = visible) else it })
                        },
                        onDragStart = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            draggedItem = setting.item
                            dragOffset = 0f
                        },
                        onDrag = ::onDrag,
                        onDragEnd = ::onDragEnd
                    )
                }
                if (showCutoffDivider && setting.item == priorityCutoffItem) {
                    LabeledSectionDivider(label = "以下按钮仅展开通知时显示")
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = MelodiaSpacing.md),
                horizontalArrangement = Arrangement.Center
            ) {
                MelodiaButton(
                    onClick = { commit(NotificationButtonLayout.DEFAULT) },
                    enabled = draggedItem == null && items != NotificationButtonLayout.DEFAULT
                ) {
                    Text("恢复默认")
                }
            }
        }
    }
}

@Composable
private fun FixedNotificationButtonRow(
    icon: ImageVector,
    title: String,
    hint: String? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f),
            modifier = Modifier.size(20.dp)
        )
        Spacer(modifier = Modifier.width(MelodiaSpacing.sm))
        Text(
            text = title,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
            fontSize = 14.sp,
            modifier = Modifier.weight(1f)
        )
        if (hint != null) {
            Text(
                text = hint,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f),
                fontSize = 11.sp,
                modifier = Modifier.padding(end = MelodiaSpacing.sm)
            )
        }
        Text(
            text = "固定",
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f),
            fontSize = 12.sp
        )
    }
}

@Composable
private fun LabeledSectionDivider(label: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = MelodiaSpacing.xs),
        verticalAlignment = Alignment.CenterVertically
    ) {
        HorizontalDivider(
            color = MelodiaColors.Divider,
            modifier = Modifier.weight(1f)
        )
        Text(
            text = label,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 11.sp,
            modifier = Modifier.padding(horizontal = MelodiaSpacing.sm)
        )
        HorizontalDivider(
            color = MelodiaColors.Divider,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun NotificationButtonEditorRow(
    setting: NotificationButtonSetting,
    isPriority: Boolean,
    isDragging: Boolean,
    dragOffsetY: Float,
    onHeightMeasured: (Int) -> Unit,
    onVisibleChange: (Boolean) -> Unit,
    onDragStart: () -> Unit,
    onDrag: (Float) -> Unit,
    onDragEnd: () -> Unit
) {
    val liftSpec = uiSpring<Dp>(stiffness = Spring.StiffnessMediumLow)
    val elevation by animateDpAsState(
        targetValue = if (isDragging) 8.dp else 0.dp,
        animationSpec = liftSpec,
        label = "notification_btn_editor_elev"
    )
    val bgColor by animateColorAsState(
        targetValue = if (isDragging) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0f),
        animationSpec = uiSpring(),
        label = "notification_btn_editor_bg"
    )
    val currentOnDragStart by rememberUpdatedState(onDragStart)
    val currentOnDrag by rememberUpdatedState(onDrag)
    val currentOnDragEnd by rememberUpdatedState(onDragEnd)
    val rowShape = RoundedCornerShape(8.dp)
    val contextTag = notificationButtonContextTag(setting.item)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .onSizeChanged { onHeightMeasured(it.height) }
            .then(
                if (isDragging) Modifier
                    .zIndex(1f)
                    .graphicsLayer { translationY = dragOffsetY }
                    .themedShadow(elevation, rowShape)
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
        Icon(
            painter = painterResource(notificationButtonIconRes(setting.item)),
            contentDescription = null,
            tint = if (setting.visible) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
            modifier = Modifier.size(20.dp)
        )
        Spacer(modifier = Modifier.width(MelodiaSpacing.sm))
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = setting.item.title,
                    color = if (setting.visible) MaterialTheme.colorScheme.onSurface
                    else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                    fontSize = 15.sp
                )
                if (isPriority) {
                    Spacer(modifier = Modifier.width(6.dp))
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.14f))
                            .padding(horizontal = 5.dp, vertical = 1.dp)
                    ) {
                        Text(
                            text = "优先",
                            color = MaterialTheme.colorScheme.primary,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
            if (contextTag != null) {
                Text(
                    text = contextTag,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    fontSize = 11.sp
                )
            }
        }
        MelodiaSwitch(
            checked = setting.visible,
            onCheckedChange = onVisibleChange
        )
    }
}

@DrawableRes
private fun notificationButtonIconRes(item: NotificationButton): Int = when (item) {
    NotificationButton.LIKE -> R.drawable.ic_favorite
    NotificationButton.DISLIKE -> R.drawable.ic_thumb_down
    NotificationButton.PLAY_MODE -> R.drawable.ic_repeat
    NotificationButton.DESKTOP_LRC -> R.drawable.ic_desktop_lyric
    NotificationButton.SLEEP_TIMER -> R.drawable.ic_sleep_timer
    NotificationButton.CLOSE_APP -> R.drawable.ic_close
    NotificationButton.SEEK_BACK -> R.drawable.ic_replay_15
    NotificationButton.SEEK_FORWARD -> R.drawable.ic_forward_15
}

private fun notificationButtonContextTag(item: NotificationButton): String? = when (item) {
    NotificationButton.DISLIKE -> "仅私人 FM 显示"
    NotificationButton.PLAY_MODE -> "私人 FM 自动隐藏"
    NotificationButton.SEEK_BACK,
    NotificationButton.SEEK_FORWARD -> "仅播客模式显示"
    else -> null
}

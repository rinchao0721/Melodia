package com.lin0721.linmusic.feature.settings.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddBox
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.CloudQueue
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.lin0721.linmusic.core.preferences.BottomBarItem
import com.lin0721.linmusic.core.preferences.BottomBarItemSetting
import com.lin0721.linmusic.core.preferences.BottomBarLayout
import com.lin0721.linmusic.core.ui.components.MelodiaButton
import com.lin0721.linmusic.core.ui.components.MelodiaDragHandle
import com.lin0721.linmusic.core.ui.components.MelodiaSwitch
import com.lin0721.linmusic.core.ui.theme.BottomSheetShape
import com.lin0721.linmusic.core.ui.theme.MelodiaColors
import com.lin0721.linmusic.core.ui.theme.MelodiaSpacing
import com.lin0721.linmusic.core.ui.theme.themedShadow
import com.lin0721.linmusic.core.ui.theme.uiSpring

// 底部导航栏自定义弹层：支持拖拽排序、显隐切换、数量限制拦截及仅图标模式
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BottomBarCustomizationSheet(
    layout: List<BottomBarItemSetting>,
    iconOnly: Boolean,
    onLayoutChange: (List<BottomBarItemSetting>) -> Unit,
    onIconOnlyChange: (Boolean) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val haptic = LocalHapticFeedback.current
    val currentOnLayoutChange by rememberUpdatedState(onLayoutChange)

    var items by remember { mutableStateOf(layout) }
    var draggedItem by remember { mutableStateOf<BottomBarItem?>(null) }
    var dragOffset by remember { mutableFloatStateOf(0f) }
    val rowHeights = remember { mutableStateMapOf<BottomBarItem, Int>() }

    LaunchedEffect(layout) {
        if (draggedItem == null) items = layout
    }

    fun commit(newItems: List<BottomBarItemSetting>) {
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

    val visibleCount = items.count { it.visible }

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
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(horizontal = MelodiaSpacing.lg)
                .padding(bottom = MelodiaSpacing.lg)
        ) {
            Text(
                text = "底栏自定义",
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 20.sp,
                fontWeight = FontWeight.ExtraBold
            )

            // 外观设置：仅显示图标开关
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = MelodiaSpacing.sm, bottom = MelodiaSpacing.xs),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "仅显示图标",
                        color = MaterialTheme.colorScheme.onSurface,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = "隐藏底栏各个选项的文字标签",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 12.sp
                    )
                }
                MelodiaSwitch(
                    checked = iconOnly,
                    onCheckedChange = onIconOnlyChange
                )
            }

            HorizontalDivider(
                color = MelodiaColors.Divider,
                modifier = Modifier.padding(vertical = MelodiaSpacing.xs)
            )

            Text(
                text = "长按拖动调整顺序，可配置 2~5 个底栏项目",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 13.sp,
                modifier = Modifier.padding(top = 4.dp, bottom = MelodiaSpacing.sm)
            )

            items.forEach { setting ->
                key(setting.item) {
                    val canToggle = if (setting.visible) {
                        visibleCount > BottomBarLayout.MIN_VISIBLE_ITEMS
                    } else {
                        visibleCount < BottomBarLayout.MAX_VISIBLE_ITEMS
                    }

                    BottomBarItemEditorRow(
                        setting = setting,
                        canToggle = canToggle,
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
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = MelodiaSpacing.md),
                horizontalArrangement = Arrangement.Center
            ) {
                MelodiaButton(
                    onClick = {
                        commit(BottomBarLayout.DEFAULT)
                        onIconOnlyChange(false)
                    },
                    enabled = draggedItem == null && (items != BottomBarLayout.DEFAULT || iconOnly)
                ) {
                    Text("恢复默认")
                }
            }
        }
    }
}

@Composable
private fun BottomBarItemEditorRow(
    setting: BottomBarItemSetting,
    canToggle: Boolean,
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
        label = "bottom_bar_editor_elev"
    )
    val bgColor by animateColorAsState(
        targetValue = if (isDragging) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0f),
        animationSpec = uiSpring(),
        label = "bottom_bar_editor_bg"
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
            imageVector = bottomBarItemIcon(setting.item),
            contentDescription = null,
            tint = if (setting.visible) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
            modifier = Modifier.size(20.dp)
        )
        Spacer(modifier = Modifier.width(MelodiaSpacing.sm))
        Text(
            text = setting.item.title,
            color = if (setting.visible) MaterialTheme.colorScheme.onSurface
            else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
            fontSize = 15.sp,
            modifier = Modifier.weight(1f)
        )
        MelodiaSwitch(
            checked = setting.visible,
            onCheckedChange = onVisibleChange,
            enabled = canToggle
        )
    }
}

// 候选项目对应图标映射
fun bottomBarItemIcon(item: BottomBarItem): ImageVector = when (item) {
    BottomBarItem.HOME -> Icons.Default.Home
    BottomBarItem.SEARCH -> Icons.Default.Search
    BottomBarItem.LIBRARY -> Icons.Default.LibraryMusic
    BottomBarItem.CREATE -> Icons.Default.AddBox
    BottomBarItem.LOCAL_MUSIC -> Icons.Default.Folder
    BottomBarItem.DOWNLOADS -> Icons.Outlined.FileDownload
    BottomBarItem.CLOUD -> Icons.Outlined.CloudQueue
    BottomBarItem.PROFILE -> Icons.Default.Person
}

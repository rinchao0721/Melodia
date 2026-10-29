package com.lin0721.linmusic.core.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.zIndex
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lin0721.linmusic.LocalBottomOverlayInset
import com.lin0721.linmusic.SuppressMiniPlayer
import com.lin0721.linmusic.core.ui.interaction.pressable
import com.lin0721.linmusic.core.ui.theme.MelodiaColors
import com.lin0721.linmusic.core.ui.theme.MelodiaPress
import com.lin0721.linmusic.core.ui.theme.MelodiaSpacing
import com.lin0721.linmusic.core.ui.theme.melodiaStatusBarTopPadding
import com.lin0721.linmusic.core.ui.theme.uiTween

// 歌曲多选在列表行上的状态与回调，歌单页与歌手页共用
@Immutable
data class TrackSelectionUi(
    val active: Boolean = false,
    val selectedIds: Set<Long> = emptySet(),
    val onEnter: (Long) -> Unit = {},
    val onToggle: (Long) -> Unit = {}
)

@Immutable
data class SelectionAction(
    val label: String,
    val icon: ImageVector,
    val onClick: () -> Unit
)

private val SelectionHeaderHeight = 56.dp
private val SelectionBarRadius = 8.dp
private const val DISABLED_ACTION_ALPHA = 0.38f
private const val SELECTION_ANIMATION_MS = 250
// 压过页面顶栏（zIndex 8~10）
private const val SELECTION_Z_INDEX = 20f
private val SelectionBarListPadding = 76.dp

// 多选态顶栏：退出在左、已选数量居中、全选在右；底色由调用方按页面主色传入，自带状态栏留白
@Composable
fun SelectionHeader(
    count: Int,
    allSelected: Boolean,
    containerColor: Color,
    onToggleAll: () -> Unit,
    onExit: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(containerColor)
            .padding(top = melodiaStatusBarTopPadding())
            .height(SelectionHeaderHeight)
    ) {
        MelodiaIconButton(
            onClick = onExit,
            modifier = Modifier.align(Alignment.CenterStart).padding(start = MelodiaSpacing.xs)
        ) {
            Icon(Icons.Rounded.Close, contentDescription = "退出多选", tint = MaterialTheme.colorScheme.onSurface)
        }
        Text(
            text = "已选 $count 首",
            color = MaterialTheme.colorScheme.onSurface,
            fontSize = 17.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.align(Alignment.Center)
        )
        MelodiaTextButton(
            onClick = onToggleAll,
            modifier = Modifier.align(Alignment.CenterEnd).padding(end = MelodiaSpacing.xs)
        ) {
            Text(if (allSelected) "取消全选" else "全选", color = MaterialTheme.colorScheme.primary, fontSize = 15.sp)
        }
    }
}

// 多选态底部操作栏：与迷你播放器同款的悬浮圆角卡片（描边 + 弥散阴影）；未选歌曲时整体置灰
@Composable
fun SelectionActionBar(
    actions: List<SelectionAction>,
    enabled: Boolean,
    modifier: Modifier = Modifier
) {
    val shape = RoundedCornerShape(SelectionBarRadius)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = MelodiaSpacing.sm)
            .padding(bottom = MelodiaSpacing.sm)
            .shadow(
                elevation = 16.dp,
                shape = shape,
                ambientColor = MelodiaColors.ShadowAmbient.copy(alpha = 0.4f),
                spotColor = MelodiaColors.ShadowSpot.copy(alpha = 0.4f)
            )
            .clip(shape)
            .background(MelodiaColors.Surface)
            .border(0.5.dp, MelodiaColors.Divider, shape)
    ) {
        actions.forEach { action ->
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .weight(1f)
                    .pressable(style = MelodiaPress.Row, enabled = enabled, role = Role.Button, onClick = action.onClick)
                    .alpha(if (enabled) 1f else DISABLED_ACTION_ALPHA)
                    .padding(vertical = 10.dp)
            ) {
                Icon(
                    imageVector = action.icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.size(22.dp)
                )
                Text(
                    text = action.label,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = 11.sp,
                    maxLines = 1,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }
        }
    }
}

// 操作栏悬浮在底栏上方，列表底部要多留出它的高度，随进出多选平滑变化
@Composable
fun rememberSelectionBottomPadding(active: Boolean): Dp {
    val padding by animateDpAsState(
        targetValue = if (active) SelectionBarListPadding else 0.dp,
        animationSpec = uiTween(SELECTION_ANIMATION_MS),
        label = "selection_bottom_padding"
    )
    return padding
}

// active 期间取实时值，退出动画播放时保持最后一个值，避免淡出过程中文字跳变
@Composable
private fun <T> rememberHeldWhile(active: Boolean, value: T): T {
    var held by remember { mutableStateOf(value) }
    if (active) held = value
    return held
}

// 多选浮层：顶栏自上滑入、操作栏自下升起，同时收起迷你播放器；歌单页与歌手页共用
@Composable
fun BoxScope.TrackSelectionOverlay(
    selectionUi: TrackSelectionUi,
    actions: List<SelectionAction>,
    visibleIds: List<Long>,
    headerColor: Color,
    onToggleSelectAll: (List<Long>) -> Unit,
    onExit: () -> Unit
) {
    SuppressMiniPlayer(selectionUi.active)
    val count = rememberHeldWhile(selectionUi.active, selectionUi.selectedIds.size)
    val allSelected = rememberHeldWhile(
        selectionUi.active,
        visibleIds.isNotEmpty() && selectionUi.selectedIds.containsAll(visibleIds)
    )
    val hasSelection = rememberHeldWhile(selectionUi.active, selectionUi.selectedIds.isNotEmpty())

    AnimatedVisibility(
        visible = selectionUi.active,
        enter = fadeIn(uiTween(SELECTION_ANIMATION_MS)) + slideInVertically(uiTween(SELECTION_ANIMATION_MS)) { -it / 2 },
        exit = fadeOut(uiTween(SELECTION_ANIMATION_MS)) + slideOutVertically(uiTween(SELECTION_ANIMATION_MS)) { -it / 2 },
        modifier = Modifier.align(Alignment.TopCenter).zIndex(SELECTION_Z_INDEX)
    ) {
        SelectionHeader(
            count = count,
            allSelected = allSelected,
            containerColor = headerColor,
            onToggleAll = { onToggleSelectAll(visibleIds) },
            onExit = onExit
        )
    }
    AnimatedVisibility(
        visible = selectionUi.active,
        enter = fadeIn(uiTween(SELECTION_ANIMATION_MS)) + slideInVertically(uiTween(SELECTION_ANIMATION_MS)) { it },
        exit = fadeOut(uiTween(SELECTION_ANIMATION_MS)) + slideOutVertically(uiTween(SELECTION_ANIMATION_MS)) { it },
        modifier = Modifier
            .align(Alignment.BottomCenter)
            .zIndex(SELECTION_Z_INDEX)
            .padding(bottom = LocalBottomOverlayInset.current)
    ) {
        SelectionActionBar(actions = actions, enabled = hasSelection)
    }
}

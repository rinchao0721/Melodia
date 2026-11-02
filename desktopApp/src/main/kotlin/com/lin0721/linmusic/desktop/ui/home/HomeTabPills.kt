package com.lin0721.linmusic.desktop.ui.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.lin0721.linmusic.desktop.ui.components.uiTween
import com.lin0721.linmusic.desktop.ui.theme.DesktopColors
import kotlinx.coroutines.delay

internal const val HOME_TAB_ALL = 0
internal const val HOME_TAB_MUSIC = 1
internal const val HOME_TAB_PODCAST = 2

// 二级胶囊展开/折叠动画时长，需与折叠后卸载的 delay 保持一致
private const val SECONDARY_ANIM_MS = 280

private val HomeTabLabels = listOf("全部", "音乐", "播客")
private val PillRadius = 16.dp
private val PillShape = RoundedCornerShape(PillRadius)
private val JoinEndShape = RoundedCornerShape(topEnd = PillRadius, bottomEnd = PillRadius)

// 首页内容类型胶囊；选中「音乐」时右侧联动展开二级胶囊「最新」，与主胶囊拼成整体
@Composable
fun HomeTabPills(
    selectedTab: Int,
    onSelect: (Int) -> Unit,
    newWorksSelected: Boolean,
    onNewWorksSelect: () -> Unit,
    modifier: Modifier = Modifier
) {
    val secondaryTarget = selectedTab == HOME_TAB_MUSIC
    var secondaryMounted by remember { mutableStateOf(secondaryTarget) }
    var secondaryVisible by remember { mutableStateOf(secondaryTarget) }

    LaunchedEffect(secondaryTarget) {
        if (secondaryTarget) {
            // 挂载与置可见隔一帧，否则 AnimatedVisibility 首次组合即是完成态，展开动画被跳过
            secondaryMounted = true
            withFrameNanos {}
            secondaryVisible = true
        } else {
            secondaryVisible = false
            delay(SECONDARY_ANIM_MS.toLong())
            secondaryMounted = false
        }
    }

    Row(modifier.padding(horizontal = 24.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        HomeTabLabels.forEachIndexed { index, label ->
            val joinsSecondary = index == HOME_TAB_MUSIC && secondaryMounted
            HomePill(
                text = label,
                selected = index == selectedTab,
                onClick = { onSelect(index) },
                // 压在二级胶囊上层，贴合处的圆角弧线盖在「最新」直边前面
                modifier = Modifier
                    .padding(start = if (index == 0) 0.dp else 8.dp)
                    .then(if (joinsSecondary) Modifier.zIndex(1f) else Modifier)
            )
            if (joinsSecondary) {
                // 平移与裁剪宽度同步，胶囊保持完整尺寸从主胶囊背后推出
                AnimatedVisibility(
                    visible = secondaryVisible,
                    enter = slideInHorizontally(uiTween(SECONDARY_ANIM_MS)) { -it } +
                        expandHorizontally(uiTween(SECONDARY_ANIM_MS), expandFrom = Alignment.Start),
                    exit = slideOutHorizontally(uiTween(SECONDARY_ANIM_MS)) { -it } +
                        shrinkHorizontally(uiTween(SECONDARY_ANIM_MS), shrinkTowards = Alignment.Start),
                    modifier = Modifier.overlapStart(PillRadius)
                ) {
                    HomePill(
                        text = "最新",
                        selected = newWorksSelected,
                        onClick = onNewWorksSelect,
                        shape = JoinEndShape,
                        selectedContainerColor = DesktopColors.TextGray,
                        // 左侧被主胶囊压住一个圆角半径，补回可见留白
                        labelStartInset = PillRadius
                    )
                }
            }
        }
    }
}

@Composable
private fun HomePill(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    shape: Shape = PillShape,
    selectedContainerColor: Color = DesktopColors.TextPrimary,
    labelStartInset: Dp = 0.dp
) {
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val containerColor by animateColorAsState(
        if (hovered) DesktopColors.Overlay.copy(alpha = 0.2f) else DesktopColors.Overlay.copy(alpha = 0.1f),
        uiTween(150),
        label = "homePillBg"
    )
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(text, fontSize = 13.sp, modifier = Modifier.padding(start = labelStartInset)) },
        modifier = modifier,
        shape = shape,
        interactionSource = interaction,
        colors = FilterChipDefaults.filterChipColors(
            containerColor = containerColor,
            labelColor = DesktopColors.TextPrimary,
            selectedContainerColor = selectedContainerColor,
            selectedLabelColor = DesktopColors.Pane
        ),
        border = null
    )
}

// 内容整体左移 amount 并少上报同等宽度，后面的胶囊据此跟随；Modifier.padding 不接受负值
@Composable
private fun Modifier.overlapStart(amount: Dp): Modifier {
    val overlapPx = with(LocalDensity.current) { amount.roundToPx() }
    return layout { measurable, constraints ->
        val placeable = measurable.measure(constraints)
        // 展开动画头几帧宽度小于圆角半径，减完会出现负尺寸
        val reportedWidth = (placeable.width - overlapPx).coerceAtLeast(0)
        layout(reportedWidth, placeable.height) { placeable.place(-overlapPx, 0) }
    }
}

package com.lin0721.linmusic.desktop.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import com.lin0721.linmusic.core.ui.theme.floatingOutline
import com.lin0721.linmusic.core.ui.theme.themedShadow
import kotlin.math.roundToInt
import com.lin0721.linmusic.desktop.ui.theme.DesktopColors
import com.lin0721.linmusic.desktop.ui.track.DangerColor

private val MenuItemHeight = 34.dp
private val MenuShape = RoundedCornerShape(6.dp)
private val ItemShape = RoundedCornerShape(4.dp)
private const val DISABLED_ALPHA = 0.4f

private const val FADE_IN_MS = 100

private val PanelGap = 4.dp
private val PanelElevation = 8.dp
private val PanelShadowMargin = 12.dp
private val PanelVerticalPadding = 8.dp
private val ScreenMargin = 8.dp

// 二级菜单到窗口底部的可用高度
internal val LocalSubMenuMaxHeight = compositionLocalOf { Dp.Infinity }

val DefaultMenuWidth = 188.dp
val DefaultSubMenuWidth = 224.dp

// 二级菜单的内容槽：内容用 State 持有，调用方重组后展示的始终是最新内容
internal class SubMenuSlot(val width: Dp, val content: State<@Composable () -> Unit>)

@Stable
class MenuScope internal constructor() {
    internal var activeSub by mutableStateOf<SubMenuSlot?>(null)
    internal var subMaxHeightPx by mutableIntStateOf(Int.MAX_VALUE)
    internal var mainHeightPx = 0

    // 主菜单顶边的窗口坐标，与悬停条目的窗口坐标相减得到二级菜单的垂直偏移
    internal var mainTopPx = 0f
    internal var subTopOffsetPx by mutableIntStateOf(0)

    // 组合阶段登记，供定位弹层与左侧预留位计算宽度；只在布局阶段读取
    internal var subWidth by mutableStateOf(0.dp)

    // 主菜单靠右对齐锚点时，二级菜单改在主菜单左侧展开
    internal var subOnLeft by mutableStateOf(false)
}

// 统一的紧凑菜单：34dp 行高、16dp 图标；二级菜单与主菜单同属一个弹层，悬停条目时在右侧展开
@Composable
fun DesktopMenu(
    expanded: Boolean,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    offset: DpOffset = DpOffset.Zero,
    width: Dp = DefaultMenuWidth,
    content: @Composable MenuScope.() -> Unit
) {
    if (!expanded) return
    val scope = remember { MenuScope() }
    val density = LocalDensity.current
    val positionProvider = remember(offset, width, density) { MenuPositionProvider(offset, width, density, scope) }
    val motion = LocalUiMotion.current
    val fade = remember { Animatable(0f) }
    LaunchedEffect(Unit) { fade.animateTo(1f, motion.tween(FADE_IN_MS)) }
    // 弹层透明，主菜单与二级菜单各自成面板；弹层位置只取决于主菜单
    Popup(
        popupPositionProvider = positionProvider,
        onDismissRequest = onDismiss,
        properties = PopupProperties(focusable = true)
    ) {
        Row(
            modifier.graphicsLayer { alpha = fade.value }.padding(PanelShadowMargin),
            horizontalArrangement = Arrangement.spacedBy(PanelGap),
            verticalAlignment = Alignment.Top
        ) {
            val subPanel: @Composable () -> Unit = {
                scope.activeSub?.let { slot ->
                    val maxHeight = if (scope.subMaxHeightPx == Int.MAX_VALUE) Dp.Infinity else with(density) { scope.subMaxHeightPx.toDp() }
                    CompositionLocalProvider(LocalSubMenuMaxHeight provides maxHeight) {
                        // 顶边对齐触发条目，放不下时整体上移到窗口可用范围内
                        Box(
                            Modifier.layout { measurable, constraints ->
                                val placeable = measurable.measure(constraints.copy(minWidth = 0, minHeight = 0))
                                val maxY = (scope.subMaxHeightPx - placeable.height).coerceAtLeast(0)
                                val y = scope.subTopOffsetPx.coerceIn(0, maxY)
                                layout(placeable.width, y + placeable.height) { placeable.place(0, y) }
                            }
                        ) {
                            Column(Modifier.menuPanel(slot.width, DesktopColors.PopupSurface)) { slot.content.value() }
                        }
                    }
                }
            }
            // 左侧预留位始终占住二级菜单的宽度，主菜单位置不随二级菜单展开收起而移动
            if (scope.subOnLeft) {
                Box(
                    Modifier.layout { measurable, constraints ->
                        val reserved = scope.subWidth.roundToPx()
                        val placeable = measurable.measure(constraints.copy(minWidth = reserved, maxWidth = reserved))
                        layout(reserved, placeable.height) { placeable.place(0, 0) }
                    },
                    contentAlignment = Alignment.TopEnd
                ) { subPanel() }
            }
            Column(
                Modifier.onSizeChanged { scope.mainHeightPx = it.height }
                    .onGloballyPositioned { scope.mainTopPx = it.positionInWindow().y }
                    .menuPanel(width, DesktopColors.PopupSurface)
            ) { scope.content() }
            if (!scope.subOnLeft) subPanel()
        }
    }
}

@Composable
private fun Modifier.menuPanel(width: Dp, background: Color): Modifier =
    this.width(width).themedShadow(PanelElevation, MenuShape).background(background, MenuShape).floatingOutline(MenuShape)
        .padding(horizontal = 4.dp, vertical = PanelVerticalPadding)

// 面板左上角落在 锚点左边缘 + offset.x、锚点下边缘 + offset.y，放不下时翻转；
// 首次定位后锁定，二级菜单展开收起不再移动主菜单
private class MenuPositionProvider(
    private val offset: DpOffset,
    private val menuWidth: Dp,
    private val density: Density,
    private val scope: MenuScope
) : PopupPositionProvider {
    private var lockedKey: Pair<IntRect, IntSize>? = null
    private var lockedPosition = IntOffset.Zero

    override fun calculatePosition(
        anchorBounds: IntRect,
        windowSize: IntSize,
        layoutDirection: LayoutDirection,
        popupContentSize: IntSize
    ): IntOffset {
        val key = anchorBounds to windowSize
        if (lockedKey == key) return lockedPosition
        val position = with(density) {
            val margin = PanelShadowMargin.roundToPx()
            val hasSub = scope.subWidth > 0.dp
            val subExtra = if (hasSub) (PanelGap + scope.subWidth).roundToPx() else 0
            val mainWidth = menuWidth.roundToPx()
            val panelHeight = if (scope.mainHeightPx > 0) scope.mainHeightPx else popupContentSize.height - 2 * margin
            val offsetX = offset.x.roundToPx()
            val offsetY = offset.y.roundToPx()
            val verticalMargin = ScreenMargin.roundToPx()

            // 优先与锚点左缘对齐、二级菜单在右；放不下则与锚点右缘对齐、二级菜单在左
            val leftAligned = anchorBounds.left + offsetX
            val rightAligned = anchorBounds.right - mainWidth - offsetX
            var subOnLeft = false
            val x = when {
                leftAligned >= 0 && leftAligned + mainWidth + subExtra <= windowSize.width -> leftAligned
                rightAligned - subExtra >= 0 && rightAligned + mainWidth <= windowSize.width -> {
                    subOnLeft = hasSub
                    rightAligned
                }
                else -> (windowSize.width - mainWidth - subExtra).coerceAtLeast(0)
            }
            if (scope.subOnLeft != subOnLeft) scope.subOnLeft = subOnLeft
            // offset.y 为负是锚点内的点击位置，向上翻转时底边贴着该点，否则贴着锚点上沿
            val flippedBottom = if (offsetY < 0) anchorBounds.bottom + offsetY else anchorBounds.top
            val vertical = listOf(
                maxOf(anchorBounds.bottom + offsetY, verticalMargin),
                flippedBottom - panelHeight,
                windowSize.height - panelHeight - verticalMargin
            )
            val y = vertical.firstOrNull { it >= verticalMargin && it + panelHeight <= windowSize.height - verticalMargin }
                ?: vertical[1].coerceAtLeast(verticalMargin)
            if (hasSub) scope.subMaxHeightPx = windowSize.height - y - verticalMargin
            IntOffset(x - margin - if (subOnLeft) subExtra else 0, y - margin)
        }
        lockedKey = key
        lockedPosition = position
        return position
    }
}

@Composable
fun MenuScope.MenuItem(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    danger: Boolean = false,
    enabled: Boolean = true,
    trailing: (@Composable () -> Unit)? = null
) {
    val source = remember { MutableInteractionSource() }
    val hovered by source.collectIsHoveredAsState()
    LaunchedEffect(hovered) { if (hovered) activeSub = null }
    MenuRow(text, icon, danger, enabled, hovered, source, onClick, modifier, trailing)
}

@Composable
fun MenuScope.SubMenuItem(
    text: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    width: Dp = DefaultSubMenuWidth,
    onOpen: () -> Unit = {},
    content: @Composable () -> Unit
) {
    val contentState = rememberUpdatedState(content)
    val slot = remember { SubMenuSlot(width, contentState) }
    if (width > subWidth) subWidth = width
    val source = remember { MutableInteractionSource() }
    val hovered by source.collectIsHoveredAsState()
    val itemTop = remember { FloatArray(1) }
    val panelPaddingPx = with(LocalDensity.current) { PanelVerticalPadding.toPx() }
    val open = {
        if (activeSub !== slot) {
            // 二级面板与主面板的上内边距相同，减去后两边的首个条目对齐
            subTopOffsetPx = (itemTop[0] - mainTopPx - panelPaddingPx).roundToInt().coerceAtLeast(0)
            activeSub = slot
            onOpen()
        }
    }
    LaunchedEffect(hovered) { if (hovered) open() }
    MenuRow(
        text = text,
        icon = icon,
        danger = false,
        enabled = true,
        highlighted = hovered || activeSub === slot,
        source = source,
        onClick = open,
        modifier = modifier.onGloballyPositioned { itemTop[0] = it.positionInWindow().y },
        trailing = {
            Icon(Icons.Rounded.ChevronRight, null, tint = DesktopColors.TextGray, modifier = Modifier.size(16.dp))
        }
    )
}

@Composable
fun MenuDivider() {
    Box(Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 4.dp).height(1.dp).background(DesktopColors.SurfaceLight.copy(alpha = 0.7f)))
}

@Composable
private fun MenuRow(
    text: String,
    icon: ImageVector?,
    danger: Boolean,
    enabled: Boolean,
    highlighted: Boolean,
    source: MutableInteractionSource,
    onClick: () -> Unit,
    modifier: Modifier,
    trailing: (@Composable () -> Unit)?,
    leading: (@Composable () -> Unit)? = null
) {
    val color = if (danger) DangerColor else DesktopColors.TextPrimary
    Row(
        modifier.fillMaxWidth().height(MenuItemHeight).clip(ItemShape)
            .background(if (highlighted && enabled) DesktopColors.SurfaceLight else Color.Transparent)
            .hoverable(source)
            .clickable(interactionSource = source, indication = null, enabled = enabled, onClick = onClick)
            .padding(horizontal = 10.dp)
            .alpha(if (enabled) 1f else DISABLED_ALPHA),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (icon != null) {
            Icon(icon, null, tint = color, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(10.dp))
        } else if (leading != null) {
            leading()
            Spacer(Modifier.width(10.dp))
        }
        Text(text, color = color, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
        trailing?.invoke()
    }
}

// 无需 MenuScope 的条目：二级菜单内容、音乐库的弹出菜单等场景使用
@Composable
fun SimpleMenuItem(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    danger: Boolean = false,
    selected: Boolean = false,
    enabled: Boolean = true,
    trailing: (@Composable () -> Unit)? = null,
    // 非图标的前置内容，如颜色色块；与 icon 同时传入时以 icon 为准
    leading: (@Composable () -> Unit)? = null
) {
    val source = remember { MutableInteractionSource() }
    val hovered by source.collectIsHoveredAsState()
    MenuRow(text, icon, danger, enabled, hovered || selected, source, onClick, modifier, trailing, leading)
}

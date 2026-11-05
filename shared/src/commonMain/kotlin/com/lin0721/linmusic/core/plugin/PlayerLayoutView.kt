package com.lin0721.linmusic.core.plugin

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lin0721.linmusic.core.format.formatDuration
import com.lin0721.linmusic.core.ui.theme.LocalMelodiaPalette
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

// 节点是否在分页器里；分页器里的封面要让出左右滑动手势
val LocalInLayoutPager = staticCompositionLocalOf { false }

// 播放器布局的通用部分：容器、分页、留白与固定文字；其余组件交给各端的 component 映射到已有实现
@Composable
fun PlayerLayoutView(
    node: PlayerLayoutNode,
    modifier: Modifier = Modifier,
    // 页面用它处理显隐与文字绑定；返回 null 时不显示这个节点
    resolve: (PlayerLayoutNode) -> PlayerLayoutNode? = { it },
    component: @Composable (node: PlayerLayoutNode, modifier: Modifier) -> Unit
) {
    val current = resolve(node) ?: return
    val content = current.contentColor ?: return NodeContent(current, modifier, resolve, component)
    val palette = LocalMelodiaPalette.current.withContentColor(content.resolve())
    CompositionLocalProvider(LocalMelodiaPalette provides palette) {
        NodeContent(current, modifier, resolve, component)
    }
}

@Composable
private fun NodeContent(
    current: PlayerLayoutNode,
    modifier: Modifier,
    resolve: (PlayerLayoutNode) -> PlayerLayoutNode?,
    component: @Composable (node: PlayerLayoutNode, modifier: Modifier) -> Unit
) {
    val nodeModifier = modifier.then(current.boxModifier()).then(current.autoHideModifier())
    when (current.type) {
        LayoutNodeType.COLUMN -> Column(
            nodeModifier,
            verticalArrangement = current.verticalArrangement(),
            horizontalAlignment = current.align.horizontal()
        ) {
            current.children.forEach { child ->
                var childModifier = child.weight?.let { Modifier.weight(it) } ?: Modifier
                child.alignSelf?.let { childModifier = childModifier.align(it.horizontal.horizontal()) }
                PlayerLayoutView(child, childModifier, resolve, component)
            }
        }
        LayoutNodeType.ROW -> Row(
            nodeModifier,
            horizontalArrangement = current.horizontalArrangement(),
            verticalAlignment = current.align.vertical()
        ) {
            current.children.forEach { child ->
                var childModifier = child.weight?.let { Modifier.weight(it) } ?: Modifier
                child.alignSelf?.let { childModifier = childModifier.align(it.vertical.vertical()) }
                PlayerLayoutView(child, childModifier, resolve, component)
            }
        }
        LayoutNodeType.BOX -> Box(nodeModifier, contentAlignment = current.align.box()) {
            current.children.forEach { child ->
                PlayerLayoutView(child, child.alignSelf?.let { Modifier.align(it.alignment()) } ?: Modifier, resolve, component)
            }
        }
        LayoutNodeType.PAGER -> LayoutPager(current, nodeModifier, resolve, component)
        LayoutNodeType.SPACER -> Spacer(nodeModifier)
        LayoutNodeType.TEXT -> if (current.marquee) {
            BasicText(current.text, modifier = nodeModifier.basicMarquee(), style = current.layoutTextStyle(), maxLines = 1)
        } else {
            BasicText(
                current.text,
                modifier = nodeModifier,
                style = current.layoutTextStyle(),
                maxLines = current.maxLines,
                overflow = TextOverflow.Ellipsis
            )
        }
        else -> component(current, nodeModifier)
    }
}

// 宿主收起控件时淡出并拦下点击，不改变布局
@Composable
private fun PlayerLayoutNode.autoHideModifier(): Modifier {
    if (!autoHide) return Modifier
    val env = LocalLayoutEnvironment.current
    val alpha by animateFloatAsState(
        targetValue = if (env.controlsVisible) 1f else 0f,
        animationSpec = if (env.motionEnabled) tween(AUTO_HIDE_FADE_MS) else snap(),
        label = "autoHide"
    )
    val hidden = !env.controlsVisible
    return Modifier.graphicsLayer { this.alpha = alpha }.then(
        if (hidden) {
            Modifier.pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) awaitPointerEvent(PointerEventPass.Initial).changes.forEach { it.consume() }
                }
            }
        } else {
            Modifier
        }
    )
}

private const val AUTO_HIDE_FADE_MS = 240

// text 节点与 time 组件共用的文字样式
@Composable
fun PlayerLayoutNode.layoutTextStyle(): TextStyle {
    val palette = LocalMelodiaPalette.current
    val (color, size, weight) = when (textStyle) {
        LayoutTextStyle.TITLE -> Triple(palette.textPrimary, 16, FontWeight.Bold)
        LayoutTextStyle.BODY -> Triple(palette.textSecondary, 13, FontWeight.Normal)
        LayoutTextStyle.CAPTION -> Triple(palette.textTertiary, 11, FontWeight.Normal)
    }
    val align = when (textAlign) {
        null -> TextAlign.Unspecified
        LayoutAlign.START -> TextAlign.Start
        LayoutAlign.CENTER -> TextAlign.Center
        LayoutAlign.END -> TextAlign.End
    }
    return TextStyle(color = color, fontSize = (size * this.size.scale).sp, fontWeight = weight, textAlign = align)
}

// 播放时间文字，定时取进度，不随每帧重组；时长未知时显示 0:00
@Composable
fun LayoutTimeText(node: PlayerLayoutNode, positionProvider: () -> Long, durationMs: Long, modifier: Modifier = Modifier) {
    val currentProvider by rememberUpdatedState(positionProvider)
    var position by remember { mutableLongStateOf(currentProvider()) }
    LaunchedEffect(Unit) {
        while (true) {
            position = currentProvider()
            delay(TIME_REFRESH_MS)
        }
    }
    val duration = durationMs.coerceAtLeast(0L)
    val text = when (node.timeFormat) {
        TimeFormat.POSITION -> formatDuration(position)
        TimeFormat.DURATION -> formatDuration(duration)
        TimeFormat.REMAINING -> "-" + formatDuration(duration - position)
        TimeFormat.BOTH -> formatDuration(position) + " / " + formatDuration(duration)
    }
    BasicText(text, modifier = modifier, style = node.layoutTextStyle(), maxLines = 1)
}

private const val TIME_REFRESH_MS = 250L

// 每个子节点一页；高度受限时页面撑满分页器，不受限（如放在滚动列表里）时按页面内容取高
@Composable
private fun LayoutPager(
    node: PlayerLayoutNode,
    modifier: Modifier,
    resolve: (PlayerLayoutNode) -> PlayerLayoutNode?,
    component: @Composable (node: PlayerLayoutNode, modifier: Modifier) -> Unit
) {
    val pages = node.children
    val state = rememberPagerState(initialPage = node.initialPage.coerceIn(0, pages.lastIndex)) { pages.size }
    val scope = rememberCoroutineScope()
    val control = remember(state, scope) {
        LayoutPagerControl(
            next = { scope.launch { state.animateScrollToPage((state.currentPage + 1).coerceAtMost(pages.lastIndex)) } },
            previous = { scope.launch { state.animateScrollToPage((state.currentPage - 1).coerceAtLeast(0)) } }
        )
    }
    BoxWithConstraints(modifier) {
        val bounded = constraints.hasBoundedHeight
        Column(if (bounded) Modifier.fillMaxSize() else Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            HorizontalPager(state = state, modifier = (if (bounded) Modifier.weight(1f) else Modifier).fillMaxWidth()) { page ->
                CompositionLocalProvider(LocalInLayoutPager provides true, LocalLayoutPagerControl provides control) {
                    PlayerLayoutView(pages[page], if (bounded) Modifier.fillMaxSize() else Modifier.fillMaxWidth(), resolve, component)
                }
            }
            PagerDots(node, state.currentPage)
        }
    }
}

@Composable
private fun PagerDots(node: PlayerLayoutNode, currentPage: Int) {
    val pages = node.children
    if (node.indicator == PagerIndicator.NONE || pages.size <= 1) return
    val palette = LocalMelodiaPalette.current
    Row(
        modifier = Modifier.padding(top = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        repeat(pages.size) { index ->
            val selected = index == currentPage
            val color = if (selected) palette.textPrimary else palette.textPrimary.copy(alpha = 0.3f)
            val dotModifier = when (node.indicator) {
                PagerIndicator.BAR -> Modifier.size(width = if (selected) 16.dp else 8.dp, height = 3.dp).clip(RoundedCornerShape(2.dp))
                else -> Modifier.size(6.dp).clip(CircleShape)
            }
            Box(dotModifier.background(color))
        }
    }
}

// 位移、尺寸上下限、尺寸、不透明度、动效、阴影、裁切形状、背景、描边、手势与内边距，按这个顺序叠加
@Composable
private fun PlayerLayoutNode.boxModifier(): Modifier {
    val tapAnimated = (animation as? LayoutAnimation.Keyframes)?.trigger == MotionTrigger.TAP
    var tapCount by remember { mutableIntStateOf(0) }
    var result: Modifier = Modifier
    if (offsetX != 0f || offsetY != 0f) result = result.offset(offsetX.dp, offsetY.dp)
    if (minWidth != null || maxWidth != null) {
        result = result.widthIn(min = minWidth?.dp ?: Dp.Unspecified, max = maxWidth?.dp ?: Dp.Unspecified)
    }
    if (minHeight != null || maxHeight != null) {
        result = result.heightIn(min = minHeight?.dp ?: Dp.Unspecified, max = maxHeight?.dp ?: Dp.Unspecified)
    }
    width?.let { result = result.width(it.dp) } ?: widthFraction?.let { result = result.fillMaxWidth(it) }
    // row 按内容取宽时没有空间可分，写了排列方式又没写宽度时占满宽度
    if (type == LayoutNodeType.ROW && width == null && widthFraction == null && arrange != null && arrange != LayoutArrange.START) {
        result = result.fillMaxWidth()
    }
    height?.let { result = result.height(it.dp) } ?: heightFraction?.let { result = result.fillMaxHeight(it) }
    opacity?.let { result = result.alpha(it) }
    animation?.let { result = result.layoutAnimation(it, tapCount) }
    val shape = layoutShape()
    if (elevation > 0f) result = result.shadow(elevation.dp, shape, clip = false)
    if (background != null || border != null) result = result.clip(shape)
    background?.let { result = result.layoutFill(it, shape) }
    border?.let { result = result.border(it.width.dp, it.color.resolve(), shape) }
    result = result.layoutGestures(this, if (tapAnimated) ({ tapCount++ }) else null)
    if (padding != LayoutPadding.Zero) {
        result = result.padding(start = padding.start.dp, top = padding.top.dp, end = padding.end.dp, bottom = padding.bottom.dp)
    }
    return result
}

// 纯色或线性渐变；angle 为 0 时从左到右，90 时从上到下
@Composable
fun Modifier.layoutFill(fill: LayoutFill, shape: Shape = RectangleShape): Modifier = when (fill) {
    is LayoutFill.Solid -> background(fill.color.resolve(), shape)
    is LayoutFill.Gradient -> {
        val colors = fill.colors.map { it.resolve() }
        val radians = fill.angle * PI.toFloat() / 180f
        drawBehind {
            val dx = cos(radians)
            val dy = sin(radians)
            val half = (abs(size.width * dx) + abs(size.height * dy)) / 2f
            val brush = Brush.linearGradient(
                colors,
                start = Offset(center.x - dx * half, center.y - dy * half),
                end = Offset(center.x + dx * half, center.y + dy * half)
            )
            drawOutline(shape.createOutline(size, layoutDirection, this), brush)
        }
    }
}

fun PlayerLayoutNode.layoutShape(): Shape = when (shape) {
    LayoutShape.RECT -> RectangleShape
    LayoutShape.ROUNDED -> corners?.toShape() ?: RoundedCornerShape(cornerRadius.dp)
    LayoutShape.CIRCLE -> CircleShape
}

fun LayoutCorners.toShape(): Shape =
    RoundedCornerShape(topStart = topStart.dp, topEnd = topEnd.dp, bottomEnd = bottomEnd.dp, bottomStart = bottomStart.dp)

private fun PlayerLayoutNode.horizontalArrangement(): Arrangement.Horizontal = when (arrange) {
    null, LayoutArrange.START -> Arrangement.spacedBy(spacing.dp, Alignment.Start)
    LayoutArrange.CENTER -> Arrangement.spacedBy(spacing.dp, Alignment.CenterHorizontally)
    LayoutArrange.END -> Arrangement.spacedBy(spacing.dp, Alignment.End)
    LayoutArrange.SPACE_BETWEEN -> Arrangement.SpaceBetween
    LayoutArrange.SPACE_AROUND -> Arrangement.SpaceAround
    LayoutArrange.SPACE_EVENLY -> Arrangement.SpaceEvenly
}

private fun PlayerLayoutNode.verticalArrangement(): Arrangement.Vertical = when (arrange) {
    null, LayoutArrange.START -> Arrangement.spacedBy(spacing.dp, Alignment.Top)
    LayoutArrange.CENTER -> Arrangement.spacedBy(spacing.dp, Alignment.CenterVertically)
    LayoutArrange.END -> Arrangement.spacedBy(spacing.dp, Alignment.Bottom)
    LayoutArrange.SPACE_BETWEEN -> Arrangement.SpaceBetween
    LayoutArrange.SPACE_AROUND -> Arrangement.SpaceAround
    LayoutArrange.SPACE_EVENLY -> Arrangement.SpaceEvenly
}

private fun LayoutPosition.alignment(): Alignment = when (this) {
    LayoutPosition.TOP_START -> Alignment.TopStart
    LayoutPosition.TOP -> Alignment.TopCenter
    LayoutPosition.TOP_END -> Alignment.TopEnd
    LayoutPosition.START -> Alignment.CenterStart
    LayoutPosition.CENTER -> Alignment.Center
    LayoutPosition.END -> Alignment.CenterEnd
    LayoutPosition.BOTTOM_START -> Alignment.BottomStart
    LayoutPosition.BOTTOM -> Alignment.BottomCenter
    LayoutPosition.BOTTOM_END -> Alignment.BottomEnd
}

private fun LayoutAlign.horizontal(): Alignment.Horizontal = when (this) {
    LayoutAlign.START -> Alignment.Start
    LayoutAlign.CENTER -> Alignment.CenterHorizontally
    LayoutAlign.END -> Alignment.End
}

private fun LayoutAlign.vertical(): Alignment.Vertical = when (this) {
    LayoutAlign.START -> Alignment.Top
    LayoutAlign.CENTER -> Alignment.CenterVertically
    LayoutAlign.END -> Alignment.Bottom
}

private fun LayoutAlign.box(): Alignment = when (this) {
    LayoutAlign.START -> Alignment.TopStart
    LayoutAlign.CENTER -> Alignment.Center
    LayoutAlign.END -> Alignment.BottomEnd
}

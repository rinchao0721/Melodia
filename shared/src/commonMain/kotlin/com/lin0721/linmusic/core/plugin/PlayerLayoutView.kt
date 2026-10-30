package com.lin0721.linmusic.core.plugin

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.ui.text.TextStyle
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lin0721.linmusic.core.ui.theme.LocalMelodiaPalette

// 播放器布局的通用部分：容器、留白与固定文字；其余组件交给各端的 component 映射到已有实现
@Composable
fun PlayerLayoutView(
    node: PlayerLayoutNode,
    modifier: Modifier = Modifier,
    component: @Composable (node: PlayerLayoutNode, modifier: Modifier) -> Unit
) {
    val nodeModifier = modifier.then(node.boxModifier())
    when (node.type) {
        LayoutNodeType.COLUMN -> Column(
            nodeModifier,
            verticalArrangement = Arrangement.spacedBy(node.spacing.dp),
            horizontalAlignment = node.align.horizontal()
        ) {
            node.children.forEach { child ->
                PlayerLayoutView(child, child.weight?.let { Modifier.weight(it) } ?: Modifier, component)
            }
        }
        LayoutNodeType.ROW -> Row(
            nodeModifier,
            horizontalArrangement = Arrangement.spacedBy(node.spacing.dp),
            verticalAlignment = node.align.vertical()
        ) {
            node.children.forEach { child ->
                PlayerLayoutView(child, child.weight?.let { Modifier.weight(it) } ?: Modifier, component)
            }
        }
        LayoutNodeType.BOX -> Box(nodeModifier, contentAlignment = node.align.box()) {
            node.children.forEach { child -> PlayerLayoutView(child, Modifier, component) }
        }
        LayoutNodeType.SPACER -> Spacer(nodeModifier)
        LayoutNodeType.TEXT -> {
            val palette = LocalMelodiaPalette.current
            val (color, size, weight) = when (node.textStyle) {
                LayoutTextStyle.TITLE -> Triple(palette.textPrimary, 16, FontWeight.Bold)
                LayoutTextStyle.BODY -> Triple(palette.textSecondary, 13, FontWeight.Normal)
                LayoutTextStyle.CAPTION -> Triple(palette.textTertiary, 11, FontWeight.Normal)
            }
            BasicText(
                node.text,
                modifier = nodeModifier,
                style = TextStyle(color = color, fontSize = (size * node.size.scale).sp, fontWeight = weight),
                maxLines = node.maxLines,
                overflow = TextOverflow.Ellipsis
            )
        }
        else -> component(node, nodeModifier)
    }
}

// 固定尺寸、裁切形状、背景与内边距，按这个顺序叠加
@Composable
private fun PlayerLayoutNode.boxModifier(): Modifier {
    var result: Modifier = Modifier
    width?.let { result = result.width(it.dp) }
    height?.let { result = result.height(it.dp) }
    val shape = layoutShape()
    background?.let { token ->
        result = result.clip(shape).background(token.read(LocalMelodiaPalette.current))
    }
    if (padding != LayoutPadding.Zero) {
        result = result.padding(start = padding.start.dp, top = padding.top.dp, end = padding.end.dp, bottom = padding.bottom.dp)
    }
    return result
}

fun PlayerLayoutNode.layoutShape(): Shape = when (shape) {
    LayoutShape.RECT -> RectangleShape
    LayoutShape.ROUNDED -> RoundedCornerShape(cornerRadius.dp)
    LayoutShape.CIRCLE -> CircleShape
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

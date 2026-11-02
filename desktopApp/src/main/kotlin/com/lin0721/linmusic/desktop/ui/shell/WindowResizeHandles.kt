package com.lin0721.linmusic.desktop.ui.shell

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.WindowScope
import com.lin0721.linmusic.desktop.platform.WindowEdge
import com.lin0721.linmusic.desktop.platform.WindowEffects
import com.lin0721.linmusic.desktop.ui.components.LocalWindowEffects
import com.lin0721.linmusic.desktop.ui.components.PointerTravel
import java.awt.Cursor
import java.awt.Dimension
import java.awt.Rectangle
import java.awt.Window
import kotlin.math.roundToInt

private val EdgeThickness = 6.dp

private enum class Edge(
    val system: WindowEdge,
    val cursor: Int,
    val left: Boolean,
    val top: Boolean,
    val right: Boolean,
    val bottom: Boolean
) {
    Left(WindowEdge.Left, Cursor.W_RESIZE_CURSOR, true, false, false, false),
    Right(WindowEdge.Right, Cursor.E_RESIZE_CURSOR, false, false, true, false),
    Top(WindowEdge.Top, Cursor.N_RESIZE_CURSOR, false, true, false, false),
    Bottom(WindowEdge.Bottom, Cursor.S_RESIZE_CURSOR, false, false, false, true),
    TopLeft(WindowEdge.TopLeft, Cursor.NW_RESIZE_CURSOR, true, true, false, false),
    TopRight(WindowEdge.TopRight, Cursor.NE_RESIZE_CURSOR, false, true, true, false),
    BottomLeft(WindowEdge.BottomLeft, Cursor.SW_RESIZE_CURSOR, true, false, false, true),
    BottomRight(WindowEdge.BottomRight, Cursor.SE_RESIZE_CURSOR, false, false, true, true)
}

// 无边框窗口没有系统缩放边框，四边四角各放一条透明热区自行调整窗口尺寸
@Composable
fun WindowScope.WindowResizeHandles(enabled: Boolean) {
    if (!enabled) return
    val awtWindow = window
    val effects = LocalWindowEffects.current
    Box(Modifier.fillMaxSize()) {
        EdgeHandle(awtWindow, effects, Edge.Left, Modifier.align(Alignment.CenterStart).width(EdgeThickness).fillMaxHeight())
        EdgeHandle(awtWindow, effects, Edge.Right, Modifier.align(Alignment.CenterEnd).width(EdgeThickness).fillMaxHeight())
        EdgeHandle(awtWindow, effects, Edge.Top, Modifier.align(Alignment.TopCenter).height(EdgeThickness).fillMaxWidth())
        EdgeHandle(awtWindow, effects, Edge.Bottom, Modifier.align(Alignment.BottomCenter).height(EdgeThickness).fillMaxWidth())
        EdgeHandle(awtWindow, effects, Edge.TopLeft, Modifier.align(Alignment.TopStart).size(EdgeThickness * 2))
        EdgeHandle(awtWindow, effects, Edge.TopRight, Modifier.align(Alignment.TopEnd).size(EdgeThickness * 2))
        EdgeHandle(awtWindow, effects, Edge.BottomLeft, Modifier.align(Alignment.BottomStart).size(EdgeThickness * 2))
        EdgeHandle(awtWindow, effects, Edge.BottomRight, Modifier.align(Alignment.BottomEnd).size(EdgeThickness * 2))
    }
}

// 优先交给系统缩放，跨屏时由系统按各屏缩放换算；系统不接管时自行按起点计算
@Composable
private fun BoxScope.EdgeHandle(window: Window, effects: WindowEffects, edge: Edge, modifier: Modifier) {
    Box(
        modifier
            .pointerHoverIcon(PointerIcon(Cursor(edge.cursor)))
            .pointerInput(edge, effects) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false).consume()
                    if (effects.beginSystemResize(window, edge.system)) return@awaitEachGesture
                    val travel = PointerTravel.start(effects) ?: return@awaitEachGesture
                    val bounds = window.bounds
                    while (true) {
                        val event = awaitPointerEvent()
                        event.changes.forEach { it.consume() }
                        if (event.changes.none { it.pressed }) break
                        val delta = travel.delta() ?: continue
                        window.bounds = resizedBounds(bounds, window.minimumSize, edge, delta.x.roundToInt(), delta.y.roundToInt())
                    }
                }
            }
    )
}

private fun resizedBounds(start: Rectangle, min: Dimension, edge: Edge, dx: Int, dy: Int): Rectangle {
    var x = start.x
    var y = start.y
    var w = start.width
    var h = start.height
    if (edge.right) w = (start.width + dx).coerceAtLeast(min.width)
    if (edge.bottom) h = (start.height + dy).coerceAtLeast(min.height)
    if (edge.left) {
        w = (start.width - dx).coerceAtLeast(min.width)
        x = start.x + start.width - w
    }
    if (edge.top) {
        h = (start.height - dy).coerceAtLeast(min.height)
        y = start.y + start.height - h
    }
    return Rectangle(x, y, w, h)
}

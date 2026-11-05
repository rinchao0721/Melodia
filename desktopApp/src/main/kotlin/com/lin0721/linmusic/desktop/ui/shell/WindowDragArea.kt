package com.lin0721.linmusic.desktop.ui.shell

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitTouchSlopOrCancellation
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.isPrimaryPressed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.window.WindowScope
import com.lin0721.linmusic.desktop.ui.components.LocalWindowEffects
import com.lin0721.linmusic.desktop.ui.components.PointerTravel
import kotlin.math.roundToInt

// 代替 Compose 的 WindowDraggableArea：它按逻辑坐标挪窗口，跨缩放比例不同的显示器时会跳变。
// 越过拖动阈值才交给系统移动，单击、双击仍照常传给内容
@Composable
fun WindowScope.WindowDragArea(modifier: Modifier = Modifier, content: @Composable () -> Unit = {}) {
    val effects = LocalWindowEffects.current
    val awtWindow = window
    Box(
        modifier.pointerInput(awtWindow, effects) {
            awaitEachGesture {
                val down = awaitFirstDown()
                if (!currentEvent.buttons.isPrimaryPressed) return@awaitEachGesture
                val start = awtWindow.location
                val travel = PointerTravel.start(effects) ?: return@awaitEachGesture
                awaitTouchSlopOrCancellation(down.id) { change, _ -> change.consume() } ?: return@awaitEachGesture
                // 系统循环以接管时的光标为锚点，先补上阈值内的位移，窗口才不会落后于光标
                travel.delta()?.let { awtWindow.setLocation(start.x + it.x.roundToInt(), start.y + it.y.roundToInt()) }
                if (effects.beginSystemMove(awtWindow)) return@awaitEachGesture
                drag(down.id) { change ->
                    change.consume()
                    val delta = travel.delta() ?: return@drag
                    awtWindow.setLocation(start.x + delta.x.roundToInt(), start.y + delta.y.roundToInt())
                }
            }
        }
    ) {
        content()
    }
}

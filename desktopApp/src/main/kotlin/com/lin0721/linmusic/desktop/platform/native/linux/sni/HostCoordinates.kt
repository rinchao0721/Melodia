package com.lin0721.linmusic.desktop.platform.native.linux.sni

import com.lin0721.linmusic.desktop.platform.native.linux.WaylandOutputInfo
import java.awt.Point
import java.awt.Rectangle
import kotlin.math.abs
import kotlin.math.roundToInt

// 托盘宿主给出的菜单坐标 → 本应用（AWT/X11 物理像素）坐标。
//
// SNI 规范说 ContextMenu(x, y) 是"屏幕坐标"，但实际观察到两类宿主行为：
//   1. 规范行为：合成器逻辑坐标（分数缩放下小于物理尺寸）
//   2. Plasma 6.7 实测：逻辑原点 + 输出内物理偏移的混合坐标
//      （双 4K/1.5 缩放：第二块屏图标物理 (7076,2129) 被报成 (5785,2108)，
//       x 差恰为两屏逻辑宽度差 1280）
// 两种模型都算候选，再用 AWT 指针挑选：XWayland 不跟踪 Wayland 面板上的指针移动，
// 指针会滞留在进入面板处，但仍落在同一块屏上，足以判定"输出↔物理屏"配对与模型。
internal fun hostPointToAwt(
    host: Point,
    outputs: List<WaylandOutputInfo>,
    screens: List<Rectangle>,
    pointer: Point?
): Point? {
    if (outputs.isEmpty() || screens.isEmpty()) return null

    // 逻辑排布与物理排布同序（合成器按同一布局摆放输出），用于无指针时兜底配对
    val orderedOutputs = outputs.sortedWith(compareBy({ it.logicalY }, { it.logicalX }))
    val orderedScreens = screens.sortedWith(compareBy({ it.y }, { it.x }))
    val candidates = mutableListOf<Candidate>()

    for ((outputIndex, output) in orderedOutputs.withIndex()) {
        if (output.logicalWidth <= 0 || output.logicalHeight <= 0) continue
        val logicalBounds = Rectangle(output.logicalX, output.logicalY, output.logicalWidth, output.logicalHeight)
        // 混合坐标空间：原点同为逻辑原点，但范围按输出的物理尺寸展开
        val hybridBounds = Rectangle(output.logicalX, output.logicalY, output.physicalWidth, output.physicalHeight)
        if (!logicalBounds.contains(host) && !hybridBounds.contains(host)) continue

        for ((screenIndex, screen) in orderedScreens.withIndex()) {
            if (screen.width != output.physicalWidth || screen.height != output.physicalHeight) continue
            val orderPenalty = abs(outputIndex - screenIndex)
            if (logicalBounds.contains(host)) {
                candidates += Candidate(scalePoint(host, logicalBounds, screen), orderPenalty)
            }
            if (hybridBounds.contains(host)) {
                // 输出内 1:1，只换原点
                candidates += Candidate(
                    Point(screen.x + host.x - logicalBounds.x, screen.y + host.y - logicalBounds.y),
                    orderPenalty
                )
            }
        }
    }

    val onScreen = candidates.filter { candidate -> screens.any { it.contains(candidate.point) } }
    if (onScreen.isEmpty()) return null

    // 指针与图标必然在同一块屏上（XWayland 只是滞后，不会跨屏），先按指针所在屏收窄候选
    val pointerScreen = pointer?.let { p -> orderedScreens.firstOrNull { it.contains(p) } }
    val pool = if (pointerScreen != null) {
        onScreen.filter { pointerScreen.contains(it.point) }.ifEmpty { onScreen }
    } else {
        onScreen
    }
    return if (pointer != null) {
        pool.minByOrNull { it.point.distanceSq(pointer) }?.point
    } else {
        pool.minByOrNull { it.orderPenalty }?.point
    }
}

// 逻辑坐标 → 物理：输出内按 物理/逻辑 比例缩放（分数缩放的规范行为）
private fun scalePoint(host: Point, logicalBounds: Rectangle, screen: Rectangle): Point = Point(
    screen.x + ((host.x - logicalBounds.x).toDouble() * screen.width / logicalBounds.width).roundToInt(),
    screen.y + ((host.y - logicalBounds.y).toDouble() * screen.height / logicalBounds.height).roundToInt(),
)

private data class Candidate(val point: Point, val orderPenalty: Int)

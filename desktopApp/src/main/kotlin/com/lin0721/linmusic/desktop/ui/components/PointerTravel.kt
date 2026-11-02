package com.lin0721.linmusic.desktop.ui.components

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.geometry.Offset
import com.lin0721.linmusic.desktop.platform.WindowEffects
import com.lin0721.linmusic.desktop.platform.fallback.NoopWindowEffects
import java.awt.MouseInfo
import java.awt.Point

val LocalWindowEffects = staticCompositionLocalOf<WindowEffects> { NoopWindowEffects() }

// MouseInfo 是各屏按自身缩放换算的逻辑坐标，跨缩放比例不同的显示器时不连续；
// 改按物理像素算位移，再以起点所在屏的缩放换回逻辑像素
class PointerTravel private constructor(
    private val effects: WindowEffects,
    private val physicalOrigin: Point?,
    private val logicalOrigin: Point?,
    private val scale: Double
) {

    // 相对按下位置的累计位移（逻辑像素）；取不到光标时为 null
    fun delta(): Offset? {
        if (physicalOrigin != null) {
            val now = effects.cursorPhysicalPosition() ?: return null
            return Offset(((now.x - physicalOrigin.x) / scale).toFloat(), ((now.y - physicalOrigin.y) / scale).toFloat())
        }
        val origin = logicalOrigin ?: return null
        val now = MouseInfo.getPointerInfo()?.location ?: return null
        return Offset((now.x - origin.x).toFloat(), (now.y - origin.y).toFloat())
    }

    companion object {
        // 无头环境下 MouseInfo 会抛 HeadlessException，取不到起点时返回 null
        fun start(effects: WindowEffects): PointerTravel? {
            val pointer = try {
                MouseInfo.getPointerInfo()
            } catch (e: RuntimeException) {
                null
            } ?: return null
            val scale = pointer.device?.defaultConfiguration?.defaultTransform?.scaleX?.takeIf { it > 0.0 } ?: 1.0
            val physical = effects.cursorPhysicalPosition()
            return PointerTravel(effects, physical, pointer.location.takeIf { physical == null }, scale)
        }
    }
}

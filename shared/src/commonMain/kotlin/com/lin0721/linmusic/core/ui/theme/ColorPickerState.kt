package com.lin0721.linmusic.core.ui.theme

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

private const val HEX_INPUT_MAX_LENGTH = 7

// 自定义主色取色器的状态：三条 HSV 滑条与十六进制输入双向同步
@Stable
class ColorPickerState(initial: Color) {

    var hsv by mutableStateOf(initial.toHsv())
        private set

    var hexText by mutableStateOf(initial.toRgbHex())
        private set

    val color: Color get() = hsv.toColor()

    val isHexValid: Boolean get() = parseRgbHex(hexText) != null

    fun updateHue(hue: Float) = updateHsv(hsv.copy(hue = hue.coerceIn(0f, 360f)))

    fun updateSaturation(saturation: Float) = updateHsv(hsv.copy(saturation = saturation.coerceIn(0f, 1f)))

    fun updateValue(value: Float) = updateHsv(hsv.copy(value = value.coerceIn(0f, 1f)))

    // 输入合法时同步滑条；灰阶色没有色相，保留当前色相避免滑块跳回 0
    fun updateHexText(text: String) {
        hexText = text.trim().take(HEX_INPUT_MAX_LENGTH)
        val parsed = parseRgbHex(hexText)?.toHsv() ?: return
        hsv = if (parsed.saturation == 0f) parsed.copy(hue = hsv.hue) else parsed
    }

    fun hueTrack(): Brush = Brush.horizontalGradient(HUE_STOPS.map { Hsv(it, 1f, 1f).toColor() })

    fun saturationTrack(): Brush = Brush.horizontalGradient(
        listOf(hsv.copy(saturation = 0f).toColor(), hsv.copy(saturation = 1f).toColor())
    )

    fun valueTrack(): Brush = Brush.horizontalGradient(listOf(Color.Black, hsv.copy(value = 1f).toColor()))

    private fun updateHsv(next: Hsv) {
        hsv = next
        hexText = next.toColor().toRgbHex()
    }

    private companion object {
        val HUE_STOPS = listOf(0f, 60f, 120f, 180f, 240f, 300f, 359.9f)
    }
}

// 主色在背景上的最低可读对比度
const val MIN_ACCENT_CONTRAST = 3f

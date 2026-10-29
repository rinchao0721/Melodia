package com.lin0721.linmusic.core.ui.skin

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import com.lin0721.linmusic.core.ui.theme.Hsl
import com.lin0721.linmusic.core.ui.theme.MelodiaPalette
import com.lin0721.linmusic.core.ui.theme.ThemeAccents
import com.lin0721.linmusic.core.ui.theme.contentColorOn
import com.lin0721.linmusic.core.ui.theme.contrastRatio
import com.lin0721.linmusic.core.ui.theme.toHsl

data class SkinContrastFix(
    val mode: SkinMode,
    val token: PaletteToken,
    val before: Color,
    val after: Color
) {
    val description: String
        get() = "${if (mode == SkinMode.DARK) "深色" else "浅色"} · ${labelOf(token)} " +
            "${SkinParser.encodeColor(before)} → ${SkinParser.encodeColor(after)}"
}

// 导入时修正对比度不足的文字色：只检查皮肤改动过的组合，内置色板的既有取舍不受影响
object SkinContrast {

    private const val SEARCH_STEPS = 24

    private class Rule(
        val foreground: PaletteToken,
        val backgrounds: List<PaletteToken>,
        val minDark: Float,
        val minLight: Float
    )

    // 深色主色文字沿用内置做法直接用主色，门槛与网易红在深色面板上的实际对比度一致
    private val rules = listOf(
        Rule(PaletteToken.TEXT_PRIMARY, listOf(PaletteToken.PANE, PaletteToken.WINDOW_BACKGROUND), 7f, 7f),
        Rule(PaletteToken.TEXT_SECONDARY, listOf(PaletteToken.PANE, PaletteToken.WINDOW_BACKGROUND), 4.5f, 4.5f),
        Rule(PaletteToken.TEXT_TERTIARY, listOf(PaletteToken.PANE, PaletteToken.WINDOW_BACKGROUND), 3f, 3f),
        Rule(PaletteToken.ACCENT_TEXT, listOf(PaletteToken.PANE), 3f, 4.5f),
        Rule(PaletteToken.ON_ACCENT, listOf(PaletteToken.ACCENT), 4.5f, 4.5f)
    )

    fun fix(skin: Skin): Pair<Skin, List<SkinContrastFix>> {
        val fixes = mutableListOf<SkinContrastFix>()
        val dark = fixMode(skin, SkinMode.DARK, fixes)
        val light = fixMode(skin, SkinMode.LIGHT, fixes)
        return skin.copy(darkColors = dark, lightColors = light) to fixes
    }

    private fun fixMode(skin: Skin, mode: SkinMode, fixes: MutableList<SkinContrastFix>): Map<PaletteToken, Color> {
        val overrides = skin.colors(mode == SkinMode.DARK)
        if (mode !in skin.modes || overrides.isEmpty()) return overrides
        val isDark = mode == SkinMode.DARK
        val result = LinkedHashMap(overrides)
        rules.forEach { rule ->
            // 皮肤没写主色时主色文字由用户主色运行时推导，写进皮肤也不会生效
            if (rule.foreground in PaletteToken.accentDerived && PaletteToken.ACCENT !in result) return@forEach
            val touched = rule.foreground in result || rule.backgrounds.any { it in result } || PaletteToken.ACCENT in result
            if (!touched) return@forEach
            val palette = resolveSkinPalette(isDark, ThemeAccents.DEFAULT_ID, null, skin.copyColors(isDark, result))
            val foreground = rule.foreground.read(palette)
            val backgrounds = rule.backgrounds.map { opaque(it.read(palette), palette) }
            val min = if (isDark) rule.minDark else rule.minLight
            if (backgrounds.all { contrastRatio(foreground.compositeOver(it), it) >= min }) return@forEach
            val fixed = if (rule.foreground == PaletteToken.ON_ACCENT) {
                contentColorOn(backgrounds.first())
            } else {
                ensureContrast(foreground, backgrounds, min)
            }
            result[rule.foreground] = fixed
            fixes += SkinContrastFix(mode, rule.foreground, foreground, fixed)
        }
        return result
    }

    // 保持色相与饱和度，沿背景的反方向调亮度到达标；调到尽头仍不够时退回黑白
    fun ensureContrast(foreground: Color, backgrounds: List<Color>, min: Float): Color {
        val opaqueForeground = foreground.copy(alpha = 1f)
        if (backgrounds.all { contrastRatio(opaqueForeground, it) >= min }) return opaqueForeground
        val brighten = backgrounds.minOf { contrastRatio(Color.White, it) } >= backgrounds.minOf { contrastRatio(Color.Black, it) }
        val hsl = opaqueForeground.toHsl()
        fun passes(lightness: Float) = backgrounds.all { contrastRatio(Hsl(hsl.hue, hsl.saturation, lightness).toColor(), it) >= min }
        val extreme = if (brighten) 1f else 0f
        if (!passes(extreme)) return if (brighten) Color.White else Color.Black
        var near = hsl.lightness
        var far = extreme
        repeat(SEARCH_STEPS) {
            val mid = (near + far) / 2f
            if (passes(mid)) far = mid else near = mid
        }
        return Hsl(hsl.hue, hsl.saturation, far).toColor()
    }

    private fun opaque(color: Color, palette: MelodiaPalette): Color =
        if (color.alpha >= 1f) color else color.compositeOver(if (palette.isDark) Color.Black else Color.White)

    private fun Skin.copyColors(isDark: Boolean, colors: Map<PaletteToken, Color>): Skin =
        if (isDark) copy(darkColors = colors) else copy(lightColors = colors)
}

private fun labelOf(token: PaletteToken): String = when (token) {
    PaletteToken.TEXT_PRIMARY -> "主要文字"
    PaletteToken.TEXT_SECONDARY -> "次要文字"
    PaletteToken.TEXT_TERTIARY -> "辅助文字"
    PaletteToken.ACCENT_TEXT -> "主色文字"
    PaletteToken.ON_ACCENT -> "主色上的文字"
    else -> token.key
}

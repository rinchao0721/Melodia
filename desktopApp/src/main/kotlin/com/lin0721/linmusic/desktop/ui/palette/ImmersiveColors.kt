package com.lin0721.linmusic.desktop.ui.palette

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import com.lin0721.linmusic.core.ui.theme.MelodiaPalettes
import com.lin0721.linmusic.core.ui.theme.coverDeepTone
import com.lin0721.linmusic.core.ui.theme.coverHeaderTint
import com.lin0721.linmusic.core.ui.theme.coverWashTint
import com.lin0721.linmusic.core.ui.theme.immersiveMidBlob
import com.lin0721.linmusic.core.ui.theme.immersiveMidFill
import com.lin0721.linmusic.desktop.ui.theme.DesktopColors
import com.lin0721.linmusic.desktop.ui.theme.ImmersiveTheme

// 封面取色铺底的沉浸页（全屏歌词、歌词卡片、迷你播放器）配色：
// fill 铺底，blob 是流动光斑，content 是文字与图标，highlight 是当前行，inactive 是非当前行，scrim 是顶部压字的渐隐遮罩；
// onLight 为浅底深字，内部按当前浅色主题取色，不切深色，blob 改作顶部封面浅调
@Immutable
data class ImmersiveColors(
    val fill: Color,
    val blob: Color,
    val content: Color,
    val inactive: Color,
    val scrim: Color,
    val onLight: Boolean = false,
    val highlight: Color = content
)

// 沉浸区两种主题都是有色底配白字：深色主色提饱和后压暗，浅色按相对亮度取中调，比深色亮但仍托得住白字
fun immersiveColors(base: Color, isDark: Boolean): ImmersiveColors {
    val vivid = base.saturateIfChromatic(0.6f)
    return ImmersiveColors(
        fill = if (isDark) vivid.darken(0.35f) else immersiveMidFill(vivid),
        blob = if (isDark) vivid.darken(0.15f) else immersiveMidBlob(vivid),
        content = Color.White,
        inactive = lerp(base.saturateIfChromatic(0.8f).lighten(1.0f), Color.White, 0.5f),
        scrim = Color.Black
    )
}

// 浅色主题的全屏歌词页：整页封面淡色、顶部浅调渐隐，深字，当前行用封面深色调，灰色封面回退主色
fun lightLyricsColors(base: Color, accentText: Color): ImmersiveColors {
    val palette = MelodiaPalettes.light
    val wash = coverWashTint(base)
    return ImmersiveColors(
        fill = wash,
        blob = coverHeaderTint(base),
        content = palette.textPrimary,
        inactive = palette.textPrimary.copy(alpha = LIGHT_INACTIVE_ALPHA),
        scrim = wash,
        onLight = true,
        highlight = coverDeepTone(base) ?: accentText
    )
}

private const val LIGHT_INACTIVE_ALPHA = 0.45f

// 沉浸页内部各层组件读取当前配色；默认值为深色兜底
val LocalImmersiveColors = compositionLocalOf { immersiveColors(FallbackBase, isDark = true) }

// 沉浸区容器：内部组件按深色主题取色，并能读到当前沉浸配色
@Composable
fun ImmersiveSurface(colors: ImmersiveColors, content: @Composable () -> Unit) {
    if (colors.onLight) {
        CompositionLocalProvider(LocalImmersiveColors provides colors, content = content)
        return
    }
    ImmersiveTheme {
        CompositionLocalProvider(LocalImmersiveColors provides colors, content = content)
    }
}

@Composable
fun rememberImmersiveColors(base: Color): ImmersiveColors {
    val isDark = DesktopColors.IsDark
    return remember(base, isDark) { immersiveColors(base, isDark) }
}

// 全屏歌词页：深色沿用沉浸配方，浅色改白底主色高亮
@Composable
fun rememberLyricsViewColors(base: Color): ImmersiveColors {
    val isDark = DesktopColors.IsDark
    val accentText = DesktopColors.AccentText
    return remember(base, isDark, accentText) {
        if (isDark) immersiveColors(base, isDark = true) else lightLyricsColors(base, accentText)
    }
}

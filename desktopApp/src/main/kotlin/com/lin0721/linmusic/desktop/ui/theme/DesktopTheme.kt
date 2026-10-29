package com.lin0721.linmusic.desktop.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.lin0721.linmusic.core.ui.skin.CoverGroup
import com.lin0721.linmusic.core.ui.skin.LocalCoverPolicy
import com.lin0721.linmusic.core.ui.skin.LocalImmersiveBasePalette
import com.lin0721.linmusic.core.ui.skin.Skin
import com.lin0721.linmusic.core.ui.skin.coverPolicy
import com.lin0721.linmusic.core.ui.skin.immersiveBasePalette
import com.lin0721.linmusic.core.ui.theme.LocalMelodiaPalette
import com.lin0721.linmusic.core.ui.theme.LocalMelodiaStyle
import com.lin0721.linmusic.core.ui.theme.LocalSkinBackdrop
import com.lin0721.linmusic.core.ui.theme.MelodiaPalette
import com.lin0721.linmusic.core.ui.theme.MelodiaPalettes
import com.lin0721.linmusic.core.ui.theme.MelodiaStyle
import com.lin0721.linmusic.core.ui.theme.SkinBackdropHost
import com.lin0721.linmusic.core.ui.theme.ThemeFont

// 色值来自当前主题的 MelodiaPalette，语义见 MelodiaPalette；仅能在组合作用域内读取
object DesktopColors {
    val WindowBackground: Color @Composable @ReadOnlyComposable get() = LocalMelodiaPalette.current.windowBackground
    val Pane: Color @Composable @ReadOnlyComposable get() = LocalMelodiaPalette.current.pane
    val CanvasFill: Color @Composable @ReadOnlyComposable get() = LocalMelodiaPalette.current.canvasFill
    val ShellFill: Color @Composable @ReadOnlyComposable get() = LocalMelodiaPalette.current.shellFill
    val PageFill: Color @Composable @ReadOnlyComposable get() = LocalMelodiaPalette.current.pageFill
    val Surface: Color @Composable @ReadOnlyComposable get() = LocalMelodiaPalette.current.surface
    val CardSurface: Color @Composable @ReadOnlyComposable get() = LocalMelodiaPalette.current.cardSurface
    val PopupSurface: Color @Composable @ReadOnlyComposable get() = LocalMelodiaPalette.current.popupSurface
    val SurfaceLight: Color @Composable @ReadOnlyComposable get() = LocalMelodiaPalette.current.surfaceLight
    val FieldOnCanvas: Color @Composable @ReadOnlyComposable get() = LocalMelodiaPalette.current.fieldOnCanvas
    val FieldOnCanvasHover: Color @Composable @ReadOnlyComposable get() = LocalMelodiaPalette.current.fieldOnCanvasHover
    val TextPrimary: Color @Composable @ReadOnlyComposable get() = LocalMelodiaPalette.current.textPrimary
    val TextGray: Color @Composable @ReadOnlyComposable get() = LocalMelodiaPalette.current.textSecondary
    val TextTertiary: Color @Composable @ReadOnlyComposable get() = LocalMelodiaPalette.current.textTertiary
    val TextDisabled: Color @Composable @ReadOnlyComposable get() = LocalMelodiaPalette.current.textDisabled
    val Accent: Color @Composable @ReadOnlyComposable get() = LocalMelodiaPalette.current.accent
    val OnAccent: Color @Composable @ReadOnlyComposable get() = LocalMelodiaPalette.current.onAccent
    val AccentText: Color @Composable @ReadOnlyComposable get() = LocalMelodiaPalette.current.accentText
    val AccentContainer: Color @Composable @ReadOnlyComposable get() = LocalMelodiaPalette.current.accentContainer
    val CoverPlaceholder: Color @Composable @ReadOnlyComposable get() = LocalMelodiaPalette.current.coverPlaceholder
    val Overlay: Color @Composable @ReadOnlyComposable get() = LocalMelodiaPalette.current.overlay
    val Hover: Color @Composable @ReadOnlyComposable get() = LocalMelodiaPalette.current.hover
    val Pressed: Color @Composable @ReadOnlyComposable get() = LocalMelodiaPalette.current.pressed
    val Selected: Color @Composable @ReadOnlyComposable get() = LocalMelodiaPalette.current.selected
    val Fill: Color @Composable @ReadOnlyComposable get() = LocalMelodiaPalette.current.fill
    val Divider: Color @Composable @ReadOnlyComposable get() = LocalMelodiaPalette.current.divider
    val Outline: Color @Composable @ReadOnlyComposable get() = LocalMelodiaPalette.current.outline
    val Scrim: Color @Composable @ReadOnlyComposable get() = LocalMelodiaPalette.current.scrim
    val Success: Color @Composable @ReadOnlyComposable get() = LocalMelodiaPalette.current.success
    val Error: Color @Composable @ReadOnlyComposable get() = LocalMelodiaPalette.current.error
    val InverseText: Color @Composable @ReadOnlyComposable get() = LocalMelodiaPalette.current.inverseText
    val IsDark: Boolean @Composable @ReadOnlyComposable get() = LocalMelodiaPalette.current.isDark
}

object DesktopDimens {
    val TitleBarHeight = 64.dp
    val PlayerBarHeight = 80.dp
    val PaneGap = 8.dp
    val PaneRadius: Dp @Composable @ReadOnlyComposable get() = LocalMelodiaStyle.current.paneRadius
    val SidebarWidth = 300.dp
    val LibraryMinWidth = 280.dp
    val LibraryMaxWidth = 420.dp
    val LibraryRailWidth = 72.dp
    val NowPlayingWidth = 340.dp
    val NowPlayingMinWidth = 280.dp
    val NowPlayingMaxWidth = 420.dp
    val NowPlayingHandleWidth = 28.dp
    val NowPlayingPeekWidth = 72.dp
    val CenterMinWidth = 400.dp
    val PaneSnapWidth = 220.dp
}

@Composable
fun MelodiaDesktopTheme(
    uiScale: Float = 1f,
    palette: MelodiaPalette = MelodiaPalettes.dark,
    style: MelodiaStyle = MelodiaStyle.Default,
    backdrop: SkinBackdropHost? = null,
    skin: Skin? = null,
    content: @Composable () -> Unit
) {
    val baseDensity = LocalDensity.current
    val scaledDensity = remember(baseDensity, uiScale) {
        Density(baseDensity.density * uiScale, baseDensity.fontScale)
    }
    val colorScheme = remember(palette) { melodiaColorScheme(palette) }
    val baseTypography = MaterialTheme.typography
    val typography = remember(baseTypography, style.font) { baseTypography.withFont(style.font) }
    CompositionLocalProvider(
        LocalDensity provides scaledDensity,
        LocalMelodiaPalette provides palette,
        LocalMelodiaStyle provides style,
        LocalSkinBackdrop provides backdrop?.takeIf { palette.backdrop != null },
        LocalCoverPolicy provides skin.coverPolicy(palette.isDark, backdropReady = backdrop != null && palette.backdrop != null),
        LocalImmersiveBasePalette provides remember(skin) { immersiveBasePalette(skin) }
    ) {
        MaterialTheme(colorScheme = colorScheme, typography = typography, content = content)
    }
}

// 沉浸区（封面色铺底、白字）内部一律按皮肤的深色配色取色，保留当前主色；浅色主题下由此切换，深色主题下原样透传；
// 播放区透出背景图时带上背景图参数，内部的 playerBackdrop 才能画出背景
@Composable
fun ImmersiveTheme(content: @Composable () -> Unit) {
    val current = LocalMelodiaPalette.current
    if (current.isDark) {
        content()
        return
    }
    val base = LocalImmersiveBasePalette.current
    val playerBackdrop = LocalCoverPolicy.current.isBackdrop(CoverGroup.PLAYER)
    val palette = remember(base, current.accent, current.backdrop, playerBackdrop) {
        base.withAccent(current.accent).copy(backdrop = if (playerBackdrop) current.backdrop else null)
    }
    val colorScheme = remember(palette) { melodiaColorScheme(palette) }
    CompositionLocalProvider(LocalMelodiaPalette provides palette) {
        MaterialTheme(colorScheme = colorScheme, typography = MaterialTheme.typography, shapes = MaterialTheme.shapes, content = content)
    }
}

private fun melodiaColorScheme(palette: MelodiaPalette): ColorScheme {
    val base = if (palette.isDark) darkColorScheme() else lightColorScheme()
    return base.copy(
        primary = palette.accent,
        onPrimary = palette.onAccent,
        background = palette.windowBackground,
        onBackground = palette.textPrimary,
        surface = palette.pane,
        onSurface = palette.textPrimary,
        surfaceVariant = palette.surface,
        onSurfaceVariant = palette.textSecondary
    ).let { if (palette.isDark) it else it.withLightContainers(palette) }
}

// 浅色下 M3 组件的容器色、描边、遮罩、错误色改取色板；深色保持原有取值不变
private fun ColorScheme.withLightContainers(palette: MelodiaPalette): ColorScheme = copy(
    surfaceContainerLowest = palette.pane,
    surfaceContainerLow = palette.cardSurface,
    surfaceContainer = palette.surface,
    surfaceContainerHigh = palette.popupSurface,
    surfaceContainerHighest = palette.surfaceLight,
    outline = palette.outline,
    outlineVariant = palette.divider,
    scrim = palette.scrim,
    error = palette.error
)

private fun Typography.withFont(font: ThemeFont): Typography {
    val family = font.fontFamily ?: return this
    return Typography(
        displayLarge = displayLarge.copy(fontFamily = family),
        displayMedium = displayMedium.copy(fontFamily = family),
        displaySmall = displaySmall.copy(fontFamily = family),
        headlineLarge = headlineLarge.copy(fontFamily = family),
        headlineMedium = headlineMedium.copy(fontFamily = family),
        headlineSmall = headlineSmall.copy(fontFamily = family),
        titleLarge = titleLarge.copy(fontFamily = family),
        titleMedium = titleMedium.copy(fontFamily = family),
        titleSmall = titleSmall.copy(fontFamily = family),
        bodyLarge = bodyLarge.copy(fontFamily = family),
        bodyMedium = bodyMedium.copy(fontFamily = family),
        bodySmall = bodySmall.copy(fontFamily = family),
        labelLarge = labelLarge.copy(fontFamily = family),
        labelMedium = labelMedium.copy(fontFamily = family),
        labelSmall = labelSmall.copy(fontFamily = family)
    )
}

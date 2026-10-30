package com.lin0721.linmusic.core.ui.theme

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.os.Build
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LocalRippleConfiguration
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import com.lin0721.linmusic.core.preferences.ThemeSettings
import com.lin0721.linmusic.core.ui.interaction.MelodiaPressIndication
import com.lin0721.linmusic.core.ui.skin.CoverGroup
import com.lin0721.linmusic.core.ui.skin.LocalCoverPolicy
import com.lin0721.linmusic.core.ui.skin.LocalImmersiveBasePalette
import com.lin0721.linmusic.core.ui.skin.Skin
import com.lin0721.linmusic.core.ui.skin.coverPolicy
import com.lin0721.linmusic.core.ui.skin.immersiveBasePalette
import com.lin0721.linmusic.core.ui.skin.resolveSkinPalette
import com.lin0721.linmusic.core.ui.skin.withBackdrop

// 深色错误态固定用品牌红，不随主色变化
private val ErrorRed = Color(0xFFC20C0C)

// Melodia 应用的全局 Material3 主题配置
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MelodiaTheme(
    themeSettings: ThemeSettings = ThemeSettings(),
    skin: Skin? = null,
    backdropImage: ImageBitmap? = null,
    content: @Composable () -> Unit
) {
    val basePalette = rememberMelodiaPalette(themeSettings, skin)
    val backdropHost = remember(backdropImage) { backdropImage?.let(::SkinBackdropHost) }
    val palette = remember(basePalette, backdropHost, skin) {
        if (backdropHost == null) basePalette else basePalette.withBackdrop(skin?.background)
    }
    val colorScheme = remember(palette) { melodiaColorScheme(palette) }
    val style = skin?.style ?: MelodiaStyle.Default
    val typography = remember(style.font) { Typography.withFont(style.font) }
    MaterialTheme(
        colorScheme = colorScheme,
        typography = typography,
        shapes = MelodiaShapes
    ) {
        CompositionLocalProvider(
            LocalMelodiaPalette provides palette,
            LocalMelodiaStyle provides style,
            LocalSkinBackdrop provides backdropHost?.takeIf { palette.backdrop != null },
            LocalCoverPolicy provides skin.coverPolicy(palette.isDark, backdropReady = backdropHost != null && palette.backdrop != null),
            LocalImmersiveBasePalette provides remember(skin) { immersiveBasePalette(skin) },
            LocalIndication provides MelodiaPressIndication.Default,
            LocalRippleConfiguration provides null,
            content = content
        )
    }
}

// 沉浸区（播放页等封面色铺底、白字）内部一律按皮肤的深色配色取色，保留当前主色；浅色主题下由此切换，深色主题下原样透传；
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
    MaterialTheme(colorScheme = colorScheme, typography = MaterialTheme.typography, shapes = MelodiaShapes) {
        CompositionLocalProvider(
            LocalMelodiaPalette provides palette,
            LocalImmersiveMidTone provides true,
            content = content
        )
    }
}

// 浅色主题下，有色铺底的页面盖住状态栏期间把状态栏图标切为浅色，离开时恢复
@Composable
fun LightStatusBarIconsWhile(active: Boolean) {
    val isDark = MelodiaColors.IsDark
    val view = LocalView.current
    DisposableEffect(view, active, isDark) {
        val window = view.context.findActivity()?.window
        val controller = window?.let { WindowCompat.getInsetsController(it, view) }
        val override = active && !isDark && controller != null
        if (override) controller?.isAppearanceLightStatusBars = false
        onDispose {
            if (override) controller?.isAppearanceLightStatusBars = true
        }
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

private fun melodiaColorScheme(palette: MelodiaPalette): ColorScheme {
    val base = if (palette.isDark) darkColorScheme() else lightColorScheme()
    return base.copy(
        primary = palette.accent,
        onPrimary = palette.onAccent,
        background = palette.pane,
        onBackground = palette.textPrimary,
        surface = palette.surface,
        onSurface = palette.textPrimary,
        surfaceVariant = palette.surfaceLight,
        onSurfaceVariant = palette.textSecondary,
        error = if (palette.isDark) ErrorRed else palette.error,
        onError = Color.White
    ).let { if (palette.isDark) it else it.withLightContainers(palette) }
}

// 浅色下 M3 组件的容器色、描边、遮罩改取色板；深色保持原有取值不变
private fun ColorScheme.withLightContainers(palette: MelodiaPalette): ColorScheme = copy(
    surfaceContainerLowest = palette.pane,
    surfaceContainerLow = palette.cardSurface,
    surfaceContainer = palette.surface,
    surfaceContainerHigh = palette.popupSurface,
    surfaceContainerHighest = palette.surfaceLight,
    outline = palette.outline,
    outlineVariant = palette.divider,
    scrim = palette.scrim
)

@Composable
fun rememberMelodiaPalette(themeSettings: ThemeSettings, skin: Skin?): MelodiaPalette {
    val isDark = themeSettings.mode.isDark(isSystemInDarkTheme())
    val accentId = themeSettings.accentId(isDark)
    val customHex = themeSettings.customHex(isDark)
    val context = LocalContext.current
    val dynamicAccent = remember(context, isDark, accentId) {
        if (accentId != ThemeAccents.DYNAMIC_ID || Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
            null
        } else if (isDark) {
            dynamicDarkColorScheme(context).primary
        } else {
            dynamicLightColorScheme(context).primary
        }
    }
    return remember(isDark, accentId, customHex, dynamicAccent, skin) {
        resolveSkinPalette(isDark, accentId, customHex, skin, dynamicAccent)
    }
}

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

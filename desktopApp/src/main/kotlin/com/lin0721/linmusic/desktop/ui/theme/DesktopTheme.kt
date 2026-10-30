package com.lin0721.linmusic.desktop.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp

// 一套主题的全部颜色令牌，层级约定见 DesktopColors
data class DesktopPalette(
    val windowBackground: Color,
    val pane: Color,
    val surface: Color,
    val cardSurface: Color,
    val popupSurface: Color,
    val surfaceLight: Color,
    val textPrimary: Color,
    val textGray: Color,
    val accent: Color,
    val coverPlaceholder: Color
)

// 经典：纯黑底 + 中性灰阶 + 网易红
private val ClassicPalette = DesktopPalette(
    windowBackground = Color(0xFF000000),
    pane = Color(0xFF121212),
    surface = Color(0xFF2A2A2A),
    cardSurface = Color(0xFF1F1F1F),
    popupSurface = Color(0xFF282828),
    surfaceLight = Color(0xFF3E3E3E),
    textPrimary = Color(0xFFFFFFFF),
    textGray = Color(0xFFB3B3B3),
    accent = Color(0xFFC20C0C),
    coverPlaceholder = Color(0xFF2C2C2C)
)

// 暖陶：偏暖的炭灰阶 + 陶土橙强调色，文字用米白而非纯白，整体对比更柔和
private val TerracottaPalette = DesktopPalette(
    windowBackground = Color(0xFF141413),
    pane = Color(0xFF1F1E1D),
    surface = Color(0xFF2C2B29),
    cardSurface = Color(0xFF262523),
    popupSurface = Color(0xFF2A2927),
    surfaceLight = Color(0xFF3D3B37),
    textPrimary = Color(0xFFECEBE7),
    textGray = Color(0xFFB8B5AE),
    accent = Color(0xFFD97757),
    coverPlaceholder = Color(0xFF2E2D2A)
)

enum class DesktopThemeStyle(val label: String, val palette: DesktopPalette) {
    CLASSIC("经典", ClassicPalette),
    TERRACOTTA("暖陶", TerracottaPalette);

    companion object {
        fun fromName(name: String?): DesktopThemeStyle = entries.firstOrNull { it.name == name } ?: CLASSIC
    }
}

// 背景分三档：窗口与边框 WindowBackground、大背景 Pane、卡片 CardSurface；
// PopupSurface 是菜单、对话框与悬停的底色，Surface 是药丸、搜索框、图标按钮、选项格子等控件的底色。
// 令牌取自当前主题，读在快照状态上，切换主题时读过它们的组合会自动重组
object DesktopColors {
    var palette: DesktopPalette by mutableStateOf(ClassicPalette)

    val WindowBackground get() = palette.windowBackground
    val Pane get() = palette.pane
    val Surface get() = palette.surface
    val CardSurface get() = palette.cardSurface
    val PopupSurface get() = palette.popupSurface
    // 行与按钮的悬停底，与菜单同色
    val PaneHover get() = PopupSurface
    val SurfaceLight get() = palette.surfaceLight
    val TextPrimary get() = palette.textPrimary
    val TextGray get() = palette.textGray
    val Accent get() = palette.accent
    val CoverPlaceholder get() = palette.coverPlaceholder
}

object DesktopDimens {
    val TitleBarHeight = 64.dp
    val PlayerBarHeight = 80.dp
    val PaneGap = 8.dp
    val PaneRadius = 8.dp
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
fun MelodiaDesktopTheme(uiScale: Float = 1f, content: @Composable () -> Unit) {
    val baseDensity = LocalDensity.current
    val scaledDensity = remember(baseDensity, uiScale) {
        Density(baseDensity.density * uiScale, baseDensity.fontScale)
    }
    CompositionLocalProvider(LocalDensity provides scaledDensity) {
        MaterialTheme(
            colorScheme = darkColorScheme(
                primary = DesktopColors.Accent,
                background = DesktopColors.WindowBackground,
                surface = DesktopColors.Pane,
                surfaceVariant = DesktopColors.Surface,
                onPrimary = DesktopColors.TextPrimary,
                onBackground = DesktopColors.TextPrimary,
                onSurface = DesktopColors.TextPrimary,
                onSurfaceVariant = DesktopColors.TextGray
            ),
            content = content
        )
    }
}

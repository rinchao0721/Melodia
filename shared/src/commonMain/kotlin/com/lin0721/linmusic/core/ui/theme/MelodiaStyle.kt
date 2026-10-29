package com.lin0721.linmusic.core.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

// 皮肤可调的非颜色样式；可空项为 null 时各组件沿用自身默认值
@Immutable
data class MelodiaStyle(
    val paneRadius: Dp = DEFAULT_PANE_RADIUS,
    val cardRadius: Dp? = null,
    val coverRadius: Dp? = null,
    val buttonRadius: Dp? = null,
    val font: ThemeFont = ThemeFont.DEFAULT,
    val density: ThemeDensity = ThemeDensity.NORMAL,
    val paneBorder: Dp? = null,
    val progress: ThemeProgressStyle = ThemeProgressStyle.LINE,
    val controls: ThemeControlStyle = ThemeControlStyle.ICON
) {
    companion object {
        val DEFAULT_PANE_RADIUS = 8.dp
        val Default = MelodiaStyle()
    }
}

val LocalMelodiaStyle = staticCompositionLocalOf { MelodiaStyle.Default }

// 皮肤文件里的取值即 key，改名会让已发布的皮肤失效
enum class ThemeFont(val key: String) {
    DEFAULT("default"),
    MONOSPACE("monospace");

    // null 表示沿用平台默认字体
    val fontFamily: FontFamily?
        get() = when (this) {
            DEFAULT -> null
            MONOSPACE -> FontFamily.Monospace
        }

    companion object {
        fun fromKey(key: String): ThemeFont? = entries.firstOrNull { it.key == key }
    }
}

enum class ThemeDensity(val key: String) {
    COMPACT("compact"),
    NORMAL("normal"),
    COMFORTABLE("comfortable");

    companion object {
        fun fromKey(key: String): ThemeDensity? = entries.firstOrNull { it.key == key }
    }
}

enum class ThemeProgressStyle(val key: String) {
    LINE("line"),
    THICK("thick");

    companion object {
        fun fromKey(key: String): ThemeProgressStyle? = entries.firstOrNull { it.key == key }
    }
}

enum class ThemeControlStyle(val key: String) {
    ICON("icon"),
    OUTLINED("outlined");

    companion object {
        fun fromKey(key: String): ThemeControlStyle? = entries.firstOrNull { it.key == key }
    }
}

private const val COMPACT_SCALE = 0.85f
private const val COMFORTABLE_SCALE = 1.15f
private const val THICK_PROGRESS_SCALE = 2f

// 列表行高与行内间距的倍数
val MelodiaStyle.densityScale: Float
    get() = when (density) {
        ThemeDensity.COMPACT -> COMPACT_SCALE
        ThemeDensity.NORMAL -> 1f
        ThemeDensity.COMFORTABLE -> COMFORTABLE_SCALE
    }

// 播放进度条轨道粗细的倍数
val MelodiaStyle.progressScale: Float
    get() = if (progress == ThemeProgressStyle.THICK) THICK_PROGRESS_SCALE else 1f

// 以下取值在皮肤没写对应项时返回调用方原本的默认值
@Composable
@ReadOnlyComposable
fun styledCardRadius(default: Dp): Dp = LocalMelodiaStyle.current.cardRadius ?: default

@Composable
@ReadOnlyComposable
fun styledCoverRadius(default: Dp): Dp = LocalMelodiaStyle.current.coverRadius ?: default

@Composable
@ReadOnlyComposable
fun styledButtonShape(default: Shape): Shape = LocalMelodiaStyle.current.buttonRadius?.let { RoundedCornerShape(it) } ?: default

@Composable
@ReadOnlyComposable
fun densityScaled(value: Dp): Dp = value * LocalMelodiaStyle.current.densityScale

@Composable
@ReadOnlyComposable
fun progressScaled(value: Dp): Dp = value * LocalMelodiaStyle.current.progressScale

val outlinedControls: Boolean
    @Composable @ReadOnlyComposable get() = LocalMelodiaStyle.current.controls == ThemeControlStyle.OUTLINED

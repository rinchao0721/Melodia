package com.lin0721.linmusic.core.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

// 平板适配断点：按可用宽度分两档（见平板适配设计文档第 4 节）
enum class MelodiaWindowSizeClass {
    Compact,
    Expanded
}

private const val EXPANDED_MIN_WIDTH_DP = 600

@Composable
fun rememberMelodiaWindowSizeClass(): MelodiaWindowSizeClass {
    val widthDp = LocalConfiguration.current.screenWidthDp
    return remember(widthDp) {
        if (widthDp >= EXPANDED_MIN_WIDTH_DP) {
            MelodiaWindowSizeClass.Expanded
        } else {
            MelodiaWindowSizeClass.Compact
        }
    }
}

val LocalMelodiaWindowSizeClass = staticCompositionLocalOf { MelodiaWindowSizeClass.Compact }

// 方向维度，与宽度断点独立组合使用。横屏下可用宽度更大、可用高度更矮，
// 部分模块（首页货架密度、MV 播放页视频+评论布局等）需要在同为 Expanded 的前提下再区分横竖屏
enum class MelodiaOrientationClass {
    Portrait,
    Landscape
}

@Composable
fun rememberMelodiaOrientationClass(): MelodiaOrientationClass {
    val configuration = LocalConfiguration.current
    return remember(configuration.screenWidthDp, configuration.screenHeightDp) {
        if (configuration.screenWidthDp >= configuration.screenHeightDp) {
            MelodiaOrientationClass.Landscape
        } else {
            MelodiaOrientationClass.Portrait
        }
    }
}

val LocalMelodiaOrientationClass = staticCompositionLocalOf { MelodiaOrientationClass.Portrait }

// 网格列数按断点取值的通用工具，各调用点自带一套
// compact/expandedPortrait/expandedLandscape 数值，避免各处重复写 when 分支
@Composable
fun rememberMelodiaGridColumns(
    compact: Int,
    expandedPortrait: Int,
    expandedLandscape: Int = expandedPortrait
): Int {
    val windowSizeClass = LocalMelodiaWindowSizeClass.current
    val orientationClass = LocalMelodiaOrientationClass.current
    return when {
        windowSizeClass == MelodiaWindowSizeClass.Expanded && orientationClass == MelodiaOrientationClass.Landscape -> expandedLandscape
        windowSizeClass == MelodiaWindowSizeClass.Expanded -> expandedPortrait
        else -> compact
    }
}

// 平板常驻播放面板宽度：竖屏 340dp / 横屏 400dp（设计文档 6.3 节建议值，真机验证后可再调）。
// 收起态的迷你播放条与展开态的面板共用同一宽度，两者上下贴齐
private val PlayerPanelWidthPortrait = 340.dp
private val PlayerPanelWidthLandscape = 400.dp

@Composable
fun rememberMelodiaPlayerPanelWidth(): Dp {
    return if (rememberMelodiaOrientationClass() == MelodiaOrientationClass.Landscape) {
        PlayerPanelWidthLandscape
    } else {
        PlayerPanelWidthPortrait
    }
}

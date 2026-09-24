package com.lin0721.linmusic.core.ui.theme

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.statusBars
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

// 外层卡片是否已避开状态栏/手势条（平板播放面板让位后的双卡片模式）。
// statusBarsPadding 这类修饰符由外层 consumeWindowInsets 自动归零，这里只服务直接读取 WindowInsets 数值的调用点
val LocalMelodiaSystemBarsConsumed = staticCompositionLocalOf { false }

@Composable
fun melodiaStatusBarTopPadding(): Dp =
    if (LocalMelodiaSystemBarsConsumed.current) 0.dp
    else WindowInsets.statusBars.asPaddingValues().calculateTopPadding()

@Composable
fun melodiaNavigationBarBottomPadding(): Dp =
    if (LocalMelodiaSystemBarsConsumed.current) 0.dp
    else WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

package com.lin0721.linmusic.desktop.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import com.lin0721.linmusic.desktop.platform.native.UiScale

// 把平台探测到的桌面缩放注入 Compose 密度。
// Windows 等平台返回 null（AWT 已正确上报）时保持默认密度，不做任何改写。
@Composable
fun ProvideUiScale(content: @Composable () -> Unit) {
    val scale = UiScale.current.detected
    if (scale == null) {
        content()
    } else {
        CompositionLocalProvider(LocalDensity provides Density(scale, 1f), content = content)
    }
}

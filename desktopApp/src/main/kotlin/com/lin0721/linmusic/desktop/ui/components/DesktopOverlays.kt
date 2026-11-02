package com.lin0721.linmusic.desktop.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue

// 插件的 Compose 页面是重量级组件，总在同窗口的 Compose 弹层之上，弹层打开时页面要让开
object DesktopOverlays {
    var active by mutableIntStateOf(0)
        private set

    @Composable
    fun Track() {
        DisposableEffect(Unit) {
            active++
            onDispose { active-- }
        }
    }
}

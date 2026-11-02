package com.lin0721.linmusic.feature.player.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FormatListNumbered
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.ui.graphics.vector.ImageVector
import com.lin0721.linmusic.core.player.PlayMode

// 播放模式图标：四种模式各有独立形状，不再只靠明暗区分状态；顺序播放用列表图标，避免和循环箭头混淆
fun PlayMode.modeIcon(): ImageVector = when (this) {
    PlayMode.SEQUENCE -> Icons.Default.FormatListNumbered
    PlayMode.LIST_LOOP -> Icons.Default.Repeat
    PlayMode.SINGLE_LOOP -> Icons.Default.RepeatOne
    PlayMode.SHUFFLE -> Icons.Default.Shuffle
}

// 无障碍/长按提示用的模式名
fun PlayMode.modeLabel(): String = when (this) {
    PlayMode.SEQUENCE -> "顺序播放"
    PlayMode.LIST_LOOP -> "列表循环"
    PlayMode.SINGLE_LOOP -> "单曲循环"
    PlayMode.SHUFFLE -> "随机播放"
}

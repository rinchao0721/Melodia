package com.lin0721.linmusic.desktop.ui.icons

import androidx.compose.ui.graphics.vector.ImageVector
import com.lin0721.linmusic.core.ui.icons.lineIcon

// 播放栏右侧自绘图标：24 格内统一占 y=3~21，2px 圆角描边，保证视觉高度一致
object PlayerBarIcons {
    // 桌面歌词：悬浮字幕条
    val DesktopLyric: ImageVector by lazy {
        lineIcon(
            "DesktopLyric",
            "M6.5 4h11A3.5 3.5 0 0 1 21 7.5v9a3.5 3.5 0 0 1-3.5 3.5h-11A3.5 3.5 0 0 1 3 16.5v-9A3.5 3.5 0 0 1 6.5 4z",
            "M7 13h10M7 16.5h6"
        )
    }

    // 全屏歌词：竖向屏幕，中间一行最长表示当前行高亮
    val Lyrics: ImageVector by lazy {
        lineIcon(
            "Lyrics",
            "M6 3h12a2 2 0 0 1 2 2v14a2 2 0 0 1-2 2H6a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2z",
            "M8 8h4M8 12h8M8 16h5"
        )
    }
}


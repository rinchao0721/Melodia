package com.lin0721.linmusic.desktop.ui.icons

import androidx.compose.ui.graphics.vector.ImageVector
import com.lin0721.linmusic.core.ui.icons.lineIcon

// 播放栏右侧自绘图标：24 格内统一落在 16×15 视觉框，2px 圆角描边，保证视觉高度一致
object PlayerBarIcons {
    // 桌面歌词：悬浮字幕条
    val DesktopLyric: ImageVector by lazy {
        lineIcon(
            "DesktopLyric",
            "M6 5h12a2 2 0 0 1 2 2v10a2 2 0 0 1 -2 2H6a2 2 0 0 1 -2 -2V7a2 2 0 0 1 2 -2z",
            "M7.5 12.5h9M7.5 15.5h5"
        )
    }

    // 全屏歌词：竖向屏幕，中间一行最长表示当前行高亮
    val Lyrics: ImageVector by lazy {
        lineIcon(
            "Lyrics",
            "M7 4.5h10a2 2 0 0 1 2 2v11a2 2 0 0 1 -2 2H7a2 2 0 0 1 -2 -2v-11a2 2 0 0 1 2 -2z",
            "M8.5 9h4M8.5 12h7M8.5 15h4.5"
        )
    }
}


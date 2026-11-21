package com.lin0721.linmusic.desktop.ui.icons

import androidx.compose.ui.graphics.vector.ImageVector
import com.lin0721.linmusic.core.ui.icons.lineIcon

// 歌词卡右上角自绘图标：与播放栏图标同规格（24 格、2px 圆角描边），两枚占位一致
object LyricCardIcons {
    // 全屏：四角括号
    val Fullscreen: ImageVector by lazy {
        lineIcon(
            "LyricFullscreen",
            "M4 9V5.5A1.5 1.5 0 0 1 5.5 4H9M15 4h3.5A1.5 1.5 0 0 1 20 5.5V9M20 15v3.5a1.5 1.5 0 0 1-1.5 1.5H15M9 20H5.5A1.5 1.5 0 0 1 4 18.5V15"
        )
    }

    // 全屏歌词：对角双箭头
    val ExpandLyrics: ImageVector by lazy {
        lineIcon("LyricExpand", "M14 4h6v6M20 4l-6.5 6.5M10 20H4v-6M4 20l6.5-6.5")
    }
}

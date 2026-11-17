package com.lin0721.linmusic.desktop.ui.icons

import androidx.compose.ui.graphics.vector.ImageVector
import com.lin0721.linmusic.core.ui.icons.lineIcon

// 下载按钮自绘图标，规格同 PlayerBarIcons：24 格、2px 圆角描边
object DownloadIcons {
    val Download: ImageVector by lazy {
        lineIcon(
            "Download",
            "M12 3.5v11",
            "M7.5 10.5L12 15l4.5-4.5",
            "M4 16.5v1.5A3 3 0 0 0 7 21h10a3 3 0 0 0 3-3v-1.5"
        )
    }

    val Check: ImageVector by lazy {
        lineIcon("Check", "M5.5 12.5l4.5 4.5L18.5 7.5")
    }
}

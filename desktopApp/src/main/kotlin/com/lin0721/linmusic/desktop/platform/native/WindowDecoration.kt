package com.lin0721.linmusic.desktop.platform.native

import java.awt.Window

// 无边框窗口的系统外观（系统圆角 / 边框色 / 最小化还原动画）。
// Windows 用 DWM + 子类化窗口过程，其他平台为 no-op 或 CSD。
@RequireAllPlatforms
interface WindowDecoration {

    fun enableSystemAnimations(window: Window)

    fun restoreWindowProc(window: Window)

    // borderRgb 为 0xRRGGBB
    fun applyFrame(window: Window, maximized: Boolean, borderRgb: Int)
}

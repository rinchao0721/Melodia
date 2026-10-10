package com.lin0721.linmusic.desktop.platform.native

import java.awt.Window

// 桌面歌词窗口的点击穿透：Windows 用 WS_EX_TRANSPARENT，
// X11/XWayland 用 Shape 扩展清空窗口输入区域；Wayland 原生会话无对应协议（no-op）。
@RequireAllPlatforms
interface DesktopLyricBehavior {

    fun setClickThrough(window: Window, enabled: Boolean)
}

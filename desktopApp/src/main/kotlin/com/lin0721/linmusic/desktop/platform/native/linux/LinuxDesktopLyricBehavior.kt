package com.lin0721.linmusic.desktop.platform.native.linux

import com.lin0721.linmusic.core.log.AppLogger
import com.lin0721.linmusic.desktop.platform.native.DesktopLyricBehavior
import com.lin0721.linmusic.desktop.platform.native.DesktopPlatform
import com.lin0721.linmusic.desktop.platform.native.PlatformImpl
import com.lin0721.linmusic.desktop.platform.native.linux.x11.X11InputShape
import java.awt.Dialog
import java.awt.Frame
import java.awt.Window

// Linux 桌面歌词点击穿透：清空窗口的 X11 输入区域（Shape 扩展），
// 鼠标事件直接落到下层窗口，恢复时把输入区域设回完整矩形。
// XWayland 与原生 X11 会话都适用；Wayland 原生会话没有对外协议可改输入区域，保持 no-op。
@PlatformImpl(DesktopPlatform.LINUX)
class LinuxDesktopLyricBehavior : DesktopLyricBehavior {

    override fun setClickThrough(window: Window, enabled: Boolean) {
        if (System.getenv("DISPLAY").isNullOrBlank()) {
            AppLogger.i(TAG, "无 X11 显示（Wayland 原生会话），桌面歌词穿透不可用")
            return
        }
        // Compose 的歌词窗口是 Window 的 Frame/Dialog 实现，标题取不到就放弃
        val title = (window as? Frame)?.title ?: (window as? Dialog)?.title ?: return
        // 窗口刚显示时可能还没映射出来，稍作重试；恢复（enabled=false）不必等待
        var applied = X11InputShape.setClickThrough(title, enabled)
        var attempt = 0
        while (enabled && applied == 0 && attempt++ < RETRY_TIMES) {
            Thread.sleep(RETRY_INTERVAL_MS)
            applied = X11InputShape.setClickThrough(title, enabled)
        }
        if (applied == 0) {
            AppLogger.w(TAG, "未找到桌面歌词窗口（$title），穿透=$enabled 未生效")
        } else {
            AppLogger.i(TAG, "桌面歌词穿透=$enabled，已处理 $applied 个窗口")
        }
    }

    private companion object {
        const val TAG = "DesktopLyric"
        const val RETRY_TIMES = 8
        const val RETRY_INTERVAL_MS = 150L
    }
}

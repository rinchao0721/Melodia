package com.lin0721.linmusic.desktop.platform.native.windows

import com.lin0721.linmusic.core.log.AppLogger
import com.lin0721.linmusic.desktop.platform.native.DesktopLyricBehavior
import com.lin0721.linmusic.desktop.platform.native.windows.winapi.User32
import com.sun.jna.Native
import java.awt.Window
import com.lin0721.linmusic.desktop.platform.native.DesktopPlatform
import com.lin0721.linmusic.desktop.platform.native.PlatformImpl

private const val TAG = "DesktopLyric"

// 锁定后加 WS_EX_TRANSPARENT，鼠标事件直接穿透到下层窗口
@PlatformImpl(DesktopPlatform.WINDOWS)
class WindowsDesktopLyricBehavior : DesktopLyricBehavior {

    override fun setClickThrough(window: Window, enabled: Boolean) {
        try {
            val hwnd = Native.getWindowPointer(window) ?: return
            val user32 = User32.INSTANCE
            val style = user32.GetWindowLongPtrW(hwnd, User32.GWL_EXSTYLE)
            val newStyle = if (enabled) {
                style or User32.WS_EX_TRANSPARENT or User32.WS_EX_LAYERED
            } else {
                style and User32.WS_EX_TRANSPARENT.inv()
            }
            user32.SetWindowLongPtrW(hwnd, User32.GWL_EXSTYLE, newStyle)
        } catch (e: UnsatisfiedLinkError) {
            AppLogger.w(TAG, "设置桌面歌词穿透失败", e)
        }
    }
}

package com.lin0721.linmusic.desktop.platform.native.windows.winapi

import com.sun.jna.Library
import com.sun.jna.Native
import com.sun.jna.Pointer
import com.sun.jna.ptr.IntByReference
import com.sun.jna.win32.W32APIOptions

// 圆角与边框色属性需要 Windows 11，旧系统调用会返回失败码
@Suppress("FunctionName")
internal interface Dwmapi : Library {
    fun DwmSetWindowAttribute(hwnd: Pointer, dwAttribute: Int, pvAttribute: IntByReference, cbAttribute: Int): Int

    companion object {
        val INSTANCE: Dwmapi by lazy { Native.load("dwmapi", Dwmapi::class.java, W32APIOptions.DEFAULT_OPTIONS) }

        const val DWMWA_TRANSITIONS_FORCEDISABLED = 3
        const val DWMWA_WINDOW_CORNER_PREFERENCE = 33
        const val DWMWA_BORDER_COLOR = 34

        const val DWMWCP_DONOTROUND = 1
        const val DWMWCP_ROUND = 2

        // 0xFFFFFFFE：不绘制边框
        const val DWMWA_COLOR_NONE = -2
    }
}

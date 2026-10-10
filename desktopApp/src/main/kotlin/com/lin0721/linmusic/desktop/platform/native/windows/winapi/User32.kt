package com.lin0721.linmusic.desktop.platform.native.windows.winapi

import com.sun.jna.Callback
import com.sun.jna.Library
import com.sun.jna.Native
import com.sun.jna.Pointer
import com.sun.jna.Structure
import com.sun.jna.win32.W32APIOptions

// 仅映射用到的几个 user32 函数；结构体按 64 位 Windows 布局
@Suppress("FunctionName")
internal interface User32 : Library {
    fun RegisterHotKey(hWnd: Pointer?, id: Int, fsModifiers: Int, vk: Int): Boolean
    fun UnregisterHotKey(hWnd: Pointer?, id: Int): Boolean
    fun GetMessageW(msg: Msg, hWnd: Pointer?, wMsgFilterMin: Int, wMsgFilterMax: Int): Int
    fun PostThreadMessageW(idThread: Int, msg: Int, wParam: Long, lParam: Long): Boolean
    fun GetWindowLongPtrW(hWnd: Pointer, nIndex: Int): Long
    fun SetWindowLongPtrW(hWnd: Pointer, nIndex: Int, dwNewLong: Long): Long
    fun SetWindowLongPtrW(hWnd: Pointer, nIndex: Int, wndProc: Callback): Pointer?
    fun SetWindowLongPtrW(hWnd: Pointer, nIndex: Int, wndProc: Pointer?): Pointer?
    fun CallWindowProcW(prevWndProc: Pointer?, hWnd: Pointer?, msg: Int, wParam: Long, lParam: Long): Long
    fun SetWindowPos(hWnd: Pointer, hWndInsertAfter: Pointer?, x: Int, y: Int, cx: Int, cy: Int, flags: Int): Boolean
    fun IsZoomed(hWnd: Pointer): Boolean
    fun GetDpiForWindow(hWnd: Pointer): Int
    fun GetSystemMetricsForDpi(nIndex: Int, dpi: Int): Int

    companion object {
        val INSTANCE: User32 by lazy { Native.load("user32", User32::class.java, W32APIOptions.DEFAULT_OPTIONS) }

        const val WM_QUIT = 0x0012
        const val WM_HOTKEY = 0x0312
        const val WM_APP = 0x8000

        const val MOD_ALT = 0x0001
        const val MOD_CONTROL = 0x0002
        const val MOD_SHIFT = 0x0004
        const val MOD_NOREPEAT = 0x4000

        const val GWL_STYLE = -16
        const val GWL_EXSTYLE = -20
        const val GWLP_WNDPROC = -4

        const val WM_NCCALCSIZE = 0x0083

        const val WS_CAPTION = 0x00C00000L
        const val WS_SYSMENU = 0x00080000L
        const val WS_THICKFRAME = 0x00040000L
        const val WS_MINIMIZEBOX = 0x00020000L
        const val WS_MAXIMIZEBOX = 0x00010000L
        const val WS_EX_TRANSPARENT = 0x00000020L
        const val WS_EX_LAYERED = 0x00080000L

        const val SWP_NOSIZE = 0x0001
        const val SWP_NOMOVE = 0x0002
        const val SWP_NOZORDER = 0x0004
        const val SWP_NOACTIVATE = 0x0010
        const val SWP_FRAMECHANGED = 0x0020

        const val SM_CXFRAME = 32
        const val SM_CYFRAME = 33
        const val SM_CXPADDEDBORDER = 92
    }
}

@Suppress("FunctionName")
internal interface Kernel32 : Library {
    fun GetCurrentThreadId(): Int

    companion object {
        val INSTANCE: Kernel32 by lazy { Native.load("kernel32", Kernel32::class.java, W32APIOptions.DEFAULT_OPTIONS) }
    }
}

@Structure.FieldOrder("hwnd", "message", "wParam", "lParam", "time", "ptX", "ptY", "lPrivate")
internal class Msg : Structure() {
    @JvmField var hwnd: Pointer? = null
    @JvmField var message: Int = 0
    @JvmField var wParam: Long = 0
    @JvmField var lParam: Long = 0
    @JvmField var time: Int = 0
    @JvmField var ptX: Int = 0
    @JvmField var ptY: Int = 0
    @JvmField var lPrivate: Int = 0
}

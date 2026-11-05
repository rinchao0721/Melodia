package com.lin0721.linmusic.desktop.platform.windows

import com.sun.jna.Callback
import com.sun.jna.Library
import com.sun.jna.Native
import com.sun.jna.Pointer
import com.sun.jna.Structure
import com.sun.jna.ptr.PointerByReference
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
    fun RegisterWindowMessageW(lpString: String): Int
    fun CreateIconIndirect(piconinfo: Pointer): Pointer?
    fun DestroyIcon(hIcon: Pointer): Boolean
    fun PostMessageW(hWnd: Pointer, msg: Int, wParam: Long, lParam: Long): Boolean
    fun DefWindowProcW(hWnd: Pointer, msg: Int, wParam: Long, lParam: Long): Long
    fun ReleaseCapture(): Boolean
    fun GetCursorPos(point: WinPoint): Boolean
    fun GetAsyncKeyState(vKey: Int): Short
    fun GetSystemMetrics(nIndex: Int): Int

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

        const val WM_NCDESTROY = 0x0082
        const val WM_NCCALCSIZE = 0x0083
        const val WM_COMMAND = 0x0111
        const val WM_SYSCOMMAND = 0x0112
        const val WM_SETTINGCHANGE = 0x001A
        const val WM_DWMSENDICONICTHUMBNAIL = 0x0323
        const val WM_DWMSENDICONICLIVEPREVIEWBITMAP = 0x0326

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
        const val SM_CXSMICON = 49
        const val SM_SWAPBUTTON = 23

        // 低 4 位为鼠标拖动方式：移动取 HTCAPTION，缩放取 WMSZ_* 边
        const val SC_SIZE = 0xF000
        const val SC_MOVE = 0xF010
        const val HTCAPTION = 2
        const val WMSZ_LEFT = 1
        const val WMSZ_RIGHT = 2
        const val WMSZ_TOP = 3
        const val WMSZ_TOPLEFT = 4
        const val WMSZ_TOPRIGHT = 5
        const val WMSZ_BOTTOM = 6
        const val WMSZ_BOTTOMLEFT = 7
        const val WMSZ_BOTTOMRIGHT = 8

        const val VK_LBUTTON = 0x01
        const val VK_RBUTTON = 0x02
    }
}

@Suppress("FunctionName")
internal interface Gdi32 : Library {
    fun CreateDIBSection(
        hdc: Pointer?,
        pbmi: Pointer,
        usage: Int,
        ppvBits: PointerByReference,
        hSection: Pointer?,
        offset: Int
    ): Pointer?

    fun CreateBitmap(width: Int, height: Int, planes: Int, bitCount: Int, bits: Pointer?): Pointer?
    fun DeleteObject(hObject: Pointer): Boolean

    companion object {
        val INSTANCE: Gdi32 by lazy { Native.load("gdi32", Gdi32::class.java, W32APIOptions.DEFAULT_OPTIONS) }
    }
}

@Suppress("FunctionName")
internal interface Ole32 : Library {
    fun CoInitializeEx(pvReserved: Pointer?, dwCoInit: Int): Int
    fun CoUninitialize()
    fun CoCreateInstance(rclsid: Pointer, pUnkOuter: Pointer?, dwClsContext: Int, riid: Pointer, ppv: PointerByReference): Int
    fun CoTaskMemFree(pv: Pointer?)

    companion object {
        val INSTANCE: Ole32 by lazy { Native.load("ole32", Ole32::class.java, W32APIOptions.DEFAULT_OPTIONS) }

        const val COINIT_MULTITHREADED = 0
        const val CLSCTX_INPROC_SERVER = 1
        const val CLSCTX_ALL = 23
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

@Structure.FieldOrder("x", "y")
internal class WinPoint : Structure() {
    @JvmField var x: Int = 0
    @JvmField var y: Int = 0
}

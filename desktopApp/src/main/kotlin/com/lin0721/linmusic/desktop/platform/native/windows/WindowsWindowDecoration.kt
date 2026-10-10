package com.lin0721.linmusic.desktop.platform.native.windows

import com.lin0721.linmusic.core.log.AppLogger
import com.lin0721.linmusic.desktop.platform.native.WindowDecoration
import com.lin0721.linmusic.desktop.platform.native.windows.winapi.Dwmapi
import com.lin0721.linmusic.desktop.platform.native.windows.winapi.User32
import com.sun.jna.Callback
import com.sun.jna.Native
import com.sun.jna.Pointer
import com.sun.jna.ptr.IntByReference
import java.awt.Window
import java.util.concurrent.ConcurrentHashMap
import com.lin0721.linmusic.desktop.platform.native.DesktopPlatform
import com.lin0721.linmusic.desktop.platform.native.PlatformImpl

private const val TAG = "WindowChrome"

// 窗口过程；须保持强引用，否则回收后原生侧回调会崩溃
private interface WndProc : Callback {
    fun callback(hwnd: Pointer?, msg: Int, wParam: Long, lParam: Long): Long
}

private class Subclass(val previous: Pointer?, val proc: WndProc)

// 无边框窗口的系统圆角、边框色与最小化/还原/显示/隐藏动画；非 Windows 11 上调用无副作用
@PlatformImpl(DesktopPlatform.WINDOWS)
class WindowsWindowDecoration : WindowDecoration {

    private val isWindows = System.getProperty("os.name").orEmpty().startsWith("Windows", ignoreCase = true)
    private val subclasses = ConcurrentHashMap<Long, Subclass>()

    // 系统只给带标题栏样式的窗口播放最小化/还原等动画：补齐样式后拦截 WM_NCCALCSIZE，
    // 让客户区占满整个窗口，外观仍是无边框
    override fun enableSystemAnimations(window: Window) = withHandle(window) { hwnd ->
        val key = Pointer.nativeValue(hwnd)
        if (subclasses.containsKey(key)) return@withHandle
        val user32 = User32.INSTANCE
        val proc = object : WndProc {
            override fun callback(hwnd: Pointer?, msg: Int, wParam: Long, lParam: Long): Long {
                val previous = subclasses[key]?.previous
                if (msg == User32.WM_NCCALCSIZE && wParam != 0L && hwnd != null) {
                    trimMaximizedBounds(hwnd, lParam)
                    return 0
                }
                return user32.CallWindowProcW(previous, hwnd, msg, wParam, lParam)
            }
        }
        // 先登记再替换窗口过程，避免回调早于登记而丢失原过程
        subclasses[key] = Subclass(null, proc)
        val previous = user32.SetWindowLongPtrW(hwnd, User32.GWLP_WNDPROC, proc)
        subclasses[key] = Subclass(previous, proc)

        val style = user32.GetWindowLongPtrW(hwnd, User32.GWL_STYLE)
        user32.SetWindowLongPtrW(
            hwnd,
            User32.GWL_STYLE,
            style or User32.WS_CAPTION or User32.WS_THICKFRAME or User32.WS_SYSMENU or
                User32.WS_MINIMIZEBOX or User32.WS_MAXIMIZEBOX
        )
        user32.SetWindowPos(
            hwnd, null, 0, 0, 0, 0,
            User32.SWP_NOMOVE or User32.SWP_NOSIZE or User32.SWP_NOZORDER or User32.SWP_NOACTIVATE or User32.SWP_FRAMECHANGED
        )
        setAttribute(hwnd, Dwmapi.DWMWA_TRANSITIONS_FORCEDISABLED, 0)
    }

    override fun restoreWindowProc(window: Window) = withHandle(window) { hwnd ->
        val entry = subclasses.remove(Pointer.nativeValue(hwnd)) ?: return@withHandle
        User32.INSTANCE.SetWindowLongPtrW(hwnd, User32.GWLP_WNDPROC, entry.previous)
    }

    // 最大化时窗口矩形会向四周溢出一圈缩放边框，客户区需要收回到工作区内
    private fun trimMaximizedBounds(hwnd: Pointer, lParam: Long) {
        val user32 = User32.INSTANCE
        if (!user32.IsZoomed(hwnd)) return
        try {
            val dpi = user32.GetDpiForWindow(hwnd)
            val horizontal = user32.GetSystemMetricsForDpi(User32.SM_CXFRAME, dpi) +
                user32.GetSystemMetricsForDpi(User32.SM_CXPADDEDBORDER, dpi)
            val vertical = user32.GetSystemMetricsForDpi(User32.SM_CYFRAME, dpi) +
                user32.GetSystemMetricsForDpi(User32.SM_CXPADDEDBORDER, dpi)
            // NCCALCSIZE_PARAMS 的首个 RECT：left, top, right, bottom
            val rect = Pointer(lParam)
            rect.setInt(0, rect.getInt(0) + horizontal)
            rect.setInt(4, rect.getInt(4) + vertical)
            rect.setInt(8, rect.getInt(8) - horizontal)
            rect.setInt(12, rect.getInt(12) - vertical)
        } catch (e: LinkageError) {
            AppLogger.w(TAG, "计算最大化边界失败", e)
        }
    }

    // 最大化时取消圆角与边框；borderRgb 为 0xRRGGBB
    override fun applyFrame(window: Window, maximized: Boolean, borderRgb: Int) = withHandle(window) { hwnd ->
        setAttribute(
            hwnd,
            Dwmapi.DWMWA_WINDOW_CORNER_PREFERENCE,
            if (maximized) Dwmapi.DWMWCP_DONOTROUND else Dwmapi.DWMWCP_ROUND
        )
        setAttribute(hwnd, Dwmapi.DWMWA_BORDER_COLOR, if (maximized) Dwmapi.DWMWA_COLOR_NONE else rgbToColorRef(borderRgb))
    }

    // COLORREF 为 0x00BBGGRR
    private fun rgbToColorRef(rgb: Int): Int =
        ((rgb and 0xFF) shl 16) or (rgb and 0xFF00) or ((rgb shr 16) and 0xFF)

    private fun setAttribute(hwnd: Pointer, attribute: Int, value: Int) {
        val hr = Dwmapi.INSTANCE.DwmSetWindowAttribute(hwnd, attribute, IntByReference(value), Int.SIZE_BYTES)
        if (hr != 0) AppLogger.d(TAG, "DwmSetWindowAttribute($attribute) 未生效 hr=0x${Integer.toHexString(hr)}")
    }

    private fun withHandle(window: Window, block: (Pointer) -> Unit) {
        if (!isWindows || !window.isDisplayable) return
        try {
            block(Native.getWindowPointer(window) ?: return)
        } catch (e: RuntimeException) {
            AppLogger.w(TAG, "设置窗口外观失败", e)
        } catch (e: LinkageError) {
            AppLogger.w(TAG, "设置窗口外观失败", e)
        }
    }
}

package com.lin0721.linmusic.desktop.platform.windows

import com.lin0721.linmusic.core.log.AppLogger
import com.lin0721.linmusic.desktop.platform.WindowEdge
import com.lin0721.linmusic.desktop.platform.WindowEffects
import com.lin0721.linmusic.desktop.platform.windows.taskbar.SystemTheme
import com.lin0721.linmusic.desktop.platform.windows.taskbar.isImmersiveColorSetChange
import com.sun.jna.Callback
import com.sun.jna.Native
import com.sun.jna.Pointer
import com.sun.jna.ptr.IntByReference
import java.awt.Point
import java.awt.Window
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList

private const val TAG = "WindowsWindowEffects"

// 窗口过程；须保持强引用，否则回收后原生侧回调会崩溃
private interface WndProc : Callback {
    fun callback(hwnd: Pointer?, msg: Int, wParam: Long, lParam: Long): Long
}

private class Subclass(val proc: WndProc) {
    @Volatile var previous: Pointer? = null

    // 已补齐标题栏样式并接管客户区计算；仅为拖动而替换窗口过程的窗口为 false
    @Volatile var framed = false
}

// 在 AWT 工具线程上同步回调；返回非空即视为已处理并作为窗口过程的结果，返回 null 则继续交给后续处理
internal fun interface WindowMessageHook {
    fun onMessage(hwnd: Pointer, msg: Int, wParam: Long, lParam: Long): Long?
}

// 无边框窗口的系统圆角、边框色与最小化/还原/显示/隐藏动画；非 Windows 11 上调用无副作用
internal class WindowsWindowEffects : WindowEffects {

    private val subclasses = ConcurrentHashMap<Long, Subclass>()
    private val hooks = CopyOnWriteArrayList<WindowMessageHook>()
    private val systemDragMessage by lazy { User32.INSTANCE.RegisterWindowMessageW("Melodia.SystemDrag") }

    fun addMessageHook(hook: WindowMessageHook) {
        hooks.addIfAbsent(hook)
    }

    fun removeMessageHook(hook: WindowMessageHook) {
        hooks.remove(hook)
    }

    override val supportsClickThrough: Boolean = true

    // 系统只给带标题栏样式的窗口播放最小化/还原等动画：补齐样式后拦截 WM_NCCALCSIZE，
    // 让客户区占满整个窗口，外观仍是无边框
    override fun install(window: Window) = withHandle(window) { hwnd ->
        val entry = subclass(hwnd)
        if (entry.framed) return@withHandle
        entry.framed = true
        val user32 = User32.INSTANCE
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

    override fun uninstall(window: Window) = withHandle(window) { hwnd ->
        val entry = subclasses.remove(Pointer.nativeValue(hwnd)) ?: return@withHandle
        User32.INSTANCE.SetWindowLongPtrW(hwnd, User32.GWLP_WNDPROC, entry.previous)
    }

    // 已替换过窗口过程的直接复用
    private fun subclass(hwnd: Pointer): Subclass {
        val key = Pointer.nativeValue(hwnd)
        subclasses[key]?.let { return it }
        val user32 = User32.INSTANCE
        val proc = object : WndProc {
            override fun callback(hwnd: Pointer?, msg: Int, wParam: Long, lParam: Long): Long {
                val entry = subclasses[key]
                val previous = entry?.previous
                if (hwnd != null) {
                    if (msg == User32.WM_NCCALCSIZE && wParam != 0L && entry?.framed == true) {
                        trimMaximizedBounds(hwnd, lParam)
                        return 0
                    }
                    val dragMessage = systemDragMessage
                    if (dragMessage != 0 && msg == dragMessage) {
                        enterSystemLoop(hwnd, wParam)
                        return 0
                    }
                    if (msg == User32.WM_NCDESTROY) subclasses.remove(key)
                    dispatchToHooks(hwnd, msg, wParam, lParam)?.let { return it }
                }
                return user32.CallWindowProcW(previous, hwnd, msg, wParam, lParam)
            }
        }
        // 先登记再替换窗口过程，避免回调早于登记而丢失原过程
        val entry = Subclass(proc)
        subclasses.putIfAbsent(key, entry)?.let { return it }
        entry.previous = user32.SetWindowLongPtrW(hwnd, User32.GWLP_WNDPROC, proc)
        return entry
    }

    // 运行在 AWT 工具线程：按下时 AWT 已在本线程捕获鼠标，不先释放系统循环不会启动
    private fun enterSystemLoop(hwnd: Pointer, command: Long) {
        try {
            if (!isPrimaryButtonDown()) return
            val user32 = User32.INSTANCE
            user32.ReleaseCapture()
            user32.DefWindowProcW(hwnd, User32.WM_SYSCOMMAND, command, 0L)
        } catch (e: RuntimeException) {
            AppLogger.w(TAG, "进入系统拖动循环失败", e)
        } catch (e: LinkageError) {
            AppLogger.w(TAG, "进入系统拖动循环失败", e)
        }
    }

    // 松开后再进入鼠标模式的循环会一直跟随光标
    private fun isPrimaryButtonDown(): Boolean {
        val user32 = User32.INSTANCE
        val button = if (user32.GetSystemMetrics(User32.SM_SWAPBUTTON) != 0) User32.VK_RBUTTON else User32.VK_LBUTTON
        return user32.GetAsyncKeyState(button) < 0
    }

    // 钩子抛出的异常不能穿过原生回调，否则会让整个进程崩溃
    private fun dispatchToHooks(hwnd: Pointer, msg: Int, wParam: Long, lParam: Long): Long? {
        for (hook in hooks) {
            try {
                hook.onMessage(hwnd, msg, wParam, lParam)?.let { return it }
            } catch (e: Exception) {
                AppLogger.w(TAG, "窗口消息钩子执行失败 msg=0x${Integer.toHexString(msg)}", e)
            }
        }
        return null
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

    override fun applyFrame(window: Window, maximized: Boolean, borderRgb: Int) = withHandle(window) { hwnd ->
        setAttribute(
            hwnd,
            Dwmapi.DWMWA_WINDOW_CORNER_PREFERENCE,
            if (maximized) Dwmapi.DWMWCP_DONOTROUND else Dwmapi.DWMWCP_ROUND
        )
        setAttribute(hwnd, Dwmapi.DWMWA_BORDER_COLOR, if (maximized) Dwmapi.DWMWA_COLOR_NONE else rgbToColorRef(borderRgb))
    }

    override fun systemBorderAccent(): Int? = SystemAccent.windowBorderAccent()

    override fun systemAppsUseLightTheme(): Boolean = SystemTheme.appsUseLightTheme()

    override fun observeSystemTheme(onChanged: () -> Unit): () -> Unit {
        val hook = WindowMessageHook { _, msg, wParam, lParam ->
            if (msg == User32.WM_SETTINGCHANGE && isImmersiveColorSetChange(wParam, lParam)) onChanged()
            null
        }
        addMessageHook(hook)
        return { removeMessageHook(hook) }
    }

    // 锁定后加 WS_EX_TRANSPARENT，鼠标事件直接穿透到下层窗口
    override fun setClickThrough(window: Window, enabled: Boolean) = withHandle(window) { hwnd ->
        val user32 = User32.INSTANCE
        val style = user32.GetWindowLongPtrW(hwnd, User32.GWL_EXSTYLE)
        val newStyle = if (enabled) {
            style or User32.WS_EX_TRANSPARENT or User32.WS_EX_LAYERED
        } else {
            style and User32.WS_EX_TRANSPARENT.inv()
        }
        user32.SetWindowLongPtrW(hwnd, User32.GWL_EXSTYLE, newStyle)
    }

    override fun beginSystemMove(window: Window): Boolean =
        postSystemCommand(window, User32.SC_MOVE or User32.HTCAPTION)

    override fun beginSystemResize(window: Window, edge: WindowEdge): Boolean =
        postSystemCommand(window, User32.SC_SIZE or edge.toWmsz())

    override fun cursorPhysicalPosition(): Point? = try {
        val point = WinPoint()
        if (User32.INSTANCE.GetCursorPos(point)) Point(point.x, point.y) else null
    } catch (e: LinkageError) {
        AppLogger.w(TAG, "读取光标位置失败", e)
        null
    }

    // 投递给 AWT 工具线程进入系统移动/缩放循环，EDT 不会被循环阻塞
    private fun postSystemCommand(window: Window, command: Int): Boolean {
        if (!window.isDisplayable) return false
        return try {
            val message = systemDragMessage
            if (message == 0 || !isPrimaryButtonDown()) return false
            val hwnd = Native.getWindowPointer(window) ?: return false
            subclass(hwnd)
            User32.INSTANCE.PostMessageW(hwnd, message, command.toLong(), 0L)
        } catch (e: RuntimeException) {
            AppLogger.w(TAG, "交给系统拖动窗口失败", e)
            false
        } catch (e: LinkageError) {
            AppLogger.w(TAG, "交给系统拖动窗口失败", e)
            false
        }
    }

    private fun WindowEdge.toWmsz(): Int = when (this) {
        WindowEdge.Left -> User32.WMSZ_LEFT
        WindowEdge.Right -> User32.WMSZ_RIGHT
        WindowEdge.Top -> User32.WMSZ_TOP
        WindowEdge.Bottom -> User32.WMSZ_BOTTOM
        WindowEdge.TopLeft -> User32.WMSZ_TOPLEFT
        WindowEdge.TopRight -> User32.WMSZ_TOPRIGHT
        WindowEdge.BottomLeft -> User32.WMSZ_BOTTOMLEFT
        WindowEdge.BottomRight -> User32.WMSZ_BOTTOMRIGHT
    }

    // COLORREF 为 0x00BBGGRR
    private fun rgbToColorRef(rgb: Int): Int =
        ((rgb and 0xFF) shl 16) or (rgb and 0xFF00) or ((rgb shr 16) and 0xFF)

    private fun setAttribute(hwnd: Pointer, attribute: Int, value: Int) {
        val hr = Dwmapi.INSTANCE.DwmSetWindowAttribute(hwnd, attribute, IntByReference(value), Int.SIZE_BYTES)
        if (hr != 0) AppLogger.d(TAG, "DwmSetWindowAttribute($attribute) 未生效 hr=0x${Integer.toHexString(hr)}")
    }

    private fun withHandle(window: Window, block: (Pointer) -> Unit) {
        if (!window.isDisplayable) return
        try {
            block(Native.getWindowPointer(window) ?: return)
        } catch (e: RuntimeException) {
            AppLogger.w(TAG, "设置窗口外观失败", e)
        } catch (e: LinkageError) {
            AppLogger.w(TAG, "设置窗口外观失败", e)
        }
    }
}

package com.lin0721.linmusic.desktop.platform.native.linux.x11

import com.lin0721.linmusic.core.log.AppLogger
import com.sun.jna.Callback
import com.sun.jna.Library
import com.sun.jna.Native
import com.sun.jna.Pointer
import com.sun.jna.Structure
import com.sun.jna.ptr.IntByReference
import com.sun.jna.ptr.LongByReference
import com.sun.jna.ptr.PointerByReference

// X11 输入区域（Shape 扩展）的最小 JNA 绑定。
//
// 原理：窗口的输入区域（ShapeInput）为空时，鼠标事件直接落到下层窗口，即"点击穿透"；
// 恢复时把输入区域设回窗口完整矩形即可。XWayland 与原生 X11 会话都适用；
// Wayland 原生窗口没有对应协议，调用方在无 DISPLAY 时跳过。
internal interface Xlib : Library {
    fun XOpenDisplay(displayName: String?): Pointer?
    fun XInitThreads(): Int
    fun XDefaultRootWindow(display: Pointer): Long
    fun XQueryTree(
        display: Pointer,
        window: Long,
        rootReturn: LongByReference?,
        parentReturn: LongByReference?,
        childrenReturn: PointerByReference?,
        nChildrenReturn: IntByReference?
    ): Int

    fun XFree(data: Pointer?): Int
    fun XInternAtom(display: Pointer, atomName: String, onlyIfExists: Int): Long
    fun XGetWindowProperty(
        display: Pointer,
        window: Long,
        property: Long,
        offset: Long,
        length: Long,
        delete: Int,
        reqType: Long,
        actualTypeReturn: LongByReference?,
        actualFormatReturn: IntByReference?,
        nItemsReturn: LongByReference?,
        bytesAfterReturn: LongByReference?,
        propReturn: PointerByReference?
    ): Int

    fun XGetGeometry(
        display: Pointer,
        drawable: Long,
        rootReturn: LongByReference?,
        xReturn: IntByReference?,
        yReturn: IntByReference?,
        widthReturn: IntByReference?,
        heightReturn: IntByReference?,
        borderWidthReturn: IntByReference?,
        depthReturn: IntByReference?
    ): Int

    fun XSetErrorHandler(handler: XErrorHandler?): Pointer?
    fun XSync(display: Pointer, discard: Int)
}

// Xlib 默认错误处理会直接结束进程，必须替换成忽略实现
internal interface XErrorHandler : Callback {
    fun callback(display: Pointer?, event: Pointer?): Int
}

internal interface Xext : Library {
    fun XShapeCombineRectangles(
        display: Pointer,
        window: Long,
        kind: Int,
        xOffset: Int,
        yOffset: Int,
        rectangles: Pointer?,
        nRectangles: Int,
        operation: Int,
        ordering: Int
    )
}

@Structure.FieldOrder("x", "y", "width", "height")
internal class XRectangle : Structure() {
    @JvmField var x: Short = 0
    @JvmField var y: Short = 0
    @JvmField var width: Short = 0
    @JvmField var height: Short = 0
}

// 会话级封装：自己开一条 X 显示连接，所有调用串行化（不与 AWT 的连接混用）
internal object X11InputShape {

    private const val TAG = "X11InputShape"

    private const val SHAPE_INPUT = 2
    private const val SHAPE_SET = 0
    private const val SHAPE_UNSORTED = 0

    // 按标题找窗口时往下钻的层数（KWin 等 WM 会把窗口重父化到框架窗口里）
    private const val TITLE_SEARCH_DEPTH = 2
    private const val MAX_ANCESTOR_DEPTH = 8

    private val lock = Any()

    private val xlib: Xlib? by lazy {
        loadLibrary(Xlib::class.java, "X11")
            .onFailure { AppLogger.w(TAG, "加载 libX11 失败", it) }
            .getOrNull()
    }

    private val xext: Xext? by lazy {
        loadLibrary(Xext::class.java, "Xext")
            .onFailure { AppLogger.w(TAG, "加载 libXext 失败", it) }
            .getOrNull()
    }

    private val errorHandler = object : XErrorHandler {
        override fun callback(display: Pointer?, event: Pointer?): Int = 0
    }

    private var display: Pointer? = null
    private var atoms: Atoms? = null

    // 返回被处理的窗口数；0 表示没找到目标窗口（调用方可重试）
    fun setClickThrough(title: String, enabled: Boolean): Int = synchronized(lock) {
        val lib = xlib ?: return 0
        val ext = xext ?: return 0
        val dpy = display ?: open(lib) ?: return 0
        val atom = atoms ?: return 0

        val chains = findWindowChains(lib, dpy, atom, title)
        if (chains.isEmpty()) return 0

        for (window in chains.flatten().distinct()) {
            if (enabled) {
                // 空矩形列表 = 清空输入区域
                ext.XShapeCombineRectangles(dpy, window, SHAPE_INPUT, 0, 0, null, 0, SHAPE_SET, SHAPE_UNSORTED)
            } else {
                val rect = fullInputRect(lib, dpy, window)
                ext.XShapeCombineRectangles(dpy, window, SHAPE_INPUT, 0, 0, rect, 1, SHAPE_SET, SHAPE_UNSORTED)
            }
        }
        lib.XSync(dpy, 0)
        chains.sumOf { it.size }
    }

    private fun open(lib: Xlib): Pointer? {
        lib.XInitThreads()
        val dpy = lib.XOpenDisplay(null) ?: run {
            AppLogger.w(TAG, "XOpenDisplay 失败（无 X11 显示）")
            return null
        }
        lib.XSetErrorHandler(errorHandler)
        atoms = Atoms(
            netWmName = lib.XInternAtom(dpy, "_NET_WM_NAME", 0),
            utf8String = lib.XInternAtom(dpy, "UTF8_STRING", 0),
            wmName = lib.XInternAtom(dpy, "WM_NAME", 0),
        )
        display = dpy
        lib.XSync(dpy, 0)
        return dpy
    }

    // 恢复输入区域：窗口完整矩形（拿不到几何时用足够大的矩形兜底）
    private fun fullInputRect(lib: Xlib, dpy: Pointer, window: Long): Pointer {
        // 注意：libX11 对出参传 null 会段错误，这里全部传真实引用
        val root = LongByReference(0)
        val x = IntByReference(0)
        val y = IntByReference(0)
        val width = IntByReference(0)
        val height = IntByReference(0)
        val border = IntByReference(0)
        val depth = IntByReference(0)
        val ok = lib.XGetGeometry(dpy, window, root, x, y, width, height, border, depth)
        val w = if (ok != 0 && width.value > 0) width.value else Short.MAX_VALUE.toInt()
        val h = if (ok != 0 && height.value > 0) height.value else Short.MAX_VALUE.toInt()
        val rect = XRectangle().apply {
            this.x = 0
            this.y = 0
            this.width = w.coerceAtMost(Short.MAX_VALUE.toInt()).toShort()
            this.height = h.coerceAtMost(Short.MAX_VALUE.toInt()).toShort()
        }
        rect.write()
        return rect.pointer
    }

    private fun findWindowChains(lib: Xlib, dpy: Pointer, atom: Atoms, title: String): List<List<Long>> {
        val root = lib.XDefaultRootWindow(dpy)
        val matches = mutableListOf<Long>()
        collectByTitle(lib, dpy, atom, root, title, 0, matches)
        return matches.map { windowWithAncestors(lib, dpy, root, it) }
    }

    private fun collectByTitle(lib: Xlib, dpy: Pointer, atom: Atoms, parent: Long, title: String, depth: Int, out: MutableList<Long>) {
        if (depth > TITLE_SEARCH_DEPTH) return
        for (child in childrenOf(lib, dpy, parent)) {
            if (windowTitle(lib, dpy, atom, child) == title) out += child
            collectByTitle(lib, dpy, atom, child, title, depth + 1, out)
        }
    }

    private fun childrenOf(lib: Xlib, dpy: Pointer, window: Long): LongArray {
        val children = PointerByReference()
        val count = IntByReference(0)
        // root/parent 出参不能传 null：libX11 会直接写指针，实测传 null 会段错误
        val root = LongByReference(0)
        val parent = LongByReference(0)
        val ok = lib.XQueryTree(dpy, window, root, parent, children, count)
        if (ok == 0) return LongArray(0)
        val pointer = children.value ?: return LongArray(0)
        return try {
            val n = count.value
            if (n <= 0) LongArray(0) else pointer.getLongArray(0, n)
        } finally {
            lib.XFree(pointer)
        }
    }

    private fun parentOf(lib: Xlib, dpy: Pointer, window: Long): Long? {
        val root = LongByReference(0)
        val parent = LongByReference(0)
        val children = PointerByReference()
        val count = IntByReference(0)
        val ok = lib.XQueryTree(dpy, window, root, parent, children, count)
        if (ok == 0) return null
        children.value?.let { lib.XFree(it) }
        return parent.value
    }

    // 客户端窗口 + 其祖先链：只处理客户端窗口时，点击仍会落在框架窗口上
    private fun windowWithAncestors(lib: Xlib, dpy: Pointer, root: Long, window: Long): List<Long> {
        val chain = mutableListOf(window)
        var current = window
        var depth = 0
        while (depth++ < MAX_ANCESTOR_DEPTH) {
            val parent = parentOf(lib, dpy, current) ?: break
            if (parent == 0L || parent == root) break
            chain += parent
            current = parent
        }
        return chain
    }

    private fun windowTitle(lib: Xlib, dpy: Pointer, atom: Atoms, window: Long): String? =
        readStringProperty(lib, dpy, window, atom.netWmName, atom.utf8String)
            ?: readStringProperty(lib, dpy, window, atom.wmName, 0)

    private fun readStringProperty(lib: Xlib, dpy: Pointer, window: Long, property: Long, requiredType: Long): String? {
        if (property == 0L) return null
        val type = LongByReference(0)
        val format = IntByReference(0)
        val count = LongByReference(0)
        val after = LongByReference(0)
        val data = PointerByReference()
        val status = lib.XGetWindowProperty(dpy, window, property, 0, 1024, 0, 0, type, format, count, after, data)
        if (status != 0) return null
        val pointer = data.value ?: return null
        return try {
            val bytes = count.value.toInt()
            if (bytes <= 0 || format.value != 8) return null
            if (requiredType != 0L && type.value != requiredType) return null
            String(pointer.getByteArray(0, bytes), Charsets.UTF_8)
        } finally {
            lib.XFree(pointer)
        }
    }

    private fun <T : Library> loadLibrary(interfaceClass: Class<T>, name: String): Result<T> =
        runCatching { Native.load(name, interfaceClass) }
            .recoverCatching { Native.load("lib$name.so.6", interfaceClass) }

    private data class Atoms(val netWmName: Long, val utf8String: Long, val wmName: Long)
}

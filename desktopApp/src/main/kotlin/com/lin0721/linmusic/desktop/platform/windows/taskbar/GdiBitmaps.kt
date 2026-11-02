package com.lin0721.linmusic.desktop.platform.windows.taskbar

import com.lin0721.linmusic.core.log.AppLogger
import com.lin0721.linmusic.desktop.platform.windows.Gdi32
import com.lin0721.linmusic.desktop.platform.windows.User32
import com.sun.jna.Memory
import com.sun.jna.Pointer
import com.sun.jna.ptr.PointerByReference

private const val TAG = "GdiBitmaps"

private const val BITMAP_INFO_HEADER_SIZE = 40
private const val BITS_PER_PIXEL = 32

// ICONINFO 按 64 位布局：fIcon@0，hbmMask@16，hbmColor@24
private const val ICON_INFO_SIZE = 32L
private const val ICON_INFO_MASK_OFFSET = 16L
private const val ICON_INFO_COLOR_OFFSET = 24L

// 缩略图位图与工具栏图标所需的 GDI 对象
internal object GdiBitmaps {

    // 1x1 全透明位图，用于静默响应悬停整窗预览；调用方负责 delete
    fun createEmptyDib(): Pointer? = createDib(1, 1, intArrayOf(0))

    // 自上而下的 32 位 DIB，像素按 ARGB 的 Int 写入（即内存中的 BGRA）；调用方负责 delete
    fun createDib(width: Int, height: Int, argb: IntArray): Pointer? {
        if (width <= 0 || height <= 0 || argb.size != width * height) return null
        return try {
            val header = Memory(BITMAP_INFO_HEADER_SIZE.toLong()).apply {
                clear()
                setInt(0, BITMAP_INFO_HEADER_SIZE)
                setInt(4, width)
                setInt(8, -height)
                setShort(12, 1)
                setShort(14, BITS_PER_PIXEL.toShort())
            }
            val bits = PointerByReference()
            val bitmap = Gdi32.INSTANCE.CreateDIBSection(null, header, 0, bits, null, 0) ?: return null
            val target = bits.value
            if (target == null) {
                Gdi32.INSTANCE.DeleteObject(bitmap)
                return null
            }
            target.write(0L, argb, 0, argb.size)
            bitmap
        } catch (e: LinkageError) {
            AppLogger.w(TAG, "创建位图失败", e)
            null
        }
    }

    // 预乘 alpha 的 ARGB 像素转为 GDI 图标；调用方负责 DestroyIcon
    fun createIcon(size: Int, premultipliedArgb: IntArray): Pointer? {
        val color = createDib(size, size, premultipliedArgb) ?: return null
        var mask: Pointer? = null
        return try {
            val maskStride = ((size + 15) / 16) * 2
            val maskBits = Memory((maskStride * size).toLong()).apply { clear() }
            mask = Gdi32.INSTANCE.CreateBitmap(size, size, 1, 1, maskBits) ?: return null
            val info = Memory(ICON_INFO_SIZE).apply {
                clear()
                setInt(0, 1)
                setPointer(ICON_INFO_MASK_OFFSET, mask)
                setPointer(ICON_INFO_COLOR_OFFSET, color)
            }
            User32.INSTANCE.CreateIconIndirect(info)
        } catch (e: LinkageError) {
            AppLogger.w(TAG, "创建图标失败", e)
            null
        } finally {
            // 图标会复制位图数据，原位图用完即释放
            mask?.let { Gdi32.INSTANCE.DeleteObject(it) }
            Gdi32.INSTANCE.DeleteObject(color)
        }
    }

    fun delete(bitmap: Pointer) {
        runCatching { Gdi32.INSTANCE.DeleteObject(bitmap) }
    }

    fun destroyIcon(icon: Pointer) {
        runCatching { User32.INSTANCE.DestroyIcon(icon) }
    }
}

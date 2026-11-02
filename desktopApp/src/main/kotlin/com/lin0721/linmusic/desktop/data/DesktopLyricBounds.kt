package com.lin0721.linmusic.desktop.data

// 桌面歌词窗口的左上角与宽度（dp）；高度随单行/双行自适应，不保存
data class DesktopLyricBounds(val x: Int, val y: Int, val width: Int) {

    fun encode(): String = "$x,$y,$width"

    companion object {
        const val MIN_WIDTH = 360
        const val MAX_WIDTH = 2400
        const val DEFAULT_WIDTH = 1000

        fun decode(raw: String): DesktopLyricBounds? {
            val parts = raw.split(',').map { it.trim().toIntOrNull() ?: return null }
            if (parts.size != 3) return null
            val (x, y, width) = parts
            if (width !in MIN_WIDTH..MAX_WIDTH) return null
            return DesktopLyricBounds(x, y, width)
        }
    }
}

package com.lin0721.linmusic.desktop.data

import com.lin0721.linmusic.core.player.floatinglyric.FloatingLyricAlign
import com.lin0721.linmusic.core.player.floatinglyric.FloatingLyricSecondary
import com.lin0721.linmusic.desktop.app.WindowBounds
import kotlin.math.roundToInt

// 迷你播放器样式：默认布局尺寸（dp）与可拖动改变的边；方卡两者都不能改以保持正方形
enum class MiniPlayerStyle(
    val label: String,
    val width: Int,
    val height: Int,
    val resizableWidth: Boolean,
    val resizableHeight: Boolean
) {
    BAR("横条", 360, 72, resizableWidth = true, resizableHeight = false),
    CARD("方卡", 240, 240, resizableWidth = false, resizableHeight = false),
    LYRIC("歌词卡", 360, 220, resizableWidth = true, resizableHeight = true);

    val defaultSize: MiniPlayerSize get() = MiniPlayerSize(width, height, 1f)
    val minWidth: Int get() = if (resizableWidth) (width * MiniPlayerSize.MIN_FACTOR).roundToInt() else width
    val maxWidth: Int get() = if (resizableWidth) MiniPlayerSize.MAX_LENGTH else width
    val minHeight: Int get() = if (resizableHeight) (height * MiniPlayerSize.MIN_FACTOR).roundToInt() else height
    val maxHeight: Int get() = if (resizableHeight) MiniPlayerSize.MAX_LENGTH else height

    fun next(): MiniPlayerStyle = entries[(ordinal + 1) % entries.size]

    companion object {
        val DEFAULT = BAR

        fun fromName(name: String?): MiniPlayerStyle = entries.firstOrNull { it.name == name } ?: DEFAULT
    }
}

// 歌词卡当前行字号
enum class MiniLyricFontSize(val label: String, val sp: Int) {
    SMALL("小", 14),
    MEDIUM("中", 16),
    LARGE("大", 18);

    companion object {
        fun fromName(name: String?): MiniLyricFontSize = entries.firstOrNull { it.name == name } ?: MEDIUM
    }
}

// 歌词卡设置，与桌面歌词各存一份
data class MiniLyricSettings(
    val fontSize: MiniLyricFontSize = MiniLyricFontSize.MEDIUM,
    val secondary: FloatingLyricSecondary = FloatingLyricSecondary.TRANSLATION,
    val karaoke: Boolean = true,
    val showSideLines: Boolean = true,
    val align: FloatingLyricAlign = FloatingLyricAlign.START
) {
    companion object {
        // 上下行已单独控制，第二行不提供“下一句”
        val SecondaryOptions = listOf(FloatingLyricSecondary.TRANSLATION, FloatingLyricSecondary.ROMA, FloatingLyricSecondary.NONE)

        fun secondaryFromKey(key: String?): FloatingLyricSecondary =
            FloatingLyricSecondary.fromKey(key).takeIf { it in SecondaryOptions } ?: MiniLyricSettings().secondary

        // 与桌面歌词默认居中不同，迷你窗默认左对齐
        fun alignFromKey(key: String?): FloatingLyricAlign =
            FloatingLyricAlign.entries.firstOrNull { it.key == key } ?: MiniLyricSettings().align
    }
}

// width/height 为布局尺寸（dp），scale 为右下角拖出的等比缩放；窗口实际大小再乘界面缩放
data class MiniPlayerSize(val width: Int, val height: Int, val scale: Float) {

    fun encode(): String = "$width,$height,$scale"

    fun constrainedTo(style: MiniPlayerStyle): MiniPlayerSize = MiniPlayerSize(
        width.coerceIn(style.minWidth, style.maxWidth),
        height.coerceIn(style.minHeight, style.maxHeight),
        scale.coerceIn(MIN_SCALE, MAX_SCALE)
    )

    companion object {
        const val MIN_FACTOR = 0.8f
        const val MIN_SCALE = 0.75f

        // 不设使用上限，只防存坏的值撑出离谱的窗口
        const val MAX_LENGTH = 10_000
        const val MAX_SCALE = 20f

        fun decode(raw: String, style: MiniPlayerStyle): MiniPlayerSize? {
            val parts = raw.split(',').map { it.trim() }
            if (parts.size != 3) return null
            val width = parts[0].toIntOrNull() ?: return null
            val height = parts[1].toIntOrNull() ?: return null
            val scale = parts[2].toFloatOrNull()?.takeIf { it.isFinite() } ?: return null
            return MiniPlayerSize(width, height, scale).constrainedTo(style)
        }
    }
}

// 迷你窗左上角（dp）
data class MiniPlayerPosition(val x: Int, val y: Int) {

    fun encode(): String = "$x,$y"

    // 窗口比区域还大时贴左上角
    fun fitInto(width: Int, height: Int, area: WindowBounds): MiniPlayerPosition = MiniPlayerPosition(
        x.coerceAtMost(area.x + area.width - width).coerceAtLeast(area.x),
        y.coerceAtMost(area.y + area.height - height).coerceAtLeast(area.y)
    )

    companion object {
        fun decode(raw: String): MiniPlayerPosition? {
            val parts = raw.split(',').map { it.trim().toIntOrNull() ?: return null }
            if (parts.size != 2) return null
            return MiniPlayerPosition(parts[0], parts[1])
        }
    }
}

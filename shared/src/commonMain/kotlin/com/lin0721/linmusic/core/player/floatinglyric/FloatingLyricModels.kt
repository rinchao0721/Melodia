package com.lin0721.linmusic.core.player.floatinglyric

import com.lin0721.linmusic.core.player.NowPlaying
import com.lin0721.linmusic.core.player.domain.LyricLine

// 第二行显示内容，key 用于偏好持久化
enum class FloatingLyricSecondary(val key: String, val label: String) {
    NEXT("next", "下一句"),
    TRANSLATION("translation", "翻译"),
    ROMA("roma", "罗马音"),
    NONE("none", "不显示");

    companion object {
        fun fromKey(key: String?): FloatingLyricSecondary = entries.firstOrNull { it.key == key } ?: TRANSLATION
    }
}

enum class FloatingLyricAlign(val key: String, val label: String) {
    START("start", "左对齐"),
    CENTER("center", "居中"),
    END("end", "右对齐");

    companion object {
        fun fromKey(key: String?): FloatingLyricAlign = entries.firstOrNull { it.key == key } ?: CENTER
    }
}

// 两端各自从偏好组装，渲染层只认这一份
data class FloatingLyricStyle(
    val fontSizeSp: Int = 18,
    val playedColor: String = FloatingLyricPalette.DEFAULT_PLAYED,
    val unplayedColor: String = FloatingLyricPalette.DEFAULT_UNPLAYED,
    val shadow: Boolean = true,
    val backgroundAlpha: Int = 0,
    val align: FloatingLyricAlign = FloatingLyricAlign.CENTER,
    val secondary: FloatingLyricSecondary = FloatingLyricSecondary.TRANSLATION,
    val bold: Boolean = true,
    // 阴影强度为不透明度百分比，模糊半径单位 dp，颜色只取 RGB
    val shadowAlpha: Int = FloatingLyricShadow.DEFAULT_ALPHA,
    val shadowBlurDp: Int = FloatingLyricShadow.DEFAULT_BLUR_DP,
    val shadowColor: String = FloatingLyricShadow.DEFAULT_COLOR
) {
    // 关闭阴影时为 null；颜色非法时回退黑色
    fun shadowArgb(): Long? {
        if (!shadow) return null
        val rgb = (FloatingLyricPalette.parseArgb(shadowColor) ?: 0xFF000000L) and 0xFFFFFFL
        val alpha = (shadowAlpha.coerceIn(0, 100) * 255 + 50) / 100
        return (alpha.toLong() shl 24) or rgb
    }
}

object FloatingLyricShadow {
    const val DEFAULT_ALPHA = 80
    const val DEFAULT_BLUR_DP = 4
    const val DEFAULT_COLOR = "#000000"

    // 偏移不开放调节，固定向下错开
    const val OFFSET_Y_DP = 1.5f

    val alphas: List<Int> = listOf(20, 40, 60, 80, 100)
    val blursDp: List<Int> = listOf(0, 2, 4, 8, 12)

    val colors: List<FloatingLyricColorOption> = listOf(
        FloatingLyricColorOption("#000000", "黑色"),
        FloatingLyricColorOption("#4B5563", "深灰"),
        FloatingLyricColorOption("#FFFFFF", "白色"),
        FloatingLyricColorOption("#E03E3E", "网易红"),
        FloatingLyricColorOption("#3B82F6", "蓝色"),
        FloatingLyricColorOption("#8B5CF6", "紫色")
    )

    fun blurLabel(blurDp: Int): String = if (blurDp == 0) "锐利" else "$blurDp dp"
}

data class FloatingLyricColorOption(val hex: String, val label: String)

object FloatingLyricPalette {
    const val DEFAULT_PLAYED = "#FFFFFF"
    const val DEFAULT_UNPLAYED = "#99FFFFFF"

    val played: List<FloatingLyricColorOption> = listOf(
        FloatingLyricColorOption("#FFFFFF", "白色"),
        FloatingLyricColorOption("#E03E3E", "网易红"),
        FloatingLyricColorOption("#10B981", "绿色"),
        FloatingLyricColorOption("#3B82F6", "蓝色"),
        FloatingLyricColorOption("#F59E0B", "琥珀"),
        FloatingLyricColorOption("#EC4899", "粉色"),
        FloatingLyricColorOption("#8B5CF6", "紫色"),
        FloatingLyricColorOption("#22D3EE", "青色")
    )

    val unplayed: List<FloatingLyricColorOption> = listOf(
        FloatingLyricColorOption("#99FFFFFF", "半透明白"),
        FloatingLyricColorOption("#FFFFFF", "白色"),
        FloatingLyricColorOption("#9CA3AF", "灰色"),
        FloatingLyricColorOption("#4B5563", "深灰"),
        FloatingLyricColorOption("#000000", "黑色")
    )

    fun labelOf(hex: String, options: List<FloatingLyricColorOption>): String =
        options.firstOrNull { it.hex.equals(hex, ignoreCase = true) }?.label ?: hex

    // 支持 #RRGGBB 与 #AARRGGBB，返回 ARGB；非法值返回 null
    fun parseArgb(hex: String): Long? {
        val body = hex.trim().removePrefix("#")
        val value = body.toLongOrNull(16) ?: return null
        return when (body.length) {
            6 -> 0xFF000000L or value
            8 -> value
            else -> null
        }
    }
}

// 悬浮歌词某一时刻要显示的内容
data class FloatingLyricFrame(
    // 为 null 时显示 fallback（无歌词、前奏、间奏空行）
    val primary: LyricLine?,
    val secondary: String?,
    val fallback: String
)

object FloatingLyricFrames {
    const val IDLE_TEXT = "Melodia 桌面歌词"

    fun of(
        lines: List<LyricLine>,
        primaryIndex: Int,
        nowPlaying: NowPlaying?,
        mode: FloatingLyricSecondary
    ): FloatingLyricFrame {
        val fallback = nowPlaying?.let { titleOf(it) } ?: IDLE_TEXT
        if (nowPlaying == null || lines.isEmpty()) return FloatingLyricFrame(null, null, fallback)

        val primary = lines.getOrNull(primaryIndex)?.takeIf { it.text.isNotBlank() }
        val secondary = when (mode) {
            FloatingLyricSecondary.NONE -> null
            FloatingLyricSecondary.TRANSLATION -> primary?.translation
            FloatingLyricSecondary.ROMA -> primary?.roma
            FloatingLyricSecondary.NEXT -> nextTextAfter(lines, primaryIndex)
        }?.takeIf { it.isNotBlank() }
        return FloatingLyricFrame(primary, secondary, fallback)
    }

    private fun titleOf(track: NowPlaying): String =
        if (track.artist.isBlank()) track.title else "${track.title} - ${track.artist}"

    // 跳过空行，前奏阶段（index = -1）给出第一句
    private fun nextTextAfter(lines: List<LyricLine>, index: Int): String? {
        for (i in (index + 1).coerceAtLeast(0) until lines.size) {
            val text = lines[i].text
            if (text.isNotBlank()) return text
        }
        return null
    }
}

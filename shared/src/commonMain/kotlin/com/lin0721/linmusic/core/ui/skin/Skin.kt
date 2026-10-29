package com.lin0721.linmusic.core.ui.skin

import androidx.compose.ui.graphics.Color
import com.lin0721.linmusic.core.ui.theme.MelodiaStyle
import kotlinx.serialization.json.JsonObject
import java.io.File

const val SKIN_FORMAT_VERSION = 1
const val BUILTIN_SKIN_ID = "builtin"

enum class SkinMode(val key: String) {
    DARK("dark"),
    LIGHT("light");

    companion object {
        fun fromKey(key: String): SkinMode? = entries.firstOrNull { it.key == key }
    }
}

enum class SkinBackgroundScope(val key: String) {
    APP("app"),
    PLAYER("player");

    companion object {
        fun fromKey(key: String): SkinBackgroundScope? = entries.firstOrNull { it.key == key }
    }
}

// image 为皮肤包内相对路径
data class SkinBackground(
    val image: String,
    val blur: Float,
    val dim: Float,
    val paneOpacity: Float,
    val scope: SkinBackgroundScope
)

// colors 只含皮肤写了的色板项，其余沿用内置色板；layouts 原样保留，由播放器布局解析
data class Skin(
    val id: String,
    val name: String,
    val author: String?,
    val version: String?,
    val modes: Set<SkinMode>,
    val darkColors: Map<PaletteToken, Color>,
    val lightColors: Map<PaletteToken, Color>,
    val background: SkinBackground?,
    val style: MelodiaStyle,
    val layouts: JsonObject?,
    val directory: File? = null
) {
    fun colors(isDark: Boolean): Map<PaletteToken, Color> = if (isDark) darkColors else lightColors

    // 只支持一种模式的皮肤锁定在该模式
    fun effectiveDark(requestedDark: Boolean): Boolean = when {
        modes.size != 1 -> requestedDark
        else -> modes.first() == SkinMode.DARK
    }

    fun assetFile(relativePath: String): File? = directory?.let { File(it, relativePath) }?.takeIf { it.isFile }
}

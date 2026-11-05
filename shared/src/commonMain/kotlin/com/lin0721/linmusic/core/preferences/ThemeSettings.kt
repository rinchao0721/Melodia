package com.lin0721.linmusic.core.preferences

import com.lin0721.linmusic.core.ui.skin.BUILTIN_SKIN_ID
import com.lin0721.linmusic.core.ui.theme.ThemeAccents

// 主题模式；持久化用 key，未知值回退深色
enum class ThemeMode(val key: String) {
    DARK("dark"),
    LIGHT("light"),
    SYSTEM("system");

    fun isDark(systemDark: Boolean): Boolean = when (this) {
        DARK -> true
        LIGHT -> false
        SYSTEM -> systemDark
    }

    companion object {
        fun fromKey(key: String?): ThemeMode = entries.firstOrNull { it.key == key } ?: DARK
    }
}

// 深浅两种模式各自记住主色选择、自定义色与皮肤
data class ThemeSettings(
    val mode: ThemeMode = ThemeMode.DARK,
    val darkAccentId: String = ThemeAccents.DEFAULT_ID,
    val lightAccentId: String = ThemeAccents.DEFAULT_ID,
    val darkCustomHex: String? = null,
    val lightCustomHex: String? = null,
    val darkSkinId: String = BUILTIN_SKIN_ID,
    val lightSkinId: String = BUILTIN_SKIN_ID
) {
    fun accentId(isDark: Boolean): String = if (isDark) darkAccentId else lightAccentId

    fun skinId(isDark: Boolean): String = if (isDark) darkSkinId else lightSkinId

    fun customHex(isDark: Boolean): String? = if (isDark) darkCustomHex else lightCustomHex
}

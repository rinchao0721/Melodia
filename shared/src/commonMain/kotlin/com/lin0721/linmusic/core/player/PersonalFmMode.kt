package com.lin0721.linmusic.core.player

import kotlinx.serialization.Serializable

// 私人FM模式枚举；场景推荐共用 mode="SCENE_RCMD"，以 subMode 区分
// 三个配色为 ARGB：base 为卡片底色，light / dark 为极光光团的同色系深浅
@Serializable
enum class PersonalFmMode(
    val mode: String,
    val subMode: String?,
    val displayName: String,
    val description: String,
    val baseColor: Long,
    val lightColor: Long,
    val darkColor: Long
) {
    DEFAULT("DEFAULT", null, "默认", "按你的口味", 0xFFA54660, 0xFFD9768F, 0xFF7D3049),
    FAMILIAR("FAMILIAR", null, "熟悉", "多放喜欢过的", 0xFFA8523F, 0xFFE08B70, 0xFF6D2C1F),
    EXPLORE("EXPLORE", null, "探索", "发现新歌", 0xFF2F7D73, 0xFF5FB3A6, 0xFF1D5750),
    EXERCISE("SCENE_RCMD", "EXERCISE", "运动", "高能节奏", 0xFFB0632A, 0xFFE6A15C, 0xFF6E3510),
    FOCUS("SCENE_RCMD", "FOCUS", "专注", "安静不打扰", 0xFF3D6C96, 0xFF79A7CF, 0xFF1F4366),
    NIGHT_EMO("SCENE_RCMD", "NIGHT_EMO", "夜晚emo", "深夜情绪", 0xFF4E3F86, 0xFF8A72C9, 0xFF2A1F55);

    val key: String get() = subMode ?: mode

    val isScene: Boolean get() = subMode != null

    companion object {
        fun fromKey(key: String?): PersonalFmMode =
            entries.firstOrNull { it.key == key } ?: DEFAULT
    }
}

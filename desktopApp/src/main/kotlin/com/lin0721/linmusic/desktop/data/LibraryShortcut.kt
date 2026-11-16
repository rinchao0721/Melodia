package com.lin0721.linmusic.desktop.data

// 音乐库侧栏快捷入口枚举，顺序同时作为默认排列顺序
enum class LibraryShortcut(
    val key: String,
    val title: String,
    val defaultVisible: Boolean
) {
    PERSONAL_FM("personal_fm", "私人FM", defaultVisible = true),
    INTELLIGENCE("intelligence", "心动模式", defaultVisible = false),
    SIMILAR_ROAMING("similar_roaming", "相似歌曲漫游", defaultVisible = false),
    DAILY_RECOMMEND("daily_recommend", "每日推荐", defaultVisible = true),
    CLOUD("cloud", "云盘", defaultVisible = false),
    LOCAL_MUSIC("local_music", "本地音乐", defaultVisible = false);

    companion object {
        fun fromKey(key: String): LibraryShortcut? = entries.firstOrNull { it.key == key }
    }
}

data class LibraryShortcutSetting(
    val item: LibraryShortcut,
    val visible: Boolean
)

// 音乐库快捷入口布局编码与向后兼容解析："personal_fm:1,intelligence:0,..."
object LibraryShortcutLayout {
    val DEFAULT: List<LibraryShortcutSetting> =
        LibraryShortcut.entries.map { LibraryShortcutSetting(it, it.defaultVisible) }

    fun encode(layout: List<LibraryShortcutSetting>): String =
        normalize(layout).joinToString(",") { "${it.item.key}:${if (it.visible) 1 else 0}" }

    fun decode(raw: String?): List<LibraryShortcutSetting> {
        if (raw.isNullOrBlank()) return DEFAULT
        val parsed = raw.split(",").mapNotNull { token ->
            val parts = token.trim().split(":", limit = 2)
            val item = LibraryShortcut.fromKey(parts[0].trim()) ?: return@mapNotNull null
            LibraryShortcutSetting(item, visible = parts.getOrNull(1)?.trim() == "1")
        }
        return normalize(parsed)
    }

    // 去重并按各入口默认值补齐缺失项，忽略未知项已在 decode 中完成
    fun normalize(layout: List<LibraryShortcutSetting>): List<LibraryShortcutSetting> {
        val distinct = layout.distinctBy { it.item }
        val missing = LibraryShortcut.entries
            .filter { item -> distinct.none { it.item == item } }
            .map { LibraryShortcutSetting(it, it.defaultVisible) }
        return distinct + missing
    }

    fun setVisible(
        layout: List<LibraryShortcutSetting>,
        item: LibraryShortcut,
        visible: Boolean
    ): List<LibraryShortcutSetting> =
        normalize(layout).map { if (it.item == item) it.copy(visible = visible) else it }

    fun toggleVisible(
        layout: List<LibraryShortcutSetting>,
        item: LibraryShortcut
    ): List<LibraryShortcutSetting> =
        normalize(layout).map { if (it.item == item) it.copy(visible = !it.visible) else it }
}

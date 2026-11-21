package com.lin0721.linmusic.core.preferences

// 移动端及大屏底部导航栏候选项目
enum class BottomBarItem(val key: String, val title: String, val isRoute: Boolean) {
    HOME("home", "主页", true),
    SEARCH("search", "搜索", true),
    LIBRARY("library", "音乐库", true),
    LOCAL_MUSIC("local_music", "本地音乐", true),
    DOWNLOADS("downloads", "下载管理", true),
    CLOUD("cloud", "我的云盘", true),
    PROFILE("profile", "个人主页", true);

    companion object {
        fun fromKey(key: String): BottomBarItem? = when (key) {
            "account" -> PROFILE
            else -> entries.firstOrNull { it.key == key }
        }
    }
}

data class BottomBarItemSetting(
    val item: BottomBarItem,
    val visible: Boolean
)

// 底栏布局持久化与校验逻辑：`home:1,search:1,library:1,local_music:0,...`
object BottomBarLayout {
    const val MIN_VISIBLE_ITEMS = 2
    const val MAX_VISIBLE_ITEMS = 5

    val DEFAULT: List<BottomBarItemSetting> = listOf(
        BottomBarItemSetting(BottomBarItem.HOME, true),
        BottomBarItemSetting(BottomBarItem.SEARCH, true),
        BottomBarItemSetting(BottomBarItem.LIBRARY, true),
        BottomBarItemSetting(BottomBarItem.LOCAL_MUSIC, false),
        BottomBarItemSetting(BottomBarItem.DOWNLOADS, false),
        BottomBarItemSetting(BottomBarItem.CLOUD, false),
        BottomBarItemSetting(BottomBarItem.PROFILE, false)
    )

    fun encode(layout: List<BottomBarItemSetting>): String =
        normalize(layout).joinToString(",") { "${it.item.key}:${if (it.visible) 1 else 0}" }

    fun decode(raw: String?): List<BottomBarItemSetting> {
        if (raw.isNullOrBlank()) {
            return DEFAULT
        }
        val parsed = raw.split(",").mapNotNull { token ->
            val parts = token.trim().split(":", limit = 2)
            val item = BottomBarItem.fromKey(parts[0].trim()) ?: return@mapNotNull null
            BottomBarItemSetting(item, visible = parts.getOrNull(1)?.trim() == "1")
        }
        return normalize(parsed)
    }

    // 去重、补齐缺失项并修正可见数量上下限
    fun normalize(layout: List<BottomBarItemSetting>): List<BottomBarItemSetting> {
        val distinct = layout.distinctBy { it.item }
        val missing = BottomBarItem.entries
            .filter { item -> distinct.none { it.item == item } }
            .map { BottomBarItemSetting(it, visible = false) }
        val merged = distinct + missing

        var visibleCount = 0
        val bounded = merged.map { setting ->
            if (setting.visible) {
                if (visibleCount < MAX_VISIBLE_ITEMS) {
                    visibleCount++
                    setting
                } else {
                    setting.copy(visible = false)
                }
            } else {
                setting
            }
        }

        // 若可见项少于下限，从前到后自动开启补足到 MIN_VISIBLE_ITEMS
        if (visibleCount < MIN_VISIBLE_ITEMS) {
            val adjusted = bounded.toMutableList()
            for (i in adjusted.indices) {
                if (!adjusted[i].visible) {
                    adjusted[i] = adjusted[i].copy(visible = true)
                    visibleCount++
                    if (visibleCount >= MIN_VISIBLE_ITEMS) break
                }
            }
            return adjusted
        }

        return bounded
    }
}

package com.lin0721.linmusic.core.preferences

// key 与播放页 LazyColumn 的 item key 共用，改名会让已保存的配置失效
enum class FullPlayerCard(val key: String, val title: String) {
    LYRICS("lyrics", "歌词"),
    COMMENTS_PREVIEW("comments_preview", "评论"),
    SONG_DETAIL("song_detail", "歌曲百科"),
    MUSIC_MEMORY("music_memory", "回忆坐标"),
    ABOUT_ARTIST("about_artist", "关于艺人"),
    ARTIST_ALBUMS("artist_albums", "艺人专辑"),
    SIMILAR_ARTISTS("similar_artists", "相似艺人");

    companion object {
        fun fromKey(key: String): FullPlayerCard? = entries.firstOrNull { it.key == key }
    }
}

data class FullPlayerCardSetting(
    val card: FullPlayerCard,
    val visible: Boolean
)

// 持久化格式：`lyrics:1,comments_preview:0,...`，按用户排序依次排列，1 显示 / 0 隐藏
object FullPlayerCardLayout {

    val DEFAULT: List<FullPlayerCardSetting> =
        FullPlayerCard.entries.map { FullPlayerCardSetting(it, visible = true) }

    fun encode(layout: List<FullPlayerCardSetting>): String =
        normalize(layout).joinToString(",") { "${it.card.key}:${if (it.visible) 1 else 0}" }

    fun decode(raw: String?): List<FullPlayerCardSetting> {
        if (raw.isNullOrBlank()) return DEFAULT
        val parsed = raw.split(",").mapNotNull { token ->
            val parts = token.trim().split(":", limit = 2)
            val card = FullPlayerCard.fromKey(parts[0].trim()) ?: return@mapNotNull null
            FullPlayerCardSetting(card, visible = parts.getOrNull(1)?.trim() != "0")
        }
        return normalize(parsed)
    }

    // 去重并补齐缺失卡片，保证新版本新增的卡片默认可见
    private fun normalize(layout: List<FullPlayerCardSetting>): List<FullPlayerCardSetting> {
        val distinct = layout.distinctBy { it.card }
        val missing = FullPlayerCard.entries
            .filter { card -> distinct.none { it.card == card } }
            .map { FullPlayerCardSetting(it, visible = true) }
        return distinct + missing
    }
}

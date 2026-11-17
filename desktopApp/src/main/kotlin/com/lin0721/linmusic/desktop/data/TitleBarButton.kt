package com.lin0721.linmusic.desktop.data

// 标题栏可自定义按钮，不包含搜索框、插件按钮、云盘上传、头像与窗口控制按钮
enum class TitleBarButton(
    val key: String,
    val title: String
) {
    HOME("home", "首页"),
    RECOGNITION("recognition", "听歌识曲"),
    MESSAGES("messages", "消息"),
    DOWNLOADS("downloads", "下载"),
    PERSONAL_FM("personal_fm", "私人FM"),
    DAILY_RECOMMEND("daily_recommend", "每日推荐"),
    SETTINGS("settings", "设置"),
    LOCAL_MUSIC("local_music", "本地音乐"),
    RECENT_PLAY("recent_play", "最近播放");

    companion object {
        fun fromKey(key: String): TitleBarButton? = entries.firstOrNull { it.key == key }
    }
}

enum class TitleBarSlot(
    val key: String,
    val label: String
) {
    CENTER_START("cs", "中间左侧"),
    CENTER_END("ce", "中间右侧"),
    TRAILING("tr", "右侧");

    companion object {
        fun fromKey(key: String): TitleBarSlot? = entries.firstOrNull { it.key == key }
    }
}

// 不在任意槽中的按钮即视为隐藏
data class TitleBarLayout(
    val centerStart: List<TitleBarButton>,
    val centerEnd: List<TitleBarButton>,
    val trailing: List<TitleBarButton>
) {
    fun buttonsIn(slot: TitleBarSlot): List<TitleBarButton> = when (slot) {
        TitleBarSlot.CENTER_START -> centerStart
        TitleBarSlot.CENTER_END -> centerEnd
        TitleBarSlot.TRAILING -> trailing
    }

    fun slotOf(button: TitleBarButton): TitleBarSlot? = when {
        button in centerStart -> TitleBarSlot.CENTER_START
        button in centerEnd -> TitleBarSlot.CENTER_END
        button in trailing -> TitleBarSlot.TRAILING
        else -> null
    }

    fun isVisible(button: TitleBarButton): Boolean = slotOf(button) != null

    val hiddenButtons: List<TitleBarButton>
        get() {
            val visible = (centerStart + centerEnd + trailing).toSet()
            return TitleBarButton.entries.filter { it !in visible }
        }

    companion object {
        val DEFAULT = TitleBarLayout(
            centerStart = listOf(TitleBarButton.HOME),
            centerEnd = listOf(TitleBarButton.RECOGNITION),
            trailing = listOf(TitleBarButton.MESSAGES, TitleBarButton.DOWNLOADS)
        )
    }
}

// 标题栏布局编解码："cs=home;ce=recognition;tr=messages,downloads"
object TitleBarLayoutCodec {

    fun encode(layout: TitleBarLayout): String {
        val normalized = normalize(layout)
        return TitleBarSlot.entries.joinToString(";") { slot ->
            "${slot.key}=${normalized.buttonsIn(slot).joinToString(",") { it.key }}"
        }
    }

    fun decode(raw: String?): TitleBarLayout {
        if (raw.isNullOrBlank()) return TitleBarLayout.DEFAULT
        val slotMap = mutableMapOf<TitleBarSlot, List<TitleBarButton>>()
        var hasNonEmptyButtonToken = false
        for (segment in raw.split(";")) {
            val parts = segment.split("=", limit = 2)
            if (parts.size != 2) continue
            val slot = TitleBarSlot.fromKey(parts[0].trim()) ?: continue
            val tokens = parts[1].split(",").map { it.trim() }.filter { it.isNotEmpty() }
            if (tokens.isNotEmpty()) hasNonEmptyButtonToken = true
            slotMap[slot] = tokens.mapNotNull(TitleBarButton::fromKey)
        }
        if (slotMap.isEmpty()) return TitleBarLayout.DEFAULT
        val normalized = normalize(
            TitleBarLayout(
                centerStart = slotMap[TitleBarSlot.CENTER_START].orEmpty(),
                centerEnd = slotMap[TitleBarSlot.CENTER_END].orEmpty(),
                trailing = slotMap[TitleBarSlot.TRAILING].orEmpty()
            )
        )
        // 区分“刻意全部隐藏（cs=;ce=;tr=）”与“所有按钮 key 都无效”，后者回落默认布局
        val parsedCount = normalized.centerStart.size + normalized.centerEnd.size + normalized.trailing.size
        if (parsedCount == 0 && hasNonEmptyButtonToken) return TitleBarLayout.DEFAULT
        return normalized
    }

    // 跨槽全局去重，同一按钮只保留首次出现的位置
    fun normalize(layout: TitleBarLayout): TitleBarLayout {
        val seen = HashSet<TitleBarButton>(TitleBarButton.entries.size)
        val cs = layout.centerStart.filter { seen.add(it) }
        val ce = layout.centerEnd.filter { seen.add(it) }
        val tr = layout.trailing.filter { seen.add(it) }
        return TitleBarLayout(cs, ce, tr)
    }

    fun setVisible(
        layout: TitleBarLayout,
        button: TitleBarButton,
        visible: Boolean
    ): TitleBarLayout {
        val normalized = normalize(layout)
        if (!visible) {
            return TitleBarLayout(
                centerStart = normalized.centerStart.filterNot { it == button },
                centerEnd = normalized.centerEnd.filterNot { it == button },
                trailing = normalized.trailing.filterNot { it == button }
            )
        }
        if (normalized.isVisible(button)) return normalized
        return normalized.copy(trailing = normalized.trailing + button)
    }

    fun toggleVisible(
        layout: TitleBarLayout,
        button: TitleBarButton
    ): TitleBarLayout = setVisible(layout, button, visible = !layout.isVisible(button))

    fun move(
        layout: TitleBarLayout,
        button: TitleBarButton,
        slot: TitleBarSlot,
        index: Int = Int.MAX_VALUE
    ): TitleBarLayout {
        val without = setVisible(layout, button, visible = false)
        fun insert(list: List<TitleBarButton>): List<TitleBarButton> =
            list.toMutableList().apply { add(index.coerceIn(0, size), button) }
        return when (slot) {
            TitleBarSlot.CENTER_START -> without.copy(centerStart = insert(without.centerStart))
            TitleBarSlot.CENTER_END -> without.copy(centerEnd = insert(without.centerEnd))
            TitleBarSlot.TRAILING -> without.copy(trailing = insert(without.trailing))
        }
    }
}

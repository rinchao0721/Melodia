package com.lin0721.linmusic.core.preferences

// 通知栏可自定义按钮项
enum class NotificationButton(
    val key: String,
    val title: String,
    val defaultVisible: Boolean
) {
    LIKE("like", "喜欢", true),
    DISLIKE("dislike", "不喜欢", true),
    PLAY_MODE("play_mode", "播放模式", false),
    DESKTOP_LRC("desktop_lrc", "悬浮歌词开关", false),
    SLEEP_TIMER("sleep_timer", "定时关闭", false),
    CLOSE_APP("close_app", "关闭", false),
    SEEK_BACK("seek_back", "快退 15 秒", false),
    SEEK_FORWARD("seek_forward", "快进 15 秒", false);

    companion object {
        fun fromKey(key: String): NotificationButton? = entries.firstOrNull { it.key == key }
    }
}

data class NotificationButtonSetting(
    val item: NotificationButton,
    val visible: Boolean
)

// 通知栏按钮布局持久化与归一化逻辑：`like:1,dislike:1,play_mode:0,...`
object NotificationButtonLayout {
    // 系统媒体卡片仅优先展示前 2 个可见自定义项，其余进入溢出槽位
    const val PRIORITY_SLOT_COUNT = 2

    val DEFAULT: List<NotificationButtonSetting> = NotificationButton.entries.map {
        NotificationButtonSetting(it, visible = it.defaultVisible)
    }

    fun encode(layout: List<NotificationButtonSetting>): String =
        normalize(layout).joinToString(",") { "${it.item.key}:${if (it.visible) 1 else 0}" }

    fun decode(raw: String?): List<NotificationButtonSetting> {
        if (raw.isNullOrBlank()) return DEFAULT
        val parsed = raw.split(",").mapNotNull { token ->
            val parts = token.trim().split(":", limit = 2)
            val item = NotificationButton.fromKey(parts[0].trim()) ?: return@mapNotNull null
            NotificationButtonSetting(item, visible = parts.getOrNull(1)?.trim() == "1")
        }
        return normalize(parsed)
    }

    // 去重并将新增或缺失项按默认可见性补齐到末尾
    fun normalize(layout: List<NotificationButtonSetting>): List<NotificationButtonSetting> {
        val distinct = layout.distinctBy { it.item }
        val missing = NotificationButton.entries
            .filter { item -> distinct.none { it.item == item } }
            .map { NotificationButtonSetting(it, visible = it.defaultVisible) }
        return distinct + missing
    }

    // 按当前播放上下文（私人 FM / 播客 / 普通模式）过滤出实际显示的自定义按钮
    fun resolveVisibleForContext(
        layout: List<NotificationButtonSetting>,
        isPersonalFm: Boolean,
        isPodcast: Boolean
    ): List<NotificationButton> = normalize(layout).mapNotNull { setting ->
        if (!setting.visible) return@mapNotNull null
        val applicable = when (setting.item) {
            NotificationButton.LIKE,
            NotificationButton.DESKTOP_LRC,
            NotificationButton.SLEEP_TIMER,
            NotificationButton.CLOSE_APP -> true
            NotificationButton.DISLIKE -> isPersonalFm
            NotificationButton.PLAY_MODE -> !isPersonalFm
            NotificationButton.SEEK_BACK,
            NotificationButton.SEEK_FORWARD -> isPodcast && !isPersonalFm
        }
        setting.item.takeIf { applicable }
    }
}

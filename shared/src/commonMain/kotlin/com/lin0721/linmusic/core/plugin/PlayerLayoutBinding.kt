package com.lin0721.linmusic.core.plugin

import com.lin0721.linmusic.core.player.PlayMode

// 播放器布局可绑定的播放状态；进度这类高频值不开放，用 time 组件显示
data class PlayerBindingState(
    val title: String = "",
    val artist: String = "",
    val album: String = "",
    val source: String = "",
    val quality: String = "",
    val playing: Boolean = false,
    val liked: Boolean = false,
    val isPodcast: Boolean = false,
    val hasLyrics: Boolean = false,
    val playMode: PlayMode = PlayMode.LIST_LOOP
) {
    fun text(field: String): String = when (field) {
        "title" -> title
        "artist" -> artist
        "album" -> album
        "source" -> source
        "quality" -> quality
        "playMode" -> playMode.bindingKey
        "playing", "liked", "isPodcast", "hasLyrics" -> if (truthy(field)) "true" else ""
        else -> ""
    }

    fun truthy(field: String): Boolean = when (field) {
        "playing" -> playing
        "liked" -> liked
        "isPodcast" -> isPodcast
        "hasLyrics" -> hasLyrics
        else -> text(field).isNotEmpty()
    }

    // 文字绑定换成实际内容，visible 为假时返回 null
    fun prepare(node: PlayerLayoutNode): PlayerLayoutNode? {
        node.visible?.let { PlayerBinding.field(it) }?.let { if (!truthy(it)) return null }
        if (node.type != LayoutNodeType.TEXT) return node
        val field = PlayerBinding.field(node.text) ?: return node
        return node.copy(text = text(field))
    }
}

private val PlayMode.bindingKey: String
    get() = when (this) {
        PlayMode.LIST_LOOP -> "listLoop"
        PlayMode.SINGLE_LOOP -> "singleLoop"
        PlayMode.SHUFFLE -> "shuffle"
    }

object PlayerBinding {
    val FIELDS = setOf("title", "artist", "album", "source", "quality", "playing", "liked", "isPodcast", "hasLyrics", "playMode")
    private val PATTERN = Regex("^\\\$player\\.([A-Za-z]+)$")
    // 看起来像绑定的写法，用来区分固定文字里偶然出现的 $
    private val LOOKS_LIKE = Regex("^\\$[a-z]+(\\.[A-Za-z0-9_]+)+$")

    fun field(value: String): String? = PATTERN.matchEntire(value)?.groupValues?.get(1)?.takeIf { it in FIELDS }

    fun isBinding(value: String): Boolean = field(value) != null

    fun looksLikeBinding(value: String): Boolean = LOOKS_LIKE.matches(value)
}

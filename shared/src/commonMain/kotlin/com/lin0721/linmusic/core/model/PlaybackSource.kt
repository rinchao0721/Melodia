package com.lin0721.linmusic.core.model

import com.lin0721.linmusic.core.source.MusicPlatform

// 播放来源类别
enum class PlaybackSourceKind { OFFICIAL, COMMUNITY, LOCAL, CACHE }

// 当前曲目的播放来源快照，按 mediaId 绑定；name 为社区音源插件名，官方来源为空
data class PlaybackSourceInfo(
    val mediaId: String,
    val kind: PlaybackSourceKind,
    val platform: MusicPlatform? = null,
    val name: String? = null
)

private const val SOURCE_NAME_MAX_CHARS = 6

private fun MusicPlatform.sourceLabel(): String = when (this) {
    MusicPlatform.NETEASE -> "网易"
    MusicPlatform.KUGOU -> "酷狗"
    MusicPlatform.QQ -> "QQ"
    else -> shortName
}

// 播放来源文案：官方为平台名，社区为「社区 · 插件短名」，本地与缓存为固定词
fun playbackSourceLabel(source: PlaybackSourceInfo): String = when (source.kind) {
    PlaybackSourceKind.OFFICIAL -> source.platform?.sourceLabel() ?: "官方"
    PlaybackSourceKind.COMMUNITY -> {
        val name = source.name?.trim()?.takeIf { it.isNotEmpty() }?.take(SOURCE_NAME_MAX_CHARS)
        if (name == null) "社区" else "社区 · $name"
    }
    PlaybackSourceKind.LOCAL -> "本地"
    PlaybackSourceKind.CACHE -> "缓存"
}

// 按两个开关拼接播放页标签，来源在前音质在后，对应部分缺失时只显示另一部分
fun composePlaybackInfoLabel(
    sourceLabel: String?,
    qualityLabel: String?,
    showSource: Boolean,
    showQuality: Boolean
): String? {
    val parts = listOfNotNull(
        sourceLabel?.takeIf { showSource && it.isNotBlank() },
        qualityLabel?.takeIf { showQuality && it.isNotBlank() }
    )
    return parts.takeIf { it.isNotEmpty() }?.joinToString(" · ")
}

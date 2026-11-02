package com.lin0721.linmusic.core.source

import kotlinx.serialization.Serializable
import kotlin.math.absoluteValue

// 外部平台歌曲通用模型，用于跨平台匹配与搜索结果
@Serializable
data class ExternalTrack(
    val id: String,
    val name: String,
    val artists: String,
    val artistList: List<String> = emptyList(),
    val albumName: String = "",
    val albumId: String = "",
    val durationMs: Long = 0,
    val coverUrl: String = "",
    val platform: MusicPlatform,
    // 酷狗专用 32 位 hex hash
    val hash: String? = null,
    // QQ 音乐数字歌曲 id，评论接口要用；id 字段存的是 songmid
    val qqSongId: Long? = null
) {
    // 搜索结果里唯一标识一首外部歌曲，用于解析状态与去重
    val resolveKey: String
        get() = "${platform.key}:$id"

    // 播放队列里的歌曲 id；取负数，与网易云的正数 id 区分，避免被当成网易云歌曲去查词、打卡
    val queueSongId: Long
        get() = -1L - "${platform.key}:$id".hashCode().toLong().absoluteValue
}

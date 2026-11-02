package com.lin0721.linmusic.core.player

import com.lin0721.linmusic.core.source.ExternalTrack
import kotlinx.serialization.Serializable

enum class PlayMode { LIST_LOOP, SINGLE_LOOP, SHUFFLE }

@Serializable
data class QueueItem(
    val songId: Long,
    val title: String,
    val artist: String,
    val coverUrl: String,
    // 本地外部音频 Uri，非空时直接本地播放
    val localUri: String? = null,
    // 播客节目所属电台 id，非播客为 0
    val radioId: Long = 0,
    // 外部平台歌曲，localUri 为空时由播放器轮到时再经换源插件解析直链
    val external: ExternalTrack? = null
)

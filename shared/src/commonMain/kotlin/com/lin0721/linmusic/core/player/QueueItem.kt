package com.lin0721.linmusic.core.player

import com.lin0721.linmusic.core.source.ExternalTrack
import kotlinx.serialization.Serializable

enum class PlayMode { SEQUENCE, LIST_LOOP, SINGLE_LOOP, SHUFFLE }

// 循环按钮的轮转：顺序 → 列表循环 → 单曲循环 → 顺序（随机由独立的随机按钮切换）
fun PlayMode.nextRepeatMode(): PlayMode = when (this) {
    PlayMode.SEQUENCE, PlayMode.SHUFFLE -> PlayMode.LIST_LOOP
    PlayMode.LIST_LOOP -> PlayMode.SINGLE_LOOP
    PlayMode.SINGLE_LOOP -> PlayMode.SEQUENCE
}

// 通知栏「播放模式」动作的完整轮转：顺序 → 列表循环 → 单曲循环 → 随机 → 顺序
fun PlayMode.nextPlayMode(): PlayMode = when (this) {
    PlayMode.SEQUENCE -> PlayMode.LIST_LOOP
    PlayMode.LIST_LOOP -> PlayMode.SINGLE_LOOP
    PlayMode.SINGLE_LOOP -> PlayMode.SHUFFLE
    PlayMode.SHUFFLE -> PlayMode.SEQUENCE
}

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

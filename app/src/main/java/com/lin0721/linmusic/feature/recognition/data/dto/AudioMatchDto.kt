package com.lin0721.linmusic.feature.recognition.data.dto

import com.lin0721.linmusic.core.model.Album
import com.lin0721.linmusic.core.model.Artist
import kotlinx.serialization.Serializable

// 未命中时 data.result 为 null、noMatchReason 非 0，code 仍为 200（已抓包确认）
@Serializable
data class AudioMatchResponse(
    val code: Int = 0,
    val message: String? = null,
    val data: AudioMatchData? = null
)

@Serializable
data class AudioMatchData(
    val result: List<AudioMatchResult>? = null,
    val noMatchReason: Int = 0
)

// startTime 为片段起点在原曲中的毫秒位置
@Serializable
data class AudioMatchResult(
    val startTime: Long = 0,
    val song: AudioMatchSong? = null
)

// 旧版歌曲结构：歌手字段为 artists、时长字段为 duration，与 Track 的 ar/dt 不同
@Serializable
data class AudioMatchSong(
    val id: Long = 0,
    val name: String = "",
    val artists: List<Artist> = emptyList(),
    val album: Album = Album(),
    val duration: Long = 0
)

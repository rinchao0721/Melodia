package com.lin0721.linmusic.core.player.data

import com.lin0721.linmusic.core.model.Track
import com.lin0721.linmusic.core.player.domain.LyricLine
import com.lin0721.linmusic.core.playlistexternal.toPlaylistTrack
import com.lin0721.linmusic.core.source.ExternalTrack
import kotlinx.coroutines.flow.first

// QQ 匹配需要歌名、歌手、专辑与时长，队列条目不带时长，只能按 songId 取一次网易详情并缓存。
class QqLyricsSource(
    private val playbackRepository: PlaybackRepository,
    private val client: QqLyricsClient
) {
    private val tracks = object : LinkedHashMap<Long, Track>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Long, Track>?): Boolean = size > MAX_TRACKS
    }

    suspend fun fetch(songId: Long): List<LyricLine> {
        val track = trackOf(songId) ?: return emptyList()
        return client.fetch(track)
    }

    // 外部平台歌曲自带歌名、歌手与时长，不用查网易详情
    suspend fun fetch(track: ExternalTrack): List<LyricLine> = client.fetch(track.toPlaylistTrack())

    private suspend fun trackOf(songId: Long): Track? {
        synchronized(tracks) { tracks[songId] }?.let { return it }
        val track = playbackRepository.getSongDetail(songId).first().getOrNull() ?: return null
        synchronized(tracks) { tracks[songId] = track }
        return track
    }

    private companion object {
        const val MAX_TRACKS = 32
    }
}

package com.lin0721.linmusic.core.source

import java.util.concurrent.ConcurrentHashMap

// 外部歌曲对应的 QQ 音乐数字 id：QQ 歌曲直接取，酷狗/酷我按歌名歌手到 QQ 搜索匹配同一首
class QQSongIdResolver(private val qqSearch: PlatformSearchProvider?) {

    // 搜到结果却匹配不上记为 0，同一首歌不重复搜索；搜索本身失败不缓存，下次重试
    private val cache = ConcurrentHashMap<String, Long>()

    suspend fun resolve(track: ExternalTrack): Long? {
        if (track.platform == MusicPlatform.QQ && track.qqSongId != null) return track.qqSongId
        cache[track.resolveKey]?.let { return it.takeIf { id -> id > 0L } }
        val search = qqSearch ?: return null
        val candidates = search.search("${track.name} ${track.artists}".trim(), 0, 10)
        if (candidates.isEmpty()) return null
        val matched = SongMatcher.pickBestCandidate(
            candidates = candidates,
            targetName = track.name,
            targetArtists = track.artistList.ifEmpty { listOf(track.artists) },
            targetAlbum = track.albumName.ifBlank { null },
            targetDurationMs = track.durationMs
        )
        val id = matched?.qqSongId ?: 0L
        cache[track.resolveKey] = id
        return id.takeIf { it > 0L }
    }
}

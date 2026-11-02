package com.lin0721.linmusic.feature.search.data

import com.lin0721.linmusic.core.source.ExternalTrack
import com.lin0721.linmusic.core.source.MusicPlatform
import com.lin0721.linmusic.core.source.NeteaseSongLookup
import com.lin0721.linmusic.core.source.SongMatcher
import com.lin0721.linmusic.feature.search.domain.SearchResultItem
import com.lin0721.linmusic.feature.search.domain.SearchType
import kotlinx.coroutines.flow.firstOrNull
import java.util.concurrent.ConcurrentHashMap

// 按歌名歌手到网易云搜索，用跨平台匹配算法挑同一首；搜到结果却匹配不上记为 0，搜索失败不缓存
class SearchNeteaseSongLookup(private val searchRepository: SearchRepository) : NeteaseSongLookup {

    private val cache = ConcurrentHashMap<String, Long>()

    override suspend fun find(track: ExternalTrack): Long? {
        cache[track.resolveKey]?.let { return it.takeIf { id -> id > 0L } }
        val keyword = "${track.name} ${track.artists}".trim()
        val page = searchRepository.search(keyword, SearchType.SONG, offset = 0, limit = 10).firstOrNull()
            ?.getOrNull() ?: return null
        val candidates = page.items.filterIsInstance<SearchResultItem.SongItem>().map { item ->
            val song = item.track
            val artistNames = song.ar.map { it.name }
            ExternalTrack(
                id = song.id.toString(),
                name = song.name,
                artists = artistNames.joinToString(" / "),
                artistList = artistNames,
                albumName = song.al.name,
                durationMs = song.dt,
                platform = MusicPlatform.NETEASE
            )
        }
        if (candidates.isEmpty()) return null
        val matched = SongMatcher.pickBestCandidate(
            candidates = candidates,
            targetName = track.name,
            targetArtists = track.artistList.ifEmpty { listOf(track.artists) },
            targetAlbum = track.albumName.ifBlank { null },
            targetDurationMs = track.durationMs
        )
        val id = matched?.id?.toLongOrNull() ?: 0L
        cache[track.resolveKey] = id
        return id.takeIf { it > 0L }
    }
}

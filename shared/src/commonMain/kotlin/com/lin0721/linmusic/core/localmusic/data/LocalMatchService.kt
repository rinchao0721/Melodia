package com.lin0721.linmusic.core.localmusic.data

import com.lin0721.linmusic.core.download.recognition.TrackNameNormalizer
import com.lin0721.linmusic.core.localmusic.domain.LocalTrack
import com.lin0721.linmusic.core.localmusic.domain.UNKNOWN_ARTIST_NAME
import com.lin0721.linmusic.core.model.Track
import com.lin0721.linmusic.feature.search.data.SearchRepository
import com.lin0721.linmusic.feature.search.domain.SearchResultItem
import com.lin0721.linmusic.feature.search.domain.SearchType
import kotlinx.coroutines.flow.first
import kotlin.math.abs

private const val DURATION_TOLERANCE_MS = 3000L
private const val CANDIDATE_LIMIT = 20

// 按“标题 歌手”搜网易云单曲，标题与歌手归一化后一致且时长相近才算命中
class LocalMatchService(private val searchRepository: SearchRepository) {

    suspend fun search(keyword: String): Result<List<Track>> {
        if (keyword.isBlank()) return Result.success(emptyList())
        return searchRepository.search(keyword.trim(), SearchType.SONG, 0, CANDIDATE_LIMIT).first()
            .map { page -> page.items.filterIsInstance<SearchResultItem.SongItem>().map { it.track } }
    }

    suspend fun findMatch(track: LocalTrack): Result<Track?> {
        if (!isMatchable(track)) return Result.success(null)
        return search("${track.title} ${track.artist}").map { candidates -> pickMatch(track, candidates) }
    }

    companion object {
        fun isMatchable(track: LocalTrack): Boolean =
            normalize(track.title).isNotEmpty() &&
                track.artist != UNKNOWN_ARTIST_NAME &&
                splitArtists(track.artist).isNotEmpty()

        fun pickMatch(track: LocalTrack, candidates: List<Track>): Track? {
            val title = normalize(track.title)
            val artists = splitArtists(track.artist)
            return candidates.firstOrNull { candidate ->
                normalize(candidate.name) == title &&
                    candidate.ar.any { normalize(it.name) in artists } &&
                    durationClose(track.durationMs, candidate.dt)
            }
        }

        internal fun splitArtists(raw: String): Set<String> = TrackNameNormalizer.splitArtists(raw)

        internal fun normalize(text: String): String = TrackNameNormalizer.normalize(text)

        private fun durationClose(localMs: Long, remoteMs: Long): Boolean =
            localMs <= 0 || remoteMs <= 0 || abs(localMs - remoteMs) <= DURATION_TOLERANCE_MS
    }
}

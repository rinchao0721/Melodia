package com.lin0721.linmusic.desktop.data.local

import com.lin0721.linmusic.core.model.Track
import com.lin0721.linmusic.feature.search.data.SearchRepository
import com.lin0721.linmusic.feature.search.domain.SearchResultItem
import com.lin0721.linmusic.feature.search.domain.SearchType
import kotlinx.coroutines.flow.first
import java.text.Normalizer

private const val DURATION_TOLERANCE_MS = 3000L
private const val CANDIDATE_LIMIT = 20

private val ARTIST_SEPARATORS = Regex("""\s*(?:/|;|、|&|,|，|\bfeat\b\.?|\bft\b\.?)\s*""", RegexOption.IGNORE_CASE)

// 按“标题 歌手”搜网易云单曲，标题与歌手归一化后一致且时长相近才算命中
class LocalMatcher(private val searchRepository: SearchRepository) {

    suspend fun search(keyword: String): Result<List<Track>> {
        if (keyword.isBlank()) return Result.success(emptyList())
        return searchRepository.search(keyword.trim(), SearchType.SONG, 0, CANDIDATE_LIMIT).first()
            .map { page -> page.items.filterIsInstance<SearchResultItem.SongItem>().map { it.track } }
    }

    // 网络失败时返回 failure，调用方保留待匹配状态下次再试
    suspend fun findMatch(track: LocalTrack): Result<Track?> {
        if (!isMatchable(track)) return Result.success(null)
        return search("${track.title} ${track.artist}").map { candidates -> pickMatch(track, candidates) }
    }

    companion object {

        fun isMatchable(track: LocalTrack): Boolean =
            normalize(track.title).isNotEmpty() && track.artist != UNKNOWN_ARTIST && splitArtists(track.artist).isNotEmpty()

        fun pickMatch(track: LocalTrack, candidates: List<Track>): Track? {
            val title = normalize(track.title)
            val artists = splitArtists(track.artist)
            return candidates.firstOrNull { candidate ->
                normalize(candidate.name) == title &&
                    candidate.ar.any { normalize(it.name) in artists } &&
                    durationClose(track.durationMs, candidate.dt)
            }
        }

        internal fun splitArtists(raw: String): Set<String> =
            raw.split(ARTIST_SEPARATORS).map(::normalize).filterTo(HashSet()) { it.isNotEmpty() }

        // 全角转半角、去大小写，只保留字母与数字（含中日韩文字）
        internal fun normalize(text: String): String =
            Normalizer.normalize(text, Normalizer.Form.NFKC).lowercase().filter { it.isLetterOrDigit() }

        private fun durationClose(localMs: Long, remoteMs: Long): Boolean =
            localMs <= 0 || remoteMs <= 0 || kotlin.math.abs(localMs - remoteMs) <= DURATION_TOLERANCE_MS
    }
}

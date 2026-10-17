package com.lin0721.linmusic.feature.localmusic.domain

import com.lin0721.linmusic.core.model.Track
import java.text.Normalizer
import java.util.Locale
import kotlin.math.abs

enum class OnlineLocalAudioFilter { RECOMMENDED, SAME_ARTIST, SAME_ALBUM, ALL }

/** A suggestion never creates an association; the user must still select its local track. */
data class OnlineLocalAudioRecommendation(
    val candidate: LocalAudioCandidate,
    val sameTitle: Boolean,
    val sameArtist: Boolean,
    val sameAlbum: Boolean,
    val matchingDuration: Boolean,
    val isRecommended: Boolean,
    val versionWarning: Boolean,
    val reason: String,
    internal val searchText: String,
    internal val rankTier: Int,
    internal val durationDifferenceMs: Long,
    internal val sourceIndex: Int
)

/**
 * Rank metadata already present in the local library. No file access, network request or write
 * belongs here. Online metadata is prepared once, and filtering reuses the prepared search text.
 */
object OnlineLocalAudioRecommendations {
    private val whitespace = Regex("\\s+")
    private val artistSeparator = Regex("\\s*(?:[,，、;；•]|\\s+/\\s+)\\s*")
    private val searchSeparator = Regex("[-–—_,，、;；•./\\\\:()（）\\[\\]{}【】]+")
    private val unknown = setOf("", "<unknown>", "unknown", "unknown artist", "unknown album", "unknown title",
        "未知", "未知艺术家", "未知歌手", "未知专辑", "未知标题", "未知曲目", "未命名")
    private val recordingMarker = Regex("(?i)\\b(live|acoustic|instrumental|karaoke|remix|demo|re[- ]?record(?:ed|ing))\\b|现场|重录|伴奏|不插电")
    private val bracketSuffix = Regex("\\s*[（(\\[【]([^()（）\\[\\]【】]+)[）)\\]】]\\s*$")
    private val versionDelimiter = Regex("\\s*[-–—:]\\s*")
    private val bareVersionSuffix = Regex("(?i)(?:^|\\s)((?:live|acoustic|instrumental|karaoke|remix|demo|re[- ]?record(?:ed|ing))(?:\\s+(?:version|recording))?|(?:\\d{4}年?\\s*)?(?:现场|重录|伴奏|不插电)(?:版|版本)?)$")
    private val specificLiveSuffix = Regex("(?i)(?:^|\\s)(live\\s+(?:at|in|from|recorded at)\\s+.+)$")

    fun rank(online: Track, candidates: List<LocalAudioCandidate>): List<OnlineLocalAudioRecommendation> =
        rank(online.name, online.ar.joinToString(" • ") { it.name }, online.al.name, online.dt, candidates)

    fun rank(
        title: String,
        artist: String,
        album: String?,
        durationMs: Long,
        candidates: List<LocalAudioCandidate>
    ): List<OnlineLocalAudioRecommendation> {
        val onlineTitle = known(normalize(title))
        val onlineBaseTitle = onlineTitle?.let(::recordingBaseTitle)?.takeIf { it.isNotEmpty() }
        val onlineArtists = artists(artist)
        val onlineAlbum = album?.let { known(LocalMatchAlbum.forComparison(it)) }
        return candidates.mapIndexed { index, source ->
            val local = LocalMatchMetadata.resolve(source)
            val originalTitle = known(normalize(source.title))
            val localTitle = known(normalize(local.title))
            val sameTitle = onlineTitle != null && (onlineTitle == originalTitle || onlineTitle == localTitle)
            val localAlbum = source.album?.let { known(LocalMatchAlbum.forComparison(it)) }
            val sameAlbum = onlineAlbum != null && onlineAlbum == localAlbum
            val sourceArtists = artists(source.artist)
            val localArtists = artists(local.artist)
            // Accept a retained lead artist from a collaboration, without splitting band names
            // such as AC/DC. Slash-serialized collaboration tags need the same album evidence,
            // following the existing preferred-local matcher.
            val sameArtist = onlineArtists.isNotEmpty() && (
                onlineArtists.any { it in sourceArtists || it in localArtists } ||
                    (sameAlbum && onlineArtists.size > 1 && onlineArtists.none { '/' in it } && "ac/dc" !in localArtists &&
                        localArtists.flatMap { it.split('/') }.map(::normalize).filter { it !in unknown }
                            .toSet().let { it.isNotEmpty() && onlineArtists.containsAll(it) })
                )
            val sameBaseTitle = onlineBaseTitle != null && localTitle != null &&
                onlineBaseTitle == recordingBaseTitle(localTitle)
            val versionWarning = (sameTitle || (sameBaseTitle && sameArtist)) && (
                (!sameTitle && sameBaseTitle) || PreferredLocalAudioMatcher.hiddenVersion(source)
                )
            // The duration tolerance is the preferred-local matcher's tolerance: matching album
            // allows 2 percent (2–5 seconds), otherwise only 2 seconds. Duration by itself never
            // makes an unrelated file a recommendation.
            val tolerance = if (sameAlbum) (durationMs / 50L).coerceIn(2_000L, 5_000L) else 2_000L
            val difference = if (durationMs > 0L && source.durationMs > 0L) abs(durationMs - source.durationMs) else Long.MAX_VALUE
            val matchingDuration = difference <= tolerance
            val tier = when {
                sameTitle -> 0
                sameBaseTitle && sameArtist -> 1
                sameArtist && sameAlbum -> 2
                sameArtist -> 3
                sameAlbum -> 4
                else -> 5
            }
            val reason = buildList {
                if (sameTitle) add("歌名一致")
                else if (sameBaseTitle && sameArtist) add("同一歌名的其他版本")
                if (sameArtist) add("同歌手")
                if (sameAlbum) add("同专辑")
                if (matchingDuration && tier < 5) add("时长接近")
                if (versionWarning) add("录音版本可能不同，请核对")
                if (isEmpty()) add("本地曲库")
            }.joinToString(" · ")
            OnlineLocalAudioRecommendation(
                candidate = source,
                sameTitle = sameTitle,
                sameArtist = sameArtist,
                sameAlbum = sameAlbum,
                matchingDuration = matchingDuration,
                isRecommended = tier <= 2,
                versionWarning = versionWarning,
                reason = reason,
                searchText = searchable(listOf(source.title, local.title, source.artist, local.artist,
                    source.album.orEmpty(), source.fileName.orEmpty()).joinToString(" ")),
                rankTier = tier,
                durationDifferenceMs = difference,
                sourceIndex = index
            )
        }.sortedWith(compareBy<OnlineLocalAudioRecommendation> { it.rankTier }
            .thenBy { it.versionWarning }
            .thenByDescending { it.sameArtist }
            .thenByDescending { it.sameAlbum }
            .thenByDescending { it.matchingDuration }
            .thenBy { it.durationDifferenceMs }
            .thenBy { it.sourceIndex })
    }

    fun filter(
        recommendations: List<OnlineLocalAudioRecommendation>,
        filter: OnlineLocalAudioFilter = OnlineLocalAudioFilter.RECOMMENDED,
        query: String = ""
    ): List<OnlineLocalAudioRecommendation> {
        val terms = searchable(LocalMatchTitle.forSearch(query)).split(' ').filter { it.isNotEmpty() }
        return recommendations.filter { row ->
            val included = when (filter) {
                OnlineLocalAudioFilter.RECOMMENDED -> row.isRecommended
                OnlineLocalAudioFilter.SAME_ARTIST -> row.sameArtist
                OnlineLocalAudioFilter.SAME_ALBUM -> row.sameAlbum
                OnlineLocalAudioFilter.ALL -> true
            }
            included && terms.all { it in row.searchText }
        }
    }

    private fun normalize(value: String): String = Normalizer.normalize(value, Normalizer.Form.NFKC)
        .trim().lowercase(Locale.ROOT).replace(whitespace, " ")

    private fun known(value: String): String? = value.takeIf { it !in unknown }

    private fun artists(value: String): Set<String> = value.split(artistSeparator)
        .map(::normalize).filter { it !in unknown }.toSet()

    private fun searchable(value: String): String = normalize(value).replace(searchSeparator, " ")
        .replace(whitespace, " ").trim()

    /** Strip only explicit recording suffixes, keeping words inside actual song names. */
    private fun recordingBaseTitle(value: String): String {
        var body = value
        while (true) {
            val bracket = bracketSuffix.find(body) ?: break
            if (!recordingMarker.containsMatchIn(bracket.groupValues[1])) break
            body = body.substring(0, bracket.range.first).trim()
        }
        val separated = versionDelimiter.findAll(body).toList().asReversed().firstOrNull { delimiter ->
            recordingMarker.containsMatchIn(body.substring(delimiter.range.last + 1))
        }
        if (separated != null) return body.substring(0, separated.range.first).trim()
        val bare = specificLiveSuffix.find(body) ?: bareVersionSuffix.find(body)
        return bare?.let { body.substring(0, it.range.first).trim() } ?: body
    }
}

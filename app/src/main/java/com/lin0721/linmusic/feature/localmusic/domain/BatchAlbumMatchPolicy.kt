package com.lin0721.linmusic.feature.localmusic.domain

import com.lin0721.linmusic.core.model.Album
import com.lin0721.linmusic.core.model.Track
import java.text.Normalizer
import java.util.Locale
import kotlin.math.abs

data class BatchAlbumLocal(val name: String, val artist: String, val tracks: List<AlbumScopedLocal>)

data class BatchAlbumCandidate(
    val album: Album,
    val score: Int,
    val reasons: List<String>,
    val isStrong: Boolean
)

/** complete must be false when the response is truncated or any album tracks could not be loaded. */
data class BatchAlbumDetail(val album: Album, val tracks: List<Track>, val complete: Boolean = true)

enum class BatchAlbumMatchStatus { AUTO, REVIEW, NO_MATCH }

data class BatchAlbumDecision(
    val status: BatchAlbumMatchStatus,
    val selected: BatchAlbumDetail? = null,
    val ranked: List<BatchAlbumCandidate> = emptyList(),
    val reason: String
)

/** Automatic album selection is stricter than the song policy used after an explicit album choice. */
object BatchAlbumMatchPolicy {
    private val whitespace = Regex("\\s+")
    private val punctuation = Regex("[\\p{P}\\s]+")
    private val artistSeparator = Regex("\\s*(?:[,，、;；•]|\\s+/\\s+)\\s*")
    private val unknownNames = setOf("", "<unknown>", "unknown", "unknown album", "unknown artist", "unknown title", "未知专辑", "未知歌手", "未知艺术家", "未知歌曲", "未知标题")
    private val editionBracket = Regex("\\s*[（(\\[【]([^()（）\\[\\]【】]+)[）)\\]】]\\s*$")
    private val editionSeparated = Regex("\\s+[-–—:]\\s+([^:]+)$")
    private val discSuffix = Regex("(?i)(?:\\s+(?:disc|cd)\\s*(?:0?[1-9]|[1-9]\\d)|\\s*[（(\\[【]\\s*(?:disc|cd)\\s*(?:0?[1-9]|[1-9]\\d)\\s*[）)\\]】])\\s*$")
    private val bareEditionSuffix = Regex("(?i)\\s+((?:(?:(?:\\d{1,3}(?:st|nd|rd|th)?\\s+)?anniversary|(?:super\\s+)?deluxe|expanded|special|legacy|collector(?:['’]s|s)?|standard)(?:\\s+(?:super|deluxe|expanded|anniversary|special|legacy|collector(?:['’]s|s)?|standard))*\\s+(?:edition|version))|(?:\\d{1,3}周年)?(?:豪华|纪念|珍藏|扩展|特别|标准)(?:版本|版))$")
    private val editionWords = setOf("deluxe", "super", "expanded", "anniversary", "edition", "version", "standard", "special", "legacy", "collector", "collectors", "s", "bonus", "track", "tracks", "disc", "discs")
    private val recordingTypes = linkedMapOf(
        "live" to Regex("(?i)\\blive\\b|现场|演唱会"),
        "remix" to Regex("(?i)\\bremix(?:ed|es)?\\b|混音"),
        "rerecord" to Regex("(?i)\\bre[- ]?record(?:ed|ing)?\\b|重录"),
        "acoustic" to Regex("(?i)\\bacoustic\\b|不插电"),
        "instrumental" to Regex("(?i)\\b(?:instrumental|karaoke)\\b|伴奏"),
        "demo" to Regex("(?i)\\bdemos?\\b|小样"),
        "compilation" to Regex("(?i)\\b(?:greatest hits|best of|compilation|collection)\\b|精选|合辑|合集")
    )

    fun queries(local: BatchAlbumLocal): List<String> {
        val artist = artists(local.artist)
        val album = releaseName(local.name)
        if (!known(album) || artist.isEmpty()) return emptyList()
        // Do not turn an unknown artist into an unbounded, album-name-only catalog search.
        val credit = local.artist.split(artistSeparator).map(String::trim).filter(::known).joinToString(" ")
        return listOf("$album $credit".trim(), "$credit $album".trim()).distinct()
    }

    fun rank(local: BatchAlbumLocal, albums: List<Album>): List<BatchAlbumCandidate> = albums
        .filter { it.id > 0L && known(it.name) }.distinctBy { it.id }
        .map { metadata(local, it) }
        .sortedWith(compareByDescending<BatchAlbumCandidate> { it.isStrong }.thenByDescending { it.score }
            .thenBy { releasePenalty(it.album.name) }.thenBy { it.album.id })

    fun decide(local: BatchAlbumLocal, details: List<BatchAlbumDetail>): BatchAlbumDecision {
        val candidates = rank(local, details.map { it.album })
        fun result(status: BatchAlbumMatchStatus, reason: String, ranked: List<BatchAlbumCandidate> = candidates,
                   selected: BatchAlbumDetail? = null) = BatchAlbumDecision(status, selected, ranked, reason)
        if (!known(releaseName(local.name))) return result(BatchAlbumMatchStatus.NO_MATCH, "本地专辑缺少有效名称，请手动选择")
        if (artists(local.artist).isEmpty()) return result(BatchAlbumMatchStatus.REVIEW, "本地专辑缺少歌手，无法自动确认同名专辑归属")
        if (candidates.isEmpty()) return result(BatchAlbumMatchStatus.NO_MATCH, "没有找到可用的网易云专辑候选")
        val locals = local.tracks.distinctBy { it.candidate.uri }
        if (locals.isEmpty()) return result(BatchAlbumMatchStatus.REVIEW, "本地专辑没有可核对的歌曲")
        val localDurations = locals.associate { it.candidate.uri to it.candidate.durationMs }
        val uniqueDetails = details.filter { it.album.id > 0L }.distinctBy { it.album.id }
        val evaluated = candidates.mapNotNull { candidate ->
            val detail = uniqueDetails.firstOrNull { it.album.id == candidate.album.id } ?: return@mapNotNull null
            // Album identity is evidence about recordings, not a decision about which encoding to
            // save. MP3/FLAC copies may identify the same album track without making it ambiguous.
            // The subsequent write plan still resolves one-online-song/one-local-file collisions.
            val decisions = locals.map { AlbumScopedMatchPolicy.evaluate(it.candidate, detail.tracks) }
            val matched = decisions.filter { it.status == AlbumScopedMatchStatus.MATCH }.associate { it.localUri to it.match!! }
            val covered = matched.values.map { it.id }.distinct().size
            val unmatched = locals.filter { it.candidate.uri !in matched }.map { row ->
                val resolved = LocalMatchMetadata.resolve(row.candidate)
                val title = titleKey(LocalMatchTitle.forSearch(resolved.title))
                if (known(resolved.title) && known(title)) title else "unknown:${row.candidate.uri}"
            }.distinct().size
            val units = covered + unmatched
            val required = minOf(3, units)
            val qualified = candidate.isStrong && detail.complete && covered >= required && covered > 0 && covered * 5 >= units * 4
            val deltas = locals.mapNotNull { row -> matched[row.candidate.uri]?.let {
                it.id to abs(it.dt - row.candidate.durationMs)
            } }.groupBy({ it.first }, { it.second }).values.map { it.min() }
            val delta = if (deltas.isEmpty()) Long.MAX_VALUE else deltas.average().toLong()
            Evidence(candidate, detail, matched, covered, units, localDurations, delta, qualified)
        }.sortedWith(compareByDescending<Evidence> { it.qualified }
            .thenByDescending { if (it.unitCount == 0) 0 else it.coveredCount * 1_000 / it.unitCount }
            .thenByDescending { it.coveredCount }
            // A sub-second average difference does not outweigh a verified original edition.
            .thenBy { if (it.averageDeltaMs == Long.MAX_VALUE) Long.MAX_VALUE else it.averageDeltaMs / 1_000L }
            .thenBy { releasePenalty(it.detail.album.name) }.thenByDescending { it.candidate.score }
            .thenBy { it.detail.album.id })
        val ranked = evaluated.map { evidence -> evidence.candidate.copy(
            reasons = evidence.candidate.reasons + "歌名和时长可确认 ${evidence.coveredCount} / ${evidence.unitCount} 首" +
                (if (evidence.matched.size == evidence.coveredCount) emptyList() else listOf("重复编码合并计数，可用文件 ${evidence.matched.size} 个")) +
                (if (evidence.detail.complete) emptyList() else listOf("在线专辑曲目未完整取得"))
        ) }
        val eligible = evaluated.filter { it.qualified }
        if (eligible.isEmpty()) return result(
            if (evaluated.any { it.candidate.score >= 50 }) BatchAlbumMatchStatus.REVIEW else BatchAlbumMatchStatus.NO_MATCH,
            "没有唯一可信的专辑对应，请核对歌手、录音版本及曲目覆盖", ranked
        )
        val best = eligible.first()
        // Do not hide an unfetched edition that could have the same name and local artist credit.
        if (evaluated.any { it.candidate.isStrong && !it.detail.complete }) return result(
            BatchAlbumMatchStatus.REVIEW, "同名候选专辑的曲目尚未完整取得，暂不自动选择", ranked
        )
        if (details.filter { it.album.id == best.detail.album.id }.map(::detailSignature).distinct().size > 1) return result(
            BatchAlbumMatchStatus.REVIEW, "同一在线专辑返回了冲突信息，请重新核对", ranked
        )
        if (eligible.drop(1).any { other -> !equivalentRecordings(best, other) }) return result(
            BatchAlbumMatchStatus.REVIEW, "多个候选可能属于不同歌手署名或录音版本，请选择对应专辑", ranked
        )
        return result(BatchAlbumMatchStatus.AUTO,
            if (eligible.size > 1) "同一专辑的发行版本已按曲目覆盖、时长接近程度和原版优先排序"
            else "专辑名、歌手和足够的歌曲歌名及时长均一致，可自动建立专辑对应",
            ranked, best.detail)
    }

    private data class Evidence(val candidate: BatchAlbumCandidate, val detail: BatchAlbumDetail,
                                val matched: Map<String, Track>, val coveredCount: Int, val unitCount: Int,
                                val localDurations: Map<String, Long>, val averageDeltaMs: Long, val qualified: Boolean)

    private fun equivalentRecordings(first: Evidence, second: Evidence): Boolean {
        if (first.detail.album.artists.flatMap { artists(it.name) }.toSet() != second.detail.album.artists.flatMap { artists(it.name) }.toSet()) return false
        if (types(first.detail.album.name) != types(second.detail.album.name)) return false
        // Unlabelled re-recordings can share album/song names. Equivalent editions must also have
        // closely agreeing durations for the local songs both candidates can identify.
        val common = first.matched.keys.intersect(second.matched.keys)
        val recordings = common.groupBy { first.matched.getValue(it).id to second.matched.getValue(it).id }.values
        if (recordings.isEmpty()) return false
        fun sameTitle(uri: String): Boolean = titleKey(first.matched.getValue(uri).name) == titleKey(second.matched.getValue(uri).name)
        fun strict(uri: String): Boolean {
            val a = first.matched.getValue(uri)
            val b = second.matched.getValue(uri)
            return sameTitle(uri) && abs(a.dt - b.dt) <= (a.dt / 50L).coerceIn(2_000L, 5_000L)
        }
        val agreed = recordings.count { recording -> strict(recording.first()) }
        if (agreed == recordings.size) return true
        if (agreed * 10 < recordings.size * 9) return false
        // A small fade-length difference must not veto an otherwise equivalent large edition set.
        // Count recordings once, and require every local encoding to clearly favor the chosen one.
        return recordings.filterNot { strict(it.first()) }.all { recording -> recording.all { uri ->
            val a = first.matched.getValue(uri)
            val b = second.matched.getValue(uri)
            val duration = first.localDurations.getValue(uri)
            sameTitle(uri) && abs(a.dt - b.dt) <= 15_000L &&
                abs(a.dt - duration) <= (a.dt / 50L).coerceIn(2_000L, 5_000L) &&
                abs(b.dt - duration) - abs(a.dt - duration) >= 2_000L
        } }
    }

    private fun metadata(local: BatchAlbumLocal, online: Album): BatchAlbumCandidate {
        val name = titleKey(local.name)
        val other = titleKey(online.name)
        val exact = name.isNotBlank() && name == other
        val sameTypes = types(local.name) == types(online.name)
        val localArtists = artists(local.artist)
        val onlineArtists = online.artists.flatMap { artists(it.name) }.toSet()
        val sameArtist = localArtists.isNotEmpty() && onlineArtists.containsAll(localArtists)
        val fullCredit = sameArtist && localArtists == onlineArtists
        val related = !exact && name.length >= 3 && other.length >= 3 && (name.contains(other) || other.contains(name))
        val strong = exact && sameTypes && sameArtist
        val score = (if (exact) 100 else if (related) 30 else 0) + (if (sameArtist) 60 else 0) +
            (if (fullCredit) 10 else 0) + (if (releasePenalty(online.name) == 0) 2 else 0)
        val reasons = buildList {
            if (exact) add("专辑名一致") else if (related) add("专辑名相近")
            if (sameArtist) add(if (fullCredit) "歌手署名一致" else "包含本地歌手署名")
            else add(if (onlineArtists.isEmpty()) "在线专辑歌手未知" else "歌手署名未确认")
            if (!sameTypes) add("现场、混音或合集等专辑类型不同")
            if (exact && normalize(local.name) != normalize(online.name)) add("发行版本注释不同")
        }
        return BatchAlbumCandidate(online, score, reasons, strong)
    }

    private fun detailSignature(detail: BatchAlbumDetail): String = buildString {
        append(titleKey(detail.album.name)).append('|')
        append(detail.album.artists.flatMap { artists(it.name) }.sorted().joinToString("/")).append('|')
        append(detail.complete).append('|')
        append(detail.tracks.map { "${it.id}:${titleKey(it.name)}:${it.dt}" }.distinct().sorted().joinToString(";"))
    }

    private fun types(value: String): Set<String> = recordingTypes.filterValues { it.containsMatchIn(value) }.keys
    private fun artists(value: String): Set<String> = value.split(artistSeparator).map(::normalize).filter(::known).toSet()
    private fun known(value: String): Boolean = normalize(value) !in unknownNames
    private fun normalize(value: String): String = Normalizer.normalize(value, Normalizer.Form.NFKC)
        .trim().lowercase(Locale.ROOT).replace(whitespace, " ")
    private fun titleKey(value: String): String = releaseName(value).let {
        if (it.any(Char::isLetter)) it.replace(punctuation, "") else it
    }

    private fun releaseName(value: String): String {
        var current = LocalMatchAlbum.forComparison(value)
        while (true) {
            val disc = discSuffix.find(current)
            val bracket = editionBracket.find(current)
            val separated = editionSeparated.find(current)
            val bare = bareEditionSuffix.find(current)
            val suffix = when {
                disc != null -> disc
                bracket != null && editionOnly(bracket.groupValues[1]) -> bracket
                separated != null && editionOnly(separated.groupValues[1]) -> separated
                bare != null && editionOnly(bare.groupValues[1]) -> bare
                else -> return current
            }
            val next = LocalMatchAlbum.forComparison(current.substring(0, suffix.range.first).trim())
            if (next.isBlank()) return current
            current = next
        }
    }

    private fun editionOnly(value: String): Boolean {
        val normalized = normalize(value)
        if (types(normalized).isNotEmpty()) return false
        if (normalized.matches(Regex("(?:\\d{1,3}周年)?(?:豪华|纪念|珍藏|扩展|特别|标准)(?:版本|版)"))) return true
        val words = normalized.replace(Regex("\\b\\d+(?:st|nd|rd|th)?\\b"), "").replace(punctuation, " ")
            .split(whitespace).filter(String::isNotBlank)
        return words.isNotEmpty() && words.all { it in editionWords } && words.any { it !in setOf("edition", "version", "track", "tracks", "disc", "discs") }
    }

    private fun releasePenalty(value: String): Int = if (normalize(value) == releaseName(value)) 0 else 1
}

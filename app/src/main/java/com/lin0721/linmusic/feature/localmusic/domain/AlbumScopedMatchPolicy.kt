package com.lin0721.linmusic.feature.localmusic.domain

import com.lin0721.linmusic.core.model.Track
import java.text.Normalizer
import java.util.Locale
import kotlin.math.abs

data class AlbumScopedLocal(val candidate: LocalAudioCandidate, val trackNumber: Int? = null)

enum class AlbumScopedMatchStatus { MATCH, REVIEW, NO_MATCH }

data class AlbumScopedMatchDecision(
    val localUri: String,
    val status: AlbumScopedMatchStatus,
    val match: Track? = null,
    val candidates: List<Track> = emptyList(),
    val reason: String
)

/**
 * Used only after the user chooses the corresponding online album. Album and artist tags no longer
 * gate a match; recording identity still needs a unique title and compatible, known duration.
 * The caller supplies that album's complete track list, never a global search result or a subset
 * made by removing already-associated tracks. A fresh evaluate() is also required before saving.
 */
object AlbumScopedMatchPolicy {
    private val whitespace = Regex("\\s+")
    private val punctuationAndSpace = Regex("[\\p{P}\\s]+")
    private val unknownTitles = setOf("", "<unknown>", "unknown", "unknown title", "未知歌曲", "未知标题")
    private val recordingMarker = Regex("(?i)\\b(live|acoustic|instrumental|karaoke|remix|demo|re[- ]?record(?:ed|ing))\\b|现场|重录|伴奏|不插电")
    private val bracketSuffix = Regex("\\s*[（(\\[【]([^()（）\\[\\]【】]+)[）)\\]】]\\s*$")
    private val separatedVersion = Regex("\\s+[-–—:]\\s+(.+)$")
    private val bareVersion = Regex("(?i)\\s+((?:live(?:\\s+(?:at|in|from)\\s+.+)?|acoustic|instrumental|karaoke|remix|demo|re[- ]?record(?:ed|ing))(?:\\s+(?:version|recording))?|(?:\\d{4}年?\\s*)?(?:现场|重录|伴奏|不插电)(?:版|版本)?)$")
    private val featuredSuffix = Regex("(?i)\\s+((?:feat\\.|ft\\.|feat(?=\\s)|ft(?=\\s)|featuring(?=\\s))\\s*(.+))$")
    private val featuredBracket = Regex("(?i)^(?:feat\\.|ft\\.|feat(?=\\s)|ft(?=\\s)|featuring(?=\\s))\\s*(.+)$")
    private val creditSeparator = Regex("\\s*(?:[,，、;；•&]|\\s+/\\s+|\\s+and\\s+)\\s*", RegexOption.IGNORE_CASE)
    private val han = Regex("\\p{IsHan}")
    private val latin = Regex("\\p{IsLatin}")
    private val versionAnnotation = Regex("(?i)\\d|\\b(?:live|acoustic|instrumental|karaoke|remix|demo|re[- ]?record(?:ed|ing)?|clean|explicit|part|pt|vol|volume|version|ver|edit|mix|edition|mono|stereo|take|radio|extended|alternate|dub|cover|session|cut)\\b|\\b(?:part|pt|vol|volume|ver(?:sion)?)[ ._-]*[ivx]+\\b|现场|混音|伴奏|不插电|演唱会|重录|重制|版本|翻唱|改编|第.+[部章节]|原声")
    private val copyNumberSuffix = Regex("\\s*\\([0-9]{1,3}\\)$")
    private val liveStart = Regex("(?i)^live(?:\\b|$)|^现场")
    private val liveSeparated = Regex("(?i)\\s*[-–—:]\\s*((?:live(?:\\b|$)|现场).*)$")
    private val liveBare = Regex("(?i)\\s+((?:live(?:\\b|$)|现场).*)$")
    private val nonLiveQualifier = Regex("(?i)\\b(?:clean|explicit|edit|extended|part|pt|vol|volume|version|ver|mono|stereo|take|cover|dub|rework|rmx)\\b|净版|消音|删减|剪辑|加长|第.+[部章节]")

    fun plan(locals: List<AlbumScopedLocal>, onlineTracks: List<Track>): List<AlbumScopedMatchDecision> {
        val decisions = locals.distinctBy { it.candidate.uri }.map { evaluate(it.candidate, onlineTracks, it.trackNumber) }
        // A preference maps one online song to one local file. Even identical encodings of a song
        // must be chosen explicitly when more than one local row claims that song in this plan.
        val collisions = decisions.filter { it.status == AlbumScopedMatchStatus.MATCH }
            .groupBy { it.match!!.id }.filterValues { it.size > 1 }.keys
        return decisions.map { decision ->
            if (decision.match?.id !in collisions) decision else decision.copy(
                status = AlbumScopedMatchStatus.REVIEW,
                match = null,
                reason = "多首本地歌曲对应同一首在线歌曲，请选择要使用的本地文件"
            )
        }
    }

    fun evaluate(local: LocalAudioCandidate, onlineTracks: List<Track>): AlbumScopedMatchDecision =
        evaluate(local, onlineTracks, null)

    private fun evaluate(local: LocalAudioCandidate, onlineTracks: List<Track>, trackNumber: Int?): AlbumScopedMatchDecision {
        val valid = onlineTracks.filter { it.id > 0L && knownTitle(it.name) }.distinctBy { it.id }
        fun result(status: AlbumScopedMatchStatus, reason: String, candidates: List<Track>, match: Track? = null) =
            AlbumScopedMatchDecision(local.uri, status, match, candidates, reason)
        fun confirmed(selected: Track, candidates: List<Track>, reason: String): AlbumScopedMatchDecision {
            val conflictingCopies = onlineTracks.filter { it.id == selected.id }.distinctBy { comparable(it.name) to it.dt }
            if (conflictingCopies.size > 1) return result(AlbumScopedMatchStatus.REVIEW, "在线专辑中同一曲目的信息存在冲突，请核对", candidates)
            return result(AlbumScopedMatchStatus.MATCH, reason, candidates, selected)
        }
        if (local.uri.isBlank()) return result(AlbumScopedMatchStatus.NO_MATCH, "本地歌曲缺少文件引用", emptyList())
        if (valid.isEmpty()) return result(AlbumScopedMatchStatus.NO_MATCH, "这张在线专辑没有可用于匹配的曲目", emptyList())
        val resolved = LocalMatchMetadata.resolve(local)
        if (!knownTitle(resolved.title)) return result(AlbumScopedMatchStatus.NO_MATCH, "本地歌曲缺少可识别的歌名，请手动选择", valid)
        val indexed = valid.withIndex().associate { it.value.id to it.index }
        // These values depend only on the current local file and the ordered artist names. Keep
        // the cache scoped to this evaluation so fresh metadata always creates fresh evidence.
        val localByCredit = mutableMapOf<List<String>, PreparedLocalTitles>()
        val localById = mutableMapOf<Long, PreparedLocalTitles>()
        val onlineLive = mutableMapOf<Long, LiveTitle?>()
        val onlineLiveBase = mutableMapOf<Long, String?>()
        val exactIds = mutableSetOf<Long>()
        val baseIds = mutableSetOf<Long>()
        valid.forEach { online ->
            val credits = online.ar.map { it.name }
            val prepared = localByCredit.getOrPut(credits) {
                val titles = localTitles(local, online, resolved.title)
                PreparedLocalTitles(titles.map(::comparable).toSet(),
                    titles.map { comparable(recordingBase(it)) }.toSet(),
                    titles.mapNotNull(::liveTitle).filter { it.generic }.map { comparable(it.base) }.toSet())
            }
            localById[online.id] = prepared
            val onlineTitles = contextualTitles(online.name, online)
            if (onlineTitles.any { comparable(it) in prepared.exact }) exactIds += online.id
            if (onlineTitles.any { comparable(recordingBase(it)) in prepared.base }) baseIds += online.id
            val live = liveTitle(online.name)
            onlineLive[online.id] = live
            onlineLiveBase[online.id] = live?.let { comparable(it.base) }
        }
        val ranked = valid.sortedWith(compareByDescending<Track> { it.id in exactIds }
            .thenByDescending { it.id in baseIds }
            .thenBy { if (local.durationMs > 0L && it.dt > 0L) abs(local.durationMs - it.dt) else Long.MAX_VALUE }
            // Only rank recommendations with the order. A disc-local number such as 1001 is not
            // converted to an album-wide index, and order can never make a row automatic.
            .thenBy { if (trackNumber != null && trackNumber in 1..valid.size) abs(indexed.getValue(it.id) + 1 - trackNumber) else Int.MAX_VALUE }
            .thenBy { indexed.getValue(it.id) })
        val titleMatches = ranked.filter { it.id in exactIds }
        val related = ranked.filter { it.id in baseIds }
        val localGenericLive = localByCredit.values.any { it.genericLiveBases.isNotEmpty() }
        if (localGenericLive) {
            val liveCandidates = ranked.filter { online ->
                val candidateBase = onlineLiveBase[online.id] ?: return@filter false
                candidateBase in localById.getValue(online.id).genericLiveBases
            }
            // A generic/specific pair must compete together. An isolated, exact generic Live
            // title retains the ordinary duration tolerance used by all other explicit titles.
            if (liveCandidates.isNotEmpty() && (titleMatches.isEmpty() || liveCandidates.any { onlineLive[it.id]?.generic == false })) {
                if (hiddenVersion(local)) return result(AlbumScopedMatchStatus.REVIEW, "文件名还有标签未注明的录音版本，请核对后选择", ranked)
                if (local.durationMs <= 0L || liveCandidates.any { it.dt <= 0L }) return result(
                    AlbumScopedMatchStatus.REVIEW, "同名现场候选缺少有效时长，无法确认具体录音", ranked)
                val near = liveCandidates.filter { abs(it.dt - local.durationMs) <= 2_000L }
                if (near.size != 1) return result(AlbumScopedMatchStatus.REVIEW,
                    if (near.isEmpty()) "现场候选的时长差超过 2 秒，请核对场次" else "有多个时长接近的现场候选，请核对场次", ranked)
                return confirmed(near.single(), ranked, "已确认专辑内只有一个同名现场录音在 2 秒时长范围内")
            }
        }
        if (titleMatches.isEmpty()) {
            return if (related.isNotEmpty()) result(AlbumScopedMatchStatus.REVIEW, "找到相近歌名，但现场、伴奏或其他版本信息不同，请核对", ranked)
            else result(AlbumScopedMatchStatus.NO_MATCH, "同专辑中未找到同名歌曲，可参考曲号和时长手动选择", ranked)
        }
        if (hiddenVersion(local)) return result(
            AlbumScopedMatchStatus.REVIEW, "文件名还有标签未注明的录音版本，请核对后选择", ranked
        )
        if (local.durationMs <= 0L) return result(AlbumScopedMatchStatus.REVIEW, "歌名相同，但本地歌曲缺少有效时长，请核对", ranked)
        val compatible = titleMatches.filter { it.dt > 0L && abs(it.dt - local.durationMs) <= tolerance(it.dt) }
        if (compatible.isEmpty()) return result(AlbumScopedMatchStatus.REVIEW,
            if (titleMatches.any { it.dt <= 0L }) "歌名相同，但在线曲目缺少有效时长，请核对" else "歌名相同，但时长差超过专辑匹配的允许范围，请核对", ranked)
        if (compatible.size > 1 || titleMatches.any { it.dt <= 0L }) return result(
            AlbumScopedMatchStatus.REVIEW, "专辑中有多个同名候选，无法仅凭歌名和时长确定，请选择", ranked
        )
        val selected = compatible.single()
        return confirmed(selected, ranked, "已确认专辑内歌名唯一对应，时长在放宽范围内；不再要求歌手和专辑标签一致")
    }

    private data class PreparedLocalTitles(val exact: Set<String>, val base: Set<String>, val genericLiveBases: Set<String>)

    private data class LiveTitle(val base: String, val generic: Boolean)

    private fun liveTitle(value: String): LiveTitle? {
        val title = LocalMatchAlbum.forComparison(value)
        val bracket = bracketSuffix.find(title)
        val separated = liveSeparated.find(title)
        val bare = liveBare.find(title)
        val descriptor = when {
            bracket != null && liveStart.containsMatchIn(bracket.groupValues[1]) -> bracket.groupValues[1]
            separated != null -> separated.groupValues[1]
            bare != null -> bare.groupValues[1]
            else -> return null
        }
        val suffix = if (bracket != null && descriptor == bracket.groupValues[1]) bracket else separated ?: bare!!
        val base = title.substring(0, suffix.range.first).trim()
        if (base.isBlank()) return null
        if (nonLiveQualifier.containsMatchIn(descriptor)) return null
        // A Live Remix or acoustic live recording cannot satisfy an ordinary generic Live label.
        if (recordingMarker.findAll("$base $descriptor").any { normalize(it.value) !in setOf("live", "现场") }) return null
        return LiveTitle(base, normalize(descriptor) in setOf("live", "现场"))
    }

    private fun hiddenVersion(local: LocalAudioCandidate): Boolean {
        val original = local.fileName ?: return PreferredLocalAudioMatcher.hiddenVersion(local)
        val dot = original.lastIndexOf('.')
        val stem = if (dot > 0) original.substring(0, dot) else original
        val extension = if (dot > 0) original.substring(dot) else ""
        val cleaned = stem.replace(copyNumberSuffix, "")
        return PreferredLocalAudioMatcher.hiddenVersion(
            if (cleaned == stem) local else local.copy(fileName = cleaned + extension)
        )
    }

    private fun localTitles(local: LocalAudioCandidate, online: Track, resolvedTitle: String): Set<String> = buildSet {
        add(local.title)
        add(resolvedTitle)
        val onlineCredits = online.ar.map { it.name }.filter { it.isNotBlank() } + online.ar.joinToString(" • ") { it.name }
        onlineCredits.filter { it.isNotBlank() }.forEach { artist ->
            // The confirmed album provides a reliable credit even when the local artist is missing.
            add(LocalMatchMetadata.resolve(local.copy(artist = artist)).title)
        }
    }.flatMap { listOf(it, LocalMatchTitle.forSearch(it)) }.flatMap { contextualTitles(it, online) }.filter(::knownTitle).toSet()

    /** Translation aliases do not erase same-language subtitles or unverified recording credits. */
    private fun contextualTitles(value: String, online: Track): Set<String> = buildSet {
        add(value)
        var current = value
        repeat(4) {
            val bracket = bracketSuffix.find(current)
            val featured = featuredSuffix.find(current)
            val next = when {
                bracket != null && verifiedFeature(bracket.groupValues[1], online) -> current.substring(0, bracket.range.first).trim()
                bracket != null && translationAnnotation(
                    featuredSuffix.find(current.substring(0, bracket.range.first))?.let { current.substring(0, it.range.first) }
                        ?: current.substring(0, bracket.range.first), bracket.groupValues[1]) -> current.substring(0, bracket.range.first).trim()
                featured != null && verifiedCredit(featured.groupValues[2], online) -> current.substring(0, featured.range.first).trim()
                else -> return@buildSet
            }
            if (next.isBlank() || next == current) return@buildSet
            add(next)
            current = next
        }
    }

    private fun translationAnnotation(main: String, annotation: String): Boolean {
        if (versionAnnotation.containsMatchIn(annotation) || annotation.trim().matches(Regex("[ivxlcdm]+", RegexOption.IGNORE_CASE))) return false
        val mainHan = han.containsMatchIn(main)
        val mainLatin = latin.containsMatchIn(main)
        val annotationHan = han.containsMatchIn(annotation)
        val annotationLatin = latin.containsMatchIn(annotation)
        return (mainHan && !mainLatin && annotationLatin && !annotationHan) ||
            (mainLatin && !mainHan && annotationHan && !annotationLatin)
    }

    private fun verifiedFeature(value: String, online: Track): Boolean = featuredBracket.matchEntire(value.trim())
        ?.groupValues?.get(1)?.let { verifiedCredit(it, online) } == true

    private fun verifiedCredit(value: String, online: Track): Boolean {
        val names = online.ar.map { normalize(it.name) }.filter { it.isNotBlank() }.toSet()
        val normalized = normalize(value)
        if (normalized in names) return true
        val parts = value.split(creditSeparator).map(::normalize).filter(String::isNotBlank)
        return parts.size > 1 && names.containsAll(parts)
    }

    private fun tolerance(durationMs: Long): Long = (durationMs / 20L).coerceIn(5_000L, 15_000L)

    private fun normalize(value: String): String = Normalizer.normalize(value, Normalizer.Form.NFKC)
        .trim().lowercase(Locale.ROOT).replace(whitespace, " ")

    private fun knownTitle(value: String): Boolean = normalize(value) !in unknownTitles

    private fun comparable(value: String): String {
        val normalized = normalize(LocalMatchAlbum.forComparison(value))
        // Punctuation in a numeric title can carry its meaning: 3.14 must not become 314.
        return if (normalized.any { it.isLetter() }) normalized.replace(punctuationAndSpace, "") else normalized
    }

    /** For suggestions only; all removed recording annotations still prevent automatic matching. */
    private fun recordingBase(value: String): String {
        var body = LocalMatchAlbum.forComparison(value)
        while (true) {
            val bracket = bracketSuffix.find(body)
            if (bracket != null && recordingMarker.containsMatchIn(bracket.groupValues[1])) {
                body = body.substring(0, bracket.range.first).trim()
                continue
            }
            val separated = separatedVersion.find(body)?.takeIf { recordingMarker.containsMatchIn(it.groupValues[1]) }
            val bare = bareVersion.find(body)
            val suffix = separated ?: bare ?: return body
            return body.substring(0, suffix.range.first).trim()
        }
    }
}

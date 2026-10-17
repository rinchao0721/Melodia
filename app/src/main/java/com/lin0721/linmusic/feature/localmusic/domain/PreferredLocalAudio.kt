package com.lin0721.linmusic.feature.localmusic.domain

import java.text.Normalizer
import java.util.Locale
import kotlin.math.abs

data class LocalAudioCandidate(
    val uri: String,
    val title: String,
    val artist: String,
    val durationMs: Long,
    // MediaStore and SAF can refer to the same physical audio file.
    val identity: String? = null,
    val album: String? = null,
    val fileName: String? = null
)

/** Matching keeps recording versions, while accepting incomplete local collaboration credits. */
object PreferredLocalAudioMatcher {
    private val whitespace = Regex("\\s+")
    private val artistSeparator = Regex("\\s*(?:[,，、;；•]|\\s+/\\s+)\\s*")
    private val unknownArtists = setOf("", "<unknown>", "unknown", "未知艺术家", "未知歌手")
    private val recordingMarker = Regex("(?i)\\b(live|acoustic|instrumental|karaoke|remix|demo|re[- ]?record(?:ed|ing))\\b|现场|重录|伴奏|不插电")
    private val versionBracket = Regex("\\s*[（(\\[【]([^()（）\\[\\]【】]+)[）)\\]】]\\s*$")
    private val versionDelimiter = Regex("\\s*[-–—:]\\s*")
    private val nestedVersionSuffix = Regex("[（(\\[【](.+)[）)\\]】]$")
    private val bareVersionSuffix = Regex("(?i)(?:^|\\s)((?:live|acoustic|instrumental|karaoke|remix|demo|re[- ]?record(?:ed|ing))(?:\\s+(?:version|recording))?|(?:\\d{4}年?\\s*)?(?:现场|重录|伴奏|不插电)(?:版|版本)?)$")
    private val specificLiveSuffix = Regex("(?i)(?:^|\\s)(live\\s+(?:at|in|from|recorded at)\\s+.+)$")
    private val genericVersions = setOf("live", "acoustic", "instrumental", "karaoke", "remix", "demo", "re-recorded", "re-recording", "现场", "重录", "伴奏", "不插电")

    private fun normalize(value: String): String = Normalizer.normalize(value, Normalizer.Form.NFKC)
        .trim().lowercase(Locale.ROOT).replace(whitespace, " ")

    private fun artists(value: String): Set<String> = value.split(artistSeparator)
        .map(::normalize).filter { it !in unknownArtists }.toSet()

    fun sameTitle(title: String, other: String): Boolean = normalize(title).isNotEmpty() && normalize(title) == normalize(other)

    fun sameLocalTitle(onlineTitle: String, localTitle: String): Boolean = LocalMatchTitle.matches(onlineTitle, localTitle)

    fun sameCandidateTitle(onlineTitle: String, candidate: LocalAudioCandidate): Boolean =
        sameLocalTitle(onlineTitle, candidate.title) || sameLocalTitle(onlineTitle, LocalMatchMetadata.resolve(candidate).title)

    internal fun hasKnownArtist(value: String): Boolean {
        val values = value.split(artistSeparator).map(::normalize)
        return values.isNotEmpty() && values.all { it !in unknownArtists }
    }

    internal fun sameAlbum(album: String?, other: String?): Boolean =
        !album.isNullOrBlank() && !other.isNullOrBlank() && LocalMatchAlbum.forComparison(album) == LocalMatchAlbum.forComparison(other)

    internal fun exactAlbum(album: String?, other: String?): Boolean =
        !album.isNullOrBlank() && !other.isNullOrBlank() && normalize(album) == normalize(other)

    internal fun completeArtistCredit(onlineArtist: String, candidate: LocalAudioCandidate): Boolean =
        (hasKnownArtist(candidate.artist) && artists(onlineArtist) == artists(candidate.artist)) ||
            artists(onlineArtist) == artists(LocalMatchMetadata.resolve(candidate).artist)

    fun sameMetadata(title: String, artist: String, candidate: LocalAudioCandidate, onlineAlbum: String? = null): Boolean {
        val names = artists(artist)
        val resolved = LocalMatchMetadata.resolve(candidate)
        if (!sameCandidateTitle(title, candidate) || names.isEmpty() || !hasKnownArtist(resolved.artist) || hiddenVersion(candidate)) return false
        // Original exact credits remain authoritative, including an artist whose
        // actual name starts with digits. File-fallback cleanup is an alternative.
        if (hasKnownArtist(candidate.artist) && names.containsAll(artists(candidate.artist))) return true
        val localNames = artists(resolved.artist)
        if (localNames.isNotEmpty() && names.containsAll(localNames)) return true
        // Some real tags serialize a verified multi-artist credit as A/B. A single
        // online artist such as AC/DC remains a whole name; require matching album.
        return names.size > 1 && names.none { '/' in it } && "ac/dc" !in localNames && sameAlbum(onlineAlbum, candidate.album) &&
            localNames.flatMap { it.split('/') }.map(::normalize).filter { it !in unknownArtists }.toSet().let {
                it.isNotEmpty() && names.containsAll(it)
            }
    }

    internal fun hiddenVersion(candidate: LocalAudioCandidate): Boolean {
        val fileName = candidate.fileName?.substringBeforeLast('.') ?: return false
        val resolved = LocalMatchMetadata.resolve(candidate)
        val nameWithoutArtist = LocalMatchMetadata.resolve(candidate.copy(title = fileName, artist = resolved.artist)).title
        val titleVersions = recordingQualifiers(resolved.title)
        return recordingQualifiers(nameWithoutArtist).any { fileVersion ->
            titleVersions.none { titleVersion -> titleVersion == fileVersion ||
                // A generic filename may omit detail already present in the title;
                // a filename's extra venue/year/remix credit must never be discarded.
                (fileVersion in genericVersions && recordingMarker.find(titleVersion)?.value == fileVersion)
            }
        }
    }

    private fun recordingQualifiers(value: String): Set<String> {
        var body = LocalMatchAlbum.forComparison(value)
        val groups = mutableListOf<String>()
        while (true) {
            val bracket = versionBracket.find(body) ?: break
            groups.add(0, bracket.groupValues[1])
            body = body.substring(0, bracket.range.first).trim()
        }
        fun canonical(text: String): String = LocalMatchAlbum.forComparison(text)
            .replace(Regex("\\b(?:version|recording)\\b"), "")
            .replace(Regex("re[- ]?record(ed|ing)"), "re-record$1")
            .replace(Regex("(现场|重录|伴奏|不插电)(?:版本|版)"), "$1")
            .replace(whitespace, " ").trim()
        val qualifiers = mutableSetOf<String>()
        groups.forEachIndexed { index, group ->
            if (recordingMarker.containsMatchIn(group)) {
                val context = groups.drop(index + 1).takeWhile { !recordingMarker.containsMatchIn(it) }
                    .joinToString("") { " ($it)" }
                qualifiers += canonical(group + context)
            }
        }
        val trailing = versionDelimiter.findAll(body).toList().asReversed().firstNotNullOfOrNull { delimiter ->
            body.substring(delimiter.range.last + 1).takeIf { recordingMarker.containsMatchIn(it) }
        } ?: specificLiveSuffix.find(body)?.groupValues?.get(1) ?: bareVersionSuffix.find(body)?.groupValues?.get(1)
        if (trailing != null) {
            val context = groups.takeWhile { !recordingMarker.containsMatchIn(it) }.joinToString("") { " ($it)" }
            qualifiers += canonical(trailing + context)
        }
        if (qualifiers.isEmpty()) {
            nestedVersionSuffix.find(LocalMatchAlbum.forComparison(value))?.groupValues?.get(1)
                ?.takeIf { recordingMarker.containsMatchIn(it) }?.let { qualifiers += canonical(it) }
        }
        return qualifiers
    }

    private fun formatRank(candidate: LocalAudioCandidate): Int = when (candidate.fileName?.substringAfterLast('.')?.lowercase(Locale.ROOT)) {
        "flac", "ape" -> 4
        "wav", "aiff", "aif" -> 3
        "m4a", "alac" -> 2
        else -> 1
    }

    internal fun matchingDuration(durationMs: Long, candidate: LocalAudioCandidate, onlineAlbum: String? = null): Boolean {
        if (durationMs <= 0L || candidate.durationMs <= 0L) return false
        val tolerance = if (sameAlbum(onlineAlbum, candidate.album)) (durationMs / 50L).coerceIn(2_000L, 5_000L) else 2_000L
        return abs(candidate.durationMs - durationMs) <= tolerance
    }

    fun uniqueMatch(title: String, artist: String, durationMs: Long, candidates: List<LocalAudioCandidate>, onlineAlbum: String? = null): String? {
        if (durationMs <= 0L) return null
        val matches = candidates.filter {
            sameMetadata(title, artist, it, onlineAlbum) && matchingDuration(durationMs, it, onlineAlbum)
        }.distinctBy { it.identity ?: it.uri }
        if (matches.size == 1) return matches.single().uri
        val equivalent = matches.filter { sameAlbum(onlineAlbum, it.album) }
        if (equivalent.isEmpty()) return null
        // A verified album/version is enough to rank equivalent files without asking
        // the user to choose another encoding or a regional/remastered release.
        return equivalent.sortedWith(compareByDescending<LocalAudioCandidate> { exactAlbum(onlineAlbum, it.album) }
            .thenByDescending { completeArtistCredit(artist, it) }
            .thenByDescending { formatRank(it) }
            .thenBy { abs(it.durationMs - durationMs) }
            .thenBy { it.uri }).first().uri
    }
}

/** An explicit choice wins, even when unavailable: never silently substitute another local version. */
suspend fun resolvePreferredLocalAudio(
    enabled: Boolean,
    manualUri: String?,
    automaticEnabled: Boolean,
    automaticUri: suspend () -> String?,
    isReadable: suspend (String) -> Boolean
): String? {
    if (!enabled) return null
    if (manualUri != null) return manualUri.takeIf { isReadable(it) }
    if (!automaticEnabled) return null
    val uri = automaticUri() ?: return null
    return uri.takeIf { isReadable(it) }
}

data class LocalAudioSelectionSettings(val enabled: Boolean, val manualUri: String?, val automaticEnabled: Boolean)

/** Revalidate user intent after a potentially slow metadata lookup, without issuing another lookup. */
suspend fun resolveCurrentPreferredLocalAudio(
    initial: LocalAudioSelectionSettings,
    latestSettings: suspend () -> LocalAudioSelectionSettings,
    automaticUri: suspend () -> String?,
    isReadable: suspend (String) -> Boolean,
): String? {
    var automatic: String? = null
    val resolved = resolvePreferredLocalAudio(initial.enabled, initial.manualUri, initial.automaticEnabled,
        automaticUri = { automaticUri().also { automatic = it } }, isReadable = isReadable)
    val latest = latestSettings()
    if (latest == initial) return resolved
    // A removed manual choice is not an automatic candidate. A newly selected unreadable manual
    // file still falls back to the ordinary player instead of silently playing the old candidate.
    return resolvePreferredLocalAudio(latest.enabled, latest.manualUri, latest.automaticEnabled,
        automaticUri = { automatic }, isReadable = isReadable)
}

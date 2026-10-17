package com.lin0721.linmusic.feature.localmusic.domain

import java.text.Normalizer
import java.util.Locale

/** Resolve display/file-name fallbacks for matching without changing the library or audio tags. */
object LocalMatchMetadata {
    private val unknownArtists = setOf("", "<unknown>", "unknown", "未知艺术家", "未知歌手")
    private val artistSeparator = Regex("\\s*(?:[,，、;；•]|\\s+/\\s+)\\s*")
    private val standardFileCredit = Regex("^(.+?)\\s+[-–—]\\s+(.+)$")
    private val audioExtensions = setOf("flac", "mp3", "m4a", "aac", "ogg", "opus", "wav", "wave", "aif", "aiff", "ape", "alac", "wma")

    fun resolve(candidate: LocalAudioCandidate): LocalAudioCandidate {
        val filenameStem = candidate.fileName?.let(::fileStem).orEmpty()
        val filenameTitle = LocalMatchTitle.forSearch(filenameStem)
        val originalTitle = Normalizer.normalize(candidate.title.ifBlank { filenameTitle }, Normalizer.Form.NFKC).trim()
        val title = LocalMatchTitle.forSearch(originalTitle)
        // Older scan/import fallbacks split "01 Artist - Song" before stripping the
        // index, leaving it attached to artist. Recover only with the actual file
        // credit and its unchanged song side; never strip digits from arbitrary tags.
        val originalCredit = standardFileCredit.matchEntire(filenameStem)
        val cleanCredit = standardFileCredit.matchEntire(filenameTitle)
        if (originalCredit != null && cleanCredit != null && normalized(filenameStem) != normalized(filenameTitle) &&
            normalized(candidate.artist) == normalized(originalCredit.groupValues[1]) &&
            normalized(title) == normalized(LocalMatchTitle.forSearch(originalCredit.groupValues[2]))) {
            return candidate.copy(title = title, artist = cleanCredit.groupValues[1].trim())
        }
        val known = candidate.artist.split(artistSeparator).filter { normalized(it) !in unknownArtists }
        val anchors = (listOf(candidate.artist) + known).filter { normalized(it) !in unknownArtists }
            .distinctBy(::normalized).sortedByDescending { it.length }
        if (anchors.isNotEmpty()) {
            for (artist in anchors) {
                val artistPattern = Normalizer.normalize(artist, Normalizer.Form.NFKC).trim().split(Regex("\\s+"))
                    .joinToString("\\s+") { Regex.escape(it) }
                val prefix = Regex("^$artistPattern\\s*[-–—_]\\s*(.+)$", RegexOption.IGNORE_CASE)
                val suffix = Regex("^(.+?)\\s*[-–—_]\\s*$artistPattern$", RegexOption.IGNORE_CASE)
                val resolved = prefix.matchEntire(originalTitle)?.groupValues?.get(1)
                    ?: suffix.matchEntire(originalTitle)?.groupValues?.get(1)
                    ?: prefix.matchEntire(title)?.groupValues?.get(1)
                    ?: suffix.matchEntire(title)?.groupValues?.get(1)
                if (!resolved.isNullOrBlank()) return candidate.copy(title = LocalMatchTitle.forSearch(resolved))
            }
            return candidate.copy(title = title)
        }
        // An unknown artist does not make every hyphenated tagged title a credit.
        // Only the conventional Artist - Song file-name fallback has that evidence.
        val credit = standardFileCredit.matchEntire(filenameTitle)
        if (credit != null && normalized(credit.groupValues[1]) !in unknownArtists) {
            val fileArtist = credit.groupValues[1].trim()
            val fileSong = LocalMatchTitle.forSearch(credit.groupValues[2])
            if (normalized(title) == normalized(filenameTitle) || normalized(title) == normalized(fileSong)) {
                return candidate.copy(title = fileSong, artist = fileArtist)
            }
            if (normalized(title) == normalized(fileArtist)) {
                return candidate.copy(title = title, artist = credit.groupValues[2].trim())
            }
        }
        return candidate.copy(title = title)
    }

    private fun fileStem(value: String): String {
        val name = value.substringAfterLast('/').substringAfterLast('\\')
        return if (name.substringAfterLast('.', "").lowercase(Locale.ROOT) in audioExtensions) name.substringBeforeLast('.') else name
    }

    private fun normalized(value: String): String = Normalizer.normalize(value, Normalizer.Form.NFKC)
        .trim().lowercase(Locale.ROOT).replace(Regex("\\s+"), " ")
}

/** Ignore release annotations; keep the actual album name and recording/version annotations. */
object LocalMatchAlbum {
    private val whitespace = Regex("\\s+")
    private val bracketSuffix = Regex("\\s*[（(\\[【]([^()（）\\[\\]【】]+)[）)\\]】]\\s*$")
    private val separatedSuffix = Regex("[-–—:]\\s*([^\\-–—:]+)$")
    private val trailingRelease = Regex("(?i)\\s+((?:(?:19|20)\\d{2}\\s+)?(?:remaster(?:ed)?(?:\\s+(?:edition|version|release))?|(?:japan(?:ese)?|us|usa|uk|eu|europe(?:an)?|korea(?:n)?|china|chinese|taiwan|hong kong|international|domestic)\\s+(?:edition|version|release))|(?:\\d{4}年?)?(?:重制|重铸|重新母带)(?:版|版本)?|(?:日本|日|美国|美|英国|英|欧洲|欧|韩国|韩|台湾|台|香港|港|大陆|中国)(?:地区)?(?:发行)?版)\\s*$")

    fun forComparison(album: String): String {
        val original = normalize(album)
        var current = original
        while (true) {
            val bracket = bracketSuffix.find(current)
            val separated = separatedSuffix.find(current)
            val next = when {
                bracket != null && isReleaseAnnotation(bracket.groupValues[1]) -> current.substring(0, bracket.range.first).trimReleaseSeparator()
                separated != null && isReleaseAnnotation(separated.groupValues[1]) -> current.substring(0, separated.range.first).trimReleaseSeparator()
                else -> trailingRelease.find(current)?.let { current.substring(0, it.range.first).trimReleaseSeparator() } ?: current
            }
            if (next.isEmpty() || next == current) return current.ifEmpty { original }
            current = next
        }
    }

    internal fun releasePenalty(album: String): Int = if (normalize(album) == forComparison(album)) 0 else 1

    private fun isReleaseAnnotation(value: String): Boolean {
        val normalized = normalize(value)
        val compact = normalized.replace(" ", "")
        if (compact.matches(Regex("(?:\\d{4}年?)?(?:重制|重铸|重新母带)(?:版|版本)?"))) return true
        if (compact.matches(Regex("(?:日本|日|美国|美|英国|英|欧洲|欧|韩国|韩|台湾|台|香港|港|大陆|中国)(?:地区)?(?:发行)?版"))) return true
        val words = normalized.replace(Regex("\\b(?:19|20)\\d{2}\\b"), "").replace(Regex("[-–—_/.,]"), " ")
            .split(whitespace).filter { it.isNotEmpty() }
        val allowed = setOf("remaster", "remastered", "edition", "version", "release", "japan", "japanese", "us", "usa", "uk", "eu", "europe", "european", "korea", "korean", "china", "chinese", "taiwan", "hong", "kong", "international", "domestic", "united", "states", "kingdom")
        return words.isNotEmpty() && words.all { it in allowed } && words.any { it !in setOf("edition", "version", "release") }
    }

    private fun normalize(value: String): String = Normalizer.normalize(value, Normalizer.Form.NFKC)
        .trim().lowercase(Locale.ROOT).replace(whitespace, " ")

    private fun String.trimReleaseSeparator(): String = trim().trimEnd('-', '–', '—', ':').trim()
}

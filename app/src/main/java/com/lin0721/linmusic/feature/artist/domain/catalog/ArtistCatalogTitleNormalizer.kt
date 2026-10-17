package com.lin0721.linmusic.feature.artist.domain.catalog

import java.text.Normalizer
import java.util.Locale

class ArtistCatalogTitleNormalizer(
    private val converter: (String) -> String = OpenCcCharacterConverter.bundled::convert
) {
    fun normalizeTitle(value: String): String {
        val converted = canonicalText(value)
        val letters = buildString {
            var offset = 0
            while (offset < converted.length) {
                val codePoint = converted.codePointAt(offset)
                if (Character.isLetterOrDigit(codePoint)) append(Character.toChars(codePoint))
                offset += Character.charCount(codePoint)
            }
        }
        // A valid album can have a punctuation-only title, e.g. Eason Chan's "?".
        return letters.ifEmpty { converted.filterNot(Char::isWhitespace) }
    }

    fun normalizeArtistName(value: String): String = normalizeTitle(value)

    fun baseTitle(value: String): String = normalizeTitle(withoutEdition(value))

    fun isEditionTitle(value: String): Boolean = normalizeTitle(value) != baseTitle(value)

    private fun canonicalText(value: String): String =
        converter(Normalizer.normalize(value, Normalizer.Form.NFKC)).lowercase(Locale.ROOT).trim()

    private fun withoutEdition(value: String): String {
        var result = canonicalText(value)
        repeat(8) {
            val bracket = trailingBracket(result)
            if (bracket != null && isEditionDescription(result.substring(bracket.first + 1, bracket.last))) {
                val prefix = result.substring(0, bracket.first).trim().trimEnd('-', '–', '—', ':').trim()
                if (prefix.isNotEmpty()) result = prefix
            } else {
                val suffix = BARE_EDITION.find(result)
                if (suffix != null) {
                    val prefix = result.substring(0, suffix.range.first).trim().trimEnd('-', '–', '—', ':').trim()
                    if (prefix.isNotEmpty()) result = prefix
                }
            }
        }
        return result
    }

    /** Peel a whole balanced descriptor; the inner brackets can themselves describe an edition. */
    private fun trailingBracket(value: String): IntRange? {
        val closing = value.lastOrNull() ?: return null
        val opening = BRACKET_PAIRS[closing] ?: return null
        var depth = 0
        for (index in value.lastIndex downTo 0) {
            when (value[index]) {
                closing -> depth++
                opening -> {
                    depth--
                    if (depth == 0) return index..value.lastIndex
                }
            }
        }
        return null
    }

    private fun isEditionDescription(value: String): Boolean {
        if (RECORDING_VERSION.containsMatchIn(value)) return false
        val text = value.replace(HYPHENATED_REMASTER, "remastered")
            .replace(EDITION_SEPARATORS, " ").trim()
        val englishWords = text.split(Regex("\\s+")).filter(String::isNotBlank)
        if (englishWords.isNotEmpty() && englishWords.all { ENGLISH_EDITION_WORD.matches(it) } &&
            englishWords.any { ENGLISH_EDITION_ANCHOR.matches(it) }) return true
        val chinese = text.filterNot(Char::isWhitespace)
        return CHINESE_EDITION.matches(chinese) && CHINESE_EDITION_ANCHOR.containsMatchIn(chinese)
    }

    companion object {
        private val BRACKET_PAIRS = mapOf(')' to '(', ']' to '[', '}' to '{', '】' to '【')
        private val HYPHENATED_REMASTER = Regex("\\bre[-‐‑–—]master(?:ed|ing)?\\b")
        private val EDITION_SEPARATORS = Regex("[.,_+/:;()\\[\\]{}【】\\-‐‑–—]")
        private val RECORDING_VERSION = Regex("\\b(?:live|remix|instrumental|acoustic|karaoke|demo|rerecorded|re-recorded)\\b|现场|演唱会|伴奏|混音|重新录制")
        private val ENGLISH_EDITION_WORD = Regex("(?:\\d{1,4}(?:st|nd|rd|th)?|super|ultimate|deluxe|expanded|anniversary|remaster(?:ed|ing)?|reissue|edition|version|special|limited|international|japan(?:ese)?|usa?|uk|eu|european|hong|kong|taiwan|chinese|china|bonus|tracks?|cd|digital(?:ly)?|original|release)")
        private val ENGLISH_EDITION_ANCHOR = Regex("(?:deluxe|expanded|anniversary|remaster(?:ed|ing)?|reissue|edition|version|international|japan(?:ese)?|usa?|uk|eu|european|taiwan)")
        private val CHINESE_EDITION = Regex("(?:\\d{1,4}年?|\\d+周年|重制|重铸|再|纪念|豪华|扩展|特别|限定|日本|日|美国|美|英国|英|欧洲|欧|香港|港|台湾|内地|中国|国际|周年|版|发行)+")
        private val CHINESE_EDITION_ANCHOR = Regex("重制|重铸|纪念|豪华|扩展|再版|特别版|限定版|日本版|日版|美国版|美版|英国版|英版|欧洲版|欧版|香港版|港版|台湾版|内地版|中国版|国际版|周年")
        private val BARE_EDITION = Regex("\\s+(?:(?:19|20)\\d{2}\\s+)?(?:(?:super\\s+|ultimate\\s+)?deluxe(?:\\s+edition)?|expanded(?:\\s+edition)?|(?:digitally\\s+)?(?:remaster(?:ed|ing)?|re[-‐‑–—]master(?:ed|ing)?)(?:\\s+(?:19|20)\\d{2})?|\\d+(?:st|nd|rd|th)?\\s+anniversary(?:\\s+(?:super\\s+)?deluxe)?(?:\\s+edition)?|(?:japanese|japan|us|uk|eu|international)\\s+(?:edition|version))\\s*$")
    }
}

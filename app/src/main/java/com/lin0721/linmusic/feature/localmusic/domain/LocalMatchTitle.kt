package com.lin0721.linmusic.feature.localmusic.domain

import java.text.Normalizer
import java.util.Locale

/** Search-only cleanup: never write the cleaned value back to a file or the library. */
object LocalMatchTitle {
    private val whitespace = Regex("\\s+")
    private val zeroPrefixedNumber = Regex("^0\\d{1,2}\\s+(\\S.*)$")
    private val markedNumber = Regex("^(0\\d{1,2}|\\d{1,2})(\\s*)([.、)\\-—])(\\s*)(\\S.*)$")

    fun forSearch(title: String): String = stripObviousTrackNumber(title)

    /** A bare number, a year, a decimal and titles such as `7 rings` remain intact. */
    fun stripObviousTrackNumber(title: String): String {
        val value = Normalizer.normalize(title, Normalizer.Form.NFKC).trim()
        val marked = markedNumber.matchEntire(value)
        if (marked != null) {
            val number = marked.groupValues[1]
            val before = marked.groupValues[2]
            val separator = marked.groupValues[3]
            val after = marked.groupValues[4]
            val remainder = marked.groupValues[5]
            // A decimal/time-like title and an unspaced title such as 10-Feet
            // are insufficient evidence for a track index.
            if (separator == "." && after.isEmpty() && remainder.first().isDigit()) return value
            if (separator in setOf("-", "—") && !number.startsWith('0') && before.isEmpty() && after.isEmpty()) return value
            return remainder.trim()
        }
        return zeroPrefixedNumber.matchEntire(value)?.groupValues?.get(1)?.trim() ?: value
    }

    /** Only the local side is stripped; the online catalog title stays authoritative. */
    fun matches(onlineTitle: String, localTitle: String): Boolean {
        val online = comparable(onlineTitle)
        if (online.isEmpty()) return false
        return online == comparable(localTitle) || online == comparable(forSearch(localTitle))
    }

    private fun comparable(value: String): String = Normalizer.normalize(value, Normalizer.Form.NFKC)
        .trim().lowercase(Locale.ROOT).replace(whitespace, " ")
}

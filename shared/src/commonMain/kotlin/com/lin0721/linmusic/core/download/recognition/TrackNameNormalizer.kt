package com.lin0721.linmusic.core.download.recognition

import java.text.Normalizer

private val ARTIST_SEPARATORS = Regex("""\s*(?:/|;|、|&|,|，|\bfeat\b\.?|\bft\b\.?)\s*""", RegexOption.IGNORE_CASE)

// 歌名、歌手比对前的归一化
object TrackNameNormalizer {

    // 全角转半角、去大小写，只保留字母与数字（含中日韩文字）
    fun normalize(text: String): String =
        Normalizer.normalize(text, Normalizer.Form.NFKC).lowercase().filter { it.isLetterOrDigit() }

    fun splitArtists(raw: String): Set<String> =
        raw.split(ARTIST_SEPARATORS).map(::normalize).filterTo(HashSet()) { it.isNotEmpty() }
}

package com.lin0721.linmusic.core.player.domain

import com.lin0721.linmusic.core.log.AppLogger
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min

// 剥离歌词首尾的作词、作曲等信息行，算法参照 SPlayer 的 lyricStripper：
// 强匹配行（关键词+分隔符或命中正则）删除；弱匹配行（带冒号/横杠）只在夹在强匹配行之间时删除，
// 避免误删"男："这类演唱者标注；遇到正常歌词行即停止，歌词中间的内容不动。
class LyricMetadataStripper(rules: LyricFilterRules) {

    private val keywords: List<String> = rules.keywords
        .map { normalize(it) }
        .filter { it.isNotEmpty() }
        .distinct()

    private val regexes: List<Regex> = rules.regexes.mapNotNull { pattern ->
        if (pattern.isBlank()) return@mapNotNull null
        try {
            Regex(pattern, RegexOption.IGNORE_CASE)
        } catch (e: Exception) {
            AppLogger.w(TAG, "无效的歌词过滤正则: $pattern ${e.message}")
            null
        }
    }

    val isEmpty: Boolean get() = keywords.isEmpty() && regexes.isEmpty()

    fun strip(lines: List<LyricLine>): List<LyricLine> {
        if (lines.isEmpty() || isEmpty) return lines
        val total = lines.size
        val start = findHeaderCutoff(lines, scanLimit(HEADER_RATIO, HEADER_MIN, HEADER_MAX, total))
        val end = findFooterCutoff(lines, start, scanLimit(FOOTER_RATIO, FOOTER_MIN, FOOTER_MAX, total))
        if (start == 0 && end == total) return lines
        AppLogger.d(TAG, "歌词过滤 $total -> ${end - start} 行")
        return lines.subList(start, end).toList()
    }

    private fun findHeaderCutoff(lines: List<LyricLine>, limit: Int): Int {
        var lastStrict = -1
        for (i in 0 until limit) {
            val text = lines[i].text.trim()
            if (text.isEmpty()) continue
            val strict = isStrictMatch(text)
            if (!strict && !looksLikeMetadata(text)) break
            if (strict) lastStrict = i
        }
        return lastStrict + 1
    }

    private fun findFooterCutoff(lines: List<LyricLine>, start: Int, limit: Int): Int {
        if (start >= lines.size) return start
        val scanEnd = max(start, lines.size - limit)
        var firstStrict = lines.size
        for (i in lines.size - 1 downTo scanEnd) {
            val text = lines[i].text.trim()
            if (text.isEmpty()) continue
            val strict = isStrictMatch(text)
            if (!strict && !looksLikeMetadata(text)) break
            if (strict) firstStrict = i
        }
        return firstStrict
    }

    private fun isStrictMatch(text: String): Boolean {
        val normalized = normalize(stripBrackets(text))
        for (keyword in keywords) {
            if (!normalized.startsWith(keyword)) continue
            val remainder = normalized.substring(keyword.length)
            if (remainder.isEmpty() || remainder[0] in SEPARATORS) return true
        }
        return regexes.any { it.containsMatchIn(text) }
    }

    private fun looksLikeMetadata(text: String): Boolean {
        val cleaned = stripBrackets(text)
        return cleaned.contains(':') || cleaned.contains('：') || cleaned.contains('-')
    }

    private companion object {
        const val TAG = "LyricMetadataStripper"
        const val HEADER_RATIO = 0.2
        const val HEADER_MIN = 20
        const val HEADER_MAX = 70
        const val FOOTER_RATIO = 0.2
        const val FOOTER_MIN = 20
        const val FOOTER_MAX = 50
        const val MAX_BRACKET_PASSES = 5

        val SEPARATORS = setOf(':', '：', ',', '，', '.', '。', '!', '！', '-', '_', '(', '（', '[', '【', '{', '『', '「')
        val BRACKETS = listOf('(' to ')', '（' to '）', '【' to '】', '[' to ']', '{' to '}', '『' to '』', '「' to '」')
        val WHITESPACE = Regex("\\s+")

        fun normalize(text: String): String = text.lowercase().replace(WHITESPACE, "")

        fun scanLimit(ratio: Double, minLines: Int, maxLines: Int, total: Int): Int {
            val proportional = ceil(total * ratio).toInt()
            return min(min(max(proportional, minLines), maxLines), total)
        }

        // "(作曲：xx)" 去掉外层括号；"(Live) 作曲：xx" 去掉开头的括号段
        fun stripBrackets(text: String): String {
            var processed = text.trim()
            var pass = 0
            var changed = true
            while (changed && pass < MAX_BRACKET_PASSES) {
                changed = false
                pass++
                for ((open, close) in BRACKETS) {
                    if (!processed.startsWith(open)) continue
                    if (processed.length >= 2 && processed.endsWith(close)) {
                        processed = processed.substring(1, processed.length - 1).trim()
                        changed = true
                        break
                    }
                    val closeIndex = processed.indexOf(close)
                    if (closeIndex > -1) {
                        val after = processed.substring(closeIndex + 1).trim()
                        if (after.isNotEmpty()) {
                            processed = after
                            changed = true
                            break
                        }
                    }
                }
            }
            return processed
        }
    }
}

package com.lin0721.linmusic.core.player.domain

// QRC 逐字歌词文本解析为 [LyricLine] 列表
object QrcLyricParser {

    private val lyricContentAttrRegex = Regex("""LyricContent="([^"]*)"""")
    private val lyricContentTagRegex = Regex("""<LyricContent>([\s\S]*?)</LyricContent>""")

    private val metaTagRegex = Regex("""^\[(ti|ar|al|by|offset|kana|romaji):""", RegexOption.IGNORE_CASE)
    private val qrcLineHeaderRegex = Regex("""^\[(\d+),(\d+)](.*)$""")
    private val lrcLineHeaderRegex = Regex("""^\[(\d{2,}):(\d{2})[.:](\d{2,3})](.*)$""")
    private val qrcWordRegex = Regex("""(.*?)\((\d+),(\d+)\)""")
    private val wordTagStripRegex = Regex("""\(\d+,\d+\)""")
    private val strongNoticeMarkers = listOf("著作权", "未经许可", "不得转载", "Provided by", "TME享有")
    private val headerNoticeMarkers = listOf("QQ音乐", "腾讯音乐", "字幕", "制作", "出品", "TME")

    fun parse(rawText: String, translation: String? = null, roma: String? = null): List<LyricLine> {
        if (rawText.isBlank()) return emptyList()
        val content = extractLyricContent(rawText)
        val lines = content.lines().mapNotNull(::parseLine).sortedBy { it.timeMs }
        return attach(attach(lines, translation, false), roma, true)
    }

    // 与 Mei 一致，按三秒内最近的起点配对；每条主歌词只接受一条译文或音译。
    private fun attach(lines: List<LyricLine>, raw: String?, romanization: Boolean): List<LyricLine> {
        if (raw.isNullOrBlank()) return lines
        val content = extractLyricContent(raw)
        val extra = content.lines().mapNotNull(::parseLine).sortedBy { it.timeMs }
        val used = mutableSetOf<Int>()
        val result = lines.toMutableList()
        val mainStartMs = lines.minOfOrNull { it.timeMs } ?: return lines
        for (line in extra) {
            if (isNotice(line, mainStartMs)) continue
            val index = result.indices.filter { it !in used && (result[it].durationMs == 0L || result[it].durationMs >= 300) }
                .filter { kotlin.math.abs(result[it].timeMs - line.timeMs) <= 3000 }
                .minWithOrNull(compareBy<Int> { kotlin.math.abs(result[it].timeMs - line.timeMs) }.thenByDescending { result[it].text.length })
                ?: continue
            result[index] = if (romanization) result[index].copy(roma = line.text)
                else result[index].copy(translation = line.text)
            used += index
        }
        return result
    }

    // 强标记任意位置都是版权声明；弱标记词也常见于正常译文，只在早于首条正文的头部区才算声明。
    private fun isNotice(line: LyricLine, mainStartMs: Long): Boolean {
        if (line.text.startsWith("//")) return true
        if (strongNoticeMarkers.any { line.text.contains(it, ignoreCase = true) }) return true
        return line.timeMs < mainStartMs && headerNoticeMarkers.any { line.text.contains(it, ignoreCase = true) }
    }

    private fun extractLyricContent(raw: String): String {
        val trimmed = raw.trim()
        lyricContentAttrRegex.find(trimmed)?.let { return decodeXmlEntities(it.groupValues[1]) }
        lyricContentTagRegex.find(trimmed)?.let { return decodeXmlEntities(it.groupValues[1]) }
        return if (trimmed.startsWith("<")) decodeXmlEntities(trimmed) else raw
    }

    private fun parseLine(rawLine: String): LyricLine? {
        val trimmed = rawLine.trim()
        if (trimmed.isEmpty() || metaTagRegex.containsMatchIn(trimmed)) return null

        val (lineStartTime, declaredDuration, body) = parseHeader(trimmed) ?: return null
        val matches = qrcWordRegex.findAll(body).toList()

        if (matches.isEmpty()) {
            val plain = body.replace(wordTagStripRegex, "").trim()
            if (plain.isEmpty()) return null
            return LyricLine(timeMs = lineStartTime, durationMs = declaredDuration, text = plain)
        }

        val wordsList = mutableListOf<WordInfo>()
        val fullTextBuilder = StringBuilder()
        // 同一行统一判断时间基准，避免相对时间跨过行起点后被误当成绝对时间。
        val absoluteTimes = (matches.first().groupValues[2].toLongOrNull() ?: 0L) >= lineStartTime

        for (match in matches) {
            val wordText = match.groupValues[1]
            val absoluteStart = match.groupValues[2].toLongOrNull() ?: 0L
            val duration = match.groupValues[3].toLongOrNull() ?: 0L

            if (wordText.isEmpty() && duration == 0L) continue

            val startOffset = if (absoluteTimes) absoluteStart - lineStartTime else absoluteStart
            wordsList.add(WordInfo(wordText, startOffset, duration))
            fullTextBuilder.append(wordText)
        }

        val lastEnd = matches.last().range.last
        if (lastEnd + 1 < body.length) {
            val trailing = body.substring(lastEnd + 1)
            if (trailing.isNotBlank()) {
                fullTextBuilder.append(trailing)
            }
        }

        val fullText = fullTextBuilder.toString()
        if (fullText.isBlank()) return null

        val lineDuration = maxOf(declaredDuration, wordsList.maxOfOrNull { it.startOffsetMs + it.durationMs } ?: 0L)
        val validWords = wordsList.takeIf { words ->
            words.all { it.startOffsetMs >= 0 && it.durationMs >= 0 } &&
                words.zipWithNext().all { (a, b) -> a.startOffsetMs <= b.startOffsetMs }
        }.orEmpty()

        return LyricLine(
            timeMs = lineStartTime,
            durationMs = lineDuration,
            text = fullText,
            words = validWords
        )
    }

    private fun parseHeader(line: String): Triple<Long, Long, String>? {
        qrcLineHeaderRegex.find(line)?.let { match ->
            val start = match.groupValues[1].toLongOrNull() ?: return null
            val dur = match.groupValues[2].toLongOrNull() ?: 0L
            return Triple(start, dur, match.groupValues[3])
        }

        lrcLineHeaderRegex.find(line)?.let { match ->
            val min = match.groupValues[1].toLongOrNull() ?: return null
            val sec = match.groupValues[2].toLongOrNull() ?: return null
            val msRaw = match.groupValues[3]
            val ms = msRaw.toLongOrNull() ?: return null
            val timeMs = min * 60_000 + sec * 1000 + if (msRaw.length == 2) ms * 10 else ms
            return Triple(timeMs, 0L, match.groupValues[4])
        }

        return null
    }

    private fun decodeXmlEntities(str: String): String {
        return str
            .replace("&quot;", "\"")
            .replace("&apos;", "'")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&amp;", "&")
            .replace(Regex("&#(\\d+);")) { match ->
                val code = match.groupValues[1].toIntOrNull()
                codePointToString(code)
            }
            .replace(Regex("&#x([0-9a-fA-F]+);")) { match ->
                val code = match.groupValues[1].toIntOrNull(16)
                codePointToString(code)
            }
    }

    private fun codePointToString(code: Int?): String {
        if (code == null || code !in 1..0x10FFFF) return ""
        return if (code <= 0xFFFF) {
            code.toChar().toString()
        } else {
            val high = ((code - 0x10000) ushr 10) + 0xD800
            val low = ((code - 0x10000) and 0x3FF) + 0xDC00
            charArrayOf(high.toChar(), low.toChar()).concatToString()
        }
    }
}

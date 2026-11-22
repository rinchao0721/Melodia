package com.lin0721.linmusic.core.player.domain

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.util.Base64

// 酷狗 KRC 逐字歌词解析器：
// - 行格式：[startMs,durationMs]<offsetMs,wordDurationMs,0>字...
// - 元数据：[language:<base64 json>]（type == 1 为逐行翻译，type == 0 为逐字音译/罗马音）
// - 无逐字标签或标准 LRC 文本时自动降级为整行 LyricLine
object KrcLyricParser {

    private val krcLineRegex = Regex("""^\[(\d+),(\d+)](.*)$""")
    private val krcWordRegex = Regex("""<(\d+),(\d+),\d+>([^<\r\n]*)""")
    private val krcWordStripRegex = Regex("""<\d+,\d+,\d+>""")
    private val languageTagRegex = Regex("""^\[language:([^\]]+)]$""", RegexOption.IGNORE_CASE)
    private val metaTagRegex = Regex("""^\[(id|ar|ti|by|hash|al|sign|qq|total|offset):""", RegexOption.IGNORE_CASE)

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    fun parse(rawText: String): List<LyricLine> {
        if (rawText.isBlank()) return emptyList()

        val translations = mutableListOf<String?>()
        val romans = mutableListOf<String?>()
        val parsedLines = mutableListOf<LyricLine>()

        for (rawLine in rawText.lines()) {
            val trimmed = rawLine.trim()
            if (trimmed.isEmpty()) continue

            languageTagRegex.find(trimmed)?.let { langMatch ->
                parseLanguageBlock(
                    base64Payload = langMatch.groupValues[1].trim(),
                    translationsOut = translations,
                    romansOut = romans
                )
                continue
            }

            if (metaTagRegex.containsMatchIn(trimmed)) continue

            parseKrcLine(trimmed)?.let(parsedLines::add)
        }

        // 无任何 KRC [startMs,durationMs] 行时优雅降级为标准/本地 LRC 解析
        if (parsedLines.isEmpty()) {
            return LyricParser.parseLocal(rawText)
        }

        val enriched = parsedLines.mapIndexed { index, line ->
            val tr = translations.getOrNull(index)?.takeIf { it.isNotBlank() }
            val rm = romans.getOrNull(index)?.takeIf { it.isNotBlank() }
            if (tr == null && rm == null) line else line.copy(translation = tr, roma = rm)
        }

        return enriched.sortedBy { it.timeMs }
    }

    private fun parseKrcLine(trimmed: String): LyricLine? {
        val match = krcLineRegex.find(trimmed) ?: return null
        val startMs = match.groupValues[1].toLongOrNull() ?: return null
        val durationMs = match.groupValues[2].toLongOrNull() ?: 0L
        val body = match.groupValues[3]

        val wordMatches = krcWordRegex.findAll(body).toList()
        if (wordMatches.isEmpty()) {
            val plain = body.replace(krcWordStripRegex, "").trim()
            if (plain.isEmpty()) return null
            return LyricLine(
                timeMs = startMs,
                durationMs = durationMs,
                text = plain,
                words = emptyList()
            )
        }

        val words = ArrayList<WordInfo>(wordMatches.size)
        val fullText = StringBuilder()

        for (wordMatch in wordMatches) {
            val offsetMs = wordMatch.groupValues[1].toLongOrNull() ?: 0L
            val wordDurationMs = wordMatch.groupValues[2].toLongOrNull() ?: 0L
            val wordText = wordMatch.groupValues[3]
            if (wordText.isEmpty() && wordDurationMs == 0L) continue
            if (wordText.isNotEmpty()) {
                words += WordInfo(
                    text = wordText,
                    startOffsetMs = offsetMs.coerceAtLeast(0L),
                    durationMs = wordDurationMs.coerceAtLeast(0L)
                )
                fullText.append(wordText)
            }
        }

        val lastEnd = wordMatches.last().range.last
        if (lastEnd + 1 < body.length) {
            val trailing = body.substring(lastEnd + 1)
            if (trailing.isNotBlank()) {
                fullText.append(trailing)
            }
        }

        val textStr = fullText.toString().trim()
        if (textStr.isEmpty()) return null

        val computedDuration = maxOf(
            durationMs,
            words.maxOfOrNull { it.startOffsetMs + it.durationMs } ?: 0L
        )
        val validWords = words.takeIf { list ->
            list.all { it.startOffsetMs >= 0L && it.durationMs >= 0L } &&
                list.zipWithNext().all { (a, b) -> a.startOffsetMs <= b.startOffsetMs }
        }.orEmpty()

        return LyricLine(
            timeMs = startMs,
            durationMs = computedDuration,
            text = textStr,
            words = validWords
        )
    }

    private fun parseLanguageBlock(
        base64Payload: String,
        translationsOut: MutableList<String?>,
        romansOut: MutableList<String?>
    ) {
        val decodedJson = runCatching {
            val bytes = Base64.getDecoder().decode(base64Payload)
            bytes.toString(Charsets.UTF_8)
        }.getOrNull()?.takeIf { it.isNotBlank() } ?: return

        val root = runCatching {
            json.parseToJsonElement(decodedJson).jsonObject
        }.getOrNull() ?: return

        val contentArray = root["content"] as? JsonArray ?: return
        for (item in contentArray) {
            val obj = item as? JsonObject ?: continue
            val type = obj["type"]?.jsonPrimitive?.intOrNull ?: continue
            val lyricContent = obj["lyricContent"] as? JsonArray ?: continue

            when (type) {
                1 -> if (translationsOut.isEmpty()) {
                    lyricContent.forEach { row ->
                        translationsOut += joinLanguageRow(row, isRoma = false)
                    }
                }
                0 -> if (romansOut.isEmpty()) {
                    lyricContent.forEach { row ->
                        romansOut += joinLanguageRow(row, isRoma = true)
                    }
                }
            }
        }
    }

    private fun joinLanguageRow(row: kotlinx.serialization.json.JsonElement, isRoma: Boolean): String? {
        val tokens = when (row) {
            is JsonArray -> row.mapNotNull { (it as? JsonPrimitive)?.contentOrNull }
            is JsonPrimitive -> listOfNotNull(row.contentOrNull)
            else -> emptyList()
        }.filter { it.isNotEmpty() }

        if (tokens.isEmpty()) return null

        val joined = if (isRoma) {
            // KRC 音译若逐音节无空格则按空格拼接，若词元自带空格则直接拼接
            val separator = if (tokens.size > 1 && tokens.none { it.any(Char::isWhitespace) }) " " else ""
            tokens.joinToString(separator).trim()
        } else {
            tokens.joinToString("").trim()
        }

        if (joined.isBlank() || joined == "//") return null
        return joined
    }
}

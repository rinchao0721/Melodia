package com.lin0721.linmusic.core.player.domain

// YRC(逐字)/LRC(逐行) 歌词文本解析为 [LyricLine] 列表的纯函数工具
object LyricParser {

    private val yrcLineRegex = Regex("""^\[(\d+),(\d+)](.*)$""")
    private val yrcWordRegex = Regex("""\((\d+),(\d+),\d+\)([^(\n]+)""")

    fun parseYrc(yrcText: String): List<LyricLine> {
        return yrcText.lines().mapNotNull { line ->
            val trimmed = line.trim()
            if (trimmed.isEmpty()) return@mapNotNull null

            yrcLineRegex.find(trimmed)?.let { match ->
                val lineStartTime = match.groupValues[1].toLongOrNull() ?: return@let null
                val lineDuration = match.groupValues[2].toLongOrNull() ?: return@let null
                val wordsContent = match.groupValues[3]

                val wordsList = mutableListOf<WordInfo>()
                val fullTextBuilder = StringBuilder()

                yrcWordRegex.findAll(wordsContent).forEach { wordMatch ->
                    val absoluteTime = wordMatch.groupValues[1].toLongOrNull() ?: 0L
                    val startOffset = absoluteTime - lineStartTime // 计算相对于行开始时间的偏移量
                    val duration = wordMatch.groupValues[2].toLongOrNull() ?: 0L
                    val wordText = wordMatch.groupValues[3]

                    wordsList.add(WordInfo(wordText, startOffset, duration))
                    fullTextBuilder.append(wordText)
                }

                LyricLine(
                    timeMs = lineStartTime,
                    durationMs = lineDuration,
                    text = fullTextBuilder.toString(),
                    words = wordsList
                )
            }
        }.sortedBy { it.timeMs }
    }

    private val lrcPattern = Regex("""\[(\d{2}):(\d{2})[.:](\d{2,3})](.*)""")

    fun parseLrc(lrcText: String): List<LyricLine> {
        return lrcText.lines().mapNotNull { line ->
            lrcPattern.find(line)?.let { match ->
                val min = match.groupValues[1].toLongOrNull() ?: return@let null
                val sec = match.groupValues[2].toLongOrNull() ?: return@let null
                val msRaw = match.groupValues[3]
                val ms = if (msRaw.length == 2) msRaw.toLong() * 10 else msRaw.toLong()
                val text = match.groupValues[4].trim()
                if (text.isEmpty()) return@let null
                LyricLine(timeMs = min * 60_000 + sec * 1000 + ms, text = text)
            }
        }.sortedBy { it.timeMs }
    }
}

package com.lin0721.linmusic.core.share

import com.lin0721.linmusic.core.player.domain.LyricLine

// 原文与翻译分开勾选；一行只要选了其中之一就占一个名额
data class LyricPick(
    val originals: Set<Int> = emptySet(),
    val translations: Set<Int> = emptySet()
) {
    val lineIndices: Set<Int> get() = originals + translations
    val lineCount: Int get() = lineIndices.size
    val isEmpty: Boolean get() = originals.isEmpty() && translations.isEmpty()
}

object LyricShareSelection {
    const val MAX_LINES = 5

    fun isSelectable(line: LyricLine?): Boolean = line != null && line.text.isNotBlank()

    fun hasTranslation(line: LyricLine?): Boolean = isSelectable(line) && !line?.translation.isNullOrBlank()

    // 默认选中当前行（含翻译）；当前行是空行或前奏时向后找第一句
    fun initial(lines: List<LyricLine>, currentIndex: Int): LyricPick {
        val start = currentIndex.coerceAtLeast(0)
        val index = (start until lines.size).firstOrNull { isSelectable(lines[it]) }
            ?: lines.indices.firstOrNull { isSelectable(lines[it]) }
            ?: return LyricPick()
        return LyricPick(
            originals = setOf(index),
            translations = if (hasTranslation(lines[index])) setOf(index) else emptySet()
        )
    }

    // 已满时新增行无效，返回原选择
    fun toggleOriginal(lines: List<LyricLine>, pick: LyricPick, index: Int): LyricPick = when {
        index in pick.originals -> pick.copy(originals = pick.originals - index)
        !isSelectable(lines.getOrNull(index)) || isFull(pick, index) -> pick
        else -> pick.copy(originals = pick.originals + index)
    }

    fun toggleTranslation(lines: List<LyricLine>, pick: LyricPick, index: Int): LyricPick = when {
        index in pick.translations -> pick.copy(translations = pick.translations - index)
        !hasTranslation(lines.getOrNull(index)) || isFull(pick, index) -> pick
        else -> pick.copy(translations = pick.translations + index)
    }

    private fun isFull(pick: LyricPick, index: Int): Boolean =
        index !in pick.lineIndices && pick.lineCount >= MAX_LINES

    // 只选了翻译的行，翻译作为该行正文排版
    fun build(lines: List<LyricLine>, pick: LyricPick): List<ShareLyricLine> = pick.lineIndices.sorted().mapNotNull { index ->
        val line = lines.getOrNull(index)?.takeIf { isSelectable(it) } ?: return@mapNotNull null
        val translation = line.translation?.trim()?.takeIf { it.isNotEmpty() && index in pick.translations }
        when {
            index in pick.originals -> ShareLyricLine(line.text.trim(), translation)
            translation != null -> ShareLyricLine(translation)
            else -> null
        }
    }

    // 选中段外侧最近的非空行
    fun context(lines: List<LyricLine>, indices: Set<Int>): Pair<String?, String?> {
        if (indices.isEmpty()) return null to null
        val first = indices.min()
        val last = indices.max()
        val before = (first - 1 downTo 0).firstOrNull { isSelectable(lines.getOrNull(it)) }?.let { lines[it].text.trim() }
        val after = (last + 1 until lines.size).firstOrNull { isSelectable(lines[it]) }?.let { lines[it].text.trim() }
        return before to after
    }

    fun content(
        title: String,
        artist: String,
        lines: List<LyricLine>,
        pick: LyricPick,
        durationMs: Long
    ): ShareCardContent {
        val indices = pick.lineIndices
        val (before, after) = context(lines, indices)
        val firstTime = indices.minOrNull()?.let { lines.getOrNull(it)?.timeMs } ?: 0L
        return ShareCardContent(
            title = title,
            artist = artist,
            lines = build(lines, pick),
            lineBefore = before,
            lineAfter = after,
            positionMs = firstTime,
            durationMs = durationMs
        )
    }

    // 竖排书笺只适合以中日文为主的歌词
    fun isMostlyCjk(lines: List<ShareLyricLine>): Boolean {
        val text = lines.joinToString("") { it.text }.filterNot { it.isWhitespace() }
        if (text.isEmpty()) return false
        val cjk = text.count { isCjk(it) }
        return cjk * 10 >= text.length * 7
    }

    private fun isCjk(c: Char): Boolean =
        c in '一'..'鿿' || c in '㐀'..'䶿' || c in '぀'..'ヿ' ||
            c in '豈'..'﫿' || c in '　'..'〿' || c in '＀'..'￯'
}

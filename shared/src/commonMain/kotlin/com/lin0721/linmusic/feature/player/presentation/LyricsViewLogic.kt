package com.lin0721.linmusic.feature.player.presentation

import com.lin0721.linmusic.core.player.domain.LyricAlignment
import com.lin0721.linmusic.core.player.domain.LyricLine

// 歌词区没有可滚动内容时显示的占位
enum class LyricsPlaceholder { Loading, Empty, PureMusic }

fun lyricsPlaceholder(lines: List<LyricLine>, isLoading: Boolean): LyricsPlaceholder? = when {
    isLoading -> LyricsPlaceholder.Loading
    lines.isEmpty() -> LyricsPlaceholder.Empty
    lines.size == 1 && lines[0].text == "纯音乐" -> LyricsPlaceholder.PureMusic
    else -> null
}

// 副行按偏好取翻译或罗马音，缺失时不显示
fun secondaryText(line: LyricLine, mode: String): String? = when (mode) {
    "translation" -> line.translation
    "roma" -> line.roma
    else -> null
}?.takeIf { it.isNotBlank() }

fun hasSecondaryText(lines: List<LyricLine>, mode: String): Boolean = lines.any { line ->
    secondaryText(line, mode) != null ||
        line.backgroundLine?.let { secondaryText(it, mode) } != null
}

// 当前高亮的行：重叠区间同时高亮，没有集合时退回主行
fun isActiveLine(index: Int, primaryIndex: Int, activeIndices: Set<Int>): Boolean =
    if (activeIndices.isNotEmpty()) index in activeIndices else index == primaryIndex

// AMLL 对唱：首位声部靠左，其余声部靠右
fun isEndAligned(line: LyricLine): Boolean = line.alignment == LyricAlignment.END

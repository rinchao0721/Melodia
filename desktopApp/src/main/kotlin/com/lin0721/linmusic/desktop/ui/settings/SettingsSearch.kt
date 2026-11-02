package com.lin0721.linmusic.desktop.ui.settings

private val WhitespaceRegex = Regex("\\s+")

// 空查询匹配一切；多个词以空白分隔，需全部命中，忽略大小写
internal fun matchesSettingsQuery(query: String, searchText: List<String>): Boolean {
    val terms = query.trim().lowercase().split(WhitespaceRegex).filter { it.isNotEmpty() }
    if (terms.isEmpty()) return true
    val haystack = searchText.joinToString("\n").lowercase()
    return terms.all { it in haystack }
}

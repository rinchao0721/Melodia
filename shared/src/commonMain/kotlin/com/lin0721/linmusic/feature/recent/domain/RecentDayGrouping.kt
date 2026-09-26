package com.lin0721.linmusic.feature.recent.domain

// 按天分区块后的一组记录，label 形如「今天」「昨天」「08-24」
data class PlayDayGroup<T>(
    val label: String,
    val items: List<T>
)

// 服务端已按 playTime 降序返回，这里按相邻项的日期标签顺序合并成块，
// 不用 Map 分组是为了避免「今天」这类标签在理论上跨天复现时被错误地拼进同一块
fun <T> List<T>.groupByPlayDay(
    nowMs: Long = System.currentTimeMillis(),
    playTimeOf: (T) -> Long
): List<PlayDayGroup<T>> {
    val groups = mutableListOf<Pair<String, MutableList<T>>>()
    for (item in this) {
        val label = formatDayHeaderLabel(playTimeOf(item), nowMs)
        val current = groups.lastOrNull()?.takeIf { it.first == label }
        if (current != null) {
            current.second.add(item)
        } else {
            groups.add(label to mutableListOf(item))
        }
    }
    return groups.map { (label, items) -> PlayDayGroup(label, items) }
}

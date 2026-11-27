package com.lin0721.linmusic.feature.playlist.presentation

import com.lin0721.linmusic.core.model.Track

// 歌曲列表多选状态：按歌曲 id 记录，排序、过滤、分页追加都不影响已选
data class TrackSelection(
    val active: Boolean = false,
    val selectedIds: Set<Long> = emptySet(),
    val anchorId: Long? = null
) {
    val count: Int get() = selectedIds.size

    fun isSelected(id: Long): Boolean = id in selectedIds

    // firstId 非空时作为首个已选项
    fun enter(firstId: Long? = null): TrackSelection =
        TrackSelection(
            active = true,
            selectedIds = if (firstId != null) linkedSetOf(firstId) else emptySet(),
            anchorId = firstId
        )

    fun toggle(id: Long): TrackSelection {
        if (!active) return this
        val next = LinkedHashSet(selectedIds)
        val added = next.add(id)
        if (!added) next.remove(id)
        val nextAnchor = if (added) id else if (anchorId == id) next.lastOrNull() else anchorId
        return copy(selectedIds = next, anchorId = nextAnchor)
    }

    // 从当前锚点连选到目标项；无锚点时等价于选中目标项并设为锚点
    fun selectRangeTo(targetId: Long, orderedIds: List<Long>): TrackSelection {
        if (!active || orderedIds.isEmpty()) return this
        val targetIdx = orderedIds.indexOf(targetId)
        if (targetIdx < 0) return this
        val resolvedAnchorIdx = anchorId?.let(orderedIds::indexOf)?.takeIf { it >= 0 }
            ?: selectedIds.lastOrNull()?.let(orderedIds::indexOf)?.takeIf { it >= 0 }
        val next = LinkedHashSet(selectedIds)
        val nextAnchor: Long
        if (resolvedAnchorIdx == null) {
            next.add(targetId)
            nextAnchor = targetId
        } else {
            val from = minOf(resolvedAnchorIdx, targetIdx)
            val to = maxOf(resolvedAnchorIdx, targetIdx)
            next.addAll(orderedIds.subList(from, to + 1))
            nextAnchor = orderedIds[resolvedAnchorIdx]
        }
        return copy(selectedIds = next, anchorId = nextAnchor)
    }

    // 选中 orderedIds 中 [startIndex..endIndex] 闭区间内的全部项（自动归一化起止顺序）
    fun selectIndexRange(orderedIds: List<Long>, startIndex: Int, endIndex: Int): TrackSelection {
        if (orderedIds.isEmpty()) return this
        val safeStart = startIndex.coerceIn(0, orderedIds.lastIndex)
        val safeEnd = endIndex.coerceIn(0, orderedIds.lastIndex)
        val from = minOf(safeStart, safeEnd)
        val to = maxOf(safeStart, safeEnd)
        val slice = orderedIds.subList(from, to + 1)
        val next = LinkedHashSet(selectedIds).apply { addAll(slice) }
        return copy(active = true, selectedIds = next, anchorId = slice.firstOrNull() ?: anchorId)
    }

    // ids 已全部选中时取消这批，否则补选缺的部分
    fun toggleAll(ids: Collection<Long>): TrackSelection {
        if (!active || ids.isEmpty()) return this
        val next = LinkedHashSet(selectedIds)
        if (next.containsAll(ids)) next.removeAll(ids.toSet()) else next.addAll(ids)
        val nextAnchor = anchorId?.takeIf { it in next } ?: next.lastOrNull()
        return copy(selectedIds = next, anchorId = nextAnchor)
    }

    fun exit(): TrackSelection = TrackSelection()

    fun pick(tracks: List<Track>): List<Track> = tracks.filter { it.id in selectedIds }
}

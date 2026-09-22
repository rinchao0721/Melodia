package com.lin0721.linmusic.core.ui.theme.vibrant

// 与 node-vibrant 的 PQueue 行为对齐：push 只是追加并标记"未排序"，
// 真正排序延迟到 pop/toList 才发生，pop 取排序后数组的最后一项（最大项）
internal class VibrantPQueue<T>(private val comparator: Comparator<T>) {
    private val contents = ArrayList<T>()
    private var sorted = false

    private fun ensureSorted() {
        if (!sorted) {
            contents.sortWith(comparator)
            sorted = true
        }
    }

    fun push(item: T) {
        contents.add(item)
        sorted = false
    }

    fun pop(): T? {
        ensureSorted()
        return if (contents.isEmpty()) null else contents.removeAt(contents.size - 1)
    }

    fun size(): Int = contents.size

    fun toList(): List<T> {
        ensureSorted()
        return contents.toList()
    }
}

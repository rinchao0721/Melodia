package com.lin0721.linmusic.feature.library.data

// 音乐库的置顶、排序与视图偏好，同步读写；置顶与自定义顺序存的是歌单 ID，按账户隔离
interface LibraryPreferences {
    fun pinnedIds(uid: Long): Set<String>
    fun setPinnedIds(uid: Long, ids: Set<String>)
    fun customPlaylistOrder(uid: Long): List<String>
    fun setCustomPlaylistOrder(uid: Long, order: List<String>)

    // 排序方式按枚举名存取，解析交给调用方
    fun sortOrderName(): String?
    fun setSortOrderName(name: String)
    fun isGridView(): Boolean
    fun setGridView(isGrid: Boolean)
}

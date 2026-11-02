package com.lin0721.linmusic.desktop.ui.shell

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.lin0721.linmusic.desktop.data.LibraryMode
import com.lin0721.linmusic.desktop.data.LibraryViewMode
import com.lin0721.linmusic.desktop.ui.library.LibraryExtraFilter
import com.lin0721.linmusic.desktop.ui.nowplaying.DockOverlay
import com.lin0721.linmusic.feature.library.ui.LibraryFilter
import com.lin0721.linmusic.feature.library.ui.LibrarySortOrder
import com.lin0721.linmusic.feature.library.ui.LibraryViewModel

// 一步历史记下的界面状态，前进/后退时整体还原
@Immutable
data class UiSnapshot(
    val homeTab: Int,
    val showNewWorks: Boolean,
    val libraryMode: LibraryMode,
    val libraryViewMode: LibraryViewMode,
    val libraryFilter: LibraryFilter?,
    // 选中「本地音乐」或「云盘」胶囊时非空，此时 libraryFilter 为空
    val libraryExtra: LibraryExtraFilter?,
    val librarySortOrder: LibrarySortOrder,
    val dockOpen: Boolean,
    val dockOverlay: DockOverlay?,
    val lyricsOpen: Boolean
)

// 音乐库筛选与排序的读写口，云端筛选在共享 ViewModel 里，「本地音乐」「云盘」胶囊为桌面端独有
interface LibraryViewControl {
    val filter: LibraryFilter?
    val extra: LibraryExtraFilter?
    val sortOrder: LibrarySortOrder
    fun toggleFilter(filter: LibraryFilter)
    fun selectFilter(filter: LibraryFilter?)
    fun selectExtra(extra: LibraryExtraFilter?)
    fun updateSortOrder(order: LibrarySortOrder)
}

fun LibraryViewModel.asViewControl(): LibraryViewControl = object : LibraryViewControl {
    private var selectedExtra by mutableStateOf<LibraryExtraFilter?>(null)
    override val filter: LibraryFilter? get() = selectedFilter.value
    override val extra: LibraryExtraFilter? get() = selectedExtra
    override val sortOrder: LibrarySortOrder get() = this@asViewControl.sortOrder.value
    override fun toggleFilter(filter: LibraryFilter) {
        if (selectedExtra != null) {
            selectedExtra = null
            this@asViewControl.selectFilter(filter)
        } else {
            this@asViewControl.toggleFilter(filter)
        }
    }
    override fun selectFilter(filter: LibraryFilter?) {
        selectedExtra = null
        this@asViewControl.selectFilter(filter)
    }
    override fun selectExtra(extra: LibraryExtraFilter?) {
        if (extra != null) this@asViewControl.selectFilter(null)
        selectedExtra = extra
    }
    override fun updateSortOrder(order: LibrarySortOrder) = this@asViewControl.updateSortOrder(order)
}

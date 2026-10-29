package com.lin0721.linmusic.desktop.ui.shell

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.lin0721.linmusic.core.share.ShareCardKind
import com.lin0721.linmusic.desktop.data.LibraryMode
import com.lin0721.linmusic.desktop.data.LibraryViewMode
import com.lin0721.linmusic.desktop.ui.comments.CommentsHost
import com.lin0721.linmusic.desktop.ui.home.HOME_TAB_ALL
import com.lin0721.linmusic.desktop.ui.home.HOME_TAB_MUSIC
import com.lin0721.linmusic.desktop.ui.library.LibraryExtraFilter
import com.lin0721.linmusic.desktop.ui.lyricsview.LyricsViewState
import com.lin0721.linmusic.desktop.ui.navigation.BackStack
import com.lin0721.linmusic.desktop.ui.navigation.DesktopRoute
import com.lin0721.linmusic.desktop.ui.nowplaying.DockOverlay
import com.lin0721.linmusic.feature.library.ui.LibraryFilter
import com.lin0721.linmusic.feature.library.ui.LibraryItem
import com.lin0721.linmusic.feature.library.ui.LibraryItemType
import com.lin0721.linmusic.feature.library.ui.LibrarySortOrder
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

// 应用壳的界面状态：快照历史、首页标签、音乐库与右侧栏、歌词界面、登录框与全局提示
@Stable
class DesktopAppState(
    val lyricsView: LyricsViewState,
    val pane: PaneLayoutState,
    private val library: LibraryViewControl,
    private val persistDockOpen: (Boolean) -> Unit
) {
    var homeTab by mutableStateOf(HOME_TAB_ALL)
        private set
    var showNewWorks by mutableStateOf(false)
        private set
    var showLogin by mutableStateOf(false)

    // 非空时显示分享卡片对话框，值为初始类型
    var shareCardKind by mutableStateOf<ShareCardKind?>(null)
    var showRecognition by mutableStateOf(false)

    // 打开设置页后要滚动到的分区（SettingsCategory.name），滚动完即清空
    var settingsFocus by mutableStateOf<String?>(null)

    // 初值为关闭：读到已保存的开启状态后，侧栏随动画展开
    private var savedDockOpen by mutableStateOf(false)

    // 音乐库展开时右侧栏保持原状态，期间的开合只在本次展开内有效，不改保存的开关值
    private var expandedDockOpen by mutableStateOf(false)

    var dockOverlay by mutableStateOf<DockOverlay?>(null)
        private set
    var playlistComments by mutableStateOf<CommentsHost?>(null)
        private set

    // 歌单评论所属页面的栈帧，还原时只在同一栈帧上恢复该面板
    private var playlistCommentsEntryId: Long? = null

    // 每次鼠标界面动作记一步，前进/后退还原整个画面
    val backStack = BackStack(DesktopRoute.Home, liveUi())

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val messages: SharedFlow<String> = _messages.asSharedFlow()

    fun showMessage(text: String) {
        _messages.tryEmit(text)
    }

    // ─── 历史 ───

    val canGoBack: Boolean get() = backStack.canGoBack
    val canGoForward: Boolean get() = backStack.canGoForward

    val isHomeAll: Boolean
        get() = backStack.current == DesktopRoute.Home && homeTab == HOME_TAB_ALL && !showNewWorks && !lyricsView.isOpen

    fun liveUi(): UiSnapshot = UiSnapshot(
        homeTab = homeTab,
        showNewWorks = showNewWorks,
        libraryMode = pane.libraryMode,
        libraryViewMode = pane.libraryViewMode,
        libraryFilter = library.filter,
        libraryExtra = library.extra,
        librarySortOrder = library.sortOrder,
        dockOpen = dockOpen,
        dockOverlay = dockOverlay,
        lyricsOpen = lyricsView.isOpen
    )

    // 不经记录入口的界面变化（拖拽、Esc、自动收起、启动读取保存值等）并入当前步骤
    fun absorbUiChange() = backStack.replaceUi(liveUi())

    // 标题栏与鼠标侧键的后退/前进
    fun navigateBack() {
        absorbUiChange()
        backStack.back()?.let { restore(it.ui) }
    }

    fun navigateForward() {
        absorbUiChange()
        backStack.forward()?.let { restore(it.ui) }
    }

    // 页面内的返回（如删除歌单后）直接回到上一个页面
    fun popBack() {
        absorbUiChange()
        backStack.backToPreviousPage()?.let { restore(it.ui) }
    }

    // 先把未记录的变化并入当前步骤，再执行动作并记成新的一步；换页时收起属于原页面的歌单评论
    private inline fun record(route: DesktopRoute? = null, action: () -> Unit) {
        absorbUiChange()
        action()
        if (route != null && route != backStack.current && dockOverlay == DockOverlay.PlaylistComments) dockOverlay = null
        // 歌词界面盖住整个工作区，跳转页面时收起，返回时随快照恢复
        if (route != null) lyricsView.close()
        backStack.push(route, liveUi())
    }

    private fun restore(ui: UiSnapshot) {
        homeTab = ui.homeTab
        showNewWorks = ui.showNewWorks
        applyLibraryMode(ui.libraryMode)
        pane.updateViewMode(ui.libraryViewMode)
        if (library.filter != ui.libraryFilter) library.selectFilter(ui.libraryFilter)
        if (library.extra != ui.libraryExtra) library.selectExtra(ui.libraryExtra)
        if (library.sortOrder != ui.librarySortOrder) library.updateSortOrder(ui.librarySortOrder)
        applyDockOpen(ui.dockOpen)
        if (ui.dockOpen) {
            val ownsComments = playlistCommentsEntryId == backStack.currentEntry.id
            dockOverlay = ui.dockOverlay?.takeIf { it != DockOverlay.PlaylistComments || ownsComments }
        }
        // 前进重新打开歌词时不再带进窗口全屏
        if (ui.lyricsOpen) {
            if (!lyricsView.isOpen) lyricsView.open()
        } else {
            lyricsView.close()
        }
        absorbUiChange()
    }

    // ─── 导航与首页 ───

    fun navigate(route: DesktopRoute) = record(route) {}

    fun goHomeAll() = record(DesktopRoute.Home) {
        homeTab = HOME_TAB_ALL
        showNewWorks = false
    }

    // 点任意主胶囊都回到该 tab 的默认内容，「最新」只能由二级胶囊单独选中；
    // 已在「音乐」默认内容时再点「音乐」则回到「全部」，收起二级胶囊
    fun selectHomeTab(tab: Int) = record {
        homeTab = if (tab == HOME_TAB_MUSIC && homeTab == HOME_TAB_MUSIC && !showNewWorks) HOME_TAB_ALL else tab
        showNewWorks = false
    }

    fun selectNewWorks(selected: Boolean) = record { showNewWorks = selected }

    fun openLibraryItem(item: LibraryItem) {
        val route = item.id.toLongOrNull()?.let { id ->
            when (item.type) {
                LibraryItemType.PLAYLIST -> DesktopRoute.Playlist(id, item.title)
                LibraryItemType.ALBUM -> DesktopRoute.Playlist(id, item.title, isAlbum = true)
                LibraryItemType.ARTIST -> DesktopRoute.Artist(id, item.title)
            }
        }
        record(route) {
            if (pane.libraryMode == LibraryMode.EXPANDED) applyLibraryMode(LibraryMode.DEFAULT)
        }
    }

    // ─── 歌词界面 ───

    fun toggleLyricsView() = record { if (lyricsView.isOpen) lyricsView.close() else lyricsView.open() }

    fun openLyricsView() = record { lyricsView.open() }

    fun openLyricsFullscreen() = record { lyricsView.openWithFullscreen() }

    fun closeLyricsView() = record { lyricsView.close() }

    // ─── 音乐库 ───

    fun setLibraryMode(mode: LibraryMode) = record { applyLibraryMode(mode) }

    // 拖动缝隙吸附形态不记步
    fun setLibraryModeByDrag(mode: LibraryMode) {
        applyLibraryMode(mode)
        absorbUiChange()
    }

    fun setLibraryViewMode(mode: LibraryViewMode) = record { pane.updateViewMode(mode) }

    fun toggleLibraryFilter(filter: LibraryFilter) = record { library.toggleFilter(filter) }

    val libraryExtra: LibraryExtraFilter? get() = library.extra

    fun toggleLibraryExtra(extra: LibraryExtraFilter) = record {
        library.selectExtra(if (library.extra == extra) null else extra)
    }

    fun openLocalPlaylist(id: Long, name: String) = record(DesktopRoute.LocalPlaylist(id, name)) {
        if (pane.libraryMode == LibraryMode.EXPANDED) applyLibraryMode(LibraryMode.DEFAULT)
    }

    fun openCloud() = record(DesktopRoute.Cloud) {
        if (pane.libraryMode == LibraryMode.EXPANDED) applyLibraryMode(LibraryMode.DEFAULT)
    }

    fun setLibrarySortOrder(order: LibrarySortOrder) = record { library.updateSortOrder(order) }

    private fun applyLibraryMode(mode: LibraryMode) {
        if (mode == LibraryMode.EXPANDED && pane.libraryMode != LibraryMode.EXPANDED) expandedDockOpen = savedDockOpen
        pane.applyLibraryMode(mode)
        afterDockChange()
    }

    // ─── 右侧栏 ───

    val dockOpen: Boolean
        get() = if (pane.libraryMode == LibraryMode.EXPANDED) expandedDockOpen else savedDockOpen

    fun syncSavedDockOpen(open: Boolean) {
        savedDockOpen = open
        afterDockChange()
        absorbUiChange()
    }

    fun setDockOpen(open: Boolean) = record { applyDockOpen(open) }

    // 拖窄收起不记步
    fun closeDockByDrag() {
        applyDockOpen(false)
        absorbUiChange()
    }

    private fun applyDockOpen(open: Boolean) {
        if (pane.libraryMode == LibraryMode.EXPANDED) {
            expandedDockOpen = open
        } else if (savedDockOpen != open) {
            savedDockOpen = open
            persistDockOpen(open)
        }
        afterDockChange()
    }

    fun isOverlayOpen(target: DockOverlay): Boolean = dockOpen && dockOverlay == target

    // 覆盖面板已显示则关掉它；否则展开右侧栏并把面板盖上去
    fun toggleOverlay(target: DockOverlay) = record {
        if (isOverlayOpen(target)) {
            dockOverlay = null
        } else {
            dockOverlay = target
            applyDockOpen(true)
        }
    }

    fun closeOverlay() = record { dockOverlay = null }

    // 盖着覆盖面板时点封面是撤掉面板露出正在播放页，而不是收起右侧栏
    fun toggleNowPlaying() = record {
        if (dockOpen && dockOverlay != null) dockOverlay = null else applyDockOpen(!dockOpen)
    }

    fun isCommentsPanelOpen(host: CommentsHost): Boolean =
        isOverlayOpen(DockOverlay.PlaylistComments) && playlistComments === host

    fun toggleCommentsPanel(host: CommentsHost) = record {
        if (isCommentsPanelOpen(host)) {
            dockOverlay = null
        } else {
            playlistComments = host
            playlistCommentsEntryId = backStack.currentEntry.id
            dockOverlay = DockOverlay.PlaylistComments
            applyDockOpen(true)
        }
    }

    // 右侧栏收起后覆盖面板一并撤掉，下次展开回到正在播放页
    private fun afterDockChange() {
        if (!dockOpen) dockOverlay = null
    }
}

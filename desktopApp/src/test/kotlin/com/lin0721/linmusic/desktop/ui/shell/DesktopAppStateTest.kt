package com.lin0721.linmusic.desktop.ui.shell

import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.WindowState
import com.lin0721.linmusic.desktop.data.LibraryMode
import com.lin0721.linmusic.desktop.data.LibraryViewMode
import com.lin0721.linmusic.desktop.ui.comments.CommentsHost
import com.lin0721.linmusic.desktop.ui.home.HOME_TAB_ALL
import com.lin0721.linmusic.desktop.ui.home.HOME_TAB_MUSIC
import com.lin0721.linmusic.desktop.ui.home.HOME_TAB_PODCAST
import com.lin0721.linmusic.desktop.ui.library.LibraryExtraFilter
import com.lin0721.linmusic.desktop.ui.lyricsview.LyricsViewState
import com.lin0721.linmusic.desktop.ui.navigation.DesktopRoute
import com.lin0721.linmusic.desktop.ui.nowplaying.DockOverlay
import com.lin0721.linmusic.feature.library.ui.LibraryFilter
import com.lin0721.linmusic.feature.library.ui.LibraryItem
import com.lin0721.linmusic.feature.library.ui.LibraryItemType
import com.lin0721.linmusic.feature.library.ui.LibrarySortOrder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.lang.reflect.Proxy

class DesktopAppStateTest {

    private class RecordingPersistence : PaneLayoutPersistence {
        val modes = mutableListOf<LibraryMode>()
        override fun saveLibraryMode(mode: LibraryMode) {
            modes += mode
        }
        override fun saveLibraryViewMode(mode: LibraryViewMode) = Unit
        override fun saveLibraryWidth(widthDp: Float) = Unit
        override fun saveDockWidth(widthDp: Float) = Unit
    }

    private class FakeLibrary : LibraryViewControl {
        override var filter: LibraryFilter? = null
        override var extra: LibraryExtraFilter? = null
        override var sortOrder: LibrarySortOrder = LibrarySortOrder.RECENTLY_PLAYED
        override fun toggleFilter(filter: LibraryFilter) {
            extra = null
            this.filter = if (this.filter == filter) null else filter
        }
        override fun selectFilter(filter: LibraryFilter?) {
            extra = null
            this.filter = filter
        }
        override fun selectExtra(extra: LibraryExtraFilter?) {
            if (extra != null) filter = null
            this.extra = extra
        }
        override fun updateSortOrder(order: LibrarySortOrder) {
            sortOrder = order
        }
    }

    private val savedDockValues = mutableListOf<Boolean>()
    private val persistence = RecordingPersistence()
    private val library = FakeLibrary()

    private fun create(mode: LibraryMode = LibraryMode.DEFAULT): DesktopAppState {
        val lyrics = LyricsViewState(FullscreenState(WindowState()))
        val pane = PaneLayoutState(mode, LibraryViewMode.LIST, 300.dp, 340.dp, persistence)
        return DesktopAppState(lyrics, pane, library) { savedDockValues += it }
    }

    // 只用到对象身份，方法不会被调用
    private fun commentsHost(): CommentsHost =
        Proxy.newProxyInstance(javaClass.classLoader, arrayOf(CommentsHost::class.java)) { _, _, _ -> null } as CommentsHost

    @Test
    fun backClosesLyricsFirstAndForwardReopensIt() {
        val state = create()
        state.navigate(DesktopRoute.Settings)
        state.openLyricsView()

        state.navigateBack()
        assertFalse(state.lyricsView.isOpen)
        assertEquals(DesktopRoute.Settings, state.backStack.current)
        assertTrue(state.canGoForward)

        state.navigateForward()
        assertTrue(state.lyricsView.isOpen)
    }

    @Test
    fun newNavigationInvalidatesLyricsReopen() {
        val state = create()
        state.openLyricsView()
        state.navigateBack()

        state.navigate(DesktopRoute.Search)

        assertFalse(state.canGoForward)
        state.navigateForward()
        assertFalse(state.lyricsView.isOpen)
    }

    @Test
    fun reopeningLyricsInvalidatesForward() {
        val state = create()
        state.openLyricsView()
        state.navigateBack()

        state.openLyricsView()

        assertFalse(state.canGoForward)
    }

    // Esc 等不记步的收起并入当前步骤，后退不会白按一次
    @Test
    fun unrecordedLyricsCloseIsAbsorbed() {
        val state = create()
        state.navigate(DesktopRoute.Settings)
        state.openLyricsView()

        state.lyricsView.close()
        state.absorbUiChange()
        state.navigateBack()

        assertEquals(DesktopRoute.Home, state.backStack.current)
        assertFalse(state.lyricsView.isOpen)
    }

    @Test
    fun homeTabChangesStepBackOneByOne() {
        val state = create()
        state.selectHomeTab(HOME_TAB_MUSIC)
        state.selectNewWorks(true)
        state.selectHomeTab(HOME_TAB_PODCAST)

        state.navigateBack()
        assertEquals(HOME_TAB_MUSIC, state.homeTab)
        assertTrue(state.showNewWorks)

        state.navigateBack()
        assertEquals(HOME_TAB_MUSIC, state.homeTab)
        assertFalse(state.showNewWorks)

        state.navigateBack()
        assertEquals(HOME_TAB_ALL, state.homeTab)
        assertFalse(state.canGoBack)

        state.navigateForward()
        assertEquals(HOME_TAB_MUSIC, state.homeTab)
    }

    @Test
    fun backToHomeRestoresTabItWasLeftOn() {
        val state = create()
        state.selectHomeTab(HOME_TAB_PODCAST)
        state.navigate(DesktopRoute.Settings)
        state.goHomeAll()

        state.navigateBack()
        state.navigateBack()

        assertEquals(DesktopRoute.Home, state.backStack.current)
        assertEquals(HOME_TAB_PODCAST, state.homeTab)
    }

    @Test
    fun uiOnlyStepsKeepPageEntry() {
        val state = create()
        state.navigate(DesktopRoute.Settings)
        val entry = state.backStack.currentEntry

        state.toggleOverlay(DockOverlay.Queue)
        state.navigateBack()

        assertEquals(entry, state.backStack.currentEntry)
        assertFalse(state.dockOpen)
    }

    @Test
    fun libraryModeAndViewModeAreRestoredAndPersisted() {
        val state = create()
        state.setLibraryMode(LibraryMode.EXPANDED)
        state.setLibraryViewMode(LibraryViewMode.LARGE_GRID)

        state.navigateBack()
        assertEquals(LibraryViewMode.LIST, state.pane.libraryViewMode)
        assertEquals(LibraryMode.EXPANDED, state.pane.libraryMode)

        state.navigateBack()
        assertEquals(LibraryMode.DEFAULT, state.pane.libraryMode)
        assertEquals(listOf(LibraryMode.EXPANDED, LibraryMode.DEFAULT), persistence.modes)
    }

    @Test
    fun libraryFilterAndSortAreRestored() {
        val state = create()
        state.toggleLibraryFilter(LibraryFilter.ALBUM)
        state.setLibrarySortOrder(LibrarySortOrder.NAME)

        state.navigateBack()
        assertEquals(LibrarySortOrder.RECENTLY_PLAYED, library.sortOrder)
        assertEquals(LibraryFilter.ALBUM, library.filter)

        state.navigateBack()
        assertNull(library.filter)

        state.navigateForward()
        state.navigateForward()
        assertEquals(LibraryFilter.ALBUM, library.filter)
        assertEquals(LibrarySortOrder.NAME, library.sortOrder)
    }

    @Test
    fun extraLibraryChipsAreRestoredWithHistory() {
        val state = create()
        state.toggleLibraryFilter(LibraryFilter.ALBUM)
        state.toggleLibraryExtra(LibraryExtraFilter.LOCAL)
        assertEquals(LibraryExtraFilter.LOCAL, library.extra)
        assertNull(library.filter)
        state.toggleLibraryExtra(LibraryExtraFilter.CLOUD)
        assertEquals(LibraryExtraFilter.CLOUD, library.extra)

        state.navigateBack()
        assertEquals(LibraryExtraFilter.LOCAL, library.extra)
        state.navigateBack()
        assertNull(library.extra)
        assertEquals(LibraryFilter.ALBUM, library.filter)

        state.navigateForward()
        state.navigateForward()
        assertEquals(LibraryExtraFilter.CLOUD, library.extra)
        state.toggleLibraryExtra(LibraryExtraFilter.CLOUD)
        assertNull(library.extra)
    }

    @Test
    fun openCloudPushesRoute() {
        val state = create()
        state.openCloud()
        assertEquals(DesktopRoute.Cloud, state.backStack.current)
    }

    @Test
    fun openLocalPlaylistPushesRouteAndLeavesExpandedLibrary() {
        val state = create(LibraryMode.EXPANDED)
        state.openLocalPlaylist(7, "本地")
        assertEquals(DesktopRoute.LocalPlaylist(7, "本地"), state.backStack.current)
        assertEquals(LibraryMode.DEFAULT, state.pane.libraryMode)
    }

    @Test
    fun dockAndOverlayAreRestored() {
        val state = create()
        state.toggleOverlay(DockOverlay.Queue)
        state.closeOverlay()

        state.navigateBack()
        assertTrue(state.isOverlayOpen(DockOverlay.Queue))

        state.navigateBack()
        assertFalse(state.dockOpen)
        assertNull(state.dockOverlay)
        assertEquals(listOf(true, false), savedDockValues)
    }

    @Test
    fun dragChangesAreFoldedIntoCurrentStep() {
        val state = create()
        state.setLibraryModeByDrag(LibraryMode.RAIL)
        assertFalse(state.canGoBack)

        state.navigate(DesktopRoute.Settings)
        state.navigateBack()

        assertEquals(LibraryMode.RAIL, state.pane.libraryMode)
    }

    @Test
    fun dockClosedByDragIsNotRecorded() {
        val state = create()
        state.setDockOpen(true)

        state.closeDockByDrag()

        assertFalse(state.dockOpen)
        assertFalse(state.canGoBack)
    }

    @Test
    fun popBackReturnsToPreviousPageSkippingUiSteps() {
        val state = create()
        state.navigate(DesktopRoute.Playlist(1, "歌单"))
        state.setLibraryViewMode(LibraryViewMode.SMALL_GRID)
        state.toggleLibraryFilter(LibraryFilter.PLAYLIST)

        state.popBack()

        assertEquals(DesktopRoute.Home, state.backStack.current)
        assertEquals(LibraryViewMode.LIST, state.pane.libraryViewMode)
        assertNull(library.filter)
    }

    @Test
    fun goHomeAllResetsTabsAndClosesLyrics() {
        val state = create()
        state.selectHomeTab(HOME_TAB_MUSIC)
        state.selectNewWorks(true)
        state.navigate(DesktopRoute.Settings)
        state.openLyricsView()
        assertFalse(state.isHomeAll)

        state.goHomeAll()

        assertEquals(DesktopRoute.Home, state.backStack.current)
        assertEquals(HOME_TAB_ALL, state.homeTab)
        assertFalse(state.showNewWorks)
        assertFalse(state.lyricsView.isOpen)
        assertTrue(state.isHomeAll)
    }

    @Test
    fun selectingMusicAgainReturnsToAll() {
        val state = create()
        state.selectHomeTab(HOME_TAB_MUSIC)
        state.selectHomeTab(HOME_TAB_MUSIC)
        assertEquals(HOME_TAB_ALL, state.homeTab)

        state.selectHomeTab(HOME_TAB_MUSIC)
        state.selectNewWorks(true)
        state.selectHomeTab(HOME_TAB_MUSIC)
        assertEquals(HOME_TAB_MUSIC, state.homeTab)
        assertFalse(state.showNewWorks)
    }

    @Test
    fun toggleOverlayOpensDockAndSecondToggleClosesPanel() {
        val state = create()

        state.toggleOverlay(DockOverlay.Queue)
        assertTrue(state.dockOpen)
        assertTrue(state.isOverlayOpen(DockOverlay.Queue))
        assertEquals(listOf(true), savedDockValues)

        state.toggleOverlay(DockOverlay.Queue)
        assertNull(state.dockOverlay)
        assertTrue(state.dockOpen)
    }

    @Test
    fun closingDockDropsOverlay() {
        val state = create()
        state.toggleOverlay(DockOverlay.Devices)

        state.setDockOpen(false)

        assertFalse(state.dockOpen)
        assertNull(state.dockOverlay)
    }

    @Test
    fun syncedClosedDockDropsOverlay() {
        val state = create()
        state.toggleOverlay(DockOverlay.Queue)

        state.syncSavedDockOpen(false)

        assertNull(state.dockOverlay)
    }

    @Test
    fun toggleNowPlayingRevealsPanelBeforeCollapsing() {
        val state = create()
        state.toggleOverlay(DockOverlay.Queue)

        state.toggleNowPlaying()
        assertTrue(state.dockOpen)
        assertNull(state.dockOverlay)

        state.toggleNowPlaying()
        assertFalse(state.dockOpen)
    }

    @Test
    fun playlistCommentsCloseWhenLeavingPageAndReturnWithIt() {
        val state = create()
        val host = commentsHost()
        state.navigate(DesktopRoute.Playlist(1, "歌单"))
        state.toggleCommentsPanel(host)
        assertTrue(state.isCommentsPanelOpen(host))

        state.navigate(DesktopRoute.Settings)
        assertFalse(state.isCommentsPanelOpen(host))
        assertNull(state.dockOverlay)

        state.navigateBack()
        assertTrue(state.isCommentsPanelOpen(host))
    }

    @Test
    fun otherOverlaysSurvivePageChange() {
        val state = create()
        state.toggleOverlay(DockOverlay.Queue)

        state.navigate(DesktopRoute.Settings)

        assertEquals(DockOverlay.Queue, state.dockOverlay)
    }

    @Test
    fun expandedLibraryKeepsItsOwnDockStateWithoutPersisting() {
        val state = create()
        state.syncSavedDockOpen(true)

        state.setLibraryMode(LibraryMode.EXPANDED)
        assertTrue(state.dockOpen)
        state.setDockOpen(false)
        assertFalse(state.dockOpen)
        assertTrue(savedDockValues.isEmpty())

        state.setLibraryMode(LibraryMode.DEFAULT)
        assertTrue(state.dockOpen)
    }

    @Test
    fun openingLibraryItemCollapsesExpandedLibrary() {
        val state = create(LibraryMode.EXPANDED)

        state.openLibraryItem(LibraryItem("7", "专辑", "", "", LibraryItemType.ALBUM))

        assertEquals(LibraryMode.DEFAULT, state.pane.libraryMode)
        assertEquals(DesktopRoute.Playlist(7, "专辑", isAlbum = true), state.backStack.current)

        state.navigateBack()
        assertEquals(LibraryMode.EXPANDED, state.pane.libraryMode)
        assertEquals(DesktopRoute.Home, state.backStack.current)
    }

    @Test
    fun libraryItemWithInvalidIdDoesNotNavigate() {
        val state = create()

        state.openLibraryItem(LibraryItem("x", "坏数据", "", "", LibraryItemType.PLAYLIST))

        assertEquals(DesktopRoute.Home, state.backStack.current)
    }
}

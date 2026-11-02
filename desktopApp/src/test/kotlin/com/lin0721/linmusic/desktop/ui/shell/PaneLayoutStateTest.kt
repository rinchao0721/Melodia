package com.lin0721.linmusic.desktop.ui.shell

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.lin0721.linmusic.desktop.data.LibraryMode
import com.lin0721.linmusic.desktop.data.LibraryViewMode
import com.lin0721.linmusic.desktop.ui.theme.DesktopDimens
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PaneLayoutStateTest {

    private class RecordingPersistence : PaneLayoutPersistence {
        val modes = mutableListOf<LibraryMode>()
        val viewModes = mutableListOf<LibraryViewMode>()
        val libraryWidths = mutableListOf<Float>()
        val dockWidths = mutableListOf<Float>()
        override fun saveLibraryMode(mode: LibraryMode) {
            modes += mode
        }
        override fun saveLibraryViewMode(mode: LibraryViewMode) {
            viewModes += mode
        }
        override fun saveLibraryWidth(widthDp: Float) {
            libraryWidths += widthDp
        }
        override fun saveDockWidth(widthDp: Float) {
            dockWidths += widthDp
        }
    }

    private val persistence = RecordingPersistence()

    private fun create(
        mode: LibraryMode = LibraryMode.DEFAULT,
        libraryWidth: Dp = 300.dp,
        dockWidth: Dp = 340.dp
    ) = PaneLayoutState(mode, LibraryViewMode.LIST, libraryWidth, dockWidth, persistence)

    @Test
    fun wideWindowKeepsRememberedWidths() {
        val measure = create().measure(available = 1600.dp, hasTrack = true, dockOpen = true)

        assertEquals(300.dp, measure.libraryDefaultWidth)
        assertEquals(340.dp, measure.dockOpenWidth)
        assertEquals(DesktopDimens.LibraryMaxWidth, measure.libraryMax)
    }

    @Test
    fun narrowWindowClampsToMinimums() {
        val measure = create(libraryWidth = 420.dp, dockWidth = 420.dp).measure(available = 900.dp, hasTrack = true, dockOpen = true)

        assertEquals(DesktopDimens.LibraryMinWidth, measure.libraryMax)
        assertEquals(DesktopDimens.LibraryMinWidth, measure.libraryDefaultWidth)
        assertEquals(DesktopDimens.NowPlayingMinWidth, measure.dockOpenWidth)
    }

    @Test
    fun railLibraryLeavesMoreRoomForDock() {
        val available = 1000.dp
        val default = create(dockWidth = 420.dp).measure(available, hasTrack = true, dockOpen = true)
        val rail = create(mode = LibraryMode.RAIL, dockWidth = 420.dp).measure(available, hasTrack = true, dockOpen = true)

        assertTrue(rail.dockMax > default.dockMax)
    }

    @Test
    fun noTrackGivesLibraryTheDockSpace() {
        val withTrack = create(libraryWidth = 420.dp).measure(available = 1000.dp, hasTrack = true, dockOpen = true)
        val withoutTrack = create(libraryWidth = 420.dp).measure(available = 1000.dp, hasTrack = false, dockOpen = true)

        assertTrue(withoutTrack.libraryMax > withTrack.libraryMax)
    }

    @Test
    fun draggingLibraryNarrowSnapsToRail() {
        val state = create()
        val modes = mutableListOf<LibraryMode>()
        state.startLibraryDrag(300.dp)

        state.dragLibrary((-200).dp, libraryMax = 420.dp, setMode = { modes += it })

        assertEquals(listOf(LibraryMode.RAIL), modes)
        assertTrue(state.resizing)
        assertTrue(state.librarySpring)
    }

    @Test
    fun draggingLibraryPastMaxOvershootsAndSavesClampedWidth() {
        val state = create(mode = LibraryMode.RAIL)
        val modes = mutableListOf<LibraryMode>()
        state.startLibraryDrag(DesktopDimens.LibraryRailWidth)

        state.dragLibrary(600.dp, libraryMax = 400.dp, setMode = { modes += it })
        assertTrue(state.resizing)
        assertEquals(listOf(LibraryMode.DEFAULT), modes)
        assertEquals(400.dp, state.libraryWidthPref)
        assertTrue(state.libraryStretch > 0.dp && state.libraryStretch < 40.dp)

        state.endLibraryDrag()
        assertFalse(state.resizing)
        assertEquals(0.dp, state.libraryStretch)
        assertEquals(listOf(400f), persistence.libraryWidths)
    }

    @Test
    fun draggingBelowMinWidthStretchesInward() {
        val state = create()
        state.startLibraryDrag(300.dp)

        state.dragLibrary((-50).dp, libraryMax = 420.dp, setMode = { error("不应切换形态") })

        assertEquals(DesktopDimens.LibraryMinWidth, state.libraryWidthPref)
        assertTrue(state.libraryStretch < 0.dp && state.libraryStretch > (-30).dp)
    }

    @Test
    fun draggingRailOutwardStretchesWithDiminishingRate() {
        val state = create(mode = LibraryMode.RAIL)
        val modes = mutableListOf<LibraryMode>()
        state.startLibraryDrag(DesktopDimens.LibraryRailWidth)

        state.dragLibrary(40.dp, libraryMax = 420.dp, setMode = { modes += it })
        val near = state.libraryStretch
        state.dragLibrary(80.dp, libraryMax = 420.dp, setMode = { modes += it })
        val far = state.libraryStretch
        assertTrue(modes.isEmpty())
        assertTrue(near > 0.dp && near < 20.dp)
        assertTrue(far - near < near)

        state.endLibraryDrag()
        assertEquals(0.dp, state.libraryStretch)
        assertTrue(state.librarySpring)
    }

    @Test
    fun draggingRailPastExpandThresholdSwitchesToDefault() {
        val state = create(mode = LibraryMode.RAIL)
        val modes = mutableListOf<LibraryMode>()
        state.startLibraryDrag(DesktopDimens.LibraryRailWidth)

        state.dragLibrary(DesktopDimens.PaneExpandWidth - DesktopDimens.LibraryRailWidth, libraryMax = 420.dp, setMode = { modes += it })

        assertEquals(listOf(LibraryMode.DEFAULT), modes)
        assertEquals(DesktopDimens.LibraryMinWidth, state.libraryWidthPref)
        assertTrue(state.libraryStretch < 0.dp)
    }

    @Test
    fun springIsKeptWhileDraggingAndClearedAfterSettle() {
        val state = create()
        state.startLibraryDrag(300.dp)

        state.settleLibrarySpring()
        assertTrue(state.librarySpring)

        state.endLibraryDrag()
        state.settleLibrarySpring()
        assertFalse(state.librarySpring)
    }

    @Test
    fun buttonModeChangeLeavesSpring() {
        val state = create()
        state.startLibraryDrag(300.dp)
        state.endLibraryDrag()

        state.applyLibraryMode(LibraryMode.EXPANDED)

        assertFalse(state.librarySpring)
    }

    @Test
    fun expandedDragKeepsDefaultBetweenThresholds() {
        val state = create(mode = LibraryMode.RAIL)
        val modes = mutableListOf<LibraryMode>()
        val setMode: (LibraryMode) -> Unit = {
            modes += it
            state.applyLibraryMode(it)
        }
        state.startLibraryDrag(DesktopDimens.LibraryRailWidth)
        state.dragLibrary(120.dp, libraryMax = 420.dp, setMode = setMode)

        // 回拖到两个阈值之间仍保持默认态
        state.dragLibrary(DesktopDimens.LibraryCollapseWidth - DesktopDimens.LibraryRailWidth + 10.dp, libraryMax = 420.dp, setMode = setMode)
        assertEquals(listOf(LibraryMode.DEFAULT), modes)

        state.dragLibrary(40.dp, libraryMax = 420.dp, setMode = setMode)
        assertEquals(listOf(LibraryMode.DEFAULT, LibraryMode.RAIL), modes)
        assertTrue(state.libraryStretch > 0.dp)
        assertTrue(state.librarySpring)
    }

    @Test
    fun draggingDockNarrowClosesIt() {
        val state = create()
        var closed = false
        state.startDockDrag(340.dp)

        state.dragDock(200.dp, dockMax = 420.dp, dockOpen = true) { closed = true }

        assertTrue(closed)
        assertFalse(state.resizing)
    }

    @Test
    fun draggingDockWiderSavesOnRelease() {
        val state = create()
        state.startDockDrag(340.dp)

        state.dragDock((-40).dp, dockMax = 420.dp, dockOpen = true) { error("不应收起") }
        state.endDockDrag()

        assertEquals(380.dp, state.dockWidthPref)
        assertEquals(listOf(380f), persistence.dockWidths)
    }

    @Test
    fun modeAndViewModeArePersistedOnlyWhenChanged() {
        val state = create()

        state.applyLibraryMode(LibraryMode.DEFAULT)
        state.applyLibraryMode(LibraryMode.EXPANDED)
        state.updateViewMode(LibraryViewMode.LIST)
        state.updateViewMode(LibraryViewMode.SMALL_GRID)

        assertEquals(listOf(LibraryMode.EXPANDED), persistence.modes)
        assertEquals(listOf(LibraryViewMode.SMALL_GRID), persistence.viewModes)
    }
}

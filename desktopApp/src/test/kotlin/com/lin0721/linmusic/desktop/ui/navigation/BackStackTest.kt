package com.lin0721.linmusic.desktop.ui.navigation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BackStackTest {

    private fun stack() = BackStack(DesktopRoute.Home, 0)

    @Test
    fun startsAtRootWithoutHistory() {
        val stack = stack()

        assertEquals(DesktopRoute.Home, stack.current)
        assertFalse(stack.canGoBack)
        assertFalse(stack.canGoForward)
    }

    @Test
    fun pushingSameRouteAndSnapshotIsIgnored() {
        val stack = stack()
        val step = stack.currentStep

        stack.push(DesktopRoute.Home, 0)
        stack.push(null, 0)

        assertEquals(step, stack.currentStep)
        assertFalse(stack.canGoBack)
    }

    @Test
    fun backAndForwardMoveBetweenStacks() {
        val stack = stack()
        stack.push(DesktopRoute.Search, 0)
        stack.push(DesktopRoute.Settings, 0)

        assertEquals(DesktopRoute.Search, stack.back()?.entry?.route)
        assertEquals(DesktopRoute.Search, stack.current)
        assertTrue(stack.canGoForward)

        assertEquals(DesktopRoute.Settings, stack.forward()?.entry?.route)
        assertFalse(stack.canGoForward)
    }

    @Test
    fun newStepClearsForwardStack() {
        val stack = stack()
        stack.push(DesktopRoute.Search, 0)
        stack.back()

        stack.push(DesktopRoute.Browse, 0)

        assertFalse(stack.canGoForward)
        assertNull(stack.forward())
        assertEquals(DesktopRoute.Browse, stack.current)
    }

    @Test
    fun backAtRootAndForwardWithoutHistoryAreNoOps() {
        val stack = stack()

        assertNull(stack.back())
        assertNull(stack.forward())
        assertEquals(DesktopRoute.Home, stack.current)
    }

    // 同一路由在历史里出现两次时栈帧 id 不同，各自保留页面现场
    @Test
    fun repeatedRouteGetsDistinctEntries() {
        val stack = stack()
        stack.push(DesktopRoute.Search, 0)
        val first = stack.currentEntry
        stack.push(DesktopRoute.Settings, 0)
        stack.push(DesktopRoute.Search, 0)

        assertEquals(first.route, stack.currentEntry.route)
        assertNotEquals(first.id, stack.currentEntry.id)
    }

    @Test
    fun uiOnlyStepKeepsPageEntry() {
        val stack = stack()
        stack.push(DesktopRoute.Search, 0)
        val entry = stack.currentEntry

        stack.push(null, 1)
        stack.push(DesktopRoute.Search, 2)

        assertEquals(entry, stack.currentEntry)
        assertEquals(2, stack.currentStep.ui)
        assertEquals(1, stack.back()?.ui)
        assertEquals(entry, stack.currentEntry)
    }

    @Test
    fun replaceUiRewritesCurrentStepWithoutAddingHistory() {
        val stack = stack()
        stack.push(DesktopRoute.Search, 1)

        stack.replaceUi(5)

        assertEquals(5, stack.currentStep.ui)
        assertEquals(0, stack.back()?.ui)
        assertEquals(5, stack.forward()?.ui)
    }

    @Test
    fun replaceUiMergesStepIdenticalToPrevious() {
        val stack = stack()
        stack.push(null, 1)

        stack.replaceUi(0)

        assertFalse(stack.canGoBack)
        assertEquals(0, stack.currentStep.ui)
    }

    @Test
    fun backToPreviousPageSkipsUiOnlySteps() {
        val stack = stack()
        stack.push(null, 1)
        stack.push(DesktopRoute.Search, 1)
        stack.push(null, 2)
        stack.push(null, 3)

        val step = stack.backToPreviousPage()

        assertEquals(DesktopRoute.Home, step?.entry?.route)
        assertEquals(1, step?.ui)
        repeat(3) { stack.forward() }
        assertEquals(3, stack.currentStep.ui)
    }

    @Test
    fun backToPreviousPageOnFirstPageIsNoOp() {
        val stack = stack()
        stack.push(null, 1)

        assertNull(stack.backToPreviousPage())
        assertEquals(1, stack.currentStep.ui)
    }

    @Test
    fun historyIsCappedDroppingOldestSteps() {
        val stack = stack()
        repeat(150) { stack.push(null, it + 1) }

        var steps = 1
        while (stack.back() != null) steps++

        assertEquals(100, steps)
        assertEquals(51, stack.currentStep.ui)
    }

    @Test
    fun liveEntriesIncludeForwardStackAndDropDiscarded() {
        val stack = stack()
        stack.push(DesktopRoute.Search, 0)
        val search = stack.currentEntry.id
        stack.push(null, 1)
        stack.back()
        stack.back()
        assertTrue(search in stack.liveEntryIds)

        stack.push(DesktopRoute.Settings, 0)

        assertFalse(search in stack.liveEntryIds)
        assertEquals(2, stack.liveEntryIds.size)
    }
}

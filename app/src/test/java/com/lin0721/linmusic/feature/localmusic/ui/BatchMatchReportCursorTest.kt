package com.lin0721.linmusic.feature.localmusic.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BatchMatchReportCursorTest {
    @Test fun confirmedRowDisappearingFromFilterShowsFollowingRowAtSameIndex() {
        assertEquals(3, nextBatchReportIndex(3, completedStillVisible = false, visibleCount = 8))
    }

    @Test fun allFilterKeepsSavedRowAndAdvancesPastIt() {
        assertEquals(4, nextBatchReportIndex(3, completedStillVisible = true, visibleCount = 8))
    }

    @Test fun finishingLastItemKeepsNearestRemainingItemVisible() {
        assertEquals(2, nextBatchReportIndex(3, completedStillVisible = false, visibleCount = 3))
    }

    @Test fun finishedFilterIsEmptyAndNeverChangesToAnotherFilter() {
        assertNull(nextBatchReportIndex(0, completedStillVisible = false, visibleCount = 0))
    }

    @Test fun backgroundMatchingCanRemoveEarlierRowsWithoutSkippingTheNextUnresolvedFile() {
        assertEquals(1, nextBatchReportIndex(4, completedStillVisible = false,
            visibleUris = listOf("earlier", "next", "following"), nextLocalUri = "next"))
    }

    @Test fun missingNextAnchorUsesNearestRemainingPosition() {
        assertEquals(1, nextBatchReportIndex(4, completedStillVisible = false,
            visibleUris = listOf("earlier", "following"), nextLocalUri = "already-resolved"))
    }
}

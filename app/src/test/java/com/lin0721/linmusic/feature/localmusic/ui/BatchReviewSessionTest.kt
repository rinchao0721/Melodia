package com.lin0721.linmusic.feature.localmusic.ui

import com.lin0721.linmusic.feature.localmusic.domain.BatchLocalMatchStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BatchReviewSessionTest {
    @Test fun chosenRowStartsOneFinitePassWithoutRepeatingEarlierRows() {
        var session = BatchReviewSession.fromUris(listOf("a", "b", "c", "b"), "b")!!
        assertEquals(listOf("b", "c", "a"), session.uris)
        assertEquals("1 / 3", session.positionLabel)
        session = session.advance(setOf("a", "b", "c"))!!
        assertEquals("c", session.currentUri)
        session = session.advance(setOf("a", "b", "c"))!!
        assertEquals("a", session.currentUri)
        assertNull(session.advance(setOf("a", "b", "c")))
    }

    @Test fun backgroundMatchesAreSkippedAndNewRowsWaitForNextPass() {
        val session = BatchReviewSession.fromUris(listOf("a", "b", "c"))!!
        assertEquals("c", session.advance(setOf("a", "c", "new"))?.currentUri)
        assertNull(session.copy(index = 2).advance(setOf("a", "new")))
    }

    @Test fun skipLeavesStatusUnchangedButDoesNotImmediatelyReopenSong() {
        val unresolved = setOf("a", "b")
        val session = BatchReviewSession.fromUris(unresolved.toList())!!
        val next = session.advance(unresolved)!!
        assertEquals("b", next.currentUri)
        assertNull(next.advance(unresolved))
        assertEquals("a", BatchReviewSession.fromUris(unresolved.toList())?.currentUri)
    }

    @Test fun emptyQueueAndFinishedQueueCloseCleanly() {
        assertNull(BatchReviewSession.fromUris(emptyList()))
        assertNull(BatchReviewSession.fromUris(listOf("a"))!!.advance(emptySet()))
    }

    @Test fun successfulAndExistingAssociationsNeverEnterUnresolvedQueue() {
        assertFalse(BatchLocalMatchStatus.MATCHED.isUnresolved())
        assertFalse(BatchLocalMatchStatus.ALREADY_LINKED.isUnresolved())
        listOf(BatchLocalMatchStatus.PENDING, BatchLocalMatchStatus.NO_MATCH,
            BatchLocalMatchStatus.NEEDS_CONFIRMATION, BatchLocalMatchStatus.ERROR).forEach {
            assertTrue(it.isUnresolved())
            assertTrue(BatchMatchFilter.REVIEW.accepts(it))
        }
    }
}

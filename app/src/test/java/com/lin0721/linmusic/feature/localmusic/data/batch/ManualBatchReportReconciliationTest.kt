package com.lin0721.linmusic.feature.localmusic.data.batch

import com.lin0721.linmusic.core.model.Album
import com.lin0721.linmusic.core.model.Artist
import com.lin0721.linmusic.core.model.Track
import com.lin0721.linmusic.feature.localmusic.domain.BatchLocalMatchStatus
import org.junit.Assert.*
import org.junit.Test

class ManualBatchReportReconciliationTest {
    private fun song(id: Long) = Track(id, "For Emma", listOf(Artist(name = "Bon Iver")), Album(name = "For Emma, Forever Ago"), dt = 220_000L)

    @Test fun unrelatedLargeLibraryDoesNotRequireFreshFileChecksOrWrites() {
        val unrelated = (1..4_351).map { ManualReportRowReference("unrelated:$it", "Song $it", it + 1_000L, it + 1_000L) }
        val related = listOf(
            ManualReportRowReference("selected", "For Emma", null, null),
            ManualReportRowReference("alias", " ＦＯＲ  Emma ", null, null),
            ManualReportRowReference("displaced-song", "Different file", 7L, null),
            ManualReportRowReference("previous-source", "Different tags", null, 8L)
        )
        assertEquals(setOf("selected", "alias", "displaced-song", "previous-source"),
            affectedManualReportUris(unrelated + related, "selected", "For Emma", setOf(7L, 8L)))
    }

    @Test fun blankTitlesDoNotTurnEveryUnknownFileIntoAnAlias() {
        val rows = listOf(ManualReportRowReference("selected", "", null, null),
            ManualReportRowReference("other", "  ", null, null))
        assertEquals(setOf("selected"), affectedManualReportUris(rows, "selected", "", emptySet()))
    }

    @Test fun latestReferenceWinsOverOlderSearchAndTappedCandidates() {
        val actual = song(9L)
        val update = reconcileManualReportRow(BatchLocalMatchStatus.MATCHED, 7L, actual, song(7L), selected = true)!!
        assertEquals(actual, update.track)
        assertEquals(BatchLocalMatchStatus.ALREADY_LINKED, update.status)
        assertFalse(update.usesExistingSource)
    }

    @Test fun duplicateEncodingKeepsExistingCanonicalSource() {
        val canonical = song(7L)
        val update = reconcileManualReportRow(BatchLocalMatchStatus.MATCHED, 7L, null, canonical, selected = false)!!
        assertEquals(BatchLocalMatchStatus.ALREADY_LINKED, update.status)
        assertEquals(canonical, update.track)
        assertTrue(update.usesExistingSource)
    }

    @Test fun successfulRowWithDisplacedOrUnreadableReferenceNeedsReview() {
        val update = reconcileManualReportRow(BatchLocalMatchStatus.ALREADY_LINKED, 7L, null, null, selected = false)!!
        assertEquals(BatchLocalMatchStatus.NEEDS_CONFIRMATION, update.status)
        assertNull(update.track)
        assertFalse(update.usesExistingSource)
    }

    @Test fun unresolvedRowWithoutNewPhysicalLinkKeepsItsCandidatesAndStatus() {
        assertNull(reconcileManualReportRow(BatchLocalMatchStatus.NEEDS_CONFIRMATION, null, null, null, selected = false))
        assertNull(reconcileManualReportRow(BatchLocalMatchStatus.NO_MATCH, null, null, null, selected = false))
        assertNull(reconcileManualReportRow(BatchLocalMatchStatus.ERROR, null, null, null, selected = false))
    }

    @Test fun unchangedSuccessfulAliasRetainsMatchedStatus() {
        val update = reconcileManualReportRow(BatchLocalMatchStatus.MATCHED, 7L, song(7L), null, selected = false)!!
        assertEquals(BatchLocalMatchStatus.MATCHED, update.status)
    }

    @Test fun explicitSelectionRequiresNoAutomaticMetadataThreshold() {
        val chosen = song(9L).copy(name = "Different spelling", dt = 5L)
        val update = reconcileManualReportRow(BatchLocalMatchStatus.NO_MATCH, null, chosen, null, selected = true)!!
        assertEquals(chosen, update.track)
        assertEquals(BatchLocalMatchStatus.ALREADY_LINKED, update.status)
    }
}

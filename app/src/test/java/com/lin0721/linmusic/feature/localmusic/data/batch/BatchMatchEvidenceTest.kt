package com.lin0721.linmusic.feature.localmusic.data.batch

import com.lin0721.linmusic.core.model.Album
import com.lin0721.linmusic.core.model.Artist
import com.lin0721.linmusic.core.model.Track
import com.lin0721.linmusic.feature.localmusic.domain.BatchOnlineMatchStatus
import com.lin0721.linmusic.feature.localmusic.domain.LocalAudioCandidate
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

class BatchMatchEvidenceTest {
    private val local = LocalAudioCandidate("content:new-mp3", "01 Song", "Artist", 180_000L,
        album = "Album", fileName = "01 Song.mp3")
    private val known = Track(id = 10, name = "Song", ar = listOf(Artist(name = "Artist")),
        al = Album(name = "Album"), dt = 180_000L)

    @Test fun alternateEncodingUsesConfirmedEvidenceWithoutCallingNetwork() = runTest {
        var calls = 0
        val result = batchMatchEvidence(local, listOf(known)) { calls++; BatchSearchPage(emptyList(), true) }
        assertEquals(0, calls)
        assertEquals(10L, (result as BatchMatchEvidence.Confirmed).decision.matchedTrack?.id)
    }
    @Test fun equivalentConfirmedIdsSelectTheStableBestWithoutCallingNetwork() = runTest {
        var calls = 0
        val result = batchMatchEvidence(local, listOf(known, known.copy(id = 11))) { calls++; BatchSearchPage(emptyList(), true) }
        assertEquals(0, calls)
        val decision = (result as BatchMatchEvidence.Confirmed).decision
        assertEquals(BatchOnlineMatchStatus.MATCHED, decision.status)
        assertEquals(10L, decision.matchedTrack?.id)
    }
    @Test fun albumMismatchCannotSuppressAnActualSearch() = runTest {
        var calls = 0
        val result = batchMatchEvidence(local, listOf(known.copy(al = Album(name = "Another Album")))) {
            calls++; BatchSearchPage(listOf(known), true)
        }
        assertEquals(1, calls)
        assertTrue(result is BatchMatchEvidence.Search)
    }
    @Test fun liveVersionCannotReuseStudioMetadata() = runTest {
        var calls = 0
        val result = batchMatchEvidence(local.copy(title = "Song (Live)", fileName = "Song (Live).mp3"), listOf(known)) {
            calls++; BatchSearchPage(emptyList(), true)
        }
        assertEquals(1, calls)
        assertTrue(result is BatchMatchEvidence.Search)
    }
    @Test fun incompleteOlderCacheStillRequestsOnlineMetadata() = runTest {
        var calls = 0
        val result = batchMatchEvidence(local, listOf(known.copy(al = Album(), dt = 0))) {
            calls++; BatchSearchPage(listOf(known), true)
        }
        assertEquals(1, calls)
        assertTrue(result is BatchMatchEvidence.Search)
    }
}

package com.lin0721.linmusic.feature.localmusic.domain

import com.lin0721.linmusic.core.model.Track
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BatchAlbumWritePolicyTest {
    private val online = Track(id = 100L, name = "Song", dt = 180_000L)
    private fun local(uri: String, file: String = "Song.mp3", duration: Long = 180_000L, bytes: Long = 5_000_000L, title: String = "Song") =
        BatchAlbumWritableLocal(AlbumScopedLocal(LocalAudioCandidate(uri, title, "", duration, fileName = file)), bytes)

    @Test fun singletonAndNonCollidingCasesKeepTheManualPlanExactly() {
        val rows = listOf(local("a"), local("b", title = "Another"))
        assertEquals(AlbumScopedMatchPolicy.plan(rows.map { it.scoped }, listOf(online)), BatchAlbumWritePolicy.plan(rows, listOf(online)))
    }

    @Test fun losslessEncodingWinsButOtherEncodingDoesNotPretendToBeSaved() {
        val rows = listOf(local("mp3", bytes = 80_000_000L), local("flac", file = "Song.FLAC", duration = 181_000L))
        val planned = BatchAlbumWritePolicy.plan(rows, listOf(online))
        assertEquals(listOf("flac"), planned.filter { it.status == AlbumScopedMatchStatus.MATCH }.map { it.localUri })
        val duplicate = planned.first { it.localUri == "mp3" }
        assertEquals(AlbumScopedMatchStatus.REVIEW, duplicate.status)
        assertNull(duplicate.match)
        assertTrue(duplicate.reason.contains("保存成功后"))
        assertTrue(AlbumScopedMatchPolicy.plan(rows.map { it.scoped }, listOf(online)).all { it.status == AlbumScopedMatchStatus.REVIEW })
    }

    @Test fun sameLosslessTierUsesClosestDurationRatherThanUncompressedFileSize() {
        val rows = listOf(local("wav", file = "Song.wav", duration = 181_000L, bytes = 90_000_000L),
            local("flac", file = "Song.flac", bytes = 20_000_000L))
        assertEquals("flac", BatchAlbumWritePolicy.plan(rows, listOf(online)).single { it.status == AlbumScopedMatchStatus.MATCH }.localUri)
    }

    @Test fun m4aContainerIsNotAssumedToBeLosslessAndOrdinaryEncodingRateCanBreakATie() {
        val rows = listOf(local("m4a", file = "Song.m4a", bytes = 30_000_000L), local("flac", file = "Song.flac", bytes = 10_000_000L))
        assertEquals("flac", BatchAlbumWritePolicy.plan(rows, listOf(online)).single { it.status == AlbumScopedMatchStatus.MATCH }.localUri)
        val ordinary = listOf(local("low", bytes = 2_000_000L), local("high", file = "Song.m4a", bytes = 8_000_000L))
        assertEquals("high", BatchAlbumWritePolicy.plan(ordinary, listOf(online)).single { it.status == AlbumScopedMatchStatus.MATCH }.localUri)
    }

    @Test fun equalQualityAndTimingUseAStableUriIndependentOfInputOrder() {
        val rows = listOf(local("z", file = "Song.flac"), local("a", file = "Song.flac"))
        listOf(rows, rows.reversed()).forEach {
            assertEquals("a", BatchAlbumWritePolicy.plan(it, listOf(online)).single { it.status == AlbumScopedMatchStatus.MATCH }.localUri)
        }
    }

    @Test fun twoSecondWholeGroupSpreadIsInclusiveButOneExtraMillisecondNeedsReview() {
        val first = local("a", file = "Song.flac")
        assertEquals(1, BatchAlbumWritePolicy.plan(listOf(first, local("b", duration = 182_000L)), listOf(online)).count { it.status == AlbumScopedMatchStatus.MATCH })
        assertTrue(BatchAlbumWritePolicy.plan(listOf(first, local("b", duration = 182_001L)), listOf(online)).all { it.status == AlbumScopedMatchStatus.REVIEW })
    }

    @Test fun adjacentDurationPairsCannotHideAnExcessiveWholeGroupSpread() {
        val rows = listOf(local("a"), local("b", duration = 181_500L), local("c", duration = 183_000L))
        assertTrue(BatchAlbumWritePolicy.plan(rows, listOf(online)).all { it.status == AlbumScopedMatchStatus.REVIEW })
    }

    @Test fun unknownDurationAndDifferentRecordingRemainUnconfirmed() {
        val rows = listOf(local("a"), local("unknown", duration = 0L),
            local("live", title = "Song (Live)", file = "Song (Live).flac"))
        val planned = BatchAlbumWritePolicy.plan(rows, listOf(online))
        assertEquals("a", planned.single { it.status == AlbumScopedMatchStatus.MATCH }.localUri)
        assertTrue(planned.filter { it.localUri != "a" }.all { it.status != AlbumScopedMatchStatus.MATCH && it.match == null })
    }

    @Test fun aTwentyFourFileAlbumSelectsTwelveSourcesAndKeepsDuplicateRecommendationsPending() {
        val tracks = (0 until 12).map { online.copy(id = 100L + it, name = "Song $it") }
        val rows = tracks.flatMapIndexed { index, track -> listOf(
            local("$index:mp3", file = "${track.name}.mp3", title = track.name),
            local("$index:flac", file = "${track.name}.flac", title = track.name)) }
        val planned = BatchAlbumWritePolicy.plan(rows, tracks)
        assertEquals(12, planned.count { it.status == AlbumScopedMatchStatus.MATCH })
        assertEquals(12, planned.count { it.status == AlbumScopedMatchStatus.REVIEW })
        assertTrue(planned.filter { it.status == AlbumScopedMatchStatus.MATCH }.all { it.localUri.endsWith(":flac") })
    }

    @Test fun repeatedUrisDoNotCreateAdditionalWritersOrAdditionalRows() {
        val rows = listOf(local("a", file = "Song.flac"), local("b"))
        assertEquals(BatchAlbumWritePolicy.plan(rows, listOf(online)), BatchAlbumWritePolicy.plan(rows + rows, listOf(online)))
        assertEquals(2, BatchAlbumWritePolicy.plan(rows + rows, listOf(online)).size)
    }
}

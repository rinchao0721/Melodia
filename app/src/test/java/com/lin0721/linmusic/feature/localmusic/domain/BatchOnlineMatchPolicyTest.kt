package com.lin0721.linmusic.feature.localmusic.domain

import com.lin0721.linmusic.core.model.Album
import com.lin0721.linmusic.core.model.Artist
import com.lin0721.linmusic.core.model.Track
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class BatchOnlineMatchPolicyTest {
    private val local = LocalAudioCandidate("content://music/1", "01 五味瓶", "Artist", 180_000L, album = "Album", fileName = "01 五味瓶.flac")
    private val online = Track(id = 100L, name = "五味瓶", ar = listOf(Artist(id = 10L, name = "Artist")), al = Album(id = 20L, name = "Album"), dt = 180_200L)

    @Test fun aUniqueCompleteMatchKeepsTheOriginalOnlineMetadata() {
        val decision = BatchOnlineMatchPolicy.evaluate(local, listOf(online))
        assertEquals(BatchOnlineMatchStatus.MATCHED, decision.status)
        assertSame(online, decision.matchedTrack)
        assertEquals(listOf(online), decision.candidates)
        assertEquals("01 五味瓶", local.title)
    }

    @Test fun incompleteLocalMetadataCannotCreateAnAutomaticAssociation() {
        listOf(
            local.copy(title = "", fileName = null), local.copy(artist = ""), local.copy(artist = "未知歌手"),
            local.copy(artist = "Artist, <unknown>"), local.copy(album = null),
            local.copy(album = "Unknown Album"), local.copy(durationMs = 0L)
        ).forEach { incomplete ->
            val decision = BatchOnlineMatchPolicy.evaluate(incomplete, listOf(online))
            assertEquals(BatchOnlineMatchStatus.NO_MATCH, decision.status)
            assertNull(decision.matchedTrack)
            assertTrue(decision.reason.isNotBlank())
        }
    }

    @Test fun sameTitleByAnotherArtistOrOnAnotherAlbumIsNotAccepted() {
        listOf(
            online.copy(ar = listOf(Artist(name = "Other Artist"))),
            online.copy(al = Album(name = "Other Album"))
        ).forEach {
            assertEquals(BatchOnlineMatchStatus.NO_MATCH, BatchOnlineMatchPolicy.evaluate(local, listOf(it)).status)
        }
    }

    @Test fun knownAlbumDurationToleranceIsBounded() {
        val longLocal = local.copy(durationMs = 304_838L)
        assertEquals(BatchOnlineMatchStatus.MATCHED, BatchOnlineMatchPolicy.evaluate(longLocal, listOf(online.copy(dt = 300_000L))).status)
        assertEquals(BatchOnlineMatchStatus.NO_MATCH, BatchOnlineMatchPolicy.evaluate(longLocal.copy(durationMs = 305_001L), listOf(online.copy(dt = 300_000L))).status)
    }

    @Test fun visibleAndFilenameOnlyVersionChangesRequireConfirmation() {
        assertEquals(BatchOnlineMatchStatus.NO_MATCH, BatchOnlineMatchPolicy.evaluate(local.copy(title = "01 五味瓶 (Live)"), listOf(online)).status)
        assertEquals(BatchOnlineMatchStatus.NO_MATCH, BatchOnlineMatchPolicy.evaluate(local.copy(fileName = "Artist - 五味瓶 (Live).flac"), listOf(online)).status)
        assertEquals(BatchOnlineMatchStatus.MATCHED,
            BatchOnlineMatchPolicy.evaluate(local.copy(title = "01 五味瓶 (Live)", fileName = "01 五味瓶 (Live).flac"), listOf(online.copy(name = "五味瓶 (Live)"))).status)
    }

    @Test fun multiArtistSlashTagsRequireAllVerifiedArtistsAndTheSameAlbum() {
        val duet = local.copy(artist = "A/B")
        val duetOnline = online.copy(ar = listOf(Artist(name = "B"), Artist(name = "A")))
        assertEquals(BatchOnlineMatchStatus.MATCHED, BatchOnlineMatchPolicy.evaluate(duet, listOf(duetOnline)).status)
        assertEquals(BatchOnlineMatchStatus.NO_MATCH, BatchOnlineMatchPolicy.evaluate(duet, listOf(duetOnline.copy(ar = listOf(Artist(name = "A"))))).status)
        assertEquals(BatchOnlineMatchStatus.NO_MATCH, BatchOnlineMatchPolicy.evaluate(duet, listOf(duetOnline.copy(al = Album(name = "Other Album")))).status)
    }

    @Test fun slashInsideOneBandNameIsPreserved() {
        val band = local.copy(artist = "AC/DC")
        assertEquals(BatchOnlineMatchStatus.MATCHED, BatchOnlineMatchPolicy.evaluate(band, listOf(online.copy(ar = listOf(Artist(name = "AC/DC"))))).status)
        assertEquals(BatchOnlineMatchStatus.NO_MATCH, BatchOnlineMatchPolicy.evaluate(band, listOf(online.copy(ar = listOf(Artist(name = "AC"))))).status)
    }

    @Test fun severalEquivalentOnlineIdsSelectAStableBestMatch() {
        val alternate = online.copy(id = 101L)
        val decision = BatchOnlineMatchPolicy.evaluate(local, listOf(online, alternate))
        assertEquals(BatchOnlineMatchStatus.MATCHED, decision.status)
        assertEquals(listOf(online, alternate), decision.candidates)
        assertSame(online, decision.matchedTrack)
    }

    @Test fun repeatedSearchPagesForTheSameSongAreNotAmbiguous() {
        val decision = BatchOnlineMatchPolicy.evaluate(local, listOf(online, online.copy()))
        assertEquals(BatchOnlineMatchStatus.MATCHED, decision.status)
        assertEquals(1, decision.candidates.size)
    }

    @Test fun incompleteOnlineMetadataDoesNotOutvoteACompleteUniqueMatch() {
        val incomplete = listOf(
            online.copy(id = 101L, ar = emptyList()),
            online.copy(id = 102L, ar = listOf(Artist(name = "Artist"), Artist(name = ""))),
            online.copy(id = 103L, al = Album(name = "")),
            online.copy(id = 104L, dt = 0L)
        )
        assertEquals(BatchOnlineMatchStatus.NO_MATCH, BatchOnlineMatchPolicy.evaluate(local, incomplete).status)
        assertSame(online, BatchOnlineMatchPolicy.evaluate(local, incomplete + online).matchedTrack)
    }

    @Test fun emptyOrInvalidSearchResultsNeverProduceAConfirmedMatch() {
        assertEquals(BatchOnlineMatchStatus.NO_MATCH, BatchOnlineMatchPolicy.evaluate(local, emptyList()).status)
        assertEquals(BatchOnlineMatchStatus.NO_MATCH, BatchOnlineMatchPolicy.evaluate(local, listOf(online.copy(id = 0L))).status)
    }

    @Test fun numericCatalogNamesAreKeptAndNoArtistAliasIsGuessed() {
        assertEquals(BatchOnlineMatchStatus.MATCHED,
            BatchOnlineMatchPolicy.evaluate(local.copy(title = "01 7 rings"), listOf(online.copy(name = "7 rings"))).status)
        assertEquals(BatchOnlineMatchStatus.NO_MATCH,
            BatchOnlineMatchPolicy.evaluate(local.copy(artist = "GAI"), listOf(online.copy(ar = listOf(Artist(name = "GAI周延"))))).status)
        assertEquals(BatchOnlineMatchStatus.NO_MATCH,
            BatchOnlineMatchPolicy.evaluate(local.copy(title = "五味瓶"), listOf(online.copy(name = "01 五味瓶"))).status)
    }

    @Test fun numberedArtistTitleAndTitleArtistFormsUseOneResolver() {
        listOf("01 Artist - 五味瓶", "01 五味瓶 - Artist", "Artist—五味瓶", "五味瓶_Artist").forEach {
            assertSame(online, BatchOnlineMatchPolicy.evaluate(local.copy(title = it), listOf(online)).matchedTrack)
        }
        val unknown = local.copy(title = "01 Artist - 五味瓶", artist = "<unknown>", fileName = "01 Artist - 五味瓶.flac")
        assertSame(online, BatchOnlineMatchPolicy.evaluate(unknown, listOf(online)).matchedTrack)
    }

    @Test fun deliberatelyReducedLocalArtistCreditsAreAcceptedButUnsharedArtistsAreNot() {
        val collaboration = online.copy(ar = listOf(Artist(name = "Artist"), Artist(name = "Guest")))
        assertSame(collaboration, BatchOnlineMatchPolicy.evaluate(local, listOf(collaboration)).matchedTrack)
        assertEquals(BatchOnlineMatchStatus.NO_MATCH,
            BatchOnlineMatchPolicy.evaluate(local.copy(artist = "Unrelated"), listOf(collaboration)).status)
        assertEquals(BatchOnlineMatchStatus.NO_MATCH,
            BatchOnlineMatchPolicy.evaluate(local.copy(artist = "Artist, Another Guest"), listOf(collaboration)).status)
    }

    @Test fun regionalAndRemasteredAlbumEditionsAreEquivalentWithinBoundedDurationTolerance() {
        listOf("Album (Japan Edition)", "Album [US Edition]", "Album - 2011 Remastered", "Album (重制版)", "Album (欧版)").forEach { album ->
            val edition = online.copy(al = Album(name = album), dt = 182_500L)
            assertSame(edition, BatchOnlineMatchPolicy.evaluate(local, listOf(edition)).matchedTrack)
        }
        assertEquals(BatchOnlineMatchStatus.NO_MATCH,
            BatchOnlineMatchPolicy.evaluate(local, listOf(online.copy(al = Album(name = "Other Album (Japan Edition)")))).status)
        assertEquals(BatchOnlineMatchStatus.NO_MATCH,
            BatchOnlineMatchPolicy.evaluate(local, listOf(online.copy(al = Album(name = "Album (Live)")))).status)
    }

    @Test fun bestEquivalentIdPrefersExactAlbumThenCompleteCreditThenClosestDuration() {
        val exact = online.copy(id = 500L, dt = 181_500L)
        val regionalCloser = online.copy(id = 1L, al = Album(name = "Album (Japan Edition)"), dt = 180_000L)
        assertSame(exact, BatchOnlineMatchPolicy.evaluate(local, listOf(regionalCloser, exact)).matchedTrack)
        val duetCloser = online.copy(id = 2L, ar = listOf(Artist(name = "Artist"), Artist(name = "Guest")), dt = 180_000L)
        assertSame(exact, BatchOnlineMatchPolicy.evaluate(local, listOf(duetCloser, exact)).matchedTrack)
        val closest = online.copy(id = 700L, dt = 180_001L)
        assertSame(closest, BatchOnlineMatchPolicy.evaluate(local, listOf(exact, closest)).matchedTrack)
        val same = closest.copy(id = 701L)
        assertSame(closest, BatchOnlineMatchPolicy.evaluate(local, listOf(same, closest)).matchedTrack)
    }

    @Test fun aStudioAndLiveSearchResultNeverBecomeEquivalentBecauseOfReleaseAnnotations() {
        val live = online.copy(id = 1L, name = "五味瓶 (Live)", al = Album(name = "Album (Japan Edition)"))
        assertSame(online, BatchOnlineMatchPolicy.evaluate(local, listOf(live, online)).matchedTrack)
        assertEquals(BatchOnlineMatchStatus.NO_MATCH,
            BatchOnlineMatchPolicy.evaluate(local.copy(fileName = "Artist - 五味瓶 (Live).flac"), listOf(online)).status)
    }

    @Test fun aGenuineNumberedCatalogTitleWinsOverTheStrippedAlternative() {
        val originalTitle = online.copy(id = 500L, name = "01 五味瓶")
        val strippedTitle = online.copy(id = 1L)
        assertSame(originalTitle, BatchOnlineMatchPolicy.evaluate(local, listOf(strippedTitle, originalTitle)).matchedTrack)
        val creditTitle = local.copy(title = "01 Artist - 五味瓶")
        assertSame(strippedTitle, BatchOnlineMatchPolicy.evaluate(creditTitle, listOf(originalTitle, strippedTitle)).matchedTrack)
    }

    @Test fun olderScannerFallbackArtistIncludesTheIndexButCanStillMatchTheCorrectArtist() {
        val older = local.copy(title = "五味瓶", artist = "01 Artist", fileName = "01 Artist - 五味瓶.flac")
        assertSame(online, BatchOnlineMatchPolicy.evaluate(older, listOf(online)).matchedTrack)
        val numericArtist = online.copy(ar = listOf(Artist(name = "01 Artist")))
        assertSame(numericArtist, BatchOnlineMatchPolicy.evaluate(older, listOf(numericArtist)).matchedTrack)
    }
}

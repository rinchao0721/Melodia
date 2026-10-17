package com.lin0721.linmusic.feature.artist.domain

import com.lin0721.linmusic.core.model.ArtistAlbum
import com.lin0721.linmusic.core.preferences.ArtistAlbumFilterSettings
import org.junit.Assert.*
import org.junit.Test

class ArtistAlbumSelectionTest {
    private val albums = listOf(
        ArtistAlbum(id = 1, size = 1),
        ArtistAlbum(id = 2, size = 7),
        ArtistAlbum(id = 3, size = 8),
        ArtistAlbum(id = 4, size = 12),
        ArtistAlbum(id = 5, size = 0)
    )

    @Test
    fun zeroIsTheDefaultAndDisplaysTheCompleteOriginalList() {
        assertEquals(0, ArtistAlbumFilterSettings.DEFAULT_MIN_TRACKS)
        assertSame(albums, ArtistAlbumSelection.visibleAlbums(albums, 0))
    }

    @Test
    fun minimumIsInclusiveAndUnknownCountsDoNotPassAPositiveMinimum() {
        assertEquals(listOf(3L, 4L), ArtistAlbumSelection.visibleAlbums(albums, 8).map { it.id })
    }

    @Test
    fun returningToZeroRestoresSinglesAndUnknownCountsWithoutRefetching() {
        ArtistAlbumSelection.visibleAlbums(albums, 8)
        assertEquals(listOf(1L, 2L, 3L, 4L, 5L), ArtistAlbumSelection.visibleAlbums(albums, 0).map { it.id })
    }

    @Test
    fun anEmptyFilteredPageContinuesUntilALaterPageContainsAlbums() {
        val first = ArtistAlbumPage(List(20) { ArtistAlbum(id = (it + 10).toLong(), size = 1) }, true)
        val second = ArtistAlbumPage(listOf(ArtistAlbum(id = 30, size = 10)), false)
        val firstVisible = ArtistAlbumSelection.visibleAlbums(first.albums, 8)
        assertTrue(firstVisible.isEmpty())
        assertTrue(ArtistAlbumSelection.shouldLoadMore(false, firstVisible.size, ArtistAlbumSelection.hasNextPage(first), false, null))
        val combined = first.albums + second.albums
        assertEquals(listOf(30L), ArtistAlbumSelection.visibleAlbums(combined, 8).map { it.id })
        assertEquals(21, combined.size) // Offset is based on original rows, never filtered rows.
        assertFalse(ArtistAlbumSelection.hasNextPage(second))
    }

    @Test
    fun existingFilteredRowsLoadWhenNearTheEnd() {
        assertFalse(ArtistAlbumSelection.shouldLoadMore(false, 2, true, false, null))
        assertTrue(ArtistAlbumSelection.shouldLoadMore(true, 2, true, false, null))
    }

    @Test
    fun errorsAndInFlightRequestsPreventRepeatedAutomaticRequests() {
        assertFalse(ArtistAlbumSelection.shouldLoadMore(true, 0, true, true, null))
        assertFalse(ArtistAlbumSelection.shouldLoadMore(true, 0, true, false, "网络异常"))
        assertTrue(ArtistAlbumSelection.shouldLoadMore(true, 0, true, false, null))
    }

    @Test
    fun anEmptyPageClaimingMoreIsRetryableWhileAnExplicitEndStopsPagination() {
        val stalled = ArtistAlbumSelection.applyPage(albums, 5, ArtistAlbumPage(emptyList(), true))
        assertTrue(stalled.hasMore)
        assertNotNull(stalled.error)
        assertFalse(ArtistAlbumSelection.shouldLoadMore(true, 0, stalled.hasMore, false, stalled.error))
        assertFalse(ArtistAlbumSelection.shouldLoadMore(true, 0, false, false, null))
    }

    @Test
    fun completeCatalogueLoadsPastViewportButStillStopsForErrorLoadingAndCompletion() {
        assertTrue(ArtistAlbumSelection.shouldLoadMore(false, 3, true, false, null, completeCatalog = true))
        assertFalse(ArtistAlbumSelection.shouldLoadMore(false, 3, true, true, null, completeCatalog = true))
        assertFalse(ArtistAlbumSelection.shouldLoadMore(false, 3, true, false, "网络异常", completeCatalog = true))
        assertFalse(ArtistAlbumSelection.shouldLoadMore(false, 3, false, false, null, completeCatalog = true))
    }

    @Test
    fun anInitialEmptyPageDoesNotPretendTheCatalogueIsComplete() {
        val first = ArtistAlbumSelection.applyPage(emptyList(), 0, ArtistAlbumPage(emptyList(), true))
        assertTrue(first.albums.isEmpty())
        assertTrue(first.hasMore)
        assertEquals(0, first.nextOffset)
        assertNotNull(first.error)
        assertFalse(ArtistAlbumSelection.shouldLoadMore(false, 0, first.hasMore, false, first.error, true))
    }

    @Test
    fun repeatedExistingIdsPreserveOffsetAndRowsUntilUserRetries() {
        val first = ArtistAlbumSelection.applyPage(emptyList(), 0, ArtistAlbumPage(albums, true))
        val repeated = ArtistAlbumSelection.applyPage(first.albums, first.nextOffset, ArtistAlbumPage(albums, true))
        assertSame(first.albums, repeated.albums)
        assertEquals(5, repeated.nextOffset)
        assertTrue(repeated.hasMore)
        assertNotNull(repeated.error)
        val retry = ArtistAlbumSelection.applyPage(repeated.albums, repeated.nextOffset,
            ArtistAlbumPage(listOf(ArtistAlbum(id = 6L)), false))
        assertEquals(listOf(1L, 2L, 3L, 4L, 5L, 6L), retry.albums.map { it.id })
        assertEquals(6, retry.nextOffset)
        assertNull(retry.error)
        assertFalse(retry.hasMore)
    }

    @Test
    fun mixedDuplicateAndNewRowsAdvanceByRawCountAndKeepStableUniqueRows() {
        val newerCopy = ArtistAlbum(id = 3, name = "Repeated edition")
        val progress = ArtistAlbumSelection.applyPage(albums, 20,
            ArtistAlbumPage(listOf(newerCopy, ArtistAlbum(id = 6), ArtistAlbum(id = 6), ArtistAlbum(id = 7)), true))
        assertEquals(24, progress.nextOffset)
        assertEquals(listOf(1L, 2L, 3L, 4L, 5L, 6L, 7L), progress.albums.map { it.id })
        assertEquals(albums[2], progress.albums.first { it.id == 3L })
        assertNull(progress.error)
        assertTrue(progress.hasMore)
    }

    @Test
    fun duplicateRowsWithinInitialPageAdvanceRawOffsetOnlyOnceInSourceList() {
        val first = ArtistAlbumSelection.applyPage(emptyList(), 0,
            ArtistAlbumPage(listOf(ArtistAlbum(id = 1), ArtistAlbum(id = 1), ArtistAlbum(id = 2)), true))
        assertEquals(3, first.nextOffset)
        assertEquals(listOf(1L, 2L), first.albums.map { it.id })
        assertNull(first.error)
    }

    @Test
    fun emptyOrRepeatedTerminalPageCompletesWithoutRetryableStall() {
        val emptyEnd = ArtistAlbumSelection.applyPage(albums, 20, ArtistAlbumPage(emptyList(), false))
        assertFalse(emptyEnd.hasMore)
        assertEquals(20, emptyEnd.nextOffset)
        assertNull(emptyEnd.error)
        val repeatedEnd = ArtistAlbumSelection.applyPage(albums, 20, ArtistAlbumPage(albums.take(2), false))
        assertFalse(repeatedEnd.hasMore)
        assertEquals(22, repeatedEnd.nextOffset)
        assertEquals(albums, repeatedEnd.albums)
        assertNull(repeatedEnd.error)
    }

    @Test
    fun completeCatalogueKeepsReadingUntilServerEndIncludingOlderAlbumsPastExpectedCount() {
        var progress = ArtistAlbumPageProgress(emptyList(), 0, true)
        val offsets = mutableListOf<Int>()
        while (ArtistAlbumSelection.shouldLoadMore(false,
                ArtistAlbumSelection.visibleAlbums(progress.albums, 8).size,
                progress.hasMore, false, progress.error, completeCatalog = true)) {
            offsets += progress.nextOffset
            val remaining = 164 - progress.nextOffset
            val count = minOf(remaining, 20)
            val page = List(count) { index ->
                val id = progress.nextOffset + index + 1
                ArtistAlbum(id = id.toLong(), size = if (id == 21 || id > 160) 10 else 1)
            }
            progress = ArtistAlbumSelection.applyPage(progress.albums, progress.nextOffset,
                ArtistAlbumPage(page, remaining > 20))
        }
        assertEquals(listOf(0, 20, 40, 60, 80, 100, 120, 140, 160), offsets)
        assertEquals(164, progress.nextOffset)
        assertEquals(164, progress.albums.size)
        assertEquals(listOf(21L, 161L, 162L, 163L, 164L),
            ArtistAlbumSelection.visibleAlbums(progress.albums, 8).map { it.id })
        assertNull(progress.error)
    }

    @Test
    fun overflowingOffsetIsAnErrorAndNeverWrapsOrAdvances() {
        val progress = ArtistAlbumSelection.applyPage(emptyList(), Int.MAX_VALUE,
            ArtistAlbumPage(listOf(ArtistAlbum(id = 1)), true))
        assertEquals(Int.MAX_VALUE, progress.nextOffset)
        assertTrue(progress.hasMore)
        assertNotNull(progress.error)
    }

    @Test
    fun invalidOnlyContinuationPageStallsWithoutRenderingInvalidAlbums() {
        val first = ArtistAlbumSelection.applyPage(emptyList(), 0,
            ArtistAlbumPage(listOf(ArtistAlbum(id = 0), ArtistAlbum(id = -1)), true))
        assertTrue(first.albums.isEmpty())
        assertEquals(0, first.nextOffset)
        assertTrue(first.hasMore)
        assertNotNull(first.error)
        val existing = ArtistAlbumSelection.applyPage(albums, 20,
            ArtistAlbumPage(listOf(ArtistAlbum(id = 0), ArtistAlbum(id = -1)), true))
        assertSame(albums, existing.albums)
        assertEquals(20, existing.nextOffset)
        assertNotNull(existing.error)
    }

    @Test
    fun mixedInvalidRowsStillAdvanceByRawPageSizeButOnlyPositiveIdsEnterSourceList() {
        val first = ArtistAlbumSelection.applyPage(emptyList(), 0,
            ArtistAlbumPage(listOf(ArtistAlbum(id = 0), ArtistAlbum(id = 1), ArtistAlbum(id = -1), ArtistAlbum(id = 2)), true))
        assertEquals(listOf(1L, 2L), first.albums.map { it.id })
        assertEquals(4, first.nextOffset)
        assertNull(first.error)
        val terminal = ArtistAlbumSelection.applyPage(first.albums, first.nextOffset,
            ArtistAlbumPage(listOf(ArtistAlbum(id = -1)), false))
        assertEquals(listOf(1L, 2L), terminal.albums.map { it.id })
        assertEquals(5, terminal.nextOffset)
        assertNull(terminal.error)
        assertFalse(terminal.hasMore)
    }

    @Test
    fun invalidSavedThresholdsAreBoundedAndNegativeThresholdsRestoreAll() {
        assertEquals(0, ArtistAlbumFilterSettings.normalizeMinTracks(-5))
        assertEquals(100, ArtistAlbumFilterSettings.normalizeMinTracks(999))
        assertSame(albums, ArtistAlbumSelection.visibleAlbums(albums, -5))
    }
}

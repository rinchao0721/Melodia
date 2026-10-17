package com.lin0721.linmusic.feature.localmusic.domain

import com.lin0721.linmusic.core.model.Album
import com.lin0721.linmusic.core.model.Artist
import com.lin0721.linmusic.core.model.Track
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class BatchAlbumMatchPolicyTest {
    private val artist = Artist(id = 1L, name = "Artist")
    private val album = Album(id = 20L, name = "Album", artists = listOf(artist))
    private val songs = listOf("First Song", "Second Song", "Third Song").mapIndexed { index, name ->
        Track(id = 100L + index, name = name, ar = listOf(artist), al = album, dt = 180_000L + index * 20_000L)
    }
    private val local = BatchAlbumLocal("Album", "Artist", songs.mapIndexed { index, song ->
        AlbumScopedLocal(LocalAudioCandidate("content://music/$index", "0${index + 1} ${song.name}", "Artist", song.dt,
            album = "Album", fileName = "0${index + 1} ${song.name}.flac"), index + 1)
    })
    private val detail = BatchAlbumDetail(album, songs)

    @Test fun searchQueriesCleanReleaseAnnotationsAndIncludeKnownArtist() {
        val queries = BatchAlbumMatchPolicy.queries(local.copy(name = "Album (2011 Remastered) (Japan Edition)"))
        assertEquals(listOf("album Artist", "Artist album"), queries)
        assertTrue(queries.none { "remaster" in it || "japan" in it })
    }

    @Test fun unknownArtistDoesNotTriggerAlbumOnlySearch() {
        listOf("", "<unknown>", "unknown", "未知歌手", "未知艺术家").forEach { name ->
            assertTrue(name, BatchAlbumMatchPolicy.queries(local.copy(artist = name)).isEmpty())
        }
    }

    @Test fun unknownAlbumDoesNotTriggerSearch() {
        listOf("", "Unknown Album", "未知专辑").forEach { name ->
            assertTrue(name, BatchAlbumMatchPolicy.queries(local.copy(name = name)).isEmpty())
        }
    }

    @Test fun searchQueryNeverStripsLiveOrRemixAnnotations() {
        val queries = BatchAlbumMatchPolicy.queries(local.copy(name = "Album (Live)"))
        assertTrue(queries.all { "live" in it })
        assertTrue(BatchAlbumMatchPolicy.queries(local.copy(name = "Album (Remix)" )).all { "remix" in it })
    }

    @Test fun aUniqueAlbumRequiresSongEvidenceBeforeAutomaticSelection() {
        val result = BatchAlbumMatchPolicy.decide(local, listOf(detail))
        assertEquals(BatchAlbumMatchStatus.AUTO, result.status)
        assertSame(detail, result.selected)
        assertTrue(result.ranked.single().reasons.any { "3 / 3" in it })
    }

    @Test fun strongMetadataRanksAboveSameNamedAlbumByAnotherArtist() {
        val unrelated = album.copy(id = 21L, artists = listOf(Artist(name = "Another Artist")))
        val ranked = BatchAlbumMatchPolicy.rank(local, listOf(unrelated, album))
        assertEquals(listOf(album.id, unrelated.id), ranked.map { it.album.id })
        assertTrue(ranked.first().isStrong)
        assertFalse(ranked.last().isStrong)
    }

    @Test fun sameNamedAlbumByAnotherArtistNeverUsesRelaxedSongPolicyToAutoConfirm() {
        val unrelated = album.copy(id = 21L, artists = listOf(Artist(name = "Another Artist")))
        val result = BatchAlbumMatchPolicy.decide(local, listOf(detail.copy(album = unrelated)))
        assertEquals(BatchAlbumMatchStatus.REVIEW, result.status)
        assertNull(result.selected)
    }

    @Test fun knownArtistButDifferentAlbumCannotAutoConfirmEvenWithSameSongs() {
        val result = BatchAlbumMatchPolicy.decide(local, listOf(detail.copy(album = album.copy(name = "Other Album"))))
        assertEquals(BatchAlbumMatchStatus.REVIEW, result.status)
        assertNull(result.selected)
    }

    @Test fun missingAlbumArtistCannotAutoConfirmFromTrackArtistsAlone() {
        val result = BatchAlbumMatchPolicy.decide(local, listOf(detail.copy(album = album.copy(artists = emptyList()))))
        assertEquals(BatchAlbumMatchStatus.REVIEW, result.status)
        assertNull(result.selected)
    }

    @Test fun reducedLocalArtistCreditCanMatchAnExplicitOnlineAlbumCredit() {
        val duet = album.copy(artists = listOf(artist, Artist(name = "Collaborator")))
        assertEquals(BatchAlbumMatchStatus.AUTO, BatchAlbumMatchPolicy.decide(local, listOf(detail.copy(album = duet))).status)
    }

    @Test fun differentFullAlbumCreditsRemainAmbiguousWhenLocalCreditIsReduced() {
        val first = detail.copy(album = album.copy(artists = listOf(artist, Artist(name = "B"))))
        val second = detail.copy(album = album.copy(id = 21L, artists = listOf(artist, Artist(name = "C"))))
        assertEquals(BatchAlbumMatchStatus.REVIEW, BatchAlbumMatchPolicy.decide(local, listOf(first, second)).status)
    }

    @Test fun slashesInRealArtistNamesArePreserved() {
        val band = local.copy(artist = "AC/DC")
        val actual = album.copy(artists = listOf(Artist(name = "AC/DC")))
        assertEquals(BatchAlbumMatchStatus.AUTO, BatchAlbumMatchPolicy.decide(band, listOf(detail.copy(album = actual))).status)
        assertFalse(BatchAlbumMatchPolicy.rank(band, listOf(album.copy(artists = listOf(Artist(name = "AC"))))).single().isStrong)
    }

    @Test fun metadataEquivalentReleaseVariantsCanAutoChooseInsteadOfAlwaysNeedingReview() {
        val remaster = detail.copy(album = album.copy(id = 21L, name = "Album (2011 Remastered)"),
            tracks = songs.map { it.copy(id = it.id + 1_000L, dt = it.dt + 200L) })
        val result = BatchAlbumMatchPolicy.decide(local, listOf(remaster, detail))
        assertEquals(BatchAlbumMatchStatus.AUTO, result.status)
        assertSame(detail, result.selected)
    }

    @Test fun regionalReleaseTagsDoNotPreventAutomaticAlbumSelection() {
        listOf("Album (Japan Edition)", "Album（日本版）", "Album - US Version").forEach { name ->
            assertEquals(name, BatchAlbumMatchStatus.AUTO,
                BatchAlbumMatchPolicy.decide(local, listOf(detail.copy(album = album.copy(name = name)))).status)
        }
    }

    @Test fun deluxeExpandedAndAnniversaryEditionsKeepTheBaseAlbumIdentity() {
        listOf("Album (Deluxe Edition)", "Album [Expanded Edition]", "Album (20th Anniversary Edition)", "Album（豪华版）").forEach { name ->
            assertTrue(name, BatchAlbumMatchPolicy.rank(local, listOf(album.copy(name = name))).single().isStrong)
            assertEquals(name, BatchAlbumMatchStatus.AUTO,
                BatchAlbumMatchPolicy.decide(local, listOf(detail.copy(album = album.copy(name = name)))).status)
        }
    }

    @Test fun bareAnniversaryAndDeluxeSuffixesAreCleanedForRetrievalAndEvidence() {
        listOf("Album 20th Anniversary Edition", "Album 20th Anniversary Deluxe Edition", "Album Deluxe Edition",
            "Album Super Deluxe Edition", "Album 20周年纪念版").forEach { name ->
            val tagged = local.copy(name = name)
            assertEquals(name, listOf("album Artist", "Artist album"), BatchAlbumMatchPolicy.queries(tagged))
            assertTrue(name, BatchAlbumMatchPolicy.rank(tagged, listOf(album)).single().isStrong)
            assertEquals(name, BatchAlbumMatchStatus.AUTO, BatchAlbumMatchPolicy.decide(tagged, listOf(detail)).status)
        }
    }

    @Test fun discNumbersAllowComparingTheLocalDiscWithAnOnlineWholeAlbum() {
        listOf("Album Disc 1", "Album CD2", "Album (Disc 01)", "Album [CD 2]",
            "Album [Special Collector's Edition] Disc 1", "Album [Special Collector's Edition] Disc 2").forEach { name ->
            val tagged = local.copy(name = name)
            assertEquals(name, listOf("album Artist", "Artist album"), BatchAlbumMatchPolicy.queries(tagged))
            assertEquals(name, BatchAlbumMatchStatus.AUTO, BatchAlbumMatchPolicy.decide(tagged, listOf(detail)).status)
        }
    }

    @Test fun actualMultiDiscLibraryNamesResolveTheirEditionAndDiscSuffixes() {
        val examples = listOf(
            "Meteora 20th Anniversary Edition" to "Meteora",
            "Amnesiac [Special Collector's Edition] Disc 1" to "Amnesiac",
            "Kid A [Special Collector's Edition] Disc 2" to "Kid A",
            "OK Computer [Special Collector's Edition] Disc 1" to "OK Computer",
            "Pablo Honey [Special Collector's Edition] Disc 2" to "Pablo Honey"
        )
        examples.forEach { (tagged, base) ->
            assertTrue(tagged, BatchAlbumMatchPolicy.rank(local.copy(name = tagged), listOf(album.copy(name = base))).single().isStrong)
        }
    }

    @Test fun recognizedSuffixesNeverDeleteRecordingMarkersOrTheAlbumWholeName() {
        listOf("Beyond 25th Anniversary", "Anniversary", "Deluxe", "Deluxe Edition", "Disc 1", "CD 2",
            "Album Live Edition", "Album (Live Deluxe Edition)", "Album Disc 1 Live").forEach { name ->
            assertFalse(name, BatchAlbumMatchPolicy.rank(local.copy(name = name), listOf(album)).single().isStrong)
        }
        val liveDisc = local.copy(name = "Album (Live) [Special Collector's Edition] Disc 1")
        assertFalse(BatchAlbumMatchPolicy.rank(liveDisc, listOf(album)).single().isStrong)
        assertTrue(BatchAlbumMatchPolicy.rank(liveDisc, listOf(album.copy(name = "Album (Live)"))).single().isStrong)
    }

    @Test fun unlabelledNumbersAndUnsupportedDiscSuffixesRemainPartOfAlbumName() {
        listOf("Album 1", "Album 2", "Album CD", "Album Disc One", "Album Disc 0", "Album Disc 100", "Album Disconnected 1").forEach { name ->
            assertFalse(name, BatchAlbumMatchPolicy.rank(local.copy(name = name), listOf(album)).single().isStrong)
        }
    }

    @Test fun explicitAndCleanEditionsRemainDifferentFromUnlabelledOrOppositeEditions() {
        val explicit = local.copy(name = "Album (Explicit)")
        assertFalse(BatchAlbumMatchPolicy.rank(explicit, listOf(album)).single().isStrong)
        assertFalse(BatchAlbumMatchPolicy.rank(explicit, listOf(album.copy(name = "Album (Clean)"))).single().isStrong)
        assertTrue(BatchAlbumMatchPolicy.queries(explicit).all { "explicit" in it })
        assertFalse(BatchAlbumMatchPolicy.rank(local.copy(name = "Album [Explicit] Disc 1"), listOf(album)).single().isStrong)
    }

    @Test fun originalEditionWinsAComparableSubsecondDurationDifference() {
        val remaster = detail.copy(album = album.copy(id = 21L, name = "Album (2011 Remastered)"))
        val original = detail.copy(tracks = songs.map { it.copy(dt = it.dt + 600L) })
        assertSame(original, BatchAlbumMatchPolicy.decide(local, listOf(remaster, original)).selected)
    }

    @Test fun substantiallyCloserDurationsCanPreferAnEquivalentEdition() {
        val remaster = detail.copy(album = album.copy(id = 21L, name = "Album (2011 Remastered)"))
        val original = detail.copy(tracks = songs.map { it.copy(dt = it.dt + 3_000L) })
        val result = BatchAlbumMatchPolicy.decide(local, listOf(original, remaster))
        assertEquals(BatchAlbumMatchStatus.AUTO, result.status)
        assertSame(remaster, result.selected)
    }

    @Test fun probableDifferentRecordingsAreNotTreatedAsEquivalentEditions() {
        val changed = detail.copy(album = album.copy(id = 21L, name = "Album (2011 Remastered)"),
            tracks = songs.map { it.copy(id = it.id + 1_000L, dt = it.dt + 8_000L) })
        val result = BatchAlbumMatchPolicy.decide(local, listOf(detail, changed))
        assertEquals(BatchAlbumMatchStatus.REVIEW, result.status)
        assertNull(result.selected)
    }

    private fun twelveSongEditions(): Triple<BatchAlbumLocal, BatchAlbumDetail, BatchAlbumDetail> {
        val tracks = (0 until 12).map { index -> songs.first().copy(id = 100L + index, name = "Song $index", dt = 270_000L + index * 1_000L) }
        val locals = local.copy(tracks = tracks.mapIndexed { index, song ->
            AlbumScopedLocal(LocalAudioCandidate("content://music/$index", song.name, "Artist", song.dt))
        })
        val original = detail.copy(tracks = tracks)
        val edition = detail.copy(album = album.copy(id = 21L, name = "Album (20th Anniversary Deluxe Edition)"),
            tracks = tracks.map { it.copy(id = it.id + 1_000L) })
        return Triple(locals, original, edition)
    }

    @Test fun oneSmallFadeDifferenceDoesNotVetoTwelveOtherwiseEquivalentSongs() {
        val (locals, original, edition) = twelveSongEditions()
        val alternate = edition.copy(tracks = edition.tracks.mapIndexed { index, song -> if (index == 0) song.copy(dt = song.dt + 5_205L) else song })
        val result = BatchAlbumMatchPolicy.decide(locals, listOf(alternate, original))
        assertEquals(BatchAlbumMatchStatus.AUTO, result.status)
        assertSame(original, result.selected)
    }

    @Test fun twoDivergentSongsOutOfTwelveCannotBeCalledAnEquivalentEditionSet() {
        val (locals, original, edition) = twelveSongEditions()
        val alternate = edition.copy(tracks = edition.tracks.mapIndexed { index, song -> if (index < 2) song.copy(dt = song.dt + 8_000L) else song })
        assertEquals(BatchAlbumMatchStatus.REVIEW, BatchAlbumMatchPolicy.decide(locals, listOf(original, alternate)).status)
    }

    @Test fun copyingTheSameAgreeingSongManyTimesDoesNotOutvoteRecordingDifferences() {
        val (locals, original, edition) = twelveSongEditions()
        val repeated = locals.copy(tracks = locals.tracks + (0 until 100).map { index -> locals.tracks.last().let {
            it.copy(candidate = it.candidate.copy(uri = "content://music/copy-$index"))
        } })
        val alternate = edition.copy(tracks = edition.tracks.mapIndexed { index, song -> if (index < 2) song.copy(dt = song.dt + 8_000L) else song })
        assertEquals(BatchAlbumMatchStatus.REVIEW, BatchAlbumMatchPolicy.decide(repeated, listOf(original, alternate)).status)
    }

    @Test fun anOutlyingSongMustClearlyFavorTheChosenAlbumForEveryLocalEncoding() {
        val (locals, original, edition) = twelveSongEditions()
        val shiftedOriginal = original.copy(tracks = original.tracks.mapIndexed { index, song -> if (index == 0) song.copy(dt = song.dt - 2_500L) else song })
        val shiftedEdition = edition.copy(tracks = edition.tracks.mapIndexed { index, song -> if (index == 0) song.copy(dt = song.dt + 3_000L) else song.copy(dt = song.dt + 500L) })
        assertEquals(BatchAlbumMatchStatus.REVIEW, BatchAlbumMatchPolicy.decide(locals, listOf(shiftedOriginal, shiftedEdition)).status)
        val alternate = edition.copy(tracks = edition.tracks.mapIndexed { index, song -> if (index == 0) song.copy(dt = song.dt + 6_000L) else song })
        val mixedLocals = locals.copy(tracks = locals.tracks + locals.tracks.first().let {
            it.copy(candidate = it.candidate.copy(uri = "content://music/other-recording", durationMs = it.candidate.durationMs + 6_000L))
        })
        assertEquals(BatchAlbumMatchStatus.REVIEW, BatchAlbumMatchPolicy.decide(mixedLocals, listOf(original, alternate)).status)
    }

    @Test fun liveRemixAndCompilationCandidatesCannotReplaceTheStudioAlbum() {
        listOf("Album (Live)", "Album (Remix)", "Album (Re-recorded)", "Album (Instrumental)", "Album (Greatest Hits)", "Album（精选合集）").forEach { name ->
            val other = detail.copy(album = album.copy(id = 21L, name = name))
            assertFalse(name, BatchAlbumMatchPolicy.rank(local, listOf(other.album)).single().isStrong)
            val result = BatchAlbumMatchPolicy.decide(local, listOf(other))
            assertEquals(name, BatchAlbumMatchStatus.REVIEW, result.status)
            assertNull(result.selected)
            assertSame(detail, BatchAlbumMatchPolicy.decide(local, listOf(other, detail)).selected)
        }
    }

    @Test fun deluxeAnnotationNeverHidesARecordingTypeChange() {
        val other = detail.copy(album = album.copy(name = "Album (Live Deluxe Edition)"))
        assertFalse(BatchAlbumMatchPolicy.rank(local, listOf(other.album)).single().isStrong)
        assertEquals(BatchAlbumMatchStatus.REVIEW, BatchAlbumMatchPolicy.decide(local, listOf(other)).status)
    }

    @Test fun localLiveAlbumRequiresTheSameExplicitLiveAlbumName() {
        val liveLocal = local.copy(name = "Album (Live)")
        val live = detail.copy(album = album.copy(name = "Album (Live)"))
        assertEquals(BatchAlbumMatchStatus.AUTO, BatchAlbumMatchPolicy.decide(liveLocal, listOf(live)).status)
        assertEquals(BatchAlbumMatchStatus.REVIEW, BatchAlbumMatchPolicy.decide(liveLocal, listOf(detail)).status)
    }

    @Test fun eightOfTenSongsWithUniqueCompatibleMetadataAreSufficientButSevenAreNot() {
        val tenSongs = (0 until 10).map { index -> songs.first().copy(id = 100L + index, name = "Song $index") }
        val tenLocal = local.copy(tracks = tenSongs.mapIndexed { index, song ->
            AlbumScopedLocal(LocalAudioCandidate("content://music/$index", song.name, "Artist", song.dt), index + 1)
        })
        assertEquals(BatchAlbumMatchStatus.AUTO, BatchAlbumMatchPolicy.decide(tenLocal, listOf(detail.copy(tracks = tenSongs.take(8)))).status)
        assertEquals(BatchAlbumMatchStatus.REVIEW, BatchAlbumMatchPolicy.decide(tenLocal, listOf(detail.copy(tracks = tenSongs.take(7)))).status)
    }

    @Test fun coverageRanksBeforeOriginalEditionPreference() {
        val tenSongs = (0 until 10).map { index -> songs.first().copy(id = 100L + index, name = "Song $index") }
        val tenLocal = local.copy(tracks = tenSongs.mapIndexed { index, song ->
            AlbumScopedLocal(LocalAudioCandidate("content://music/$index", song.name, "Artist", song.dt))
        })
        val original = detail.copy(tracks = tenSongs.take(8))
        val expanded = detail.copy(album = album.copy(id = 21L, name = "Album (Expanded Edition)"), tracks = tenSongs)
        assertSame(expanded, BatchAlbumMatchPolicy.decide(tenLocal, listOf(original, expanded)).selected)
    }

    @Test fun shortAlbumsNeedAllAvailableLocalSongsToAgree() {
        assertEquals(BatchAlbumMatchStatus.AUTO, BatchAlbumMatchPolicy.decide(local.copy(tracks = local.tracks.take(1)), listOf(detail)).status)
        assertEquals(BatchAlbumMatchStatus.AUTO, BatchAlbumMatchPolicy.decide(local.copy(tracks = local.tracks.take(2)), listOf(detail)).status)
        assertEquals(BatchAlbumMatchStatus.REVIEW,
            BatchAlbumMatchPolicy.decide(local.copy(tracks = local.tracks.take(2)), listOf(detail.copy(tracks = songs.take(1)))).status)
    }

    @Test fun matchingOnlyTheFirstSongCannotSelectAWholeAlbum() {
        assertEquals(BatchAlbumMatchStatus.REVIEW, BatchAlbumMatchPolicy.decide(local, listOf(detail.copy(tracks = songs.take(1)))).status)
    }

    @Test fun truncatedAlbumDetailsNeverAuthorizeAutomaticSelection() {
        val truncated = detail.copy(complete = false)
        val result = BatchAlbumMatchPolicy.decide(local, listOf(truncated))
        assertEquals(BatchAlbumMatchStatus.REVIEW, result.status)
        assertNull(result.selected)
    }

    @Test fun unresolvedCredibleEditionBlocksChoosingAnotherEditionPrematurely() {
        val incomplete = detail.copy(album = album.copy(id = 21L, name = "Album (2011 Remastered)"), complete = false)
        assertEquals(BatchAlbumMatchStatus.REVIEW, BatchAlbumMatchPolicy.decide(local, listOf(detail, incomplete)).status)
    }

    @Test fun incompleteUnrelatedAlbumDoesNotBlockAnOtherwiseVerifiedAlbum() {
        val incomplete = detail.copy(album = album.copy(id = 21L, name = "Different Album"), complete = false)
        assertEquals(BatchAlbumMatchStatus.AUTO, BatchAlbumMatchPolicy.decide(local, listOf(detail, incomplete)).status)
    }

    @Test fun songVersionDifferencesStillReduceCoverageInAlbumAutoSelection() {
        val liveSongs = songs.map { it.copy(name = "${it.name} (Live)") }
        assertEquals(BatchAlbumMatchStatus.REVIEW, BatchAlbumMatchPolicy.decide(local, listOf(detail.copy(tracks = liveSongs))).status)
    }

    @Test fun unknownLocalSongDurationCannotBeUsedAsEvidence() {
        val unknown = local.copy(tracks = local.tracks.map { it.copy(candidate = it.candidate.copy(durationMs = 0L)) })
        assertEquals(BatchAlbumMatchStatus.REVIEW, BatchAlbumMatchPolicy.decide(unknown, listOf(detail)).status)
    }

    @Test fun duplicateLocalEncodingsDoNotMakeAlbumIdentityAmbiguous() {
        val duplicate = local.copy(tracks = local.tracks + local.tracks.first().copy(candidate = local.tracks.first().candidate.copy(uri = "content://music/duplicate")))
        val result = BatchAlbumMatchPolicy.decide(duplicate, listOf(detail))
        assertEquals(BatchAlbumMatchStatus.AUTO, result.status)
        assertTrue(result.ranked.single().reasons.any { "3 / 3" in it })
        assertEquals(2, AlbumScopedMatchPolicy.plan(duplicate.tracks, songs).count { it.status == AlbumScopedMatchStatus.REVIEW })
    }

    @Test fun manyCopiesOfOneSongCannotOutvoteTheOtherUnmatchedAlbumSongs() {
        val first = local.tracks.first()
        val copies = (0 until 50).map { first.copy(candidate = first.candidate.copy(uri = "content://music/copy-$it")) }
        val unrelated = (0 until 3).map { index -> first.copy(candidate = first.candidate.copy(uri = "content://music/wrong-$index",
            title = "Wrong Song $index", fileName = "Wrong Song $index.flac")) }
        val result = BatchAlbumMatchPolicy.decide(local.copy(tracks = copies + unrelated), listOf(detail))
        assertEquals(BatchAlbumMatchStatus.REVIEW, result.status)
        assertTrue(result.ranked.single().reasons.any { "1 / 4" in it })
    }

    @Test fun aNinetySevenFileMultiEncodingAlbumUsesDistinctRecordingEvidence() {
        val copies = (0 until 97).map { index -> local.tracks[index % 3].let { row ->
            row.copy(candidate = row.candidate.copy(uri = "content://music/copy-$index", durationMs = row.candidate.durationMs + index % 3 * 100L))
        } }
        val result = BatchAlbumMatchPolicy.decide(local.copy(tracks = copies), listOf(detail))
        assertEquals(BatchAlbumMatchStatus.AUTO, result.status)
        assertTrue(result.ranked.single().reasons.any { "3 / 3" in it })
        assertTrue(result.ranked.single().reasons.any { "97" in it })
    }

    @Test fun repeatedLocalUrisAndRepeatedSearchResultsDoNotInflateEvidenceOrCreateAmbiguity() {
        val repeated = local.copy(tracks = local.tracks + local.tracks)
        assertEquals(BatchAlbumMatchStatus.AUTO, BatchAlbumMatchPolicy.decide(repeated, listOf(detail, detail.copy())).status)
        assertEquals(1, BatchAlbumMatchPolicy.rank(local, listOf(album, album.copy())).size)
    }

    @Test fun conflictingResponsesForTheSameAlbumNeedReview() {
        val changed = detail.copy(tracks = songs.dropLast(1))
        val result = BatchAlbumMatchPolicy.decide(local, listOf(detail, changed))
        assertEquals(BatchAlbumMatchStatus.REVIEW, result.status)
        assertNull(result.selected)
    }

    @Test fun emptyAndInvalidCatalogDataCannotConfirmAnAlbum() {
        assertEquals(BatchAlbumMatchStatus.NO_MATCH, BatchAlbumMatchPolicy.decide(local, emptyList()).status)
        assertEquals(BatchAlbumMatchStatus.NO_MATCH, BatchAlbumMatchPolicy.decide(local, listOf(detail.copy(album = album.copy(id = 0L)))).status)
        assertEquals(BatchAlbumMatchStatus.REVIEW, BatchAlbumMatchPolicy.decide(local, listOf(detail.copy(tracks = emptyList()))).status)
        assertEquals(BatchAlbumMatchStatus.REVIEW, BatchAlbumMatchPolicy.decide(local.copy(tracks = emptyList()), listOf(detail)).status)
    }

    @Test fun unknownLocalArtistOrAlbumCannotSelectAnAlbum() {
        assertEquals(BatchAlbumMatchStatus.REVIEW, BatchAlbumMatchPolicy.decide(local.copy(artist = "<unknown>"), listOf(detail)).status)
        assertEquals(BatchAlbumMatchStatus.NO_MATCH, BatchAlbumMatchPolicy.decide(local.copy(name = "Unknown Album"), listOf(detail)).status)
    }

    @Test fun numericAlbumTitlesAreNotConfusedByPunctuation() {
        val numeric = local.copy(name = "3.14")
        assertTrue(BatchAlbumMatchPolicy.rank(numeric, listOf(album.copy(name = "3.14"))).single().isStrong)
        assertFalse(BatchAlbumMatchPolicy.rank(numeric, listOf(album.copy(name = "314"))).single().isStrong)
    }

    @Test fun metadataRankingAndFinalSelectionAreStableAcrossInputOrder() {
        val remaster = detail.copy(album = album.copy(id = 21L, name = "Album (2011 Remastered)"))
        assertEquals(BatchAlbumMatchPolicy.rank(local, listOf(album, remaster.album)), BatchAlbumMatchPolicy.rank(local, listOf(remaster.album, album)))
        assertEquals(BatchAlbumMatchPolicy.decide(local, listOf(detail, remaster)), BatchAlbumMatchPolicy.decide(local, listOf(remaster, detail)))
    }
}

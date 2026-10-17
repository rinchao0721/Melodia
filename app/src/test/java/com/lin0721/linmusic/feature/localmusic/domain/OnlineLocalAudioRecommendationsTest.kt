package com.lin0721.linmusic.feature.localmusic.domain

import com.lin0721.linmusic.core.model.Album
import com.lin0721.linmusic.core.model.Artist
import com.lin0721.linmusic.core.model.Track
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OnlineLocalAudioRecommendationsTest {
    private val online = Track(id = 1, name = "For Emma", ar = listOf(Artist(name = "Bon Iver")),
        al = Album(name = "For Emma, Forever Ago"), dt = 220_720L)
    private val local = LocalAudioCandidate("content://music/1", "For Emma", "Bon Iver", 220_720L,
        album = "For Emma, Forever Ago", fileName = "Bon Iver - For Emma.flac")

    @Test fun exactTitleLeadsEvenWhenAnUnrelatedTitleHasIdenticalDuration() {
        val unrelated = local.copy(uri = "other", title = "Other song", artist = "Other artist", album = "Other album", fileName = null)
        val titleOnly = local.copy(uri = "title-only", artist = "Other artist", album = "Other album", durationMs = 260_000L, fileName = null)
        val rows = OnlineLocalAudioRecommendations.rank(online, listOf(unrelated, titleOnly, local))
        assertEquals(listOf(local.uri, titleOnly.uri, unrelated.uri), rows.map { it.candidate.uri })
        assertTrue(rows[2].matchingDuration)
        assertFalse(rows[2].isRecommended)
        assertEquals(listOf(local.uri, titleOnly.uri), OnlineLocalAudioRecommendations.filter(rows).map { it.candidate.uri })
    }

    @Test fun numberedTitleAndArtistAtEitherEndAreRecommendedWithoutChangingMetadata() {
        listOf("01 Bon Iver - For Emma", "For Emma - Bon Iver", "01 For Emma_Bon Iver").forEach { title ->
            val source = local.copy(title = title)
            val row = OnlineLocalAudioRecommendations.rank(online, listOf(source)).single()
            assertTrue(title, row.sameTitle)
            assertTrue(title, row.sameArtist)
            assertTrue(title, row.isRecommended)
            assertEquals(title, row.candidate.title)
        }
    }

    @Test fun unknownArtistCanBeRecoveredOnlyFromActualFilenameEvidence() {
        val withFilename = local.copy(title = "01 Bon Iver - For Emma", artist = "<unknown>", fileName = "01 Bon Iver - For Emma.flac")
        val unknown = withFilename.copy(uri = "unknown", fileName = null)
        val rows = OnlineLocalAudioRecommendations.rank(online, listOf(withFilename, unknown)).associateBy { it.candidate.uri }
        assertTrue(rows.getValue(withFilename.uri).sameArtist)
        assertTrue(rows.getValue(withFilename.uri).sameTitle)
        assertFalse(rows.getValue(unknown.uri).sameArtist)
        assertFalse(rows.getValue(unknown.uri).sameTitle)
    }

    @Test fun reducedCollaborationCreditsAndRegionalRemasterAlbumAreCompatible() {
        val duet = online.copy(ar = listOf(Artist(name = "Bon Iver"), Artist(name = "Guest")))
        val regional = local.copy(album = "For Emma, Forever Ago (Japan Edition) [2011 Remastered]", durationMs = 224_000L)
        val row = OnlineLocalAudioRecommendations.rank(duet, listOf(regional)).single()
        assertTrue(row.sameTitle)
        assertTrue(row.sameArtist)
        assertTrue(row.sameAlbum)
        assertTrue(row.matchingDuration)
        assertFalse(row.versionWarning)
        assertTrue(row.reason.contains("同歌手"))
    }

    @Test fun slashArtistNameStaysWholeAndSlashCollaborationNeedsMatchingAlbum() {
        val band = Track(name = "Song", ar = listOf(Artist(name = "AC"), Artist(name = "DC")), al = Album(name = "Album"))
        val file = local.copy(title = "Song", artist = "AC/DC", album = "Album", fileName = null)
        assertFalse(OnlineLocalAudioRecommendations.rank(band, listOf(file)).single().sameArtist)
        val duet = band.copy(ar = listOf(Artist(name = "Singer"), Artist(name = "Guest")))
        val together = file.copy(artist = "Singer/Guest")
        assertTrue(OnlineLocalAudioRecommendations.rank(duet, listOf(together)).single().sameArtist)
        assertFalse(OnlineLocalAudioRecommendations.rank(duet.copy(al = Album(name = "Other")), listOf(together)).single().sameArtist)
        assertTrue(OnlineLocalAudioRecommendations.rank(band.copy(ar = listOf(Artist(name = "AC/DC"))), listOf(file)).single().sameArtist)
    }

    @Test fun studioVersionPrecedesLiveVersionAndHiddenFilenameVersionsAreFlagged() {
        val live = local.copy(uri = "live", title = "For Emma (Live at Tokyo)", fileName = "Bon Iver - For Emma (Live at Tokyo).flac")
        val hidden = local.copy(uri = "hidden", fileName = "Bon Iver - For Emma (Live at Tokyo).flac")
        val rows = OnlineLocalAudioRecommendations.rank(online, listOf(live, hidden, local))
        assertEquals(local.uri, rows.first().candidate.uri)
        assertEquals(listOf(false, true, true), rows.map { it.versionWarning })
        assertTrue(rows.all { it.isRecommended })
        assertTrue(rows.first { it.candidate.uri == live.uri }.reason.contains("版本"))
    }

    @Test fun exactLiveRecordingLeadsAnotherVenueAndStudioWithoutErasingVersionNames() {
        val liveOnline = online.copy(name = "For Emma (Live at Tokyo)")
        val tokyo = local.copy(uri = "tokyo", title = liveOnline.name, fileName = null)
        val london = local.copy(uri = "london", title = "For Emma (Live at London)", fileName = null)
        val rows = OnlineLocalAudioRecommendations.rank(liveOnline, listOf(local.copy(fileName = null), london, tokyo))
        assertEquals(tokyo.uri, rows.first().candidate.uri)
        assertTrue(rows.first().sameTitle)
        assertFalse(rows.first().versionWarning)
        assertTrue(rows.drop(1).all { !it.sameTitle && it.versionWarning })
    }

    @Test fun wordsWithinActualTitleDoNotTurnAnUnrelatedSongIntoAnotherVersion() {
        val row = OnlineLocalAudioRecommendations.rank("Live and Let Die", "Artist", "Album", 180_000L,
            listOf(local.copy(title = "Another Song", artist = "Artist", album = "Other", fileName = null))).single()
        assertFalse(row.sameTitle)
        assertFalse(row.versionWarning)
        assertFalse(row.isRecommended)
    }

    @Test fun artistAndAlbumFiltersExposeRelatedTracksButAllKeepsUnrelatedLibraryEntries() {
        val artistOnly = local.copy(uri = "artist", title = "Other song", album = "Other album", fileName = null)
        val albumOnly = local.copy(uri = "album", title = "Guest song", artist = "Guest", fileName = null)
        val sameRelease = artistOnly.copy(uri = "release", album = local.album)
        val unrelated = artistOnly.copy(uri = "unrelated", artist = "Other")
        val rows = OnlineLocalAudioRecommendations.rank(online, listOf(unrelated, albumOnly, artistOnly, sameRelease, local))
        assertEquals(listOf(local.uri, sameRelease.uri), OnlineLocalAudioRecommendations.filter(rows).map { it.candidate.uri })
        assertEquals(listOf(local.uri, sameRelease.uri, artistOnly.uri),
            OnlineLocalAudioRecommendations.filter(rows, OnlineLocalAudioFilter.SAME_ARTIST).map { it.candidate.uri })
        assertEquals(listOf(local.uri, sameRelease.uri, albumOnly.uri),
            OnlineLocalAudioRecommendations.filter(rows, OnlineLocalAudioFilter.SAME_ALBUM).map { it.candidate.uri })
        assertEquals(5, OnlineLocalAudioRecommendations.filter(rows, OnlineLocalAudioFilter.ALL).size)
    }

    @Test fun unknownMetadataNeverMatchesOtherUnknownMetadataOrZeroDuration() {
        val row = OnlineLocalAudioRecommendations.rank("未知曲目", "未知歌手", "未知专辑", 0L,
            listOf(local.copy(title = "未知曲目", artist = "未知歌手", album = "未知专辑", durationMs = 0L, fileName = null))).single()
        assertFalse(row.sameTitle)
        assertFalse(row.sameArtist)
        assertFalse(row.sameAlbum)
        assertFalse(row.matchingDuration)
        assertFalse(row.isRecommended)
        assertTrue(OnlineLocalAudioRecommendations.filter(listOf(row)).isEmpty())
    }

    @Test fun searchSupportsCleanTitleCombinedCreditAlbumAndNumberedQuery() {
        val numbered = local.copy(title = "01 Bon Iver - For Emma", fileName = "01 Bon Iver - For Emma.flac")
        val rows = OnlineLocalAudioRecommendations.rank(online, listOf(numbered, local.copy(uri = "other", title = "Other song", artist = "Other artist", album = "Other album", fileName = null)))
        listOf("For Emma", "01 Bon Iver - For Emma", "emma bon", "Forever Ago", "Ｂｏｎ　Ｉｖｅｒ").forEach { query ->
            val found = OnlineLocalAudioRecommendations.filter(rows, OnlineLocalAudioFilter.RECOMMENDED, query)
            assertEquals(query, listOf(numbered.uri), found.map { it.candidate.uri })
        }
        assertTrue(OnlineLocalAudioRecommendations.filter(rows, OnlineLocalAudioFilter.ALL, "missing").isEmpty())
        assertEquals(2, OnlineLocalAudioRecommendations.filter(rows, OnlineLocalAudioFilter.ALL, " ").size)
    }

    @Test fun meaningfulNumericTitlesArePreservedAndEqualCandidatesKeepLibraryOrder() {
        val numeric = local.copy(title = "7 rings", fileName = null)
        val rows = OnlineLocalAudioRecommendations.rank("7 rings", local.artist, local.album, local.durationMs,
            listOf(numeric.copy(uri = "z"), numeric.copy(uri = "a")))
        assertTrue(rows.all { it.sameTitle })
        assertEquals(listOf("z", "a"), rows.map { it.candidate.uri })
        assertFalse(OnlineLocalAudioRecommendations.rank("rings", local.artist, null, local.durationMs,
            listOf(numeric)).single().sameTitle)
    }

    @Test fun largeLibraryHasOnlyRelevantRecommendationsAndSearchDoesNotReorderIt() {
        val unrelated = (0 until 4_351).map { index -> local.copy(uri = "other/$index", title = "Song $index",
            artist = "Artist $index", album = "Album $index", fileName = null) }
        val rows = OnlineLocalAudioRecommendations.rank(online, unrelated + local)
        assertEquals(local.uri, rows.first().candidate.uri)
        assertEquals(listOf(local.uri), OnlineLocalAudioRecommendations.filter(rows).map { it.candidate.uri })
        val searched = OnlineLocalAudioRecommendations.filter(rows, OnlineLocalAudioFilter.ALL, "Artist 100")
        assertEquals(unrelated.filter { "100" in it.artist }.map { it.uri }, searched.map { it.candidate.uri })
    }
}

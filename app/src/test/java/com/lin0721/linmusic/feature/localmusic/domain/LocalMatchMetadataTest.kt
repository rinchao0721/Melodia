package com.lin0721.linmusic.feature.localmusic.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class LocalMatchMetadataTest {
    private val local = LocalAudioCandidate("content://music/1", "For Emma", "Bon Iver", 220_720L, album = "For Emma, Forever Ago", fileName = "Bon Iver - For Emma.flac")

    @Test fun knownArtistIsAnAnchorAtEitherEndAfterObviousTrackIndexCleanup() {
        listOf("01 Bon Iver - For Emma", "For Emma - Bon Iver", "Bon  Iver—For Emma", "01 For Emma_Bon Iver").forEach {
            val result = LocalMatchMetadata.resolve(local.copy(title = it))
            assertEquals("For Emma", result.title)
            assertEquals("Bon Iver", result.artist)
            assertEquals(local.uri, result.uri)
            assertEquals(local.album, result.album)
        }
    }

    @Test fun chineseCreditsAndTaggedTitleWithoutSpacesAroundTheSeparatorWork() {
        assertEquals("五味瓶", LocalMatchMetadata.resolve(local.copy(title = "01 周云蓬 - 五味瓶", artist = "周云蓬")).title)
        assertEquals("五味瓶", LocalMatchMetadata.resolve(local.copy(title = "五味瓶—周云蓬", artist = "周云蓬")).title)
    }

    @Test fun conventionalFilenameCanRecoverMissingArtistWithoutMutatingInput() {
        val source = local.copy(title = "01 Bon Iver - For Emma", artist = "<unknown>", fileName = "01 Bon Iver - For Emma.flac")
        val recovered = LocalMatchMetadata.resolve(source)
        assertEquals("For Emma", recovered.title)
        assertEquals("Bon Iver", recovered.artist)
        assertEquals("01 Bon Iver - For Emma", source.title)
        assertEquals("<unknown>", source.artist)
        assertEquals("For Emma", LocalMatchMetadata.resolve(source.copy(title = "")).title)
        assertEquals("Bon Iver", LocalMatchMetadata.resolve(source.copy(title = "For Emma")).artist)
        assertEquals("Bon Iver", LocalMatchMetadata.resolve(source.copy(title = "For Emma", fileName = "For Emma - Bon Iver.mp3")).artist)
    }

    @Test fun unrelatedHyphensAndArtistNameSlashesStayIntact() {
        assertEquals("Love - Hate", LocalMatchMetadata.resolve(local.copy(title = "Love - Hate", artist = "Singer")).title)
        val unanchored = local.copy(title = "Love - Hate", artist = "<unknown>", fileName = "track.flac")
        assertEquals("Love - Hate", LocalMatchMetadata.resolve(unanchored).title)
        assertEquals("<unknown>", LocalMatchMetadata.resolve(unanchored).artist)
        val band = LocalMatchMetadata.resolve(local.copy(title = "AC/DC - Highway to Hell", artist = "AC/DC"))
        assertEquals("Highway to Hell", band.title)
        assertEquals("AC/DC", band.artist)
    }

    @Test fun meaningfulNumericTitlesAndRecordingVersionsRemainPresent() {
        listOf("7 rings", "1989", "3.14", "10-Feet", "Song (Live)", "Song (Acoustic)").forEach {
            assertEquals(it, LocalMatchMetadata.resolve(local.copy(title = it)).title)
        }
        assertEquals("7 rings", LocalMatchMetadata.resolve(local.copy(title = "01 Bon Iver - 7 rings")).title)
    }

    @Test fun fullWidthMetadataAndMultipleTaggedArtistsCanAnchorTheFilenameCredit() {
        assertEquals("For Emma", LocalMatchMetadata.resolve(local.copy(title = "Ｂｏｎ Ｉｖｅｒ - For Emma", artist = "Ｂｏｎ Ｉｖｅｒ")).title)
        assertEquals("Song", LocalMatchMetadata.resolve(local.copy(title = "A - Song", artist = "A • B")).title)
    }

    @Test fun olderScannerSplitWithTrackIndexAttachedToArtistIsRecoveredUsingFilenameEvidence() {
        val older = local.copy(title = "For Emma", artist = "01 Bon Iver", fileName = "01 Bon Iver - For Emma.flac")
        val result = LocalMatchMetadata.resolve(older)
        assertEquals("For Emma", result.title)
        assertEquals("Bon Iver", result.artist)
        assertEquals("01 Bon Iver", older.artist)
        assertEquals("01 Bon Iver", LocalMatchMetadata.resolve(older.copy(fileName = null)).artist)
        assertEquals("01 Bon Iver", LocalMatchMetadata.resolve(older.copy(title = "Another Song")).artist)
        assertEquals("01 Bon Iver", LocalMatchMetadata.resolve(older.copy(fileName = "01 Other Artist - For Emma.flac")).artist)
    }

    @Test fun knownNumericArtistNameCanAnchorACombinedTitleBeforeIndexCleanup() {
        val band = local.copy(title = "01 Band - Song", artist = "01 Band", fileName = "01 Band - Song.flac")
        assertEquals("Song", LocalMatchMetadata.resolve(band).title)
        assertEquals("01 Band", LocalMatchMetadata.resolve(band).artist)
    }

    @Test fun albumReleaseAnnotationsAreStrippedWithoutChangingTheCoreAlbum() {
        listOf("Album (Japan Edition)", "Album [US Release]", "Album (EU Edition)", "Album - 2011 Remastered",
            "Album (Remastered 2011)", "Album Remastered", "Album(日本版)", "Album (2011年 重制版)",
            "Album (重铸版)", "Album (Japan Edition) [Remastered]").forEach {
            assertEquals("album", LocalMatchAlbum.forComparison(it))
        }
        assertEquals("love - hate", LocalMatchAlbum.forComparison("Love - Hate - Remastered"))
        assertEquals("album", LocalMatchAlbum.forComparison("Album - (Remastered)"))
    }

    @Test fun albumCoreRecordingVariantsTrackNumbersAndRegionalWordsAreNotDeleted() {
        listOf("Album (Live)", "Album (Acoustic)", "Album (Instrumental)", "01 Album", "Japan", "US", "Album (Deluxe)", "Other Album").forEach {
            assertEquals(it.lowercase(), LocalMatchAlbum.forComparison(it))
        }
    }
}

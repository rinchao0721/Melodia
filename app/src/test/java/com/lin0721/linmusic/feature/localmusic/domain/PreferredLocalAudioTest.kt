package com.lin0721.linmusic.feature.localmusic.domain

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

class PreferredLocalAudioTest {
    private val studio = LocalAudioCandidate("content://studio", "Song", "Artist", 180_000L)

    @Test fun uniqueExactMetadataAndDurationMatch() {
        assertEquals(studio.uri, PreferredLocalAudioMatcher.uniqueMatch(" SONG ", "artist", 181_000L, listOf(studio)))
    }

    @Test fun obviousLocalTrackNumberIsIgnoredWithoutChangingAlbumComparison() {
        val numbered = studio.copy(title = "01 Song", album = "01 Album")
        assertEquals(numbered.uri, PreferredLocalAudioMatcher.uniqueMatch("Song", "Artist", 181_000L, listOf(numbered)))
        assertEquals(numbered.uri, PreferredLocalAudioMatcher.uniqueMatch("Song", "Artist", 183_000L, listOf(numbered), "01 Album"))
        assertNull(PreferredLocalAudioMatcher.uniqueMatch("Song", "Artist", 183_000L, listOf(numbered), "Album"))
    }

    @Test fun catalogTrackNumberAndNumericSongTitlesRemainAuthoritative() {
        assertNull(PreferredLocalAudioMatcher.uniqueMatch("01 Song", "Artist", 180_000L, listOf(studio)))
        val numeric = studio.copy(title = "7 rings")
        assertEquals(numeric.uri, PreferredLocalAudioMatcher.uniqueMatch("7 rings", "Artist", 180_000L, listOf(numeric)))
        assertNull(PreferredLocalAudioMatcher.uniqueMatch("rings", "Artist", 180_000L, listOf(numeric)))
    }

    @Test fun ambiguousCopiesAreNotChosen() {
        assertNull(PreferredLocalAudioMatcher.uniqueMatch("Song", "Artist", 180_000L, listOf(studio, studio.copy(uri = "content://copy"))))
    }

    @Test fun repeatedDatabaseReferenceIsOneCandidate() {
        assertEquals(studio.uri, PreferredLocalAudioMatcher.uniqueMatch("Song", "Artist", 180_000L, listOf(studio, studio)))
    }

    @Test fun scannedAndImportedUrisForTheSamePhysicalFileAreOneCandidate() {
        val scanned = studio.copy(identity = "file:1:2:0:500")
        val imported = studio.copy(uri = "content://documents/same-file", identity = scanned.identity)
        assertEquals(scanned.uri, PreferredLocalAudioMatcher.uniqueMatch("Song", "Artist", 180_000L, listOf(scanned, imported)))
    }

    @Test fun genuinelyDifferentFilesStayAmbiguousEvenWithEqualMetadata() {
        val scanned = studio.copy(identity = "file:1:2:0:500")
        val other = studio.copy(uri = "content://documents/other-file", identity = "file:1:3:0:500")
        assertNull(PreferredLocalAudioMatcher.uniqueMatch("Song", "Artist", 180_000L, listOf(scanned, other)))
    }

    @Test fun liveRemasterAndAcousticSuffixesArePreserved() {
        listOf("Song (Live)", "Song - Remastered", "Song (Acoustic)", "Song (伴奏)").forEach { title ->
            assertNull(PreferredLocalAudioMatcher.uniqueMatch("Song", "Artist", 180_000L, listOf(studio.copy(title = title))))
        }
    }

    @Test fun missingOrDifferentDurationIsNotAccepted() {
        assertNull(PreferredLocalAudioMatcher.uniqueMatch("Song", "Artist", 0L, listOf(studio)))
        assertNull(PreferredLocalAudioMatcher.uniqueMatch("Song", "Artist", 182_001L, listOf(studio)))
        assertNull(PreferredLocalAudioMatcher.uniqueMatch("Song", "Artist", 180_000L, listOf(studio.copy(durationMs = 0L))))
    }

    @Test fun sameTitleByAnotherSingerIsNotAccepted() {
        assertNull(PreferredLocalAudioMatcher.uniqueMatch("Song", "Another Artist", 180_000L, listOf(studio)))
        assertNull(PreferredLocalAudioMatcher.uniqueMatch("Song", "未知艺术家", 180_000L, listOf(studio.copy(artist = "未知艺术家"))))
    }

    @Test fun completeArtistSetIsRequiredAndOrderCanDiffer() {
        val duet = studio.copy(artist = "A, B")
        assertEquals(duet.uri, PreferredLocalAudioMatcher.uniqueMatch("Song", "B • A", 180_000L, listOf(duet)))
        assertNull(PreferredLocalAudioMatcher.uniqueMatch("Song", "A", 180_000L, listOf(duet)))
    }

    @Test fun slashInsideArtistNameIsPreserved() {
        val band = studio.copy(artist = "AC/DC")
        assertEquals(band.uri, PreferredLocalAudioMatcher.uniqueMatch("Song", "AC/DC", 180_000L, listOf(band)))
        assertNull(PreferredLocalAudioMatcher.uniqueMatch("Song", "AC, DC", 180_000L, listOf(band)))
    }

    @Test fun realSameAlbumEncodingCopiesPreferFlacWithinVerifiedDurationTolerance() {
        val mp3 = LocalAudioCandidate("content://mp3", "STAY TUNE", "Suchmos", 298_747L, album = "LOVE&VICE", fileName = "Suchmos - STAY TUNE.mp3")
        val flac = mp3.copy(uri = "content://flac", identity = "other-file", fileName = "Suchmos - STAY TUNE.flac")
        assertEquals(flac.uri, PreferredLocalAudioMatcher.uniqueMatch("STAY TUNE", "Suchmos", 295_858L, listOf(mp3, flac), "LOVE&VICE"))
    }

    @Test fun extendedDurationToleranceRequiresMatchingAlbumAndHasFiveSecondCap() {
        val candidate = studio.copy(durationMs = 304_838L, album = "Confirmed Album")
        assertEquals(candidate.uri, PreferredLocalAudioMatcher.uniqueMatch("Song", "Artist", 300_000L, listOf(candidate), "Confirmed Album"))
        assertNull(PreferredLocalAudioMatcher.uniqueMatch("Song", "Artist", 300_000L, listOf(candidate)))
        assertNull(PreferredLocalAudioMatcher.uniqueMatch("Song", "Artist", 300_000L, listOf(candidate), "Another Album"))
        assertNull(PreferredLocalAudioMatcher.uniqueMatch("Song", "Artist", 300_000L, listOf(candidate.copy(durationMs = 305_001L)), "Confirmed Album"))
    }

    @Test fun differentAlbumCopiesStillRequireManualChoice() {
        val one = studio.copy(album = "First", fileName = "song.flac")
        val two = studio.copy(uri = "content://second", identity = "second-file", album = "Second", fileName = "song.mp3")
        assertNull(PreferredLocalAudioMatcher.uniqueMatch("Song", "Artist", 180_000L, listOf(one, two), "Third"))
    }

    @Test fun filenameLiveEvidenceRejectsMislabelledStudioTag() {
        val live = studio.copy(album = "Album", fileName = "Artist - Song (Live at Tokyo).flac")
        assertNull(PreferredLocalAudioMatcher.uniqueMatch("Song", "Artist", 180_000L, listOf(live), "Album"))
    }

    @Test fun verifiedMultiArtistSlashCreditMatchesOnlyWithSameAlbum() {
        val duet = studio.copy(artist = "陈奕迅/eason and the duo band", album = "L.O.V.E.")
        assertEquals(duet.uri, PreferredLocalAudioMatcher.uniqueMatch("Song", "陈奕迅 • eason and the duo band", 180_000L, listOf(duet), "L.O.V.E."))
        assertNull(PreferredLocalAudioMatcher.uniqueMatch("Song", "陈奕迅 • eason and the duo band", 180_000L, listOf(duet)))
    }

    @Test fun combinedLocalTitlesAndPartialCollaborationCreditsMatchInForwardPlayback() {
        val local = studio.copy(title = "01 Artist - Song", album = "Album", fileName = "01 Artist - Song.flac")
        assertEquals(local.uri, PreferredLocalAudioMatcher.uniqueMatch("Song", "Artist • Guest", 180_000L, listOf(local), "Album"))
        assertEquals(local.uri, PreferredLocalAudioMatcher.uniqueMatch("Song", "Artist", 180_000L, listOf(local.copy(title = "Song - Artist")), "Album"))
        assertNull(PreferredLocalAudioMatcher.uniqueMatch("Song", "Other • Guest", 180_000L, listOf(local), "Album"))
    }

    @Test fun filenameMetadataFallbackWorksInForwardPlaybackAndKeepsBandSlashLiteral() {
        val unknown = studio.copy(title = "01 Artist - Song", artist = "<unknown>", album = "Album", fileName = "01 Artist - Song.mp3")
        assertEquals(unknown.uri, PreferredLocalAudioMatcher.uniqueMatch("Song", "Artist", 180_000L, listOf(unknown), "Album"))
        val band = studio.copy(artist = "AC/DC", album = "Album")
        assertNull(PreferredLocalAudioMatcher.uniqueMatch("Song", "AC • DC", 180_000L, listOf(band), "Album"))
    }

    @Test fun olderScannerArtistWithTrackIndexAlsoMatchesDuringForwardPlayback() {
        val older = studio.copy(artist = "01 Artist", album = "Album", fileName = "01 Artist - Song.flac")
        assertEquals(older.uri, PreferredLocalAudioMatcher.uniqueMatch("Song", "Artist", 180_000L, listOf(older), "Album"))
        assertEquals(older.uri, PreferredLocalAudioMatcher.uniqueMatch("Song", "01 Artist", 180_000L, listOf(older), "Album"))
    }

    @Test fun equivalentRegionalAlbumFilesCanChooseTheBestReadableEncoding() {
        val mp3 = studio.copy(album = "Album (Japan Edition)", fileName = "Artist - Song.mp3")
        val flac = mp3.copy(uri = "content://lossless", album = "Album (Remastered)", fileName = "Artist - Song.flac")
        assertEquals(flac.uri, PreferredLocalAudioMatcher.uniqueMatch("Song", "Artist", 180_000L, listOf(mp3, flac), "Album"))
        val original = mp3.copy(uri = "content://original", album = "Album")
        assertEquals(original.uri, PreferredLocalAudioMatcher.uniqueMatch("Song", "Artist", 180_000L, listOf(original, flac), "Album"))
    }

    @Test fun verifiedAlbumCopiesTakePriorityOverAnotherAlbumWithoutChangingItsCoreName() {
        val target = studio.copy(album = "Album (US Edition)", fileName = "song.flac")
        val unrelated = studio.copy(uri = "content://other", album = "Other Album", fileName = "song.flac")
        assertEquals(target.uri, PreferredLocalAudioMatcher.uniqueMatch("Song", "Artist", 180_000L, listOf(unrelated, target), "Album"))
        assertNull(PreferredLocalAudioMatcher.uniqueMatch("Song", "Artist", 184_000L, listOf(target.copy(durationMs = 180_000L)), "Album"))
    }

    @Test fun filenameReleaseAnnotationsDoNotBlockButRecordingAnnotationsStillDo() {
        val release = studio.copy(album = "Album (Remastered)", fileName = "Artist - Song (2011 Remastered).flac")
        assertEquals(release.uri, PreferredLocalAudioMatcher.uniqueMatch("Song", "Artist", 180_000L, listOf(release), "Album"))
        assertNull(PreferredLocalAudioMatcher.uniqueMatch("Song", "Artist", 180_000L,
            listOf(release.copy(fileName = "Artist - Song (Live).flac")), "Album"))
    }

    @Test fun filenameRecordingVenueYearAndRemixCreditMustNotDisappearBehindAGenericTag() {
        listOf(
            "Song (Live)" to "Artist - Song (Live at Tokyo).flac",
            "Song (Live)" to "Artist - Song - Live at Tokyo.flac",
            "Song (Live)" to "Artist - Song (Live) (Tokyo).flac",
            "Song (Live)" to "Artist - Song (Live at Tokyo (Japan Edition)).flac",
            "Song (重录)" to "Artist - Song (2020 重录版).flac",
            "Song (Remix)" to "Artist - Song (DJ Example Remix).flac",
            "Song (Live at London)" to "Artist - Song (Live at Tokyo).flac"
        ).forEach { (title, filename) ->
            val recording = studio.copy(title = title, album = "Album", fileName = filename)
            assertNull(filename, PreferredLocalAudioMatcher.uniqueMatch(title, "Artist", 180_000L, listOf(recording), "Album"))
        }
    }

    @Test fun completeRecordingTagsCanBeMoreSpecificThanTheFilenameAndReleaseLabelsAreAllowed() {
        val recording = studio.copy(title = "Song (Live at Tokyo)", album = "Album", fileName = "Artist - Song (Live).flac")
        assertEquals(recording.uri, PreferredLocalAudioMatcher.uniqueMatch(recording.title, "Artist", 180_000L, listOf(recording), "Album"))
        listOf("Artist - Song (Live at Tokyo) (2011 Remastered).flac",
            "Artist - Song (Live at Tokyo - Japan Edition).flac").forEach { filename ->
            assertEquals(recording.uri, PreferredLocalAudioMatcher.uniqueMatch(recording.title, "Artist", 180_000L,
                listOf(recording.copy(fileName = filename)), "Album"))
        }
        val generic = recording.copy(title = "Song (Live)", fileName = "Artist - Song (Live (2011 Remastered)).flac")
        assertEquals(generic.uri, PreferredLocalAudioMatcher.uniqueMatch(generic.title, "Artist", 180_000L, listOf(generic), "Album"))
    }

    @Test fun recordingWordsInsideASongTitleAreNotVersionSuffixes() {
        val song = studio.copy(title = "Live and Let Die", album = "Album", fileName = "Artist - Live and Let Die.flac")
        assertEquals(song.uri, PreferredLocalAudioMatcher.uniqueMatch(song.title, "Artist", 180_000L, listOf(song), "Album"))
        assertEquals(song.uri, PreferredLocalAudioMatcher.uniqueMatch(song.title, "Artist", 180_000L,
            listOf(song.copy(fileName = "Artist - Live and Let Die (Japan Edition).flac")), "Album"))
        listOf("Artist - Song-Live.flac", "Artist - Song (Re-recorded 2020).flac").forEach { filename ->
            assertNull(PreferredLocalAudioMatcher.uniqueMatch("Song", "Artist", 180_000L,
                listOf(studio.copy(album = "Album", fileName = filename)), "Album"))
        }
    }

    @Test fun manualChoiceHasPriorityAndDoesNotRunAutoMatching() = runBlocking {
        assertEquals("content://manual", resolvePreferredLocalAudio(true, "content://manual", true,
            automaticUri = { error("Manual choice must win") }, isReadable = { true }))
    }

    @Test fun unreadableManualChoiceFallsBackWithoutChoosingAnotherLocalVersion() = runBlocking {
        assertNull(resolvePreferredLocalAudio(true, "content://missing", true,
            automaticUri = { error("Must fall back to normal downloaded/online resolution") }, isReadable = { false }))
    }

    @Test fun unreadableAutomaticCandidateFallsBack() = runBlocking {
        assertNull(resolvePreferredLocalAudio(true, null, true, automaticUri = { studio.uri }, isReadable = { false }))
    }

    @Test fun disablingPreferenceOrUnlinkExclusionAvoidsLookup() = runBlocking {
        var checked = false
        assertNull(resolvePreferredLocalAudio(false, "content://manual", true,
            automaticUri = { error("Disabled") }, isReadable = { checked = true; true }))
        assertFalse(checked)
        assertNull(resolvePreferredLocalAudio(true, null, false,
            automaticUri = { error("Unlinked songs cannot auto-rematch") }, isReadable = { true }))
    }
}

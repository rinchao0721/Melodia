package com.lin0721.linmusic.feature.localmusic.domain

import com.lin0721.linmusic.core.model.Album
import com.lin0721.linmusic.core.model.Artist
import com.lin0721.linmusic.core.model.Track
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class AlbumScopedMatchPolicyTest {
    private val local = LocalAudioCandidate("content://music/1", "01 五味瓶", "Artist", 180_000L,
        album = "Album (日本版)", fileName = "01 五味瓶.flac")
    private val online = Track(id = 100L, name = "五味瓶", ar = listOf(Artist(name = "Artist")),
        al = Album(id = 20L, name = "Album (2011 Remastered)"), dt = 180_200L)

    @Test fun explicitAlbumScopeAcceptsMissingOrReducedArtistAndDifferentReleaseTags() {
        listOf("", "<unknown>", "未知歌手", "Artist", "A").forEach { artist ->
            val decision = AlbumScopedMatchPolicy.evaluate(local.copy(artist = artist, album = null),
                listOf(online.copy(ar = listOf(Artist(name = "A"), Artist(name = "B")))))
            assertEquals(AlbumScopedMatchStatus.MATCH, decision.status)
            assertEquals(online.id, decision.match!!.id)
        }
    }

    @Test fun titleAndDurationRemainRequiredDespiteConfirmedAlbum() {
        val decision = AlbumScopedMatchPolicy.evaluate(local.copy(title = "Another song", fileName = "Another song.flac"), listOf(online))
        assertEquals(AlbumScopedMatchStatus.NO_MATCH, decision.status)
        assertNull(decision.match)
        assertEquals(listOf(online), decision.candidates)
    }

    @Test fun widenedToleranceHasFiveSecondMinimum() {
        val shortOnline = online.copy(dt = 40_000L)
        assertEquals(AlbumScopedMatchStatus.MATCH, AlbumScopedMatchPolicy.evaluate(local.copy(durationMs = 45_000L), listOf(shortOnline)).status)
        assertEquals(AlbumScopedMatchStatus.REVIEW, AlbumScopedMatchPolicy.evaluate(local.copy(durationMs = 45_001L), listOf(shortOnline)).status)
    }

    @Test fun widenedToleranceIsFivePercentForNormalSongs() {
        val normal = online.copy(dt = 200_000L)
        assertEquals(AlbumScopedMatchStatus.MATCH, AlbumScopedMatchPolicy.evaluate(local.copy(durationMs = 210_000L), listOf(normal)).status)
        assertEquals(AlbumScopedMatchStatus.MATCH, AlbumScopedMatchPolicy.evaluate(local.copy(durationMs = 190_000L), listOf(normal)).status)
        assertEquals(AlbumScopedMatchStatus.REVIEW, AlbumScopedMatchPolicy.evaluate(local.copy(durationMs = 210_001L), listOf(normal)).status)
    }

    @Test fun widenedToleranceIsCappedAtFifteenSeconds() {
        val long = online.copy(dt = 600_000L)
        assertEquals(AlbumScopedMatchStatus.MATCH, AlbumScopedMatchPolicy.evaluate(local.copy(durationMs = 615_000L), listOf(long)).status)
        assertEquals(AlbumScopedMatchStatus.REVIEW, AlbumScopedMatchPolicy.evaluate(local.copy(durationMs = 615_001L), listOf(long)).status)
    }

    @Test fun missingDurationsNeedReviewRatherThanAutomaticSave() {
        assertEquals(AlbumScopedMatchStatus.REVIEW, AlbumScopedMatchPolicy.evaluate(local.copy(durationMs = 0L), listOf(online)).status)
        assertEquals(AlbumScopedMatchStatus.REVIEW, AlbumScopedMatchPolicy.evaluate(local, listOf(online.copy(dt = 0L))).status)
    }

    @Test fun artistPrefixesAndSuffixesAndTrackNumbersUseSharedMetadataResolver() {
        listOf("01 Artist - 五味瓶", "01 五味瓶 - Artist", "Artist—五味瓶", "五味瓶_Artist").forEach { title ->
            assertEquals(title, AlbumScopedMatchStatus.MATCH,
                AlbumScopedMatchPolicy.evaluate(local.copy(title = title), listOf(online)).status)
        }
    }

    @Test fun confirmedOnlineCreditCanResolveFileCreditsWithoutLocalArtist() {
        listOf("Artist - 五味瓶", "五味瓶 - Artist").forEach { title ->
            val decision = AlbumScopedMatchPolicy.evaluate(local.copy(title = title, artist = "", fileName = "$title.flac"), listOf(online))
            assertEquals(title, AlbumScopedMatchStatus.MATCH, decision.status)
        }
    }

    @Test fun normalizedPunctuationAndCaseDoNotBlockAUniqueSong() {
        val candidate = local.copy(title = "01 Don’t Stop—Me Now!", fileName = "01 Don’t Stop—Me Now!.flac")
        val catalog = online.copy(name = "DON'T STOP ME NOW")
        assertSame(catalog, AlbumScopedMatchPolicy.evaluate(candidate, listOf(catalog)).match)
    }

    @Test fun releaseOnlyTitleSuffixesDoNotSplitTheSameRecording() {
        listOf(" (2011 Remastered)", " [Japan Edition]", "（日本版）").forEach { suffix ->
            val tagged = local.copy(title = "五味瓶$suffix", fileName = "五味瓶$suffix.flac")
            assertEquals(suffix, AlbumScopedMatchStatus.MATCH, AlbumScopedMatchPolicy.evaluate(tagged, listOf(online)).status)
            assertEquals(suffix, AlbumScopedMatchStatus.MATCH,
                AlbumScopedMatchPolicy.evaluate(local, listOf(online.copy(name = "五味瓶$suffix"))).status)
        }
    }

    @Test fun releaseSuffixCleanupNeverRemovesLiveRecordingIdentity() {
        val liveRemaster = online.copy(name = "五味瓶 (Live) (2011 Remastered)")
        assertEquals(AlbumScopedMatchStatus.REVIEW, AlbumScopedMatchPolicy.evaluate(local, listOf(liveRemaster)).status)
        val sameLive = local.copy(title = "五味瓶 [Live]", fileName = "五味瓶 [Live].flac")
        assertSame(liveRemaster, AlbumScopedMatchPolicy.evaluate(sameLive, listOf(liveRemaster)).match)
    }

    @Test fun oppositeScriptTranslationSuffixesCanUseTheOriginalTitleInEitherDirection() {
        listOf("行动 (Move)" to "行动", "蒙在鼓里 (Be Kept in the Dark)" to "蒙在鼓里",
            "这就是你 (This Is You)" to "这就是你", "Pacific (太平洋)" to "Pacific").forEach { (bilingual, plain) ->
            val candidate = local.copy(title = bilingual, fileName = "$bilingual.flac")
            assertEquals(bilingual, AlbumScopedMatchStatus.MATCH,
                AlbumScopedMatchPolicy.evaluate(candidate, listOf(online.copy(name = plain))).status)
            assertEquals(bilingual, AlbumScopedMatchStatus.MATCH,
                AlbumScopedMatchPolicy.evaluate(candidate.copy(title = plain, fileName = "$plain.flac"), listOf(online.copy(name = bilingual))).status)
        }
    }

    @Test fun translationCleanupDoesNotBypassDurationOrRecordingChecks() {
        val candidate = local.copy(title = "乌鸦 (Crow)", fileName = "乌鸦 (Crow).flac", durationMs = 425_353L)
        val catalog = online.copy(name = "乌鸦", dt = 317_884L)
        assertEquals(AlbumScopedMatchStatus.REVIEW, AlbumScopedMatchPolicy.evaluate(candidate, listOf(catalog)).status)
        val live = local.copy(title = "行动 (Move) (Live)", fileName = "行动 (Move) (Live).flac")
        assertTrue(AlbumScopedMatchPolicy.evaluate(live, listOf(online.copy(name = "行动"))).status != AlbumScopedMatchStatus.MATCH)
    }

    @Test fun sameScriptSubtitlesRemainPartOfTheSongIdentity() {
        listOf("Exit Music (For a Film)" to "Exit Music", "Ultra Violet (Light My Way)" to "Ultra Violet", "五味瓶（续篇）" to "五味瓶").forEach { (full, short) ->
            val candidate = local.copy(title = full, fileName = "$full.flac")
            assertTrue(full, AlbumScopedMatchPolicy.evaluate(candidate, listOf(online.copy(name = short))).status != AlbumScopedMatchStatus.MATCH)
        }
    }

    @Test fun crossScriptVersionAnnotationsAndNumbersCannotBeTreatedAsTranslations() {
        listOf("Live", "Remix", "Instrumental", "Clean", "Explicit", "Part II", "PartII", "Version 2", "II").forEach { annotation ->
            val full = "五味瓶 ($annotation)"
            assertTrue(annotation, AlbumScopedMatchPolicy.evaluate(local.copy(title = full, fileName = "$full.flac"), listOf(online)).status != AlbumScopedMatchStatus.MATCH)
        }
        listOf("现场", "混音", "伴奏", "重录版", "版本二", "第二部").forEach { annotation ->
            val full = "Pacific ($annotation)"
            assertTrue(annotation, AlbumScopedMatchPolicy.evaluate(local.copy(title = full, fileName = "$full.flac"), listOf(online.copy(name = "Pacific"))).status != AlbumScopedMatchStatus.MATCH)
        }
    }

    @Test fun differentOnlineIdsForTheSameTranslationAliasStillNeedReview() {
        val candidate = local.copy(title = "行动 (Move)", fileName = "行动 (Move).flac")
        val first = online.copy(name = "行动")
        val second = online.copy(id = 101L, name = "行动 (Move)")
        assertEquals(AlbumScopedMatchStatus.REVIEW, AlbumScopedMatchPolicy.evaluate(candidate, listOf(first, second)).status)
    }

    @Test fun featuredCreditsCanBeOmittedOnlyWhenExplicitlyVerifiedByOnlineArtists() {
        val candidate = local.copy(title = "GIRL feat.呂布 (女孩)", fileName = "GIRL feat.呂布 (女孩).flac")
        val credited = online.copy(name = "GIRL", ar = listOf(Artist(name = "Suchmos"), Artist(name = "呂布")))
        assertSame(credited, AlbumScopedMatchPolicy.evaluate(candidate, listOf(credited)).match)
        assertTrue(AlbumScopedMatchPolicy.evaluate(candidate, listOf(credited.copy(ar = listOf(Artist(name = "Suchmos"))))).status != AlbumScopedMatchStatus.MATCH)
    }

    @Test fun verifiedFeaturedCreditAndTranslationCanBeInEitherSuffixOrder() {
        val credited = online.copy(name = "GIRL", ar = listOf(Artist(name = "Suchmos"), Artist(name = "呂布")))
        listOf("GIRL (女孩) feat.呂布", "GIRL (feat.呂布)", "GIRL ft. 呂布").forEach { title ->
            assertSame(title, credited, AlbumScopedMatchPolicy.evaluate(local.copy(title = title, fileName = "$title.flac"), listOf(credited)).match)
        }
        val plain = local.copy(title = "GIRL", fileName = "GIRL.flac")
        assertEquals(AlbumScopedMatchStatus.MATCH, AlbumScopedMatchPolicy.evaluate(plain, listOf(credited.copy(name = "GIRL (feat.呂布)"))).status)
    }

    @Test fun everyFeaturedCreditMustBePresentInTheActualOnlineArtistList() {
        val candidate = local.copy(title = "Song feat. B & C", fileName = "Song feat. B & C.flac")
        val credited = online.copy(name = "Song", ar = listOf(Artist(name = "A"), Artist(name = "B")))
        assertTrue(AlbumScopedMatchPolicy.evaluate(candidate, listOf(credited)).status != AlbumScopedMatchStatus.MATCH)
        assertEquals(AlbumScopedMatchStatus.MATCH,
            AlbumScopedMatchPolicy.evaluate(candidate, listOf(credited.copy(ar = credited.ar + Artist(name = "C")))).status)
    }

    @Test fun genericLiveCanUseOneUniqueSpecificLiveWithinTwoSeconds() {
        val candidate = local.copy(title = "五味瓶 (Live)", fileName = "五味瓶 (Live).flac")
        val live = online.copy(name = "五味瓶 (Live at Madison Square Garden, 1987)", dt = candidate.durationMs + 2_000L)
        assertSame(live, AlbumScopedMatchPolicy.evaluate(candidate, listOf(live)).match)
        assertEquals(AlbumScopedMatchStatus.REVIEW, AlbumScopedMatchPolicy.evaluate(candidate, listOf(live.copy(dt = candidate.durationMs + 2_001L))).status)
        val chinese = candidate.copy(title = "五味瓶（现场）", fileName = "五味瓶（现场）.flac")
        assertSame(live, AlbumScopedMatchPolicy.evaluate(chinese, listOf(live)).match)
    }

    @Test fun multipleNearbyLiveVenuesNeedReviewEvenWhenOneGenericTitleMatchesExactly() {
        val candidate = local.copy(title = "五味瓶 (Live)", fileName = "五味瓶 (Live).flac")
        val generic = online.copy(name = "五味瓶 (Live)")
        val specific = online.copy(id = 101L, name = "五味瓶 (Live at Tokyo)")
        assertEquals(AlbumScopedMatchStatus.REVIEW, AlbumScopedMatchPolicy.evaluate(candidate, listOf(generic, specific)).status)
        assertEquals(AlbumScopedMatchStatus.REVIEW,
            AlbumScopedMatchPolicy.evaluate(candidate, listOf(specific, generic.copy(name = "五味瓶 Live"))).status)
        val paris = specific.copy(id = 102L, name = "五味瓶 (Live at Paris)")
        assertEquals(AlbumScopedMatchStatus.REVIEW, AlbumScopedMatchPolicy.evaluate(candidate, listOf(specific, paris)).status)
    }

    @Test fun aLiveCandidateWithUnknownDurationPreventsGenericLiveConfirmation() {
        val candidate = local.copy(title = "五味瓶 (Live)", fileName = "五味瓶 (Live).flac")
        val generic = online.copy(name = "五味瓶 (Live)")
        val unknown = online.copy(id = 101L, name = "五味瓶 (Live at Unknown Venue)", dt = 0L)
        assertEquals(AlbumScopedMatchStatus.REVIEW, AlbumScopedMatchPolicy.evaluate(candidate, listOf(generic, unknown)).status)
        assertEquals(AlbumScopedMatchStatus.REVIEW,
            AlbumScopedMatchPolicy.evaluate(candidate.copy(durationMs = 0L), listOf(generic.copy(name = "五味瓶 (Live at Tokyo)"))).status)
    }

    @Test fun aFartherLiveVenueDoesNotOutvoteOneUniqueNearLiveRecording() {
        val candidate = local.copy(title = "五味瓶 (Live)", fileName = "五味瓶 (Live).flac")
        val near = online.copy(name = "五味瓶 (Live at Tokyo)")
        val far = online.copy(id = 101L, name = "五味瓶 (Live at Paris)", dt = candidate.durationMs + 20_000L)
        assertSame(near, AlbumScopedMatchPolicy.evaluate(candidate, listOf(far, near)).match)
    }

    @Test fun explicitLocalVenuesYearsAndUnknownVenueLabelsDoNotBecomeGenericLive() {
        listOf("Live at Paris", "Live 1987", "Live at Unknown Venue").forEach { qualifier ->
            val candidate = local.copy(title = "五味瓶 ($qualifier)", fileName = "五味瓶 ($qualifier).flac")
            val tokyo = online.copy(name = "五味瓶 (Live at Tokyo)")
            assertEquals(qualifier, AlbumScopedMatchStatus.REVIEW, AlbumScopedMatchPolicy.evaluate(candidate, listOf(tokyo)).status)
        }
    }

    @Test fun genericLiveNeverMatchesStudioOrAnAcousticRemixRecording() {
        val live = local.copy(title = "五味瓶 (Live)", fileName = "五味瓶 (Live).flac")
        assertEquals(AlbumScopedMatchStatus.REVIEW, AlbumScopedMatchPolicy.evaluate(live, listOf(online)).status)
        listOf("Live Remix", "Live Acoustic", "Live Instrumental", "Live Clean", "Live Explicit", "Live Radio Edit", "Live Extended").forEach { qualifier ->
            assertEquals(qualifier, AlbumScopedMatchStatus.REVIEW,
                AlbumScopedMatchPolicy.evaluate(live, listOf(online.copy(name = "五味瓶 ($qualifier)"))).status)
        }
        assertEquals(AlbumScopedMatchStatus.REVIEW,
            AlbumScopedMatchPolicy.evaluate(local, listOf(online.copy(name = "五味瓶 (Live at Tokyo)"))).status)
    }

    @Test fun genericLiveQualifierGuardDoesNotTreatWordsInTheSongBaseAsVersions() {
        val live = local.copy(title = "Clean Part (Live)", fileName = "Clean Part (Live).flac")
        val venue = online.copy(name = "Clean Part (Live at Radio City, 2017)")
        assertSame(venue, AlbumScopedMatchPolicy.evaluate(live, listOf(venue)).match)
    }

    @Test fun isolatedExactGenericLiveRetainsExistingFivePercentTolerance() {
        val live = local.copy(title = "五味瓶 (Live)", fileName = "五味瓶 (Live).flac", durationMs = 180_000L)
        val exact = online.copy(name = "五味瓶 (Live)", dt = 185_000L)
        assertSame(exact, AlbumScopedMatchPolicy.evaluate(live, listOf(exact)).match)
        val specific = online.copy(id = 101L, name = "五味瓶 (Live at Tokyo)", dt = 220_000L)
        assertEquals(AlbumScopedMatchStatus.REVIEW, AlbumScopedMatchPolicy.evaluate(live, listOf(exact, specific)).status)
    }

    @Test fun genericLiveStillHonorsHiddenFilenameVenueAndConflictingOnlineCopies() {
        val live = local.copy(title = "五味瓶 (Live)", fileName = "五味瓶 (Live at Paris).flac")
        val tokyo = online.copy(name = "五味瓶 (Live at Tokyo)")
        assertEquals(AlbumScopedMatchStatus.REVIEW, AlbumScopedMatchPolicy.evaluate(live, listOf(tokyo)).status)
        val generic = live.copy(fileName = "五味瓶 (Live).flac")
        assertEquals(AlbumScopedMatchStatus.REVIEW,
            AlbumScopedMatchPolicy.evaluate(generic, listOf(tokyo, tokyo.copy(dt = 0L))).status)
    }

    @Test fun windowsCopyNumbersDoNotMakeFullyTaggedRemixesAppearToHaveHiddenVersions() {
        val remix = local.copy(title = "五味瓶 (Jacknife Lee Remix)")
        val catalog = online.copy(name = "五味瓶 (Jacknife Lee Remix)")
        listOf("五味瓶 (Jacknife Lee Remix)(1).flac", "Artist - 五味瓶 (Jacknife Lee Remix) (2).flac", "五味瓶 (Jacknife Lee Remix)(999).wav").forEach { file ->
            assertSame(file, catalog, AlbumScopedMatchPolicy.evaluate(remix.copy(fileName = file), listOf(catalog)).match)
        }
    }

    @Test fun copyNumberCleanupDoesNotRemoveYearsPartsOrTheActualRecordingVersion() {
        val remix = local.copy(title = "五味瓶 (Remix)")
        listOf("五味瓶 (Other Remix)(1).flac", "五味瓶 (Remix)(2024).flac", "五味瓶 (Remix)(Part 1).flac").forEach { file ->
            assertEquals(file, AlbumScopedMatchStatus.REVIEW,
                AlbumScopedMatchPolicy.evaluate(remix.copy(fileName = file), listOf(online.copy(name = "五味瓶 (Remix)"))).status)
        }
        val hidden = local.copy(fileName = "五味瓶 (Live)(1).flac")
        assertEquals(AlbumScopedMatchStatus.REVIEW, AlbumScopedMatchPolicy.evaluate(hidden, listOf(online)).status)
    }

    @Test fun numberedParenthesesInTheTagRemainPartOfTheActualSongTitle() {
        val numbered = local.copy(title = "五味瓶 (Live)(1)", fileName = "五味瓶 (Live)(1).flac")
        assertTrue(AlbumScopedMatchPolicy.evaluate(numbered, listOf(online.copy(name = "五味瓶 (Live at Tokyo)"))).status != AlbumScopedMatchStatus.MATCH)
        assertEquals(online.copy(name = numbered.title).name,
            AlbumScopedMatchPolicy.evaluate(numbered, listOf(online.copy(name = numbered.title))).match?.name)
    }

    @Test fun numericTitlesAreNotTreatedAsTrackNumbers() {
        listOf("7 rings", "1989", "3.14", "01", "10-Feet").forEach { name ->
            val candidate = local.copy(title = name, fileName = "$name.flac")
            assertEquals(name, AlbumScopedMatchStatus.MATCH,
                AlbumScopedMatchPolicy.evaluate(candidate, listOf(online.copy(name = name))).status)
        }
        assertEquals(AlbumScopedMatchStatus.NO_MATCH,
            AlbumScopedMatchPolicy.evaluate(local.copy(title = "3.14", fileName = "3.14.flac"), listOf(online.copy(name = "314"))).status)
        assertEquals(AlbumScopedMatchStatus.NO_MATCH,
            AlbumScopedMatchPolicy.evaluate(local.copy(title = "01", fileName = "01.flac"), listOf(online.copy(name = "1"))).status)
    }

    @Test fun catalogTrackNumbersAreNeverStripped() {
        assertEquals(AlbumScopedMatchStatus.NO_MATCH,
            AlbumScopedMatchPolicy.evaluate(local.copy(title = "五味瓶"), listOf(online.copy(name = "01 五味瓶"))).status)
    }

    @Test fun differentRecordingVersionsAreSuggestionsOnly() {
        listOf("(Live)", "[Remix]", "(Instrumental)", "(Acoustic)", "(Demo)", "(Re-recorded)", "（伴奏）", "（重录版）").forEach { version ->
            val changed = online.copy(name = "五味瓶 $version")
            val decision = AlbumScopedMatchPolicy.evaluate(local, listOf(changed))
            assertEquals(version, AlbumScopedMatchStatus.REVIEW, decision.status)
            assertNull(decision.match)
            assertEquals(listOf(changed), decision.candidates)
        }
    }

    @Test fun versionInLocalTitleIsNotDiscarded() {
        val changed = local.copy(title = "01 五味瓶 (Live)", fileName = "01 五味瓶 (Live).flac")
        assertEquals(AlbumScopedMatchStatus.REVIEW, AlbumScopedMatchPolicy.evaluate(changed, listOf(online)).status)
        val sameLive = online.copy(name = "五味瓶 [LIVE]")
        assertSame(sameLive, AlbumScopedMatchPolicy.evaluate(changed, listOf(sameLive)).match)
    }

    @Test fun hiddenFilenameVersionCannotProduceAutomaticAssociation() {
        val decision = AlbumScopedMatchPolicy.evaluate(local.copy(fileName = "Artist - 五味瓶 (Live).flac"), listOf(online))
        assertEquals(AlbumScopedMatchStatus.REVIEW, decision.status)
        assertNull(decision.match)
    }

    @Test fun liveVenuesRemainPartOfVersionIdentity() {
        val candidate = local.copy(title = "五味瓶 (Live at Wembley)", fileName = "五味瓶 (Live at Wembley).flac")
        val different = online.copy(name = "五味瓶 (Live at Tokyo)")
        assertEquals(AlbumScopedMatchStatus.REVIEW, AlbumScopedMatchPolicy.evaluate(candidate, listOf(different)).status)
    }

    @Test fun realTitleStartingWithLiveIsNotReducedToAnOrdinarySong() {
        val candidate = local.copy(title = "Live Forever", fileName = "Live Forever.flac")
        assertEquals(AlbumScopedMatchStatus.NO_MATCH, AlbumScopedMatchPolicy.evaluate(candidate, listOf(online.copy(name = "Forever"))).status)
    }

    @Test fun multipleCompatibleSameNamedTracksInADiscSetNeedReview() {
        val alternate = online.copy(id = 101L, dt = 180_500L)
        assertEquals(AlbumScopedMatchStatus.REVIEW, AlbumScopedMatchPolicy.evaluate(local, listOf(online, alternate)).status)
    }

    @Test fun durationCanDistinguishTwoSameNamedAlbumTracks() {
        val long = online.copy(id = 101L, dt = 400_000L)
        val decision = AlbumScopedMatchPolicy.evaluate(local, listOf(long, online))
        assertEquals(AlbumScopedMatchStatus.MATCH, decision.status)
        assertSame(online, decision.match)
    }

    @Test fun missingDurationOnASecondSameNamedTrackIsNotSilentlyIgnored() {
        val unknown = online.copy(id = 101L, dt = 0L)
        assertEquals(AlbumScopedMatchStatus.REVIEW, AlbumScopedMatchPolicy.evaluate(local, listOf(online, unknown)).status)
    }

    @Test fun repeatedIdenticalOnlineIdsAreNotAmbiguous() {
        val decision = AlbumScopedMatchPolicy.evaluate(local, listOf(online, online.copy()))
        assertEquals(AlbumScopedMatchStatus.MATCH, decision.status)
        assertEquals(listOf(online), decision.candidates)
    }

    @Test fun conflictingCopiesOfAnOnlineIdNeedReview() {
        val changed = online.copy(dt = online.dt + 100L)
        assertEquals(AlbumScopedMatchStatus.REVIEW, AlbumScopedMatchPolicy.evaluate(local, listOf(online, changed)).status)
    }

    @Test fun twoLocalFilesCannotAutomaticallyClaimTheSameOnlineSong() {
        val duplicate = local.copy(uri = "content://music/2")
        val decisions = AlbumScopedMatchPolicy.plan(listOf(AlbumScopedLocal(local), AlbumScopedLocal(duplicate)), listOf(online))
        assertEquals(2, decisions.size)
        assertTrue(decisions.all { it.status == AlbumScopedMatchStatus.REVIEW && it.match == null })
        assertTrue(decisions.all { it.candidates.single() == online })
    }

    @Test fun planPreservesLocalOrderAndDropsRepeatedUriRows() {
        val second = local.copy(uri = "content://music/2", title = "第二首", fileName = "第二首.flac")
        val secondOnline = online.copy(id = 101L, name = "第二首")
        val decisions = AlbumScopedMatchPolicy.plan(listOf(AlbumScopedLocal(second), AlbumScopedLocal(local), AlbumScopedLocal(local)), listOf(online, secondOnline))
        assertEquals(listOf(second.uri, local.uri), decisions.map { it.localUri })
        assertEquals(listOf(secondOnline.id, online.id), decisions.map { it.match!!.id })
    }

    @Test fun equalAlbumSizeAndTrackNumberNeverAuthorizeDifferentTitles() {
        val other = online.copy(id = 101L, name = "Another song")
        val decisions = AlbumScopedMatchPolicy.plan(listOf(AlbumScopedLocal(local, 1)), listOf(other))
        assertEquals(AlbumScopedMatchStatus.NO_MATCH, decisions.single().status)
        assertNull(decisions.single().match)
    }

    @Test fun multidiscTrackNumbersAreNeverConvertedToAnAutomaticPairing() {
        listOf(1, 1001, 2001, -1).forEach { number ->
            val decisions = AlbumScopedMatchPolicy.plan(listOf(AlbumScopedLocal(local, number)),
                listOf(online.copy(id = 101L), online))
            assertEquals(number.toString(), AlbumScopedMatchStatus.REVIEW, decisions.single().status)
        }
    }

    @Test fun trackOrderOnlyBreaksEqualDurationRecommendationTies() {
        val candidate = local.copy(title = "Wrong song", fileName = "Wrong song.flac")
        val first = online.copy(id = 101L, name = "First")
        val second = online.copy(id = 102L, name = "Second")
        val decision = AlbumScopedMatchPolicy.plan(listOf(AlbumScopedLocal(candidate, 2)), listOf(first, second)).single()
        assertEquals(AlbumScopedMatchStatus.NO_MATCH, decision.status)
        assertEquals(listOf(second, first), decision.candidates)
    }

    @Test fun unknownTitlesNeverBecomeAutomaticEvenWithMatchingTextAndDuration() {
        listOf("", "unknown", "<unknown>", "未知歌曲").forEach { title ->
            val decision = AlbumScopedMatchPolicy.evaluate(local.copy(title = title, fileName = null), listOf(online.copy(name = title)))
            assertEquals(title, AlbumScopedMatchStatus.NO_MATCH, decision.status)
            assertNull(decision.match)
        }
    }

    @Test fun invalidReferencesAndInvalidOnlineIdsCannotBeSaved() {
        assertEquals(AlbumScopedMatchStatus.NO_MATCH, AlbumScopedMatchPolicy.evaluate(local.copy(uri = ""), listOf(online)).status)
        assertEquals(AlbumScopedMatchStatus.NO_MATCH, AlbumScopedMatchPolicy.evaluate(local, listOf(online.copy(id = 0L))).status)
    }

    @Test fun freshMetadataRecheckRejectsAChangedRecording() {
        assertEquals(AlbumScopedMatchStatus.MATCH, AlbumScopedMatchPolicy.evaluate(local, listOf(online)).status)
        val fresh = local.copy(durationMs = 250_000L)
        assertEquals(AlbumScopedMatchStatus.REVIEW, AlbumScopedMatchPolicy.evaluate(fresh, listOf(online)).status)
        assertNull(AlbumScopedMatchPolicy.evaluate(fresh, listOf(online)).match)
    }

    @Test fun evaluateNeverUsesTracksOutsideTheChosenAlbumInput() {
        val albumOnly = online.copy(id = 101L, name = "Different title")
        val decision = AlbumScopedMatchPolicy.evaluate(local, listOf(albumOnly))
        assertNull(decision.match)
        assertEquals(listOf(albumOnly), decision.candidates)
        assertTrue(decision.candidates.none { it.id == online.id })
    }

    @Test fun albumScopedRelaxationDoesNotAlterGlobalAutomaticMatching() {
        val incomplete = local.copy(artist = "", album = null)
        assertEquals(AlbumScopedMatchStatus.MATCH, AlbumScopedMatchPolicy.evaluate(incomplete, listOf(online)).status)
        assertEquals(BatchOnlineMatchStatus.NO_MATCH, BatchOnlineMatchPolicy.evaluate(incomplete, listOf(online)).status)
    }
}

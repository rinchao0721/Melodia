package com.lin0721.linmusic.feature.artist.domain.catalog

import com.lin0721.linmusic.core.model.ArtistAlbum
import com.lin0721.linmusic.feature.artist.domain.ArtistAlbumSelection
import java.time.LocalDate
import java.time.ZoneOffset
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test

class ArtistCatalogMatcherTest {
    private fun group(id: String, title: String, primary: String? = "Album", secondary: List<String>? = emptyList(),
                      date: String? = null, aliases: List<String> = emptyList(), artist: String = "correct-artist") =
        ArtistCatalogReleaseGroup(id, title, date, primary, secondary, aliases, listOf(ArtistCatalogCredit(artist, "Artist")))

    private fun album(id: Long, title: String, date: String? = null, size: Int = 10, type: String? = null) = ArtistAlbum(
        id = id, name = title, size = size, type = type,
        publishTime = date?.let { LocalDate.parse(it).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli() } ?: 0
    )

    @Test
    fun albumNeedsAnExplicitEmptySecondaryTypeList() {
        assertEquals(ArtistCatalogClassification.ORDINARY_ALBUM, ArtistCatalogMatcher.classify(group("a", "A")))
        assertEquals(ArtistCatalogClassification.UNKNOWN, ArtistCatalogMatcher.classify(group("a", "A", secondary = null)))
        assertEquals(ArtistCatalogClassification.UNKNOWN, ArtistCatalogMatcher.classify(group("a", "A", primary = null)))
        assertEquals(ArtistCatalogClassification.UNKNOWN, ArtistCatalogMatcher.classify(group("a", "A", primary = "Unrecognized")))
    }

    @Test
    fun knownPrimaryAndAllSecondaryTypesAreExcluded() {
        listOf("Single", "EP", "Broadcast", "Other").forEach {
            assertEquals(ArtistCatalogClassification.EXCLUDED, ArtistCatalogMatcher.classify(group("a", "A", primary = it)))
        }
        listOf("Live", "Compilation", "Remix", "Soundtrack", "DJ-mix", "Demo").forEach {
            assertEquals(ArtistCatalogClassification.EXCLUDED, ArtistCatalogMatcher.classify(group("a", "A", secondary = listOf(it))))
        }
    }

    @Test
    fun liveForTodayIsKeptBecauseItsCatalogTypeIsAnOrdinaryAlbum() {
        val groups = listOf(group("studio", "Live for Today"), group("live", "THE LIVE", secondary = listOf("Live")))
        val result = ArtistCatalogMatcher.select(listOf(album(1, "Live for Today"), album(2, "THE LIVE")), groups)
        assertEquals(listOf(1L), result.albums.map { it.id })
        assertEquals(1, result.excludedCount)
    }

    @Test
    fun traditionalChineseCatalogTitlesMatchSimplifiedOnlineTitlesAndNames() {
        val result = ArtistCatalogMatcher.select(listOf(album(1, "认了吧")), listOf(group("album", "認了吧")))
        assertEquals(listOf(1L), result.albums.map { it.id })
        val artist = ArtistCatalogArtist("id", "陳奕迅")
        assertEquals(listOf(artist), ArtistCatalogMatcher.exactNameCandidates(listOf(artist), listOf("陈奕迅")))
    }

    @Test
    fun aliasesCanIdentifyAnAlbumWithoutGuessingAnUnrecognizedTitle() {
        val groups = listOf(group("album", "認了吧", aliases = listOf("Admit It")))
        val result = ArtistCatalogMatcher.select(listOf(album(1, "Admit It"), album(2, "A Similar Name")), groups)
        assertEquals(listOf(1L), result.albums.map { it.id })
        assertEquals(ArtistCatalogTitleMatch.ALIAS, result.matches.single().titleMatch)
        assertEquals(listOf(2L), result.unmatchedAlbums.map { it.id })
    }

    @Test
    fun remasterRegionalAndDeluxeEditionsCollapseToOneOriginalGroup() {
        val groups = listOf(group("album", "The Key", date = "2013-07-22"))
        val albums = listOf(
            album(1, "The Key (2018 Remastered)", "2018-01-01"),
            album(2, "The Key (Japanese Edition)", "2013-07-25"),
            album(3, "The Key Deluxe Edition", "2013-07-22", 20),
            album(4, "The Key", "2013-07-22", 10)
        )
        val result = ArtistCatalogMatcher.select(albums, groups)
        assertEquals(listOf(4L), result.albums.map { it.id })
        assertEquals(3, result.duplicateCount)
        assertEquals(1, result.matchedCount)
    }

    @Test
    fun regionalEditionIsStillUsableWhenThereIsNoOriginalOnlineVersion() {
        val result = ArtistCatalogMatcher.select(listOf(album(1, "The Key（日本版）")), listOf(group("album", "The Key")))
        assertEquals(listOf(1L), result.albums.map { it.id })
        assertEquals(ArtistCatalogTitleMatch.EDITION, result.matches.single().titleMatch)
    }

    @Test
    fun qualifyingEditionIsSelectedBeforeMinimumTrackFilterAndResetRestoresOriginal() {
        val source = listOf(album(1, "A", size = 7), album(2, "A (Deluxe Edition)", size = 12))
        val catalog = listOf(group("a", "A"))
        val filtered = ArtistCatalogMatcher.select(source, catalog, minTracks = 8)
        assertEquals(listOf(2L), ArtistAlbumSelection.visibleAlbums(filtered.albums, 8).map { it.id })
        assertEquals(listOf(1L), filtered.duplicateAlbums.map { it.id })
        assertEquals(2, filtered.matchedCount + filtered.duplicateCount)
        assertTrue(filtered.unmatchedReleaseGroups.isEmpty())
        assertEquals(listOf(1L), ArtistCatalogMatcher.select(source, catalog, minTracks = 0).albums.map { it.id })
        assertEquals(listOf(1L, 2L), source.map { it.id })
    }

    @Test
    fun unknownCountDoesNotHideAKnownQualifyingEditionButZeroPreservesOriginal() {
        val source = listOf(album(1, "A", size = 0), album(2, "A (Japanese Edition)", size = 8))
        val catalog = listOf(group("a", "A"))
        assertEquals(2L, ArtistCatalogMatcher.select(source, catalog, minTracks = 8).albums.single().id)
        assertEquals(1L, ArtistCatalogMatcher.select(source, catalog, minTracks = -1).albums.single().id)
        val noQualifyingEdition = ArtistCatalogMatcher.select(source, catalog, minTracks = 9)
        assertTrue(ArtistAlbumSelection.visibleAlbums(noQualifyingEdition.albums, 9).isEmpty())
        assertEquals(2, noQualifyingEdition.matchedCount + noQualifyingEdition.duplicateCount)
    }

    @Test
    fun minimumTrackPreferenceCannotPromoteLiveOrAmbiguousGroups() {
        val source = listOf(album(1, "A", size = 7), album(2, "A (Live)", size = 30), album(3, "B", size = 30))
        val catalog = listOf(group("a", "A"), group("live", "A (Live)", secondary = listOf("Live")),
            group("b1", "B"), group("b2", "B"))
        val result = ArtistCatalogMatcher.select(source, catalog, minTracks = 8)
        assertTrue(ArtistAlbumSelection.visibleAlbums(result.albums, 8).isEmpty())
        assertEquals(listOf(2L), result.excludedAlbums.map { it.id })
        assertEquals(listOf(3L), result.ambiguousAlbums.map { it.id })
    }

    @Test
    fun recordingVersionsAreNotRemovedAsHarmlessEditionSuffixes() {
        val result = ArtistCatalogMatcher.select(
            listOf(album(1, "The Key (Live)"), album(2, "The Key (Remix)"), album(3, "The Key (Acoustic)")),
            listOf(group("album", "The Key"))
        )
        assertTrue(result.albums.isEmpty())
        assertEquals(3, result.unmatchedCount)
    }

    @Test
    fun exactCatalogTitleWinsOverAHeuristicallyStrippedEditionSuffix() {
        val result = ArtistCatalogMatcher.select(
            listOf(album(1, "The Key (Deluxe Edition)")),
            listOf(group("original", "The Key"), group("separate", "The Key (Deluxe Edition)"))
        )
        assertEquals("separate", result.matches.single().group.id)
    }

    @Test
    fun identicalTitlesInDifferentGroupsAreReportedAsAmbiguous() {
        val result = ArtistCatalogMatcher.select(listOf(album(1, "Same")), listOf(group("a", "Same"), group("b", "Same")))
        assertTrue(result.albums.isEmpty())
        assertEquals(1, result.ambiguousAlbums.size)
        assertEquals(1, result.unmatchedCount)
        assertEquals(2, result.unmatchedReleaseGroups.size)
    }

    @Test
    fun unknownClassificationIsNotPromotedToAnOrdinaryAlbum() {
        val result = ArtistCatalogMatcher.select(listOf(album(1, "Same")), listOf(group("a", "Same", secondary = null)))
        assertTrue(result.albums.isEmpty())
        assertEquals(1, result.unmatchedCount)
    }

    @Test
    fun boundArtistMustBePresentInCatalogCredits() {
        val result = ArtistCatalogMatcher.select(listOf(album(1, "A")), listOf(group("a", "A", artist = "someone-else")), boundArtistMbid = "correct-artist")
        assertTrue(result.albums.isEmpty())
        assertEquals(1, result.unmatchedCount)
    }

    @Test
    fun earliestCompatibleCanonicalEditionIsPreferredAndDateFailuresAreTolerated() {
        val result = ArtistCatalogMatcher.select(
            listOf(album(1, "A", "2020-01-01"), album(2, "A", "2008-07-01"), album(3, "A")),
            listOf(group("a", "A", date = "2008"))
        )
        assertEquals(2L, result.albums.single().id)
        val unknownDate = ArtistCatalogMatcher.select(listOf(album(1, "A"), album(2, "A", "2010-01-01")), listOf(group("a", "A", date = "bad date")))
        assertEquals(2L, unknownDate.albums.single().id)
    }

    @Test
    fun punctuationOnlyTitleRemainsDistinctFromAnEmptyTitle() {
        val result = ArtistCatalogMatcher.select(listOf(album(1, "?"), album(2, "   ")), listOf(group("a", "?")))
        assertEquals(listOf(1L), result.albums.map { it.id })
        assertEquals(listOf(2L), result.unmatchedAlbums.map { it.id })
    }

    @Test
    fun eachFetchedOnlineAlbumIsAccountedForAndMissingCatalogGroupsAreCounted() {
        val groups = listOf(group("a", "A"), group("b", "B"), group("c", "C"), group("live", "Concert", secondary = listOf("Live")), group("ep", "Short", primary = "EP"))
        val albums = listOf(album(1, "A"), album(2, "A (Remastered)"), album(3, "B"), album(4, "Concert"), album(5, "Short"), album(6, "Unknown"))
        val result = ArtistCatalogMatcher.select(albums, groups)
        assertEquals(2, result.matchedCount)
        assertEquals(1, result.duplicateCount)
        assertEquals(2, result.excludedCount)
        assertEquals(1, result.unmatchedCount)
        assertEquals(6, result.matchedCount + result.duplicateCount + result.excludedCount + result.unmatchedCount)
        assertEquals(listOf("c"), result.unmatchedReleaseGroups.map { it.id })
    }

    @Test
    fun twoDistinctKnownAlbumsSupportIdentityButTwoEditionsOfOneAlbumDoNot() {
        val groups = listOf(group("a", "A"), group("b", "B"))
        val one = ArtistCatalogMatcher.identityEvidence(listOf(album(1, "A"), album(2, "A (Deluxe Edition)")), groups)
        assertEquals(1, one.matchedCount)
        assertFalse(one.canAutoBind)
        val two = ArtistCatalogMatcher.identityEvidence(listOf(album(1, "A"), album(2, "B")), groups, boundArtistMbid = "correct-artist")
        assertEquals(2, two.matchedCount)
        assertTrue(two.canAutoBind)
    }

    @Test
    fun searchScoreAloneIsNotIdentityEvidenceAndAllExactNameDuplicatesRemainVisible() {
        val artists = listOf(ArtistCatalogArtist("a", "Different", score = 100), ArtistCatalogArtist("b", "Artist", score = 90), ArtistCatalogArtist("c", "Artist", score = 85))
        assertEquals(listOf("b", "c"), ArtistCatalogMatcher.exactNameCandidates(artists, listOf("Artist")).map { it.id })
        assertFalse(ArtistCatalogMatcher.identityEvidence(emptyList(), emptyList()).canAutoBind)
    }

    @Test
    fun u2JoshuaTreeAlbumDoesNotCollideWithTheSameNameInterview() {
        val groups = listOf(
            group("6f3e9fa6-be7a-3de8-a2b2-2072ece8a54d", "The Joshua Tree", date = "1987-03-09"),
            group("f997cb8f-f393-46e5-87eb-5a152e0a2480", "The Joshua Tree", primary = "Other", secondary = listOf("Interview"), date = "2006")
        )
        val result = ArtistCatalogMatcher.select(listOf(
            album(2021179, "The Joshua Tree", "1987-03-09", 11, "专辑"),
            album(175254346, "The Joshua Tree (Super Deluxe)", "2017-06-02", 49, "专辑"),
            album(34463596, "The Joshua Tree (Deluxe Edition) [Remastered]", "1987-03-09", 25, "专辑")
        ), groups)
        assertEquals(listOf(2021179L), result.albums.map { it.id })
        assertEquals(2, result.duplicateCount)
        assertTrue(result.ambiguousAlbums.isEmpty())
        assertEquals("6f3e9fa6-be7a-3de8-a2b2-2072ece8a54d", result.matches.single().group.id)
    }

    @Test
    fun u2UnforgettableFireUsesSourcePrimaryTypeBecauseAlbumAndSingleShareTheirYear() {
        val groups = listOf(
            group("c84d00f4-afd3-3184-be62-6ae5f135aa38", "The Unforgettable Fire", date = "1984-10-01"),
            group("e3925636-f3a0-3fbd-b59e-c54391a71a58", "The Unforgettable Fire", primary = "Single", date = "1984")
        )
        val original = album(2021143, "The Unforgettable Fire", "1984-10-01", 10, "专辑")
        val result = ArtistCatalogMatcher.select(listOf(original, album(2, original.name, "1984-10-01", 1, "Single")), groups)
        assertEquals(listOf(2021143L), result.albums.map { it.id })
        assertEquals(listOf(2L), result.excludedAlbums.map { it.id })
        assertEquals("c84d00f4-afd3-3184-be62-6ae5f135aa38", result.matches.single().group.id)
        assertEquals(1, ArtistCatalogMatcher.select(listOf(original.copy(type = null)), groups).ambiguousAlbums.size)
    }

    @Test
    fun anExplicitSourceAlbumLabelCannotReplaceAnExactCatalogEpWithItsStrippedStudioTitle() {
        val groups = listOf(group("original", "A"), group("ep", "A (Deluxe Edition)", primary = "EP"))
        val result = ArtistCatalogMatcher.select(listOf(album(1, "A (Deluxe Edition)", type = "Album")), groups)
        assertTrue(result.albums.isEmpty())
        assertEquals(1, result.excludedCount)
        assertEquals(1, ArtistCatalogMatcher.select(listOf(album(1, "A (Deluxe Edition)")), groups).excludedCount)
    }

    @Test
    fun sourceTypeCannotPromoteAnExplicitSingleOrBypassLiveClassification() {
        val result = ArtistCatalogMatcher.select(listOf(
            album(1, "A", type = "Single"),
            album(2, "Under a Blood Red Sky (Live)", type = "专辑")
        ), listOf(group("studio", "A"), group("live", "Under a Blood Red Sky (Live)", secondary = listOf("Live"))))
        assertTrue(result.albums.isEmpty())
        assertEquals(2, result.excludedCount)
    }

    @Test
    fun unknownSourceTypesAndSameTypeGroupCollisionsRemainAmbiguous() {
        val albumAndSingle = listOf(group("album", "A"), group("single", "A", primary = "Single"))
        assertEquals(1, ArtistCatalogMatcher.select(listOf(album(1, "A", type = "unrecognized")), albumAndSingle).ambiguousAlbums.size)
        val studioAndLive = listOf(group("studio", "A"), group("live", "A", secondary = listOf("Live")))
        assertEquals(1, ArtistCatalogMatcher.select(listOf(album(1, "A", type = "Album")), studioAndLive).ambiguousAlbums.size)
        assertEquals(1, ArtistCatalogMatcher.select(listOf(album(1, "A", type = "Album")), listOf(group("unknown", "A", secondary = null))).unmatchedCount)
    }

    @Test
    fun aCatalogOriginalTitleThatContainsEditionWordsStillWinsBeforeStripping() {
        val result = ArtistCatalogMatcher.select(
            listOf(album(1, "A (Super Deluxe)", type = "Album")),
            listOf(group("base", "A"), group("original", "A (Super Deluxe)"))
        )
        assertEquals("original", result.matches.single().group.id)
        assertEquals(ArtistCatalogTitleMatch.TITLE, result.matches.single().titleMatch)
    }

    @Test
    fun sourcePrimaryTypeDoesNotBypassTheBoundArtistCredit() {
        val result = ArtistCatalogMatcher.select(
            listOf(album(1, "A", type = "专辑")),
            listOf(group("wrong", "A", artist = "someone-else"), group("right-single", "A", primary = "Single")),
            boundArtistMbid = "correct-artist"
        )
        assertTrue(result.albums.isEmpty())
        assertEquals(1, result.excludedCount)
    }

    @Test
    fun albumChronologyUsesTheCatalogOriginalReleaseRatherThanTheStreamingRemasterDate() {
        val result = ArtistCatalogMatcher.select(
            listOf(album(1, "Old (Super Deluxe)", "2025-01-01"), album(2, "New", "2017-01-01"), album(3, "Undated")),
            listOf(group("old", "Old", date = "1987-03-09"), group("new", "New", date = "2017-12-01"), group("undated", "Undated", date = "invalid"))
        )
        assertEquals(listOf(2L, 1L, 3L), result.albums.map { it.id })
        assertEquals(result.albums.map { it.id }, result.matches.map { it.album.id })
    }

    @Test
    fun actualAllThatYouCannotLeaveBehindAnniversaryNameCollapsesWithoutSubstringGuessing() {
        val result = ArtistCatalogMatcher.select(listOf(
            album(97578742, "All That You Can’t Leave Behind (20th Anniversary Edition / Super Deluxe / Remastered 2020)", "2000-10-30", 51, "专辑"),
            album(2, "All That You Can’t Leave Behind (Live)", type = "专辑"),
            album(3, "All That You Can’t Leave Behind Instrumentals", type = "专辑")
        ), listOf(group("88c59b55-200d-3ac1-beff-8eb99830807e", "All That You Can’t Leave Behind", date = "2000-10-30")))
        assertEquals(listOf(97578742L), result.albums.map { it.id })
        assertEquals(2, result.unmatchedCount)
    }

    @Test
    fun completeU2CatalogReplayKeepsAllFourteenIssuedOrdinaryAlbumsAndAccountsForEverySourceRecord() {
        // Public metadata captured from the complete NetEase and MusicBrainz responses on
        // 2026-10-15. The fixture deliberately contains the singles, interviews and live
        // groups that collided with the classic albums; it is not a studio-only whitelist.
        val fixtureText = requireNotNull(javaClass.getResourceAsStream("/catalog/u2-full-replay-2026-10-15.json"))
            .bufferedReader().use { it.readText() }
        val fixture = Json { ignoreUnknownKeys = true }.decodeFromString<U2Replay>(fixtureText)
        assertEquals(104493L, fixture.artistId)
        assertEquals(164, fixture.albums.size)
        assertEquals(203, fixture.groups.size)
        val result = ArtistCatalogMatcher.select(fixture.albums, fixture.groups, boundArtistMbid = fixture.artistMbid)
        assertEquals(setOf(
            "Boy", "October", "War", "The Unforgettable Fire", "The Joshua Tree", "Achtung Baby", "Zooropa", "Pop",
            "All That You Can’t Leave Behind", "How to Dismantle an Atomic Bomb", "No Line on the Horizon",
            "Songs of Innocence", "Songs of Experience", "Songs of Surrender"
        ), result.matches.map { it.group.title }.toSet())
        assertEquals(14, result.matchedCount)
        assertTrue(result.albums.any { it.id == 2021179L })
        assertTrue(result.albums.any { it.id == 2021143L })
        assertFalse(result.albums.any { it.id == 2021187L }) // Rattle and Hum has catalog Soundtrack secondary types.
        assertTrue(result.excludedAlbums.any { it.id == 2021187L })
        assertEquals(164, result.matchedCount + result.duplicateCount + result.excludedCount + result.unmatchedCount)
        assertEquals(164, (result.albums + result.duplicateAlbums + result.excludedAlbums + result.unmatchedAlbums + result.ambiguousAlbums)
            .map { it.id }.toSet().size)
        assertTrue(result.unmatchedReleaseGroups.any { it.title == "Carnaval de Luz" }) // Announced for 2026-11-13, absent online.
    }

    @Serializable
    private data class U2Replay(
        val artistId: Long,
        val artistMbid: String,
        val albums: List<ArtistAlbum>,
        val groups: List<ArtistCatalogReleaseGroup>
    )
}

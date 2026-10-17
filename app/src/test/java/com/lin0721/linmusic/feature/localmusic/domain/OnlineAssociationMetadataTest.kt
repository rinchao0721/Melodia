package com.lin0721.linmusic.feature.localmusic.domain

import com.lin0721.linmusic.core.model.Album
import com.lin0721.linmusic.core.model.Artist
import com.lin0721.linmusic.core.model.SongPrivilege
import com.lin0721.linmusic.core.model.Track
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class OnlineAssociationMetadataTest {
    private val artist = Artist(id = 10, name = "U2", picUrl = "cached-artist")
    private val cached = Track(id = 22004078L, name = "Beautiful Day", ar = listOf(artist),
        al = Album(id = 20, name = "All That You Can't Leave Behind", picUrl = "cached-cover",
            artists = listOf(artist), publishTime = 123L), dt = 248_000L, fee = 8,
        privilege = SongPrivilege(dlLevel = "lossless", st = -1), publishTime = 456L)

    @Test fun sparseOfflinePlayerTrackGetsAlbumArtistAndDurationFromItsCachedOnlineRecord() {
        val primary = Track(id = cached.id, name = cached.name)
        val result = onlineAssociationMetadata(primary, cached)
        assertEquals(primary.id, result.id)
        assertEquals(primary.name, result.name)
        assertEquals(cached.ar, result.ar)
        assertEquals(cached.al, result.al)
        assertEquals(cached.dt, result.dt)
        assertEquals(primary.fee, result.fee)
        assertEquals(primary.privilege, result.privilege)
        assertEquals(primary.publishTime, result.publishTime)
    }

    @Test fun allPopulatedPrimaryFieldsAndPlaybackPermissionsRemainAuthoritative() {
        val primary = Track(id = cached.id, name = "Primary title", ar = listOf(Artist(id = 30, name = "Primary artist")),
            al = Album(id = 40, name = "Primary album", picUrl = "primary-cover",
                artists = listOf(Artist(name = "Album artist")), publishTime = 789L), dt = 250_000L,
            fee = 4, privilege = SongPrivilege(plLevel = "hires", st = 0), publishTime = 999L)
        assertEquals(primary, onlineAssociationMetadata(primary, cached))
    }

    @Test fun anotherSongAndNonpositiveSongIdsCannotSupplyMetadata() {
        val primary = Track(id = cached.id)
        assertSame(primary, onlineAssociationMetadata(primary, cached.copy(id = cached.id + 1)))
        assertSame(primary, onlineAssociationMetadata(primary, null))
        listOf(0L, -1L).forEach { id ->
            val invalid = primary.copy(id = id)
            assertSame(invalid, onlineAssociationMetadata(invalid, cached.copy(id = id)))
        }
    }

    @Test fun blankTitleAndExplicitUnknownAlbumOrArtistLabelsCanBeFilled() {
        listOf("", "   ", "未知专辑", "UNKNOWN", " <unknown> ", "Unknown Album", "ｕｎｋｎｏｗｎ").forEach { album ->
            val primary = Track(id = cached.id, name = "  ", al = Album(name = album),
                ar = listOf(Artist(name = "<unknown>"), Artist(name = "未知艺术家")))
            val result = onlineAssociationMetadata(primary, cached)
            assertEquals(album, cached.name, result.name)
            assertEquals(album, cached.al.name, result.al.name)
            assertEquals(album, cached.ar, result.ar)
        }
    }

    @Test fun partialAlbumFieldsAreFilledIndividuallyAndOneValidPrimaryArtistKeepsItsCredit() {
        val primary = Track(id = cached.id, name = "Known title", ar = listOf(Artist(name = ""), Artist(name = "Known artist")),
            al = Album(name = "Known primary album", picUrl = " "), dt = -1L)
        val result = onlineAssociationMetadata(primary, cached)
        assertEquals(primary.name, result.name)
        assertEquals(primary.ar, result.ar)
        assertEquals(primary.al.name, result.al.name)
        assertEquals(cached.al.id, result.al.id)
        assertEquals(cached.al.picUrl, result.al.picUrl)
        assertEquals(cached.al.artists, result.al.artists)
        assertEquals(cached.al.publishTime, result.al.publishTime)
        assertEquals(cached.dt, result.dt)
    }

    @Test fun incompleteCacheDoesNotEraseUsablePrimaryMetadataOrInventPositiveValues() {
        val primary = Track(id = cached.id, name = "Known title", ar = listOf(artist),
            al = Album(id = 20, name = "Known album", picUrl = "cover"), dt = 245_000L)
        assertEquals(primary, onlineAssociationMetadata(primary, Track(id = primary.id)))
        val unknown = Track(id = cached.id, al = Album(name = "未知专辑"), ar = listOf(Artist(name = "未知歌手")))
        assertEquals(unknown, onlineAssociationMetadata(unknown, Track(id = unknown.id)))
    }
}

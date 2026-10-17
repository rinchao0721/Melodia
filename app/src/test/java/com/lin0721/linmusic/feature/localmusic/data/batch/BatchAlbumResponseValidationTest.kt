package com.lin0721.linmusic.feature.localmusic.data.batch

import com.lin0721.linmusic.core.model.Album
import com.lin0721.linmusic.core.model.Track
import com.lin0721.linmusic.core.network.AppError
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Test

class BatchAlbumResponseValidationTest {
    private val albumId = 42L
    private val track = Track(id = 101L, name = "One", al = Album(id = albumId))
    private fun response(vararg songs: Track) = BatchAlbumDetailResponse(
        code = 200, album = BatchAlbumInfo(id = albumId, name = "Studio album"), songs = songs.toList())

    @Test fun foreignAlbumTrackCannotReachAutomaticMatching() {
        val foreign = track.copy(al = Album(id = 99L, name = "Different album"))
        assertSame(AppError.ParseError, assertThrows(AppError.ParseError::class.java) {
            validateBatchAlbumResponse(response(track, foreign.copy(id = 102L)), albumId)
        })
    }

    @Test fun missingAlbumIdAndMatchingAlbumIdRemainValid() {
        validateBatchAlbumResponse(response(track, track.copy(id = 102L, al = Album())), albumId)
    }

    @Test fun correctSongMetadataCannotHideWrongResponseAlbum() {
        val wrongAlbum = response(track).copy(album = BatchAlbumInfo(id = 99L, name = "Studio album"))
        assertSame(AppError.ParseError, assertThrows(AppError.ParseError::class.java) {
            validateBatchAlbumResponse(wrongAlbum, albumId)
        })
    }

    @Test fun conflictingDuplicateSongIdIsRejectedWithoutChoosingOneVersion() {
        assertSame(AppError.ParseError, assertThrows(AppError.ParseError::class.java) {
            validateBatchAlbumResponse(response(track, track.copy(name = "One (Live)", dt = 500_000L)), albumId)
        })
    }

    @Test fun identicalRepeatsRemainIntactForLaterCompletenessChecks() {
        val repeated = response(track, track)
        validateBatchAlbumResponse(repeated, albumId)
        assertEquals(2, repeated.songs.size)
    }

    @Test fun businessErrorSurvivesEvenWhenOtherResponseFieldsAreAbsent() {
        val error = assertThrows(AppError.BizError::class.java) {
            validateBatchAlbumResponse(BatchAlbumDetailResponse(code = 512), albumId)
        }
        assertEquals(512, error.code)
    }
}

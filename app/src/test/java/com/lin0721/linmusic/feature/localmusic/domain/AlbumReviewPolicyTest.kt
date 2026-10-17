package com.lin0721.linmusic.feature.localmusic.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AlbumReviewPolicyTest {
    private val reviewed = "a".repeat(64)
    private val changed = "b".repeat(64)

    @Test
    fun `manual confirmation completes album even with no saved song references`() {
        assertEquals(AlbumCompletion.MANUALLY_REVIEWED,
            AlbumReviewPolicy.completion(reviewed, reviewed, linkedSongs = 0, totalSongs = 12))
    }

    @Test
    fun `legacy partial scope and candidate preview do not imply human confirmation`() {
        assertEquals(AlbumCompletion.NONE,
            AlbumReviewPolicy.completion(reviewed, null, linkedSongs = 9, totalSongs = 12))
    }

    @Test
    fun `completed song coverage still skips albums without a human confirmation`() {
        assertEquals(AlbumCompletion.ALL_SONGS_LINKED,
            AlbumReviewPolicy.completion(reviewed, null, linkedSongs = 12, totalSongs = 12))
    }

    @Test
    fun `changed local content invalidates earlier human confirmation`() {
        assertEquals(AlbumCompletion.NONE,
            AlbumReviewPolicy.completion(changed, reviewed, linkedSongs = 9, totalSongs = 12))
    }

    @Test
    fun `empty album is not automatically considered complete`() {
        assertEquals(AlbumCompletion.NONE,
            AlbumReviewPolicy.completion(reviewed, null, linkedSongs = 0, totalSongs = 0))
    }

    @Test
    fun `failed saves and cancellation cannot create manual completion`() {
        assertFalse(AlbumReviewPolicy.canConfirm(reviewed, reviewed, stopped = true, saveFailed = false))
        assertFalse(AlbumReviewPolicy.canConfirm(reviewed, reviewed, stopped = false, saveFailed = true))
        assertFalse(AlbumReviewPolicy.canConfirm(reviewed, reviewed, stopped = true, saveFailed = true))
    }

    @Test
    fun `removed or changed album cannot complete an old review session`() {
        assertFalse(AlbumReviewPolicy.canConfirm(reviewed, null, stopped = false, saveFailed = false))
        assertFalse(AlbumReviewPolicy.canConfirm(reviewed, changed, stopped = false, saveFailed = false))
    }

    @Test
    fun `successful unchanged review can be recorded without requiring all songs to match`() {
        assertTrue(AlbumReviewPolicy.canConfirm(reviewed, reviewed, stopped = false, saveFailed = false))
    }
}

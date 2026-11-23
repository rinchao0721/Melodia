package com.lin0721.linmusic.core.player

import com.lin0721.linmusic.core.platform.TrackId
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CurrentTrackReuseTest {

    private fun item(songId: Long, trackId: TrackId? = null) =
        QueueItem(songId = songId, title = "t", artist = "a", coverUrl = "", trackId = trackId)

    private fun reuse(
        loadedMediaId: String? = "100",
        loadedItem: QueueItem? = item(100),
        isLoaded: Boolean = true,
        isPendingSwitch: Boolean = false,
        target: QueueItem? = item(100),
        playContext: String? = null,
        startPositionMs: Long = 0L
    ) = canReuseLoadedTrack(loadedMediaId, loadedItem, isLoaded, isPendingSwitch, target, playContext, startPositionMs)

    @Test
    fun `same loaded track is reused`() {
        assertTrue(reuse())
    }

    @Test
    fun `different song is not reused`() {
        assertFalse(reuse(target = item(101)))
    }

    @Test
    fun `same numeric id on another platform is not reused`() {
        val kugou = TrackId(com.lin0721.linmusic.core.source.MusicPlatform.KUGOU, "100")
        assertFalse(reuse(target = item(100, kugou)))
    }

    @Test
    fun `not loaded or pending switch is not reused`() {
        assertFalse(reuse(isLoaded = false))
        assertFalse(reuse(isPendingSwitch = true))
    }

    @Test
    fun `personal fm and explicit resume position keep the full flow`() {
        assertFalse(reuse(playContext = PlaybackController.CONTEXT_PERSONAL_FM))
        assertFalse(reuse(startPositionMs = 5_000L))
    }

    @Test
    fun `missing loaded item or target is not reused`() {
        assertFalse(reuse(loadedItem = null))
        assertFalse(reuse(target = null))
        assertFalse(reuse(loadedMediaId = null))
    }
}

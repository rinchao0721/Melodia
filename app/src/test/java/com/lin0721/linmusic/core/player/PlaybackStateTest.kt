package com.lin0721.linmusic.core.player

import android.net.Uri
import androidx.media3.common.MediaItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class PlaybackStateTest {

    @Test
    fun `PlaybackState 默认值与 durationMs 正确初始化`() {
        val defaultState = PlaybackState()
        assertEquals(-1L, defaultState.songId)
        assertEquals("", defaultState.title)
        assertEquals(0L, defaultState.lastPositionMs)
        assertEquals(0L, defaultState.durationMs)

        val customState = PlaybackState(
            songId = 1001L,
            title = "测试歌曲",
            artist = "测试歌手",
            coverUrl = "https://example.com/cover.jpg",
            lastPositionMs = 45000L,
            durationMs = 180000L
        )
        assertEquals(1001L, customState.songId)
        assertEquals(45000L, customState.lastPositionMs)
        assertEquals(180000L, customState.durationMs)
    }

    @Test
    fun `RestoredTrack 携带断点进度与总时长`() {
        val mediaItem = MediaItem.Builder()
            .setMediaId("2002")
            .build()

        val restored = RestoredTrack(mediaItem, positionMs = 30000L, durationMs = 240000L)
        assertEquals("2002", restored.mediaItem.mediaId)
        assertEquals(30000L, restored.positionMs)
        assertEquals(240000L, restored.durationMs)
    }
}

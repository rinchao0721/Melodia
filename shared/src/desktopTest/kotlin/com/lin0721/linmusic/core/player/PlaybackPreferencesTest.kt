package com.lin0721.linmusic.core.player

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.nio.file.Files
import com.lin0721.linmusic.core.preferences.PreferencesStores

class PlaybackPreferencesTest {

    private lateinit var dir: File
    private lateinit var preferences: PlaybackPreferences

    @Before
    fun setUp() {
        dir = Files.createTempDirectory("melodia-playback-prefs-test").toFile()
        preferences = PlaybackPreferences(PreferencesStores.get(File(dir, "playback.preferences_pb")))
    }

    @After
    fun tearDown() {
        dir.deleteRecursively()
    }

    private fun fmTrack(id: Long, cover: String = "cover/$id", title: String = "歌曲$id") =
        RecentFmTrack(songId = id, coverUrl = cover, title = title)

    @Test
    fun `保存与读取最近FM曲目并最多保留3首`() = runBlocking {
        assertTrue(preferences.recentFmTracks.first().isEmpty())

        preferences.saveRecentFmTracks(
            listOf(fmTrack(1), fmTrack(2), fmTrack(3), fmTrack(4))
        )

        assertEquals(
            listOf(fmTrack(1), fmTrack(2), fmTrack(3)),
            preferences.recentFmTracks.first()
        )
    }

    @Test
    fun `不足3首时按实际数量保存且追加新曲目后截断为3首`() = runBlocking {
        preferences.saveRecentFmTracks(listOf(fmTrack(10), fmTrack(20)))
        assertEquals(listOf(fmTrack(10), fmTrack(20)), preferences.recentFmTracks.first())

        preferences.saveRecentFmTracks(listOf(fmTrack(30), fmTrack(40)))
        assertEquals(
            listOf(fmTrack(30), fmTrack(40), fmTrack(10)),
            preferences.recentFmTracks.first()
        )
    }

    @Test
    fun `同songId去重并保留最新顺序与封面`() = runBlocking {
        preferences.saveRecentFmTracks(listOf(fmTrack(1, "old/1"), fmTrack(2), fmTrack(3)))
        preferences.saveRecentFmTracks(
            listOf(fmTrack(0, "invalid"), fmTrack(2, "new/2"), fmTrack(2, "dup/2"), fmTrack(4))
        )

        assertEquals(
            listOf(fmTrack(2, "new/2"), fmTrack(4), fmTrack(1, "old/1")),
            preferences.recentFmTracks.first()
        )
    }
}

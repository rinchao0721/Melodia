package com.lin0721.linmusic.desktop.data.local

import com.lin0721.linmusic.core.model.Artist
import com.lin0721.linmusic.core.model.Track
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalMatcherTest {

    private fun local(title: String, artist: String, durationMs: Long = 200_000L) =
        LocalTrack(path = "C:\\music\\a.mp3", title = title, artist = artist, durationMs = durationMs)

    private fun remote(id: Long, name: String, vararg artists: String, dt: Long = 200_000L) =
        Track(id = id, name = name, ar = artists.map { Artist(name = it) }, dt = dt)

    @Test
    fun normalizeIgnoresCaseWidthAndPunctuation() {
        assertEquals(LocalMatcher.normalize("Hello, World!"), LocalMatcher.normalize("ＨＥＬＬＯ　ｗｏｒｌｄ"))
        assertEquals("晴天", LocalMatcher.normalize(" 晴天 "))
    }

    @Test
    fun splitArtistsHandlesCommonSeparators() {
        assertEquals(setOf("周杰伦", "费玉清"), LocalMatcher.splitArtists("周杰伦/费玉清"))
        assertEquals(setOf("a", "b", "c"), LocalMatcher.splitArtists("A feat. B & C"))
    }

    @Test
    fun picksFirstCandidateWithSameTitleArtistAndCloseDuration() {
        val candidates = listOf(
            remote(1, "晴天 (Live)", "周杰伦"),
            remote(2, "晴天", "翻唱歌手"),
            remote(3, "晴天", "周杰伦", dt = 210_000L),
            remote(4, "晴天", "周杰伦", dt = 201_000L)
        )
        assertEquals(4L, LocalMatcher.pickMatch(local("晴天", "周杰伦"), candidates)?.id)
    }

    @Test
    fun unknownDurationSkipsDurationCheck() {
        val match = LocalMatcher.pickMatch(local("晴天", "周杰伦", durationMs = 0), listOf(remote(7, "晴天", "周杰伦", dt = 999_000L)))
        assertEquals(7L, match?.id)
    }

    @Test
    fun noMatchWhenArtistDiffers() {
        assertNull(LocalMatcher.pickMatch(local("晴天", "周杰伦"), listOf(remote(1, "晴天", "五月天"))))
    }

    @Test
    fun unknownArtistIsNotMatchable() {
        assertFalse(LocalMatcher.isMatchable(local("晴天", UNKNOWN_ARTIST)))
        assertFalse(LocalMatcher.isMatchable(local("!!!", "周杰伦")))
        assertTrue(LocalMatcher.isMatchable(local("晴天", "周杰伦")))
    }
}

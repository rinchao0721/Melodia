package com.lin0721.linmusic.core.player.data

import org.junit.Test
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue

class InstrumentalLyricsTest {
    @Test
    fun recordingCreditsDoNotHideFlareLyrics() {
        val lyrics = """
            {"t":212019,"c":[{"tx":"乐器录音师 Instrumental Recording Engineer：沈锦天"}]}
            [00:14.614]I feel the pain and anger, wicked monster, losin' control
            [00:17.950]I'm ready for a battle
        """.trimIndent()
        assertFalse(isInstrumentalLyrics(lyrics))
    }

    @Test
    fun instrumentalWordInSungLyricsDoesNotHideTheSong() {
        assertFalse(isInstrumentalLyrics("[00:01.00]Instrumental in my life"))
        assertFalse(isInstrumentalLyrics("[00:01.00]Instrumental\n[00:02.00]Sung lyrics"))
    }

    @Test
    fun standaloneInstrumentalMarkersAreRecognized() {
        assertTrue(isInstrumentalLyrics("[00:00.00]纯音乐，请欣赏"))
        assertTrue(isInstrumentalLyrics("[00:00.00]（纯音乐，请欣赏）"))
        assertTrue(isInstrumentalLyrics("[00:00.00] [ Instrumental ] "))
        assertTrue(isInstrumentalLyrics("[00:00.00]Instrumental"))
        assertTrue(isInstrumentalLyrics("[0,1000](0,1000,0)纯音乐"))
        assertFalse(isInstrumentalLyrics(null))
        assertFalse(isInstrumentalLyrics(""))
    }
}

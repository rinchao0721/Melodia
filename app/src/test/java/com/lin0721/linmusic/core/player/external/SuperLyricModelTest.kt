package com.lin0721.linmusic.core.player.external

import com.hchen.superlyricapi.SuperLyricData
import com.hchen.superlyricapi.SuperLyricLine
import com.hchen.superlyricapi.SuperLyricWord
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class SuperLyricModelTest {

    @Test
    fun `SuperLyricWord 构造与属性正确`() {
        val word = SuperLyricWord("测试", 1000L, 1500L)
        assertEquals("测试", word.word)
        assertEquals(1000L, word.startTime)
        assertEquals(1500L, word.endTime)
        assertEquals(500L, word.delay)
    }

    @Test
    fun `SuperLyricLine 构造与包含逐字数组正确`() {
        val words = arrayOf(
            SuperLyricWord("Hello", 1000L, 1400L),
            SuperLyricWord("World", 1500L, 2000L)
        )
        val line = SuperLyricLine("Hello World", words, 1000L, 2000L)
        assertEquals("Hello World", line.text)
        assertEquals(1000L, line.startTime)
        assertEquals(2000L, line.endTime)
        assertEquals(2, line.words?.size)
        assertEquals("Hello", line.words?.get(0)?.word)
        assertEquals("World", line.words?.get(1)?.word)
    }

    @Test
    fun `SuperLyricData 链式赋值与读取正确`() {
        val line = SuperLyricLine("Line 1", null, 0L, 1000L)
        val trans = SuperLyricLine("Line 1 Trans", null, 0L, 1000L)
        val roma = SuperLyricLine("Line 1 Roma", null, 0L, 1000L)

        val data = SuperLyricData()
            .setTitle("Song Title")
            .setArtist("Artist Name")
            .setAlbum("Album Name")
            .setLyric(line)
            .setTranslation(trans)
            .setSecondary(roma)

        assertEquals("Song Title", data.title)
        assertEquals("Artist Name", data.artist)
        assertEquals("Album Name", data.album)
        assertNotNull(data.lyric)
        assertEquals("Line 1", data.lyric?.text)
        assertNotNull(data.translation)
        assertEquals("Line 1 Trans", data.translation?.text)
        assertNotNull(data.secondary)
        assertEquals("Line 1 Roma", data.secondary?.text)
        assertNull(data.base64Icon)
    }
}

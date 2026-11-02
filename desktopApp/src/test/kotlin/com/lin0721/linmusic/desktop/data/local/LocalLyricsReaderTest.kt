package com.lin0721.linmusic.desktop.data.local

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import java.io.File
import java.nio.charset.Charset
import java.nio.file.Files

class LocalLyricsReaderTest {

    private lateinit var dir: File

    @Before
    fun setUp() {
        dir = Files.createTempDirectory("melodia-lyrics-test").toFile()
    }

    @After
    fun tearDown() {
        dir.deleteRecursively()
    }

    @Test
    fun decodesUtf8WithAndWithoutBom() {
        val text = "[00:01.00]你好"
        assertEquals(text, LocalLyricsReader.decode(text.toByteArray(Charsets.UTF_8)))
        val bom = byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte()) + text.toByteArray(Charsets.UTF_8)
        assertEquals(text, LocalLyricsReader.decode(bom))
    }

    @Test
    fun fallsBackToGb18030ForGbkFiles() {
        val text = "[00:01.00]晴天"
        assertEquals(text, LocalLyricsReader.decode(text.toByteArray(Charset.forName("GBK"))))
    }

    @Test
    fun decodesUtf16LittleEndianWithBom() {
        val text = "[00:01.00]歌词"
        val bytes = byteArrayOf(0xFF.toByte(), 0xFE.toByte()) + text.toByteArray(Charsets.UTF_16LE)
        assertEquals(text, LocalLyricsReader.decode(bytes))
    }

    @Test
    fun readsSidecarIgnoringExtensionCase() {
        val audio = File(dir, "song.flac").apply { writeBytes(byteArrayOf(1, 2, 3)) }
        File(dir, "song.LRC").writeText("[00:00.00]line", Charsets.UTF_8)
        assertEquals("[00:00.00]line", LocalLyricsReader.read(audio.path))
    }

    @Test
    fun returnsNullWithoutLyrics() {
        val audio = File(dir, "plain.mp3").apply { writeBytes(byteArrayOf(1, 2, 3)) }
        assertNull(LocalLyricsReader.read(audio.path))
        assertNull(LocalLyricsReader.read(File(dir, "missing.mp3").path))
    }
}

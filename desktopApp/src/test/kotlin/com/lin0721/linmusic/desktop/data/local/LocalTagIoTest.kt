package com.lin0721.linmusic.desktop.data.local

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.File
import java.nio.file.Files
import javax.sound.sampled.AudioFileFormat
import javax.sound.sampled.AudioFormat
import javax.sound.sampled.AudioInputStream
import javax.sound.sampled.AudioSystem

class LocalTagIoTest {

    private lateinit var dir: File

    @Before
    fun setUp() {
        dir = Files.createTempDirectory("melodia-tag-test").toFile()
    }

    @After
    fun tearDown() {
        dir.deleteRecursively()
    }

    // 两秒静音 WAV
    private fun silentWav(name: String): File {
        val format = AudioFormat(8000f, 16, 1, true, false)
        val frames = 16000L
        val bytes = ByteArray((frames * format.frameSize).toInt())
        val file = File(dir, name)
        AudioInputStream(ByteArrayInputStream(bytes), format, frames).use { AudioSystem.write(it, AudioFileFormat.Type.WAVE, file) }
        return file
    }

    @Test
    fun writesAndReadsBackTags() {
        val file = silentWav("song.wav")
        val edit = LocalTagEdit(title = "晴天", artist = "周杰伦", album = "叶惠美", year = "2003", trackNumber = "3/11", lyrics = "")
        val result = LocalTagIo.write(file, edit, CoverChange.Keep)
        assertTrue(result.exceptionOrNull()?.toString() ?: "", result.isSuccess)

        val read = LocalTagIo.read(file)
        assertEquals("晴天", read.title)
        assertEquals("周杰伦", read.artist)
        assertEquals("叶惠美", read.album)
        assertEquals(2003, read.year)
        assertEquals(3, read.trackNumber)
        assertTrue(read.durationMs in 1500L..2500L)
        assertEquals("3/11", LocalTagIo.readEditable(file)?.trackNumber)
    }

    @Test
    fun blankFieldsAreRemoved() {
        val file = silentWav("歌手 - 标题.wav")
        LocalTagIo.write(file, LocalTagEdit("临时", "某人", "专辑", "", "", ""), CoverChange.Keep).getOrThrow()
        LocalTagIo.write(file, LocalTagEdit("临时", "", "", "", "", ""), CoverChange.Keep).getOrThrow()
        val read = LocalTagIo.read(file)
        assertEquals("", read.album)
        // 歌手被清空后回退到文件名推断
        assertEquals("歌手", read.artist)
        assertNull(read.artwork)
    }
}

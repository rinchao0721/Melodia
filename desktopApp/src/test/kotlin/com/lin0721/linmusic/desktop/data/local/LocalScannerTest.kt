package com.lin0721.linmusic.desktop.data.local

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Before
import org.junit.Test
import java.io.File
import java.nio.file.Files

class LocalScannerTest {

    private lateinit var dir: File
    private lateinit var scanner: LocalScanner

    @Before
    fun setUp() {
        dir = Files.createTempDirectory("melodia-local-scan").toFile()
        scanner = LocalScanner { File(dir, "covers") }
    }

    @After
    fun tearDown() {
        dir.deleteRecursively()
    }

    private fun audio(relative: String): File =
        File(dir, relative).apply {
            parentFile.mkdirs()
            writeBytes(ByteArray(16) { it.toByte() })
        }

    @Test
    fun collectsAudioFilesAndSkipsExcludedFolders() {
        audio("lib/a.mp3")
        audio("lib/sub/b.FLAC")
        audio("lib/skip/c.mp3")
        File(dir, "lib/cover.jpg").writeBytes(byteArrayOf(1))
        val files = scanner.collectFiles(File(dir, "lib").path, listOf(normalizePath(File(dir, "lib/skip").path)))
        assertEquals(setOf("a.mp3", "b.FLAC"), files.map { File(it).name }.toSet())
    }

    @Test
    fun untaggableFileFallsBackToFileName() {
        val file = audio("lib/周杰伦 - 晴天.ape")
        val track = scanner.refresh(normalizePath(file.path), null)!!
        assertEquals("晴天", track.title)
        assertEquals("周杰伦", track.artist)
        assertEquals(LocalMatchState.PENDING, track.matchState)
    }

    @Test
    fun unchangedFileReusesPreviousRecord() {
        val file = audio("lib/a.mp3")
        val path = normalizePath(file.path)
        val first = scanner.refresh(path, null)!!.copy(songId = 9, matchState = LocalMatchState.MANUAL)
        assertSame(first, scanner.refresh(path, first))
    }

    @Test
    fun changedFileResetsAutomaticMatchOnly() {
        val file = audio("lib/歌手 - 新标题.wv")
        val path = normalizePath(file.path)
        val auto = LocalTrack(path, "旧标题", "歌手", lastModified = 1, songId = 5, matchState = LocalMatchState.AUTO)
        val refreshed = scanner.refresh(path, auto)!!
        assertEquals(LocalMatchState.PENDING, refreshed.matchState)
        assertNull(refreshed.songId)

        val manual = auto.copy(matchState = LocalMatchState.MANUAL)
        assertEquals(5L, scanner.refresh(path, manual)!!.songId)
    }

    @Test
    fun coverUriUsesTripleSlashFileScheme() {
        val track = LocalTrack(normalizePath(File(dir, "a.mp3").path), "a", "b", lastModified = 1, hasCover = true)
        assertEquals(true, scanner.coverUri(track)?.startsWith("file:///"))
        assertNull(scanner.coverUri(track.copy(hasCover = false)))
    }

    @Test
    fun missingFileReturnsNull() {
        assertNull(scanner.refresh(normalizePath(File(dir, "nope.mp3").path), null))
    }

    @Test
    fun guessesArtistAndTitleFromFileName() {
        assertEquals("A" to "B - C", LocalTagIo.guessFromFileName(File("A - B - C.mp3")))
        assertEquals(null to "Only", LocalTagIo.guessFromFileName(File("Only.mp3")))
    }
}

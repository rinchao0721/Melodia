package com.lin0721.linmusic.desktop.data.local

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.nio.file.Files

class LocalLibraryStoreTest {

    private lateinit var dir: File

    @Before
    fun setUp() {
        dir = Files.createTempDirectory("melodia-local-store").toFile()
    }

    @After
    fun tearDown() {
        dir.deleteRecursively()
    }

    @Test
    fun roundTripsLibrary() {
        val store = LocalLibraryStore(File(dir, "lib.json"))
        val data = LocalLibraryData(
            playlists = listOf(LocalPlaylist(id = 3, name = "本地", createdAt = 7, folders = listOf("D:\\Music"))),
            tracks = listOf(LocalTrack("D:\\Music\\a.mp3", "a", "b", songId = 11, matchState = LocalMatchState.AUTO)),
            filter = LocalScanFilter(minDurationSec = 30, excludedFolders = listOf("D:\\Music\\tmp")),
            builtins = listOf(LocalBuiltinState(LOCAL_DOWNLOADS_ID, isPinned = true))
        )
        store.save(data)
        assertEquals(data, store.load())
        assertTrue(File(dir, "lib.json.tmp").exists().not())
    }

    @Test
    fun missingFileLoadsEmptyLibrary() {
        assertEquals(LocalLibraryData(), LocalLibraryStore(File(dir, "none.json")).load())
    }

    @Test
    fun corruptFileIsBackedUpAndReset() {
        val file = File(dir, "lib.json").apply { writeText("{not json") }
        assertEquals(LocalLibraryData(), LocalLibraryStore(file).load())
        assertEquals("{not json", File(dir, "lib.json.broken").readText())
    }
}

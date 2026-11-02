package com.lin0721.linmusic.desktop.player.cache

import java.io.File
import java.nio.file.Files
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class AudioCacheTest {

    private lateinit var dir: File

    @Before
    fun setUp() {
        dir = Files.createTempDirectory("melodia-audio-cache-test").toFile()
    }

    @After
    fun tearDown() {
        dir.deleteRecursively()
    }

    private fun file(name: String, size: Int, modified: Long): File =
        File(dir, name).apply {
            writeBytes(ByteArray(size))
            setLastModified(modified)
        }

    @Test
    fun commitRenamesTempAndMakesItPlayable() {
        val cache = AudioCache(dir)
        val temp = cache.tempFile(7, "lossless").apply { writeBytes(ByteArray(100)) }

        assertNull(cache.completeFile(7, "lossless"))
        assertTrue(runBlocking { cache.commit(temp) })

        assertFalse(temp.exists())
        assertNotNull(cache.completeFile(7, "lossless"))
        assertNull("不同音质不命中", cache.completeFile(7, "standard"))
        assertEquals(setOf(7L), cache.playableIds(listOf(7, 8)))
    }

    @Test
    fun emptyTempIsRejected() {
        val cache = AudioCache(dir)
        val temp = cache.tempFile(1, "standard").apply { writeBytes(ByteArray(0)) }
        assertFalse(runBlocking { cache.commit(temp) })
        assertNull(cache.completeFile(1, "standard"))
    }

    @Test
    fun tempFilesAreInvisibleAndCleanedOnStart() {
        val cache = AudioCache(dir)
        cache.tempFile(2, "standard").writeBytes(ByteArray(10))
        assertTrue(cache.playableIds(listOf(2)).isEmpty())
        assertEquals(0L, cache.totalSize())

        AudioCache(dir)
        assertFalse(cache.tempFile(2, "standard").exists())
    }

    @Test
    fun hitRefreshesModifiedTime() {
        val cache = AudioCache(dir)
        val hit = file("1_standard.mka", 100, 1_000)
        file("2_standard.mka", 100, 2_000)

        assertNotNull(cache.completeFile(1, "standard"))

        assertTrue(hit.lastModified() > 2_000)
        assertEquals(setOf("1_standard.mka", "2_standard.mka"), cache.completeFiles().map { it.name }.toSet())
    }

    @Test
    fun anyCompleteFilePicksMostRecentAndMatchesWholeId() {
        val cache = AudioCache(dir)
        file("12_standard.mka", 10, 1_000)
        file("12_lossless.mka", 10, 2_000)
        file("123_standard.mka", 10, 3_000)

        assertEquals("12_lossless.mka", cache.anyCompleteFile(12)?.name)
        assertEquals("123_standard.mka", cache.anyCompleteFile(123)?.name)
        assertNull(cache.anyCompleteFile(1))
    }

    @Test
    fun clearRemovesEverything() {
        val cache = AudioCache(dir)
        file("1_standard.mka", 10, 1_000)
        cache.clear()
        assertEquals(0L, cache.totalSize())
        assertTrue(cache.playableIds(listOf(1)).isEmpty())
    }
}

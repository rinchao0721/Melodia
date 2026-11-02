package com.lin0721.linmusic.desktop.data

import com.lin0721.linmusic.core.player.data.LyricsCache
import com.lin0721.linmusic.desktop.player.cache.AudioCache
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.nio.file.Files

class DesktopCacheManagerTest {

    private lateinit var dir: File
    private lateinit var audioDir: File
    private lateinit var lyricsDir: File
    private lateinit var metadataDir: File
    private var imageBytes = 0L
    private var imageCleared = false

    @Before
    fun setUp() {
        dir = Files.createTempDirectory("melodia-cache-manager-test").toFile()
        audioDir = File(dir, "audio_cache")
        lyricsDir = File(dir, "lyrics")
        metadataDir = File(dir, "meta_cache")
    }

    @After
    fun tearDown() {
        dir.deleteRecursively()
    }

    private fun manager() = DesktopCacheManager(
        audioCache = AudioCache(audioDir),
        lyricsCache = LyricsCache(dir),
        lyricsDir = lyricsDir,
        metadataDir = metadataDir,
        imageCacheSize = { imageBytes },
        clearImageCache = {
            imageCleared = true
            imageBytes = 0L
        }
    )

    private fun file(parent: File, name: String, size: Int, modified: Long): File =
        File(parent, name).apply {
            parentFile.mkdirs()
            writeBytes(ByteArray(size))
            setLastModified(modified)
        }

    @Test
    fun usageSumsAllCategoriesAndIgnoresTempFiles() {
        file(audioDir, "1_standard.mka", 100, 1_000)
        file(File(lyricsDir, "amll"), "1.ttml", 20, 1_000)
        file(File(lyricsDir, "amll"), "2.ttml.tmp", 999, 1_000)
        file(File(metadataDir, "7"), "playlist_1.json", 5, 1_000)
        file(File(metadataDir, "7"), "playlist_2.json.tmp", 999, 1_000)
        imageBytes = 300

        val usage = manager().usage()

        assertEquals(100L, usage.audio)
        assertEquals(300L, usage.image)
        assertEquals(20L, usage.lyrics)
        assertEquals(5L, usage.metadata)
        assertEquals(425L, usage.total)
    }

    @Test
    fun enforceEvictsOldestAcrossAudioLyricsAndMetadata() {
        file(audioDir, "1_standard.mka", 100, 3_000)
        file(File(lyricsDir, "amll"), "1.ttml", 100, 1_000)
        file(File(metadataDir, "7"), "playlist_1.json", 100, 2_000)

        manager().enforce(250)

        assertFalse(File(lyricsDir, "amll/1.ttml").exists())
        assertTrue(File(metadataDir, "7/playlist_1.json").exists())
        assertTrue(File(audioDir, "1_standard.mka").exists())
    }

    @Test
    fun enforceCountsImageButNeverEvictsIt() {
        file(audioDir, "1_standard.mka", 100, 1_000)
        file(audioDir, "2_standard.mka", 100, 2_000)
        imageBytes = 300

        manager().enforce(450)

        assertFalse(File(audioDir, "1_standard.mka").exists())
        assertTrue(File(audioDir, "2_standard.mka").exists())
        assertEquals(300L, imageBytes)
    }

    @Test
    fun enforceKeepsEverythingWhenWithinLimit() {
        file(audioDir, "1_standard.mka", 100, 1_000)
        file(File(lyricsDir, "amll"), "1.ttml", 100, 2_000)

        manager().enforce(200)

        assertTrue(File(audioDir, "1_standard.mka").exists())
        assertTrue(File(lyricsDir, "amll/1.ttml").exists())
    }

    @Test
    fun unlimitedNeverEvicts() {
        file(audioDir, "1_standard.mka", 100, 1_000)

        manager().enforce(CacheLimits.UNLIMITED)

        assertTrue(File(audioDir, "1_standard.mka").exists())
    }

    @Test
    fun audioHitRefreshesRecencySoItSurvivesEviction() {
        val manager = manager()
        file(audioDir, "1_standard.mka", 100, 1_000)
        file(audioDir, "2_standard.mka", 100, 2_000)
        AudioCache(audioDir).completeFile(1, "standard")

        manager.enforce(150)

        assertTrue(File(audioDir, "1_standard.mka").exists())
        assertFalse(File(audioDir, "2_standard.mka").exists())
    }

    @Test
    fun clearRemovesAudioLyricsAndImageButKeepsMetadata() {
        file(audioDir, "1_standard.mka", 100, 1_000)
        file(File(lyricsDir, "amll"), "1.ttml", 20, 1_000)
        val metadata = file(File(metadataDir, "7"), "playlist_1.json", 5, 1_000)
        imageBytes = 300

        val manager = manager()
        manager.clear()

        assertTrue(imageCleared)
        val usage = manager.usage()
        assertEquals(0L, usage.audio)
        assertEquals(0L, usage.lyrics)
        assertEquals(0L, usage.image)
        assertTrue(metadata.exists())
    }

    @Test
    fun limitOptionsEndWithUnlimitedAndDropTheOldMinimum() {
        assertEquals(CacheLimits.UNLIMITED, CacheLimits.options.last())
        assertEquals(1024L * 1024 * 1024, CacheLimits.options.first())
        assertEquals(5, CacheLimits.options.size)
    }

    @Test
    fun normalizeRaisesLegacySmallValuesAndKeepsUnlimited() {
        assertEquals(1024L * 1024 * 1024, CacheLimits.normalize(500L * 1024 * 1024))
        assertEquals(2048L * 1024 * 1024, CacheLimits.normalize(2048L * 1024 * 1024))
        assertEquals(CacheLimits.UNLIMITED, CacheLimits.normalize(CacheLimits.UNLIMITED))
    }
}

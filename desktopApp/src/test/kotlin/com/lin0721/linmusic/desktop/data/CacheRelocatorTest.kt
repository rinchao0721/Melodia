package com.lin0721.linmusic.desktop.data

import com.lin0721.linmusic.core.preferences.PreferencesStores
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.nio.file.Files

class CacheRelocatorTest {

    private lateinit var base: File
    private lateinit var defaultRoot: File
    private lateinit var selected: File
    private lateinit var preferences: DesktopPreferences

    @Before
    fun setUp() {
        base = Files.createTempDirectory("melodia-relocator-test").toFile()
        defaultRoot = File(base, "default_cache")
        selected = File(base, "selected").apply { mkdirs() }
        preferences = DesktopPreferences(PreferencesStores.get(File(base, "desktop.preferences_pb")))
        file(defaultRoot, "audio_cache/1_standard.mka", 100)
        file(defaultRoot, "meta_cache/7/playlist_1.json", 5)
        file(defaultRoot, "lyrics/amll/1.ttml", 20)
        file(defaultRoot, "image_cache/cover.0", 50)
        file(defaultRoot, "download_tmp/dl_1.part", 9)
    }

    @After
    fun tearDown() {
        base.deleteRecursively()
    }

    private fun relocator(effective: File = defaultRoot) =
        CacheRelocator(preferences, effectiveRoot = effective, defaultRoot = defaultRoot, ioDispatcher = Dispatchers.IO)

    private fun file(root: File, path: String, size: Int): File =
        File(root, path).apply {
            parentFile.mkdirs()
            writeBytes(ByteArray(size))
        }

    private fun targetRoot(dir: File = selected) = File(dir, "MelodiaCache")

    @Test
    fun copiesAudioMetadataAndLyricsButNotImages() = runBlocking {
        val result = relocator().switchTo(selected) {}

        assertEquals(RelocationResult.Switched, result)
        val target = targetRoot()
        assertEquals(100L, File(target, "audio_cache/1_standard.mka").length())
        assertEquals(5L, File(target, "meta_cache/7/playlist_1.json").length())
        assertEquals(20L, File(target, "lyrics/amll/1.ttml").length())
        assertFalse(File(target, "image_cache").exists())
        assertTrue("原目录在重启前保持不动", File(defaultRoot, "audio_cache/1_standard.mka").exists())
        assertEquals(selected.absolutePath, preferences.cacheRoot.first())
        assertEquals(defaultRoot.absolutePath, preferences.pendingCacheCleanup.first())
    }

    @Test
    fun reportsProgressUpToTheTotal() = runBlocking {
        val reports = mutableListOf<RelocationProgress>()

        relocator().switchTo(selected) { reports += it }

        assertEquals(125L, reports.last().totalBytes)
        assertEquals(125L, reports.last().copiedBytes)
        assertEquals(reports.sortedBy { it.copiedBytes }.map { it.copiedBytes }, reports.map { it.copiedBytes })
    }

    @Test
    fun skipsTempAndRecordingFiles() = runBlocking {
        file(defaultRoot, "audio_cache/2_standard.rec.1.mka", 30)
        file(defaultRoot, "meta_cache/7/playlist_2.json.tmp", 30)

        relocator().switchTo(selected) {}

        assertFalse(File(targetRoot(), "audio_cache/2_standard.rec.1.mka").exists())
        assertFalse(File(targetRoot(), "meta_cache/7/playlist_2.json.tmp").exists())
    }

    @Test
    fun cancelRemovesWhatWasCopiedAndKeepsPriorContent() = runBlocking {
        val keep = file(targetRoot(), "keep.bin", 3)
        lateinit var job: Job
        job = launch(Dispatchers.IO, start = CoroutineStart.LAZY) {
            relocator().switchTo(selected) { progress -> if (progress.copiedBytes > 0) job.cancel() }
        }
        job.start()
        job.join()

        assertTrue(job.isCancelled)
        assertFalse(File(targetRoot(), "audio_cache").exists())
        assertFalse(File(targetRoot(), "meta_cache").exists())
        assertTrue(keep.exists())
        assertNull(preferences.cacheRoot.first())
        assertNull(preferences.pendingCacheCleanup.first())
    }

    @Test
    fun cancelRemovesTheTargetFolderItCreated() = runBlocking {
        lateinit var job: Job
        job = launch(Dispatchers.IO, start = CoroutineStart.LAZY) {
            relocator().switchTo(selected) { progress -> if (progress.copiedBytes > 0) job.cancel() }
        }
        job.start()
        job.join()

        assertFalse(targetRoot().exists())
        assertTrue("所选目录本身保留", selected.exists())
    }

    @Test
    fun rejectsMissingDirectory() = runBlocking {
        val result = relocator().switchTo(File(base, "nope")) {}

        assertEquals("所选目录不存在", (result as RelocationResult.Failed).reason)
        assertNull(preferences.cacheRoot.first())
    }

    @Test
    fun rejectsFolderInsideCurrentCache() = runBlocking {
        val result = relocator().switchTo(defaultRoot) {}

        assertTrue(result is RelocationResult.Failed)
        assertFalse(File(defaultRoot, "MelodiaCache").exists())
        assertNull(preferences.cacheRoot.first())
    }

    @Test
    fun unchangedWhenTargetIsAlreadyInUse() = runBlocking {
        val effective = targetRoot()
        file(effective, "audio_cache/1_standard.mka", 10)

        val result = relocator(effective).switchTo(selected) {}

        assertEquals(RelocationResult.Unchanged, result)
        assertEquals(selected.absolutePath, preferences.cacheRoot.first())
        assertNull(preferences.pendingCacheCleanup.first())
    }

    @Test
    fun switchingBackBeforeRestartDropsTheIntermediateCopy() = runBlocking {
        val relocator = relocator()
        relocator.switchTo(selected) {}
        assertTrue(File(targetRoot(), "audio_cache").exists())

        val result = relocator.switchTo(null) {}

        assertEquals(RelocationResult.Unchanged, result)
        assertFalse(File(targetRoot(), "audio_cache").exists())
        assertTrue(File(defaultRoot, "audio_cache/1_standard.mka").exists())
        assertNull(preferences.cacheRoot.first())
        assertNull(preferences.pendingCacheCleanup.first())
    }

    @Test
    fun stateTellsWhetherTheChoiceHasTakenEffect() = runBlocking {
        val relocator = relocator()
        relocator.switchTo(selected) {}

        val pending = relocator.state.first()
        assertEquals(targetRoot(), pending.root)
        assertTrue(pending.isCustom)
        assertTrue(pending.pendingRestart)

        val afterRestart = relocator(targetRoot()).state.first()
        assertFalse(afterRestart.pendingRestart)
    }

    @Test
    fun cleanupDeletesOldCacheAfterSwitchTookEffectButKeepsDownloadTemp() = runBlocking {
        relocator().switchTo(selected) {}

        relocator(targetRoot()).cleanupPending()

        assertFalse(File(defaultRoot, "audio_cache").exists())
        assertFalse(File(defaultRoot, "image_cache").exists())
        assertFalse(File(defaultRoot, "meta_cache").exists())
        assertFalse(File(defaultRoot, "lyrics").exists())
        assertTrue("下载中间文件不属于缓存", File(defaultRoot, "download_tmp/dl_1.part").exists())
        assertNull(preferences.pendingCacheCleanup.first())
    }

    @Test
    fun cleanupWaitsUntilTheSwitchTakesEffect() = runBlocking {
        val relocator = relocator()
        relocator.switchTo(selected) {}

        relocator.cleanupPending()

        assertTrue(File(defaultRoot, "audio_cache/1_standard.mka").exists())
        assertEquals(defaultRoot.absolutePath, preferences.pendingCacheCleanup.first())
    }
}

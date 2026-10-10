package com.lin0721.linmusic.desktop.platform

import com.lin0721.linmusic.desktop.platform.native.AppPaths

import coil3.PlatformContext
import java.io.File
import java.nio.file.Files
import okio.Path.Companion.toOkioPath
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class DesktopImageLoaderTest {

    private lateinit var dir: File

    @Before
    fun setUp() {
        dir = Files.createTempDirectory("melodia-image-loader-test").toFile()
    }

    @After
    fun tearDown() {
        dir.deleteRecursively()
    }

    @Test
    fun usesGivenCacheDirectoryAndCapacity() {
        val cacheDir = File(dir, "image_cache")
        val loader = DesktopImageLoader.create(PlatformContext.INSTANCE, cacheDir, maxBytes = 12_345L)

        val disk = loader.diskCache!!
        assertEquals(cacheDir.toOkioPath(), disk.directory)
        assertEquals(12_345L, disk.maxSize)
        assertTrue("应配置内存缓存", (loader.memoryCache?.maxSize ?: 0L) > 0L)
    }

    @Test
    fun defaultCapacityIs500Mb() {
        assertEquals(500L * 1024 * 1024, DesktopImageLoader.DISK_CACHE_MAX_BYTES)
    }

    @Test
    fun diskCacheIsWritableAndClearable() {
        val loader = DesktopImageLoader.create(PlatformContext.INSTANCE, File(dir, "image_cache"), maxBytes = 1024L * 1024)
        val disk = loader.diskCache!!

        val editor = disk.openEditor("cover-key")!!
        File(editor.data.toString()).writeBytes(ByteArray(2048))
        File(editor.metadata.toString()).writeBytes(ByteArray(16))
        editor.commit()
        assertTrue("写入后占用应大于 0", disk.size > 0L)

        disk.clear()
        assertEquals(0L, disk.size)
    }

    @Test
    fun legacyTempCacheIsDeletedInBackground() {
        val legacy = File(dir, "coil3_disk_cache").apply { mkdirs() }
        File(legacy, "old.bin").writeBytes(ByteArray(100))

        DesktopImageLoader.deleteLegacyCache(legacy)

        val deadline = System.currentTimeMillis() + 5_000
        while (legacy.exists() && System.currentTimeMillis() < deadline) Thread.sleep(20)
        assertFalse(legacy.exists())
    }

    @Test
    fun missingLegacyCacheIsIgnored() {
        DesktopImageLoader.deleteLegacyCache(File(dir, "不存在"))
    }

    @Test
    fun cacheLivesOutsideRoamingDataDirWhenLocalAppDataExists() {
        val local = System.getenv("LOCALAPPDATA")?.takeIf { it.isNotBlank() } ?: return
        assertTrue(AppPaths.current.imageCacheDir.path.startsWith(local))
        assertEquals("image_cache", AppPaths.current.imageCacheDir.name)
    }
}

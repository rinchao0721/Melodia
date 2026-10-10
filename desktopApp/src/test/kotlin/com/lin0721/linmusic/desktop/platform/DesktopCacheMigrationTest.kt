package com.lin0721.linmusic.desktop.platform

import com.lin0721.linmusic.desktop.platform.native.AppPaths

import java.io.File
import java.nio.file.Files
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class DesktopCacheMigrationTest {

    private lateinit var root: File

    @Before
    fun setUp() {
        root = Files.createTempDirectory("melodia-cache-migration-test").toFile()
    }

    @After
    fun tearDown() {
        root.deleteRecursively()
    }

    private fun populated(name: String): File =
        File(root, name).apply {
            File(this, "sub").mkdirs()
            File(this, "a.bin").writeBytes(ByteArray(10))
            File(this, "sub/b.bin").writeBytes(ByteArray(20))
        }

    private fun awaitGone(file: File): Boolean {
        val deadline = System.currentTimeMillis() + 5_000
        while (file.exists() && System.currentTimeMillis() < deadline) Thread.sleep(20)
        return !file.exists()
    }

    @Test
    fun movesWholeDirectoryAndKeepsContent() {
        val old = populated("old")
        val new = File(root, "local/nested/new")

        DesktopCacheMigration.migrate(old, new)

        assertFalse(old.exists())
        assertEquals(10, File(new, "a.bin").length())
        assertEquals(20, File(new, "sub/b.bin").length())
    }

    @Test
    fun missingOldDirectoryIsIgnored() {
        val new = File(root, "new")
        DesktopCacheMigration.migrate(File(root, "不存在"), new)
        assertFalse(new.exists())
    }

    @Test
    fun samePathIsLeftAlone() {
        val dir = populated("same")
        DesktopCacheMigration.migrate(dir, File(root, "same"))
        assertTrue(File(dir, "a.bin").exists())
    }

    @Test
    fun existingEmptyTargetIsReplacedByOldContent() {
        val old = populated("old")
        val new = File(root, "new").apply { mkdirs() }

        DesktopCacheMigration.migrate(old, new)

        assertFalse(old.exists())
        assertTrue(File(new, "a.bin").exists())
    }

    @Test
    fun nonEmptyTargetWinsAndOldIsDiscarded() {
        val old = populated("old")
        val new = File(root, "new").apply { mkdirs() }
        File(new, "keep.bin").writeBytes(ByteArray(5))

        DesktopCacheMigration.migrate(old, new)

        assertTrue("旧目录应被后台删除", awaitGone(old))
        assertTrue("新位置已有内容不应被覆盖", File(new, "keep.bin").exists())
        assertFalse(File(new, "a.bin").exists())
    }

    @Test
    fun cacheDirsAreAllUnderLocalCacheDir() {
        val local = AppPaths.current.cacheDir
        listOf(AppPaths.current.imageCacheDir, AppPaths.current.audioCacheDir, AppPaths.current.metadataCacheDir, AppPaths.current.downloadTempDir)
            .forEach { assertEquals(local, it.parentFile) }
    }
}

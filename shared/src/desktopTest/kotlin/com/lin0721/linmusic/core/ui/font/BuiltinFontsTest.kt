package com.lin0721.linmusic.core.ui.font

import java.io.File
import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BuiltinFontsTest {

    @Test
    fun `extracts figtree font files and reuses cached directory`() {
        val tempDir = Files.createTempDirectory("builtin-fonts-test").toFile()
        try {
            val builtinFonts = BuiltinFonts(tempDir)
            val asset = builtinFonts.figtree()
            assertNotNull(asset)
            asset!!
            assertEquals(FONT_ID_BUILTIN_FIGTREE, asset.entry.id)
            assertEquals(FontSource.BUILTIN, asset.entry.source)
            assertEquals(4, asset.entry.faces.size)
            assertTrue(File(asset.directory, ".version").isFile)
            asset.entry.faces.forEach { face ->
                val file = File(asset.directory, face.file)
                assertTrue(file.isFile)
                assertTrue(file.length() > 1000L)
            }

            val cached = builtinFonts.figtree()
            assertEquals(asset.directory, cached?.directory)
        } finally {
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun `reextracts figtree when version changes`() {
        val tempDir = Files.createTempDirectory("builtin-fonts-version").toFile()
        try {
            val first = BuiltinFonts(tempDir, version = "1").figtree()!!
            val regularFile = File(first.directory, "Figtree-Regular.ttf")
            regularFile.writeText("stale")

            // 版本未变时直接复用现有文件
            val unchanged = BuiltinFonts(tempDir, version = "1").figtree()!!
            assertEquals("stale", File(unchanged.directory, "Figtree-Regular.ttf").readText())

            // 版本变化后重新解压覆盖
            val updated = BuiltinFonts(tempDir, version = "2").figtree()!!
            assertTrue(File(updated.directory, "Figtree-Regular.ttf").length() > 1000L)
            assertEquals("2", File(updated.directory, ".version").readText())
        } finally {
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun `returns null when figtree resource is missing`() {
        val tempDir = Files.createTempDirectory("builtin-fonts-missing").toFile()
        try {
            val builtinFonts = BuiltinFonts(tempDir, source = { null })
            assertNull(builtinFonts.figtree())
        } finally {
            tempDir.deleteRecursively()
        }
    }
}

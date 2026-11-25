package com.lin0721.linmusic.core.ui.font

import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FontStoreTest {

    @Test
    fun `imports multiple faces and groups by family name`() {
        val tempDir = Files.createTempDirectory("font-store-test").toFile()
        try {
            val sourceDir = File(tempDir, "src").apply { mkdirs() }
            val regular = File(sourceDir, "Brand-Regular.ttf").apply {
                writeBytes(minimalSfnt("Brand Sans", weight = 400, italic = false))
            }
            val bold = File(sourceDir, "Brand-Bold.ttf").apply {
                writeBytes(minimalSfnt("Brand Sans", weight = 700, italic = false))
            }
            val store = FontStore(File(tempDir, "fonts")) { _, _, _ -> true }

            val result = store.importFiles(listOf(regular, bold))
            assertTrue(result.failures.isEmpty())
            assertEquals(1, result.imported.size)
            val entry = result.imported.first()
            assertEquals("Brand Sans", entry.name)
            assertEquals(FontSource.IMPORTED, entry.source)
            assertEquals(2, entry.faces.size)
            assertEquals(listOf(entry), store.list())

            assertTrue(store.delete(entry.id))
            assertTrue(store.list().isEmpty())
        } finally {
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun `rejects invalid font and oversize file`() {
        val tempDir = Files.createTempDirectory("font-store-limit").toFile()
        try {
            val sourceDir = File(tempDir, "src").apply { mkdirs() }
            val invalid = File(sourceDir, "bad.ttf").apply { writeBytes(byteArrayOf(1, 2, 3, 4)) }
            val store = FontStore(File(tempDir, "fonts")) { _, _, _ -> true }

            val result = store.importFiles(listOf(invalid))
            assertTrue(result.imported.isEmpty())
            assertEquals(1, result.failures.size)
        } finally {
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun `registers skin font and removes with skin`() {
        val tempDir = Files.createTempDirectory("font-store-skin").toFile()
        try {
            val skinDir = File(tempDir, "skin").apply { mkdirs() }
            val assetsDir = File(skinDir, "assets").apply { mkdirs() }
            File(assetsDir, "Theme.ttf").writeBytes(minimalSfnt("ThemeFont", weight = 400, italic = false))

            val store = FontStore(File(tempDir, "fonts")) { _, _, _ -> true }
            val entry = store.registerSkinFont(
                skinId = "my-skin",
                skinName = "我的皮肤",
                faces = listOf(FontFaceSpec("assets/Theme.ttf", weight = 400, italic = false)),
                skinDirectory = skinDir
            )!!
            assertNotNull(entry)
            assertEquals(FontSource.SKIN, entry.source)
            assertEquals("my-skin", entry.skinId)
            assertNotNull(store.get(entry.id))

            assertTrue(store.removeSkinFonts("my-skin"))
            assertNull(store.get(entry.id))
        } finally {
            tempDir.deleteRecursively()
        }
    }

    private fun minimalSfnt(family: String, weight: Int, italic: Boolean): ByteArray {
        val rawFamily = family.toByteArray(StandardCharsets.UTF_16BE)
        val nameHeaderSize = 6 + 12
        val nameTable = ByteBuffer.allocate(nameHeaderSize + rawFamily.size).order(ByteOrder.BIG_ENDIAN).apply {
            putShort(0); putShort(1); putShort(nameHeaderSize.toShort())
            putShort(3); putShort(1); putShort(0x0409); putShort(1)
            putShort(rawFamily.size.toShort()); putShort(0)
            put(rawFamily)
        }.array()
        val os2Table = ByteBuffer.allocate(82).order(ByteOrder.BIG_ENDIAN).apply {
            putShort(1); putShort(0); putShort(weight.toShort())
            position(62); putShort(if (italic) 1 else 0)
        }.array()
        val nameOffset = 12 + 32
        val os2Offset = nameOffset + nameTable.size
        return ByteBuffer.allocate(os2Offset + os2Table.size).order(ByteOrder.BIG_ENDIAN).apply {
            putInt(0x00010000); putShort(2); putShort(0); putShort(0); putShort(0)
            putInt(0x6E616D65); putInt(0); putInt(nameOffset); putInt(nameTable.size)
            putInt(0x4F532F32); putInt(0); putInt(os2Offset); putInt(os2Table.size)
            put(nameTable); put(os2Table)
        }.array()
    }
}

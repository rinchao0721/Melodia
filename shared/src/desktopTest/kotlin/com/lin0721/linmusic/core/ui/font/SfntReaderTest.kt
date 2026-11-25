package com.lin0721.linmusic.core.ui.font

import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.charset.StandardCharsets
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SfntReaderTest {

    @Test
    fun `parses valid TTF with UTF-16BE typographic names and OS2 flags`() {
        val bytes = buildMinimalSfnt(
            version = 0x00010000,
            names = listOf(
                NameEntry(platformId = 3, encodingId = 1, languageId = 0x0409, nameId = 1, text = "Legacy Family"),
                NameEntry(platformId = 3, encodingId = 1, languageId = 0x0409, nameId = 2, text = "Regular"),
                NameEntry(platformId = 3, encodingId = 1, languageId = 0x0409, nameId = 16, text = "Melodia Sans"),
                NameEntry(platformId = 3, encodingId = 1, languageId = 0x0409, nameId = 17, text = "Bold Italic")
            ),
            weight = 700,
            fsSelection = 0x0001, // italic
            codePageRange1 = 1L shl 17 // 简体中文
        )

        val result = SfntReader.read(bytes)
        assertTrue(result is SfntParseResult.Success)
        val success = result as SfntParseResult.Success
        assertEquals("Melodia Sans", success.metadata.familyName)
        assertEquals("Bold Italic", success.metadata.subfamilyName)
        assertEquals(700, success.metadata.weight)
        assertTrue(success.metadata.isItalic)
        assertTrue(success.metadata.hasChinese)
    }

    @Test
    fun `parses OTF and true headers with Mac Roman fallback`() {
        for (version in listOf(0x4F54544F, 0x74727565)) {
            val bytes = buildMinimalSfnt(
                version = version,
                names = listOf(
                    NameEntry(platformId = 1, encodingId = 0, languageId = 0, nameId = 1, text = "MacFont"),
                    NameEntry(platformId = 1, encodingId = 0, languageId = 0, nameId = 2, text = "Regular")
                ),
                weight = 400,
                fsSelection = 0,
                codePageRange1 = 0L
            )
            val result = SfntReader.read(bytes)
            assertTrue(result is SfntParseResult.Success)
            val success = result as SfntParseResult.Success
            assertEquals("MacFont", success.metadata.familyName)
            assertEquals("Regular", success.metadata.subfamilyName)
            assertFalse(success.metadata.isItalic)
            assertFalse(success.metadata.hasChinese)
        }
    }

    @Test
    fun `rejects ttc and woff headers`() {
        val ttc = ByteArray(16)
        ttc[0] = 't'.code.toByte(); ttc[1] = 't'.code.toByte(); ttc[2] = 'c'.code.toByte(); ttc[3] = 'f'.code.toByte()
        assertTrue(SfntReader.read(ttc) is SfntParseResult.Failure)

        val woff = ByteArray(16)
        woff[0] = 'w'.code.toByte(); woff[1] = 'O'.code.toByte(); woff[2] = 'F'.code.toByte(); woff[3] = 'F'.code.toByte()
        assertTrue(SfntReader.read(woff) is SfntParseResult.Failure)
    }

    @Test
    fun `handles truncated and out-of-bounds tables without throwing`() {
        assertTrue(SfntReader.read(byteArrayOf(0, 1, 0, 0)) is SfntParseResult.Failure)

        val badTable = ByteBuffer.allocate(12 + 16).order(ByteOrder.BIG_ENDIAN).apply {
            putInt(0x00010000)
            putShort(1)
            putShort(0); putShort(0); putShort(0)
            putInt(0x6E616D65) // "name"
            putInt(0)
            putInt(1000) // 越界偏移
            putInt(50)
        }.array()
        assertTrue(SfntReader.read(badTable) is SfntParseResult.Failure)
    }

    private data class NameEntry(
        val platformId: Int,
        val encodingId: Int,
        val languageId: Int,
        val nameId: Int,
        val text: String
    )

    private fun buildMinimalSfnt(
        version: Int,
        names: List<NameEntry>,
        weight: Int,
        fsSelection: Int,
        codePageRange1: Long
    ): ByteArray {
        val stringBytesList = names.map { entry ->
            if (entry.platformId == 1) {
                entry.text.toByteArray(StandardCharsets.US_ASCII)
            } else {
                entry.text.toByteArray(StandardCharsets.UTF_16BE)
            }
        }
        val stringStorageSize = stringBytesList.sumOf { it.size }
        val nameHeaderSize = 6 + names.size * 12
        val nameTable = ByteBuffer.allocate(nameHeaderSize + stringStorageSize).order(ByteOrder.BIG_ENDIAN).apply {
            putShort(0) // format
            putShort(names.size.toShort())
            putShort(nameHeaderSize.toShort())
            var strOffset = 0
            names.forEachIndexed { idx, entry ->
                val raw = stringBytesList[idx]
                putShort(entry.platformId.toShort())
                putShort(entry.encodingId.toShort())
                putShort(entry.languageId.toShort())
                putShort(entry.nameId.toShort())
                putShort(raw.size.toShort())
                putShort(strOffset.toShort())
                strOffset += raw.size
            }
            stringBytesList.forEach { put(it) }
        }.array()

        val os2Table = ByteBuffer.allocate(82).order(ByteOrder.BIG_ENDIAN).apply {
            putShort(1) // version
            putShort(0) // xAvgCharWidth
            putShort(weight.toShort())
            position(62)
            putShort(fsSelection.toShort())
            position(78)
            putInt(codePageRange1.toInt())
        }.array()

        val numTables = 2
        val headerAndDirSize = 12 + numTables * 16
        val nameOffset = headerAndDirSize
        val os2Offset = nameOffset + nameTable.size

        return ByteBuffer.allocate(os2Offset + os2Table.size).order(ByteOrder.BIG_ENDIAN).apply {
            putInt(version)
            putShort(numTables.toShort())
            putShort(0); putShort(0); putShort(0)

            putInt(0x6E616D65) // name
            putInt(0)
            putInt(nameOffset)
            putInt(nameTable.size)

            putInt(0x4F532F32) // OS/2
            putInt(0)
            putInt(os2Offset)
            putInt(os2Table.size)

            put(nameTable)
            put(os2Table)
        }.array()
    }
}

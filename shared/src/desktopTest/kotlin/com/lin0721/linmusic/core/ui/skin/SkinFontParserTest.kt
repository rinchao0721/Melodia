package com.lin0721.linmusic.core.ui.skin

import com.lin0721.linmusic.core.ui.font.FontFaceSpec
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.file.Files
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SkinFontParserTest {

    @Test
    fun `parses style font family asset and faces without unsupported warning`() {
        val tempDir = Files.createTempDirectory("skin-font-parser").toFile()
        try {
            val assets = File(tempDir, "assets").apply { mkdirs() }
            File(assets, "Regular.ttf").writeBytes(byteArrayOf(0, 1, 0, 0))
            File(assets, "Bold.ttf").writeBytes(byteArrayOf(0, 1, 0, 0))

            val json = """
                {
                  "format": 1,
                  "id": "font-skin",
                  "name": "Font Skin",
                  "modes": ["dark"],
                  "style": {
                    "font": {
                      "family": "assets/Regular.ttf",
                      "faces": [
                        { "file": "assets/Regular.ttf", "weight": 400 },
                        { "file": "assets/Bold.ttf", "weight": 700, "italic": true }
                      ]
                    }
                  }
                }
            """.trimIndent()

            val result = SkinParser.parse(json, tempDir)
            assertTrue(result is SkinParseResult.Success)
            val success = result as SkinParseResult.Success
            assertTrue(success.warnings.none { it.contains("暂不支持随包字体文件") })
            assertEquals(
                listOf(
                    FontFaceSpec("assets/Regular.ttf", weight = 400, italic = false),
                    FontFaceSpec("assets/Bold.ttf", weight = 700, italic = true)
                ),
                success.skin.style.fontFaces
            )
        } finally {
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun `SkinPackage allows font asset larger than 10MB image limit`() {
        val tempDir = Files.createTempDirectory("skin-package-font").toFile()
        try {
            val zipBytes = ByteArrayOutputStream().use { baos ->
                ZipOutputStream(baos).use { zos ->
                    zos.putNextEntry(ZipEntry("skin.json"))
                    zos.write("""{"format":1,"id":"big-font","name":"Big","modes":["dark"],"style":{"font":{"family":"assets/Big.ttf"}}}""".toByteArray())
                    zos.closeEntry()

                    zos.putNextEntry(ZipEntry("assets/Big.ttf"))
                    zos.write(ByteArray(11 * 1024 * 1024)) // 11MB，超过图片 10MB 但在字体 30MB 内
                    zos.closeEntry()
                }
                baos.toByteArray()
            }

            val stageDir = File(tempDir, "stage")
            val result = SkinPackage.stage(ByteArrayInputStream(zipBytes), stageDir)
            assertTrue(result is SkinStageResult.Success)
        } finally {
            tempDir.deleteRecursively()
        }
    }
}

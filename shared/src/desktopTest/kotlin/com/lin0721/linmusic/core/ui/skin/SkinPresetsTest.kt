package com.lin0721.linmusic.core.ui.skin

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.unit.IntSize
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
import java.nio.file.Files
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import javax.imageio.ImageIO

class SkinPresetsTest {

    // 按真实像素算背景图平均色，与导入时的对比度检查一致
    private object ImageIoCodec : SkinImageCodec {
        override fun readSize(file: File): IntSize? = ImageIO.read(file)?.let { IntSize(it.width, it.height) }
        override fun decode(file: File, maxSide: Int): ImageBitmap? = null
        override fun fromPixels(width: Int, height: Int, argb: IntArray): ImageBitmap? = null
        override fun decodePixels(file: File, maxSide: Int): SkinPixels? {
            val image = ImageIO.read(file) ?: return null
            return SkinPixels(image.width, image.height, image.getRGB(0, 0, image.width, image.height, null, 0, image.width))
        }
    }

    private lateinit var root: File

    @Before
    fun setUp() {
        root = Files.createTempDirectory("skin-presets").toFile()
    }

    @After
    fun tearDown() {
        root.deleteRecursively()
    }

    private fun resource(name: String): InputStream? = SkinPresets::class.java.getResourceAsStream("/skins/$name")

    private fun zip(id: String): ByteArray {
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { zip ->
            zip.putNextEntry(ZipEntry(SkinPaths.MANIFEST))
            zip.write("""{"format":1,"id":"$id","name":"测试"}""".toByteArray())
            zip.closeEntry()
        }
        return out.toByteArray()
    }

    @Test
    fun `内置预设全部可解析且无需修正`() {
        val presets = SkinPresets(File(root, "presets"), ImageIoCodec)
        assertEquals(
            listOf("melodia-paper", "melodia-sakura", "melodia-terminal", "melodia-forest", "melodia-mono", "melodia-mint"),
            presets.ids
        )
        presets.ids.forEach { id ->
            val input = resource("$id.$SKIN_FILE_EXTENSION") ?: error("缺少皮肤包 $id")
            val result = input.use { SkinPackage.stage(it, File(root, "check/$id"), ImageIoCodec) }
            assertTrue("$id 解包失败：$result", result is SkinStageResult.Success)
            val staged = (result as SkinStageResult.Success).staged
            assertEquals(id, staged.skin.id)
            assertTrue("$id 有警告：${staged.warnings}", staged.warnings.isEmpty())
            assertTrue("$id 需要修正：${staged.fixDescriptions}", staged.fixDescriptions.isEmpty())
        }
        assertEquals(presets.ids, presets.list().map { it.id })
    }

    @Test
    fun `版本不变不重复解压，版本变化重新解压`() {
        var version = "1"
        val source: (String) -> InputStream? = { name ->
            when (name) {
                "index.txt" -> ByteArrayInputStream("# 注释\nmelodia-demo $version\nbad-id 1\n".toByteArray())
                "melodia-demo.$SKIN_FILE_EXTENSION" -> ByteArrayInputStream(zip("melodia-demo"))
                else -> null
            }
        }
        val first = SkinPresets(root, source = source)
        assertEquals(listOf("melodia-demo"), first.ids)
        val skin = first.load("melodia-demo") ?: error("预设未加载")
        val marker = File(skin.directory, "marker")
        marker.writeText("x")
        assertNotNull(SkinPresets(root, source = source).load("melodia-demo"))
        assertTrue(marker.exists())
        version = "2"
        assertNotNull(SkinPresets(root, source = source).load("melodia-demo"))
        assertFalse(marker.exists())
    }

    @Test
    fun `预设与列表 id 不一致时不加载`() {
        val source: (String) -> InputStream? = { name ->
            when (name) {
                "index.txt" -> ByteArrayInputStream("melodia-demo 1".toByteArray())
                "melodia-demo.$SKIN_FILE_EXTENSION" -> ByteArrayInputStream(zip("melodia-other"))
                else -> null
            }
        }
        val presets = SkinPresets(root, source = source)
        assertEquals(null, presets.load("melodia-demo"))
        assertFalse(File(root, "melodia-demo").exists())
    }

    @Test
    fun `与预设同 id 的导入另存且预设不可删除`() {
        val store = SkinStore(File(root, "skins"))
        val staged = (store.stage(ByteArrayInputStream(zip("melodia-paper"))) as SkinStageResult.Success).staged
        val installed = store.install(staged, replace = true) ?: error("安装失败")
        assertNotEquals("melodia-paper", installed.id)
        assertEquals("纸墨护眼", store.load("melodia-paper")?.name)
        assertFalse(store.delete("melodia-paper"))
        assertEquals(listOf(installed.id), store.list().map { it.id })
    }
}

package com.lin0721.linmusic.core.ui.skin

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import com.lin0721.linmusic.core.ui.theme.MelodiaPalette
import com.lin0721.linmusic.core.ui.theme.MelodiaPalettes
import com.lin0721.linmusic.core.ui.theme.ThemeAccents
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.file.Files
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class SkinPackageTest {

    private lateinit var root: File
    private lateinit var store: SkinStore

    @Before
    fun setUp() {
        root = Files.createTempDirectory("skin-test").toFile()
        store = SkinStore(File(root, "skins"))
    }

    @After
    fun tearDown() {
        root.deleteRecursively()
    }

    private fun zip(vararg entries: Pair<String, ByteArray>): ByteArray {
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { zip ->
            entries.forEach { (name, bytes) ->
                zip.putNextEntry(ZipEntry(name))
                zip.write(bytes)
                zip.closeEntry()
            }
        }
        return out.toByteArray()
    }

    private fun manifest(id: String = "demo", extra: String = "") =
        """{"format":1,"id":"$id","name":"演示"$extra}""".toByteArray()

    private fun stage(bytes: ByteArray): SkinStageResult = store.stage(ByteArrayInputStream(bytes))

    private fun staged(bytes: ByteArray): StagedSkin {
        val result = stage(bytes)
        assertTrue("应解包成功：$result", result is SkinStageResult.Success)
        return (result as SkinStageResult.Success).staged
    }

    @Test
    fun `正常皮肤包带背景图`() {
        val result = staged(
            zip(
                SkinPaths.MANIFEST to manifest(extra = ""","background":{"image":"assets/bg.png"}"""),
                "assets/bg.png" to ByteArray(16)
            )
        )
        assertNotNull(result.skin.background)
        assertTrue(File(result.directory, "assets/bg.png").isFile)
    }

    @Test
    fun `路径穿越直接拒绝`() {
        assertTrue(stage(zip(SkinPaths.MANIFEST to manifest(), "../evil.png" to ByteArray(1))) is SkinStageResult.Failure)
        assertTrue(stage(zip(SkinPaths.MANIFEST to manifest(), "assets/../../evil.png" to ByteArray(1))) is SkinStageResult.Failure)
        assertTrue(stage(zip(SkinPaths.MANIFEST to manifest(), "/abs.png" to ByteArray(1))) is SkinStageResult.Failure)
        assertFalse(File(root, "evil.png").exists())
    }

    @Test
    fun `非白名单文件忽略不落盘`() {
        val result = staged(zip(SkinPaths.MANIFEST to manifest(), "run.sh" to ByteArray(4), "assets/x.exe" to ByteArray(4)))
        assertEquals(2, result.warnings.size)
        assertFalse(File(result.directory, "run.sh").exists())
        assertFalse(File(result.directory, "assets/x.exe").exists())
    }

    @Test
    fun `超大文件与缺少描述文件都失败`() {
        val big = ByteArray(10 * 1024 * 1024 + 1)
        assertTrue(stage(zip(SkinPaths.MANIFEST to manifest(), "assets/bg.png" to big)) is SkinStageResult.Failure)
        assertTrue(stage(zip("assets/bg.png" to ByteArray(4))) is SkinStageResult.Failure)
        assertTrue(stage("not a zip".toByteArray()) is SkinStageResult.Failure)
    }

    @Test
    fun `对比度修正写回描述文件`() {
        val grey = "#8A8A8A"
        val result = staged(
            zip(SkinPaths.MANIFEST to manifest(extra = ""","colors":{"light":{"pane":"$grey","windowBackground":"$grey"}}"""))
        )
        assertTrue(result.fixes.isNotEmpty())
        val reread = SkinParser.parse(File(result.directory, SkinPaths.MANIFEST).readText()) as SkinParseResult.Success
        assertEquals(result.skin.lightColors, reread.skin.lightColors)
    }

    @Test
    fun `安装、覆盖、另存、删除`() {
        val first = store.install(staged(zip(SkinPaths.MANIFEST to manifest())), replace = false)
        assertEquals("demo", first?.id)
        val copy = store.install(staged(zip(SkinPaths.MANIFEST to manifest())), replace = false)
        assertEquals("demo-2", copy?.id)
        val replaced = store.install(staged(zip(SkinPaths.MANIFEST to manifest())), replace = true)
        assertEquals("demo", replaced?.id)
        assertEquals(listOf("demo", "demo-2"), store.list().map { it.id }.sorted())
        assertTrue(store.delete("demo-2"))
        assertNull(store.load("demo-2"))
        assertNull(store.load("../skins"))
        assertNull(store.load(BUILTIN_SKIN_ID))
    }

    @Test
    fun `导出当前主题再导入色板不变`() {
        val dark = MelodiaPalettes.dark.withAccent(Color(0xFF3D8BFF))
        val light = MelodiaPalettes.light.withAccent(ThemeAccents.light.first().color)
        val out = ByteArrayOutputStream()
        SkinPackage.export(out, "mine", "我的主题", dark, light, source = null)
        val result = staged(out.toByteArray())
        assertTrue(result.fixes.isEmpty())
        // 文件里按 8 位通道存色，按 ARGB 比较
        fun argb(palette: MelodiaPalette) = palette.toTokenMap().mapValues { it.value.toArgb() }
        assertEquals(argb(dark), argb(resolveSkinPalette(true, ThemeAccents.DEFAULT_ID, null, result.skin)))
        assertEquals(argb(light), argb(resolveSkinPalette(false, ThemeAccents.DEFAULT_ID, null, result.skin)))
    }
}

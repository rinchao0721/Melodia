package com.lin0721.linmusic.core.ui.skin

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.unit.IntSize
import com.lin0721.linmusic.core.preferences.PreferencesStores
import com.lin0721.linmusic.core.preferences.SettingsPreferences
import com.lin0721.linmusic.core.ui.theme.MelodiaPalettes
import com.lin0721.linmusic.core.ui.theme.ThemeAccents
import com.lin0721.linmusic.core.ui.theme.contrastRatio
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
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

class SkinBackdropTest {

    // 不依赖 Skia：尺寸与像素直接给定
    private class FakeCodec(private val size: IntSize, private val color: Int) : SkinImageCodec {
        override fun readSize(file: File): IntSize = size
        override fun decode(file: File, maxSide: Int): ImageBitmap? = null
        override fun fromPixels(width: Int, height: Int, argb: IntArray): ImageBitmap? = null
        override fun decodePixels(file: File, maxSide: Int): SkinPixels = SkinPixels(4, 4, IntArray(16) { color })
    }

    private lateinit var root: File

    @Before
    fun setUp() {
        root = Files.createTempDirectory("skin-backdrop").toFile()
    }

    @After
    fun tearDown() {
        root.deleteRecursively()
    }

    private fun skinZip(id: String = "bg", extra: String = ""): ByteArray {
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { zip ->
            zip.putNextEntry(ZipEntry(SkinPaths.MANIFEST))
            zip.write("""{"format":1,"id":"$id","name":"背景","background":{"image":"assets/bg.jpg"$extra}}""".toByteArray())
            zip.closeEntry()
            zip.putNextEntry(ZipEntry("assets/bg.jpg"))
            zip.write(ByteArray(8))
            zip.closeEntry()
        }
        return out.toByteArray()
    }

    @Test
    fun `盒式模糊保持纯色并把亮点摊开`() {
        val uniform = IntArray(25) { 0xFF336699.toInt() }
        SkinImages.boxBlur(uniform, 5, 5, 2)
        assertTrue(uniform.all { it == 0xFF336699.toInt() })

        val spot = IntArray(49) { 0xFF000000.toInt() }
        spot[24] = 0xFFFFFFFF.toInt()
        SkinImages.boxBlur(spot, 7, 7, 1)
        assertTrue((spot[24] and 0xFF) in 1 until 255)
        assertTrue((spot[16] and 0xFF) > 0)
        assertEquals(0, spot[0] and 0xFF)

        val tiny = IntArray(2) { 0xFF808080.toInt() }
        SkinImages.boxBlur(tiny, 2, 1, 10)
        assertTrue(tiny.all { it == 0xFF808080.toInt() })
    }

    @Test
    fun `没有背景图时铺底与原色一致`() {
        val palette = MelodiaPalettes.dark
        assertEquals(palette.pane, palette.pageFill)
        assertEquals(palette.pane, palette.shellFill)
        assertEquals(palette.windowBackground, palette.canvasFill)
    }

    @Test
    fun `有背景图时内部透明外壳半透明`() {
        val background = SkinBackground("assets/bg.jpg", blur = 0f, dim = 0.3f, paneOpacity = 0.8f, scope = SkinBackgroundScope.APP)
        val palette = MelodiaPalettes.light.withBackdrop(background)
        assertEquals(0f, palette.pageFill.alpha)
        assertEquals(palette.pane.copy(alpha = 1f), palette.pageFill.copy(alpha = 1f))
        assertEquals(0f, palette.canvasFill.alpha)
        assertEquals(0.8f, palette.shellFill.alpha, 0.01f)
        assertEquals(MelodiaPalettes.light.pane, palette.pane)
    }

    @Test
    fun `背景图超过尺寸上限拒绝导入`() {
        val store = SkinStore(File(root, "skins"), FakeCodec(IntSize(5000, 3000), 0xFF000000.toInt()))
        assertTrue(store.stage(ByteArrayInputStream(skinZip())) is SkinStageResult.Failure)
    }

    @Test
    fun `亮背景图透过半透明深色面板时修正文字`() {
        val store = SkinStore(File(root, "skins"), FakeCodec(IntSize(1920, 1080), 0xFFFFFFFF.toInt()))
        val result = store.stage(ByteArrayInputStream(skinZip(extra = ""","paneOpacity":0.5"""))) as SkinStageResult.Success
        val staged = result.staged
        assertTrue(staged.fixes.isNotEmpty())
        val palette = resolveSkinPalette(true, ThemeAccents.DEFAULT_ID, null, staged.skin)
        val canvas = palette.windowBackground.copy(alpha = 0f).compositeOver(Color.White)
        val pane = palette.pane.copy(alpha = 0.5f).compositeOver(canvas)
        assertTrue(contrastRatio(palette.textPrimary, pane) >= 7f || palette.textPrimary == Color.Black)
        assertTrue(contrastRatio(palette.textSecondary, pane) >= 4.5f)
    }

    @Test
    fun `安装即应用，删除当前皮肤回到默认`() = runBlocking {
        val settings = SettingsPreferences(PreferencesStores.get(File(root, "settings.preferences_pb")))
        val repository = SkinRepository(SkinStore(File(root, "skins")), settings)
        val staged = (repository.stage(ByteArrayInputStream(skinZip(id = "night"))) as SkinStageResult.Success).staged
        val installed = repository.installAndApply(staged, replace = false)
        assertEquals("night", installed?.id)
        assertEquals("night", settings.themeSettings.first().skinId)
        assertEquals(listOf("night"), repository.installed.value.map { it.id })

        repository.delete("night")
        assertEquals(BUILTIN_SKIN_ID, settings.themeSettings.first().skinId)
        assertTrue(repository.installed.value.isEmpty())
        assertNull(repository.load("night"))
    }
}

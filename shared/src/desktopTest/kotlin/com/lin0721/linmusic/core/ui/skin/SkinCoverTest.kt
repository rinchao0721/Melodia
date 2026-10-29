package com.lin0721.linmusic.core.ui.skin

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.unit.IntSize
import com.lin0721.linmusic.core.ui.theme.Hsl
import com.lin0721.linmusic.core.ui.theme.MelodiaPalettes
import com.lin0721.linmusic.core.ui.theme.MelodiaStyle
import com.lin0721.linmusic.core.ui.theme.contrastRatio
import com.lin0721.linmusic.core.ui.theme.coverHeaderTint
import com.lin0721.linmusic.core.ui.theme.immersiveMidFill
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

class SkinCoverTest {

    private class FakeCodec(private val color: Int) : SkinImageCodec {
        override fun readSize(file: File): IntSize = IntSize(1920, 1080)
        override fun decode(file: File, maxSide: Int): ImageBitmap? = null
        override fun fromPixels(width: Int, height: Int, argb: IntArray): ImageBitmap? = null
        override fun decodePixels(file: File, maxSide: Int): SkinPixels = SkinPixels(2, 2, IntArray(4) { color })
    }

    private lateinit var root: File

    @Before
    fun setUp() {
        root = Files.createTempDirectory("skin-cover").toFile()
    }

    @After
    fun tearDown() {
        root.deleteRecursively()
    }

    private fun parse(extra: String): SkinParseResult.Success =
        SkinParser.parse("""{"format":1,"id":"c","name":"取色"$extra}""") as SkinParseResult.Success

    private fun skin(
        cover: SkinCover = SkinCover(),
        background: SkinBackground? = null,
        modes: Set<SkinMode> = SkinMode.entries.toSet(),
        darkColors: Map<PaletteToken, Color> = emptyMap()
    ) = Skin("c", "取色", null, null, modes, darkColors, emptyMap(), background, MelodiaStyle.Default, null, cover = cover)

    private val background = SkinBackground("assets/bg.png", 0f, 0.1f, 0.6f, SkinBackgroundScope.APP)

    @Test
    fun `缺省全部跟随封面`() {
        val cover = parse("").skin.cover
        CoverGroup.entries.forEach { assertEquals(CoverSource.COVER, cover.rule(it).source) }
    }

    @Test
    fun `三种来源与两种颜色写法`() {
        val parsed = parse(
            ""","cover":{
                "header":{"source":"backdrop"},
                "cards":{"source":"color","color":"#3A2F5B"},
                "player":{"source":"color","color":{"dark":"#112233","light":"#AABBCC"}}}"""
        )
        val cover = parsed.skin.cover
        assertEquals(CoverSource.BACKDROP, cover.rule(CoverGroup.HEADER).source)
        assertEquals(Color(0xFF3A2F5B), cover.rule(CoverGroup.CARDS).color(isDark = false))
        assertEquals(Color(0xFF3A2F5B), cover.rule(CoverGroup.CARDS).color(isDark = true))
        assertEquals(Color(0xFF112233), cover.rule(CoverGroup.PLAYER).color(isDark = true))
        assertEquals(Color(0xFFAABBCC), cover.rule(CoverGroup.PLAYER).color(isDark = false))
        assertTrue(parsed.warnings.isEmpty())
    }

    @Test
    fun `无效写法按跟随封面并给出提示`() {
        val parsed = parse(
            ""","cover":{
                "header":{"source":"neon"},
                "cards":{"source":"color"},
                "player":{"source":"color","color":{"dark":"#112233"}},
                "footer":{"source":"cover"}}"""
        )
        val cover = parsed.skin.cover
        assertEquals(CoverSource.COVER, cover.rule(CoverGroup.HEADER).source)
        assertEquals(CoverSource.COVER, cover.rule(CoverGroup.CARDS).source)
        assertNull(cover.rule(CoverGroup.PLAYER).color(isDark = false))
        assertEquals(4, parsed.warnings.size)
    }

    @Test
    fun `运行时策略：背景图未就绪或缺色时退回跟随封面`() {
        val cover = SkinCover(
            mapOf(
                CoverGroup.HEADER to CoverRule(CoverSource.BACKDROP),
                CoverGroup.PLAYER to CoverRule(CoverSource.COLOR, dark = Color.Red)
            )
        )
        val target = skin(cover, background)
        val ready = target.coverPolicy(isDark = true, backdropReady = true)
        assertEquals(CoverTint.Backdrop, ready.header)
        assertEquals(CoverTint.Fixed(Color.Red), ready.player)
        assertNull(ready.base(CoverGroup.HEADER, Color.Blue))
        assertEquals(Color.Red, ready.base(CoverGroup.PLAYER, Color.Blue))
        assertEquals(Color.Blue, ready.base(CoverGroup.CARDS, Color.Blue))

        val notReady = target.coverPolicy(isDark = false, backdropReady = false)
        assertEquals(CoverTint.FollowCover, notReady.header)
        assertEquals(CoverTint.FollowCover, notReady.player)
        assertEquals(CoverPolicy.Default, (null as Skin?).coverPolicy(isDark = true, backdropReady = true))
    }

    @Test
    fun `指定颜色经色调换算后文字可读`() {
        (0 until 24).forEach { step ->
            val color = Hsl(step * 15f, 0.8f, 0.5f).toColor()
            val card = immersiveMidFill(color)
            assertTrue("hue=${step * 15}", contrastRatio(Color.White, card) >= 4.5f)
            val header = coverHeaderTint(color)
            assertTrue("hue=${step * 15}", contrastRatio(MelodiaPalettes.light.textSecondary, header) >= 4.5f)
        }
    }

    @Test
    fun `播放区透出亮背景图时加深遮罩`() {
        val cover = SkinCover(mapOf(CoverGroup.PLAYER to CoverRule(CoverSource.BACKDROP)))
        val bright = SkinContrast.playerDim(skin(cover, background), Color.White)
        assertTrue(bright != null && bright > background.dim)
        val palette = immersiveBasePalette(null)
        val under = palette.windowBackground.copy(alpha = bright!!).compositeOver(Color.White)
        assertTrue(contrastRatio(palette.textPrimary, under) >= 4.5f)
        assertNull(SkinContrast.playerDim(skin(cover, background), Color.Black))
        assertNull(SkinContrast.playerDim(skin(SkinCover(), background), Color.White))
    }

    @Test
    fun `沉浸区色板叠加皮肤深色配色`() {
        assertEquals(MelodiaPalettes.dark, immersiveBasePalette(null))
        val pane = Color(0xFF101828)
        val palette = immersiveBasePalette(skin(darkColors = mapOf(PaletteToken.PANE to pane, PaletteToken.ACCENT to Color.Green)))
        assertEquals(pane, palette.pane)
        assertEquals(MelodiaPalettes.dark.accent, palette.accent)
    }

    @Test
    fun `只支持浅色的皮肤也检查深色配色`() {
        val grey = Color(0xFF8A8A8A)
        val (_, fixes) = SkinContrast.fix(skin(modes = setOf(SkinMode.LIGHT), darkColors = mapOf(PaletteToken.PANE to grey)))
        assertTrue(fixes.any { it.mode == SkinMode.DARK })
    }

    @Test
    fun `导入时写回加深后的播放区遮罩`() {
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { zip ->
            zip.putNextEntry(ZipEntry(SkinPaths.MANIFEST))
            zip.write(
                """{"format":1,"id":"bright","name":"亮","background":{"image":"assets/bg.png","dim":0.1},
                    "cover":{"player":{"source":"backdrop"}}}""".toByteArray()
            )
            zip.closeEntry()
            zip.putNextEntry(ZipEntry("assets/bg.png"))
            zip.write(ByteArray(8))
            zip.closeEntry()
        }
        val store = SkinStore(File(root, "skins"), FakeCodec(0xFFFFFFFF.toInt()))
        val staged = (store.stage(ByteArrayInputStream(out.toByteArray())) as SkinStageResult.Success).staged
        val dim = staged.skin.background!!.playerDim!!
        assertTrue(dim > 0.1f)
        assertTrue(staged.fixDescriptions.any { it.startsWith("播放区遮罩") })
        val reread = SkinParser.parse(File(staged.directory, SkinPaths.MANIFEST).readText()) as SkinParseResult.Success
        assertEquals(dim, reread.skin.background!!.playerDim!!, 0.001f)
    }
}

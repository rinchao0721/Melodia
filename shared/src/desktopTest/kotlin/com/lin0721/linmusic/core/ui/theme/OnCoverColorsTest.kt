package com.lin0721.linmusic.core.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OnCoverColorsTest {

    // 沉浸区深色色板的次要、辅助文字
    private val SECONDARY = MelodiaPalettes.dark.textSecondary
    private val TERTIARY = MelodiaPalettes.dark.textTertiary

    // 蓝、黄、绿、红、紫、灰、近白、近黑封面原色
    private val covers = listOf(
        Color(0xFF2F6FD6), Color(0xFFE0B020), Color(0xFF3FAE5C), Color(0xFFC0392B),
        Color(0xFF7B4FD0), Color(0xFF808080), Color(0xFFF2F2F2), Color(0xFF151515)
    )

    // 各预设主色与内置皮肤主色（薄荷、森林等）
    private val accents = ThemeAccents.light.map { it.color } + ThemeAccents.dark.map { it.color } +
        listOf(Color(0xFF0E7A5F), Color(0xFF3F6B3A), Color(0xFF111111))

    private fun lightBackgrounds(cover: Color) = listOf(immersiveMidFill(cover))

    @Test
    fun `强调色在浅色中调铺底上对比度达标`() {
        covers.forEach { cover ->
            val backgrounds = lightBackgrounds(cover)
            accents.forEach { accent ->
                val result = onCoverColors(backgrounds, Color.White, accent, SECONDARY, TERTIARY)
                backgrounds.forEach { background ->
                    val ratio = contrastRatio(result.accent, background)
                    assertTrue("cover=$cover accent=$accent ratio=$ratio", ratio >= 4.49f)
                }
            }
        }
    }

    @Test
    fun `次要文字在铺底上对比度达标`() {
        covers.forEach { cover ->
            val backgrounds = lightBackgrounds(cover)
            val result = onCoverColors(backgrounds, Color.White, accents.first(), SECONDARY, TERTIARY)
            backgrounds.forEach { background ->
                val ratio = contrastRatio(result.contentSecondary.compositeOver(background), background)
                assertTrue("cover=$cover ratio=$ratio", ratio >= 4.49f)
            }
        }
    }

    @Test
    fun `色板次要与辅助文字在中调铺底上达标`() {
        covers.forEach { cover ->
            val background = immersiveMidFill(cover)
            val result = onCoverColors(listOf(background), Color.White, accents.first(), SECONDARY, TERTIARY)
            assertTrue("cover=$cover", contrastRatio(result.textSecondary, background) >= 4.49f)
            assertTrue("cover=$cover", contrastRatio(result.textTertiary, background) >= 2.99f)
        }
    }

    @Test
    fun `截图场景薄荷主色在蓝色铺底上提亮`() {
        val background = immersiveMidFill(Color(0xFF2F6FD6))
        val mint = Color(0xFF0E7A5F)
        val result = onCoverColors(listOf(background), Color.White, mint, SECONDARY, TERTIARY)
        assertTrue(contrastRatio(mint, background) < 2f)
        assertTrue(contrastRatio(result.accent, background) >= 4.49f)
        assertTrue(result.accent.toHsl().lightness > mint.toHsl().lightness)
    }

    @Test
    fun `已达标时原样保留`() {
        val background = Color(0xFF101010)
        val accent = Color(0xFF3D8BFF)
        val result = onCoverColors(listOf(background), Color.White, accent, SECONDARY, TERTIARY)
        assertEquals(accent, result.accent)
        assertEquals(0.75f, result.contentSecondary.alpha, 0.001f)
    }

    @Test
    fun `没有铺底时不调整`() {
        val accent = Color(0xFFC20C0C)
        val result = onCoverColors(emptyList(), Color.White, accent, SECONDARY, TERTIARY)
        assertEquals(accent, result.accent)
    }
}

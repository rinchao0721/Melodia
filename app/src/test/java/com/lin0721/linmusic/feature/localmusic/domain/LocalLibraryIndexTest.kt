package com.lin0721.linmusic.feature.localmusic.domain

import com.lin0721.linmusic.feature.localmusic.data.scan.encodeTrackNumber
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LocalLibraryIndexTest {

    // ======================= 歌手拆分 =======================

    @Test
    fun `逗号分隔的多位歌手拆开`() {
        assertEquals(listOf("水浪愛巳", "康貞蘭"), splitArtists("水浪愛巳, 康貞蘭"))
    }

    @Test
    fun `中文顿号与全角逗号都能拆`() {
        assertEquals(listOf("芳賀敬太", "永田大祐", "茶太"), splitArtists("芳賀敬太、永田大祐，茶太"))
    }

    @Test
    fun `feat 与 ft 前后的歌手拆开且不区分大小写`() {
        assertEquals(listOf("Alan Walker", "Sabrina"), splitArtists("Alan Walker feat. Sabrina"))
        assertEquals(listOf("A", "B"), splitArtists("A FT B"))
    }

    @Test
    fun `名字里包含 feat 字样不误拆`() {
        assertEquals(listOf("Featherweight"), splitArtists("Featherweight"))
    }

    @Test
    fun `重复与空白片段被去掉`() {
        assertEquals(listOf("茶太"), splitArtists("茶太 / 茶太 / "))
    }

    @Test
    fun `空歌手名归为未知艺术家`() {
        assertEquals(listOf(UNKNOWN_ARTIST_NAME), splitArtists("  "))
    }

    // ======================= 音轨号编码 =======================

    @Test
    fun `音轨号取斜杠前的数字`() {
        assertEquals(3, encodeTrackNumber("3/12", null))
    }

    @Test
    fun `碟号编码进千位`() {
        assertEquals(2005, encodeTrackNumber("5", "2/2"))
    }

    @Test
    fun `无效音轨号返回空`() {
        assertNull(encodeTrackNumber("A1", "1"))
        assertNull(encodeTrackNumber(null, "1"))
        assertNull(encodeTrackNumber("0", null))
    }

    // ======================= 专辑名识别 =======================

    @Test
    fun `展示层保留扫描层已经核实的专辑标签`() {
        assertEquals("50 Classical Masterworks", albumTitleOf("50 Classical Masterworks"))
    }

    @Test
    fun `正常专辑名保留`() {
        assertEquals("魔法使いの夜", albumTitleOf(" 魔法使いの夜 "))
    }

    @Test
    fun `空专辑名与 unknown 视为无专辑`() {
        assertNull(albumTitleOf(""))
        assertNull(albumTitleOf("<unknown>"))
        assertNull(albumTitleOf(null))
    }

    @Test
    fun `导入条目的嵌入专辑名直接保留`() {
        assertEquals("Music", albumTitleOf("Music"))
    }
}

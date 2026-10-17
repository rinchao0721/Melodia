package com.lin0721.linmusic.feature.artist.domain.catalog

import org.junit.Assert.*
import org.junit.Test

class ArtistCatalogTitleNormalizerTest {
    private val normalizer = ArtistCatalogTitleNormalizer()

    @Test
    fun bundledConverterLoadsTheCompleteOfficialCharacterDictionary() {
        assertEquals(4148, OpenCcCharacterConverter.bundled.mappingCount)
        assertEquals("陈奕迅认了吧音乐台里", OpenCcCharacterConverter.bundled.convert("陳奕迅認了吧音樂臺裏"))
        assertEquals("干", OpenCcCharacterConverter.bundled.convert("乾")) // First upstream target, not an invented mapping.
        assertNotNull(javaClass.getResourceAsStream("/catalog/opencc/LICENSE"))
    }

    @Test
    fun caseWidthPunctuationAndChineseScriptsFoldConsistently() {
        assertEquals(normalizer.normalizeTitle("Ｌ．Ｏ．Ｖ．Ｅ．"), normalizer.normalizeTitle("L.O.V.E."))
        assertEquals(normalizer.normalizeTitle("認了吧"), normalizer.normalizeTitle("认了吧"))
    }

    @Test
    fun editionOnlyDescriptorsAreRemovedAndNestedTrailingDescriptorsAreSupported() {
        listOf("A (2018 Remastered)", "A [Deluxe Edition]", "A (US Version)", "A（豪華重製版）", "A (Japan Edition) (Deluxe Edition)", "A 10th Anniversary Edition").forEach {
            assertEquals(it, "a", normalizer.baseTitle(it))
        }
    }

    @Test
    fun meaningfulOriginalSuffixesAndRecordingVersionsArePreserved() {
        listOf("A (Part II)", "A (Live)", "A (Remix)", "A (Acoustic)", "A (Live Remastered)", "Live for Today").forEach {
            assertEquals(it, normalizer.normalizeTitle(it), normalizer.baseTitle(it))
        }
    }

    @Test
    fun superDeluxeAndNestedRemasterDescriptionsResolveToTheOriginalAlbum() {
        listOf(
            "The Joshua Tree (Super Deluxe)",
            "The Joshua Tree (Super Deluxe Edition (2017 Remastered))",
            "The Joshua Tree (2017 Remastered) Super Deluxe Edition",
            "The Joshua Tree [Deluxe Edition / 30th Anniversary]",
            "The Joshua Tree (Re-mastered Version)",
            "The Joshua Tree — 30th Anniversary Super Deluxe Edition",
            "The Joshua Tree (2017 Remastered) (US Version) (Digital Edition) (Deluxe Edition) (Limited Edition)"
        ).forEach { assertEquals(it, "thejoshuatree", normalizer.baseTitle(it)) }
    }

    @Test
    fun fullwidthNestedDescriptorsNormalizeBeforeParsingAndRecordingSuffixesRemain() {
        assertEquals("thejoshuatree", normalizer.baseTitle("Ｔｈｅ Ｊｏｓｈｕａ Ｔｒｅｅ（Ｓｕｐｅｒ Ｄｅｌｕｘｅ Ｅｄｉｔｉｏｎ）"))
        listOf(
            "The Joshua Tree (Live) (Super Deluxe Edition)",
            "The Joshua Tree (Super Deluxe Live Edition)",
            "The Joshua Tree (2017 Remastered / Acoustic)",
            "The Joshua Tree (Super Deluxe Edition (Live))"
        ).forEach { assertTrue(it, normalizer.baseTitle(it).contains("live") || normalizer.baseTitle(it).contains("acoustic")) }
    }

    @Test
    fun converterCanBeInjectedAndSupplementaryUnicodeLettersArePreserved() {
        val identity = ArtistCatalogTitleNormalizer { it }
        assertEquals("𠮷野家", identity.normalizeTitle("𠮷野家"))
        assertNotEquals(identity.normalizeTitle("認了吧"), identity.normalizeTitle("认了吧"))
    }
}

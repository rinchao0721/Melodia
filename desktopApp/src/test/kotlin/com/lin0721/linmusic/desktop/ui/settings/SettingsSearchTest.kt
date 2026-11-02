package com.lin0721.linmusic.desktop.ui.settings

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsSearchTest {

    private val cacheDir = listOf("缓存目录", "D:/Music/MelodiaCache", "存储", "Cache")

    @Test
    fun blankQueryMatchesEverything() {
        assertTrue(matchesSettingsQuery("", cacheDir))
        assertTrue(matchesSettingsQuery("   ", cacheDir))
        assertTrue(matchesSettingsQuery("", emptyList()))
    }

    @Test
    fun singleTermMatchesAnyField() {
        assertTrue(matchesSettingsQuery("缓存", cacheDir))
        assertTrue(matchesSettingsQuery("存储", cacheDir))
        assertTrue(matchesSettingsQuery("melodia", cacheDir))
    }

    @Test
    fun matchingIgnoresCase() {
        assertTrue(matchesSettingsQuery("CACHE", cacheDir))
        assertTrue(matchesSettingsQuery("mElOdIaCaChE", cacheDir))
    }

    @Test
    fun allTermsMustHit() {
        assertTrue(matchesSettingsQuery("缓存 目录", cacheDir))
        assertTrue(matchesSettingsQuery("  缓存   存储  ", cacheDir))
        assertFalse(matchesSettingsQuery("缓存 快捷键", cacheDir))
    }

    @Test
    fun unrelatedQueryDoesNotMatch() {
        assertFalse(matchesSettingsQuery("快捷键", cacheDir))
        assertFalse(matchesSettingsQuery("缓存", emptyList()))
    }
}

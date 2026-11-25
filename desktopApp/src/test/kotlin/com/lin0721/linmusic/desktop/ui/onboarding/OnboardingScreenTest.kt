package com.lin0721.linmusic.desktop.ui.onboarding

import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OnboardingScreenTest {

    @Test
    fun screenStateFollowsPreference() {
        assertEquals(OnboardingScreenState.LOADING, resolveOnboardingScreen(null))
        assertEquals(OnboardingScreenState.SHOWN, resolveOnboardingScreen(false))
        assertEquals(OnboardingScreenState.HIDDEN, resolveOnboardingScreen(true))
    }

    @Test
    fun tilesFallBackToGradientWithoutCovers() {
        val tiles = collageTiles(emptyList(), 6)
        assertEquals(6, tiles.size)
        assertTrue(tiles.all { it == null })
        assertEquals(emptyList<String?>(), collageTiles(listOf("a"), 0))
    }

    @Test
    fun tilesCycleThroughCovers() {
        val urls = listOf("a", "b", "c")
        val tiles = collageTiles(urls, 7)
        assertEquals(7, tiles.size)
        assertEquals(urls.toSet(), tiles.toSet())
        val five = listOf("1", "2", "3", "4", "5")
        assertEquals(five, collageTiles(five, 5))
    }

    @Test
    fun gridDimensionsStayInRange() {
        assertEquals(4, collageColumns(100.dp, 132.dp))
        assertEquals(9, collageColumns(1200.dp, 132.dp))
        assertEquals(16, collageColumns(10000.dp, 132.dp))
        assertEquals(6, collageRows(700.dp, 138.dp))
    }

    @Test
    fun twoStepStateTogglesBetweenModeAndPlatform() {
        var showPlatformStep = false
        assertFalse(showPlatformStep)
        showPlatformStep = true
        assertTrue(showPlatformStep)
        showPlatformStep = false
        assertFalse(showPlatformStep)
    }
}

package com.lin0721.linmusic.desktop.app

import com.lin0721.linmusic.core.preferences.PreferencesStores
import com.lin0721.linmusic.desktop.data.DesktopPreferences
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.nio.file.Files

class WindowBoundsTest {

    private val primary = WindowBounds(0, 0, 1920, 1080)
    private val secondary = WindowBounds(1920, 0, 1920, 1080)

    private lateinit var dir: File

    @Before
    fun setUp() {
        dir = Files.createTempDirectory("melodia-window-test").toFile()
    }

    @After
    fun tearDown() {
        dir.deleteRecursively()
    }

    private fun preferences(name: String = "prefs") =
        DesktopPreferences(PreferencesStores.get(File(dir, "$name.preferences_pb")))

    @Test
    fun encodeAndDecodeRoundTrip() {
        val bounds = WindowBounds(-120, 40, 1280, 800)
        assertEquals(bounds, WindowBounds.decode(bounds.encode()))
    }

    @Test
    fun decodeRejectsMalformedAndTooSmall() {
        assertNull(WindowBounds.decode(""))
        assertNull(WindowBounds.decode("1,2,3"))
        assertNull(WindowBounds.decode("a,b,c,d"))
        assertNull(WindowBounds.decode("0,0,100,100"))
        assertNull(WindowBounds.decode("0,0,1280,800,5"))
    }

    @Test
    fun windowInsideScreenIsReachable() {
        assertTrue(WindowBounds(100, 100, 1280, 800).isReachableOn(listOf(primary)))
    }

    @Test
    fun windowOnUnpluggedMonitorIsNotReachable() {
        val onSecondary = WindowBounds(2000, 100, 1280, 800)
        assertTrue(onSecondary.isReachableOn(listOf(primary, secondary)))
        assertFalse(onSecondary.isReachableOn(listOf(primary.copy(width = 1500))))
    }

    @Test
    fun windowMostlyOffScreenButWithEnoughTitleStripIsReachable() {
        assertTrue(WindowBounds(-1000, 50, 1280, 800).isReachableOn(listOf(primary)))
        assertFalse(WindowBounds(-1200, 50, 1280, 800).isReachableOn(listOf(primary)))
    }

    @Test
    fun windowAboveOrBelowScreenIsNotReachable() {
        assertFalse(WindowBounds(100, -200, 1280, 800).isReachableOn(listOf(primary)))
        assertFalse(WindowBounds(100, 1060, 1280, 800).isReachableOn(listOf(primary)))
    }

    @Test
    fun noScreensMeansNotReachable() {
        assertFalse(WindowBounds(0, 0, 1280, 800).isReachableOn(emptyList()))
    }

    @Test
    fun defaultsWhenNothingSaved() = runBlocking {
        val saved = preferences().loadWindow()
        assertNull(saved.bounds)
        assertFalse(saved.maximized)
    }

    @Test
    fun savedBoundsAndMaximizedSurviveReload() = runBlocking {
        val prefs = preferences()
        val bounds = WindowBounds(200, 150, 1400, 900)
        prefs.saveWindow(bounds, false)
        assertEquals(SavedWindow(bounds, false), prefs.loadWindow())
        prefs.saveWindow(null, true)
        assertEquals(SavedWindow(bounds, true), prefs.loadWindow())
    }

    @Test
    fun scaledMinSizeGrowsWithScale() {
        assertEquals(MIN_WINDOW_WIDTH to MIN_WINDOW_HEIGHT, scaledMinWindowSize(1f, 1920, 1080))
        assertEquals(1440 to 900, scaledMinWindowSize(1.5f, 1920, 1080))
    }

    @Test
    fun scaledMinSizeCappedByScreen() {
        assertEquals(1920 to 1080, scaledMinWindowSize(2.5f, 1920, 1080))
    }

    @Test
    fun scaledMinSizeNeverBelowBaseWhenScaleBelowOne() {
        assertEquals(MIN_WINDOW_WIDTH to MIN_WINDOW_HEIGHT, scaledMinWindowSize(0.8f, 1920, 1080))
    }

    @Test
    fun scaledMinSizeNeverBelowBaseWhenScreenUnknown() {
        assertEquals(MIN_WINDOW_WIDTH to MIN_WINDOW_HEIGHT, scaledMinWindowSize(2f, 0, 0))
    }
}

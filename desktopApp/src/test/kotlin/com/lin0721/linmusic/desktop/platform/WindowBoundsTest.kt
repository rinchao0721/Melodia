package com.lin0721.linmusic.desktop.platform

import com.lin0721.linmusic.core.preferences.PreferencesStores
import java.io.File
import java.nio.file.Files
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

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

    // ── clampInto：恢复窗口时夹紧到屏幕内（旧版像素坐标按逻辑坐标换算后可能越界） ──

    private val logicalScreens = listOf(
        WindowBounds(0, 0, 2560, 1440),
        WindowBounds(2560, 0, 2560, 1440)
    )

    @Test
    fun clampIntoKeepsWindowAlreadyInside() {
        val bounds = WindowBounds(100, 100, 1200, 800)
        assertEquals(bounds, bounds.clampInto(logicalScreens))
    }

    @Test
    fun clampIntoTranslatesWindowBackInside() {
        // 右侧与下方都超出第二块屏幕：平移到屏内（y 上移到 116 以完整显示）
        val bounds = WindowBounds(4798, 477, 2084, 1324)
        assertEquals(WindowBounds(3036, 116, 2084, 1324), bounds.clampInto(logicalScreens))
    }

    @Test
    fun clampIntoShrinksOversizedWindow() {
        val bounds = WindowBounds(0, 0, 4000, 2000)
        assertEquals(WindowBounds(0, 0, 2560, 1440), bounds.clampInto(logicalScreens))
    }

    @Test
    fun clampIntoFallsBackToFirstScreenWhenFullyOutside() {
        val bounds = WindowBounds(9000, 0, 1200, 800)
        assertEquals(WindowBounds(1360, 0, 1200, 800), bounds.clampInto(logicalScreens))
    }

    @Test
    fun clampIntoWithoutScreensKeepsBounds() {
        val bounds = WindowBounds(100, 100, 1200, 800)
        assertEquals(bounds, bounds.clampInto(emptyList()))
    }
}

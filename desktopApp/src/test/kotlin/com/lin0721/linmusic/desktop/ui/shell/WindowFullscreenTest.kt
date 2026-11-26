package com.lin0721.linmusic.desktop.ui.shell

import androidx.compose.ui.window.WindowPlacement
import androidx.compose.ui.window.WindowState
import com.lin0721.linmusic.desktop.platform.BorderlessFullscreenRestore
import com.lin0721.linmusic.desktop.platform.WindowEdge
import com.lin0721.linmusic.desktop.platform.WindowEffects
import com.lin0721.linmusic.desktop.platform.windows.MonitorInfo
import com.lin0721.linmusic.desktop.platform.windows.WinRect
import com.lin0721.linmusic.desktop.platform.windows.WindowPlacementInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeFalse
import org.junit.Test
import java.awt.GraphicsEnvironment
import java.awt.Point
import java.awt.Window

class WindowFullscreenTest {

    private class FakeWindowEffects(
        override val supportsBorderlessFullscreen: Boolean = true,
        private val enterResult: ((Window) -> BorderlessFullscreenRestore?)? = null
    ) : WindowEffects {
        var enterCalls = 0
        var exitCalls = 0
        var reapplyCalls = 0
        var lastExitRestore: BorderlessFullscreenRestore? = null

        override fun install(window: Window) = Unit
        override fun uninstall(window: Window) = Unit
        override fun applyFrame(window: Window, maximized: Boolean, borderRgb: Int) = Unit
        override fun systemBorderAccent(): Int? = null
        override fun systemAppsUseLightTheme(): Boolean = false
        override fun observeSystemTheme(onChanged: () -> Unit): () -> Unit = {}
        override val supportsClickThrough: Boolean = false
        override fun setClickThrough(window: Window, enabled: Boolean) = Unit
        override fun beginSystemMove(window: Window): Boolean = false
        override fun beginSystemResize(window: Window, edge: WindowEdge): Boolean = false
        override fun cursorPhysicalPosition(): Point? = null

        override fun enterBorderlessFullscreen(window: Window): BorderlessFullscreenRestore? {
            enterCalls++
            return enterResult?.invoke(window)
        }

        override fun exitBorderlessFullscreen(window: Window, restore: BorderlessFullscreenRestore) {
            exitCalls++
            lastExitRestore = restore
        }

        override fun reapplyBorderlessFullscreen(window: Window, restore: BorderlessFullscreenRestore) {
            reapplyCalls++
        }
    }

    private fun testWindow(displayable: Boolean): Window {
        assumeFalse(GraphicsEnvironment.isHeadless())
        return object : Window(null) {
            override fun isDisplayable(): Boolean = displayable
        }
    }

    private fun sampleRestore(wasMaximized: Boolean) = BorderlessFullscreenRestore(
        x = 100,
        y = 80,
        width = 1280,
        height = 800,
        wasMaximized = wasMaximized,
        normalLeft = 100,
        normalTop = 80,
        normalRight = 1380,
        normalBottom = 880,
        fullscreenX = -1920,
        fullscreenY = 0,
        fullscreenWidth = 1920,
        fullscreenHeight = 1080
    )

    @Test
    fun `无绑定窗口时回退到Compose全屏并正常还原`() {
        val windowState = WindowState(placement = WindowPlacement.Maximized)
        val effects = FakeWindowEffects(supportsBorderlessFullscreen = true) { sampleRestore(wasMaximized = true) }
        val fullscreen = FullscreenState(windowState, effects)

        fullscreen.toggle()
        assertTrue(fullscreen.isFullscreen)
        assertFalse(fullscreen.isBorderlessFullscreen)
        assertEquals(WindowPlacement.Fullscreen, windowState.placement)
        assertEquals(0, effects.enterCalls)

        fullscreen.exit()
        assertFalse(fullscreen.isFullscreen)
        assertEquals(WindowPlacement.Maximized, windowState.placement)
    }

    @Test
    fun `不支持无边框全屏的平台回退到Compose全屏`() {
        val window = testWindow(displayable = true)
        val windowState = WindowState(placement = WindowPlacement.Floating)
        val effects = FakeWindowEffects(supportsBorderlessFullscreen = false)
        val fullscreen = FullscreenState(windowState, effects).apply { attachWindow(window) }

        fullscreen.toggle()
        assertTrue(fullscreen.isFullscreen)
        assertFalse(fullscreen.isBorderlessFullscreen)
        assertEquals(WindowPlacement.Fullscreen, windowState.placement)
        assertEquals(0, effects.enterCalls)

        fullscreen.exit()
        assertFalse(fullscreen.isFullscreen)
        assertEquals(WindowPlacement.Floating, windowState.placement)
    }

    @Test
    fun `窗口尚未可显示时回退到Compose全屏`() {
        val window = testWindow(displayable = false)
        val windowState = WindowState(placement = WindowPlacement.Floating)
        val effects = FakeWindowEffects(supportsBorderlessFullscreen = true) { sampleRestore(wasMaximized = false) }
        val fullscreen = FullscreenState(windowState, effects).apply { attachWindow(window) }

        fullscreen.toggle()
        assertTrue(fullscreen.isFullscreen)
        assertFalse(fullscreen.isBorderlessFullscreen)
        assertEquals(WindowPlacement.Fullscreen, windowState.placement)
        assertEquals(0, effects.enterCalls)
    }

    @Test
    fun `浮动窗口进入与退出无边框全屏不触发AWT全屏且保留Floating`() {
        val window = testWindow(displayable = true)
        val windowState = WindowState(placement = WindowPlacement.Floating)
        val restore = sampleRestore(wasMaximized = false)
        val effects = FakeWindowEffects(supportsBorderlessFullscreen = true) { restore }
        val fullscreen = FullscreenState(windowState, effects).apply { attachWindow(window) }

        fullscreen.toggle()
        assertTrue(fullscreen.isFullscreen)
        assertTrue(fullscreen.isBorderlessFullscreen)
        assertEquals(WindowPlacement.Floating, windowState.placement)
        assertEquals(1, effects.enterCalls)
        assertEquals(0, fullscreen.syncGeneration)

        fullscreen.exit()
        assertFalse(fullscreen.isFullscreen)
        assertFalse(fullscreen.isBorderlessFullscreen)
        assertEquals(WindowPlacement.Floating, windowState.placement)
        assertEquals(1, effects.exitCalls)
        assertEquals(restore, effects.lastExitRestore)
    }

    @Test
    fun `最大化窗口进入与退出无边框全屏时正确同步placement与原生恢复`() {
        val window = testWindow(displayable = true)
        val windowState = WindowState(placement = WindowPlacement.Maximized)
        val restore = sampleRestore(wasMaximized = true)
        val effects = FakeWindowEffects(supportsBorderlessFullscreen = true) { restore }
        val fullscreen = FullscreenState(windowState, effects).apply { attachWindow(window) }

        fullscreen.toggle()
        assertTrue(fullscreen.isFullscreen)
        assertTrue(fullscreen.isBorderlessFullscreen)
        assertEquals(WindowPlacement.Floating, windowState.placement)
        assertEquals(1, effects.enterCalls)
        val genAfterEnter = fullscreen.syncGeneration
        assertTrue(genAfterEnter > 0)

        fullscreen.syncBorderlessWindow()
        assertEquals(1, effects.reapplyCalls)

        fullscreen.exit()
        assertFalse(fullscreen.isFullscreen)
        assertFalse(fullscreen.isBorderlessFullscreen)
        assertEquals(WindowPlacement.Maximized, windowState.placement)
        assertEquals(1, effects.exitCalls)
        assertEquals(restore, effects.lastExitRestore)
        assertTrue(fullscreen.syncGeneration > genAfterEnter)

        fullscreen.syncBorderlessWindow()
        assertEquals(2, effects.exitCalls)
    }

    @Test
    fun `原生进入无边框全屏失败时回退到Compose全屏`() {
        val window = testWindow(displayable = true)
        val windowState = WindowState(placement = WindowPlacement.Maximized)
        val effects = FakeWindowEffects(supportsBorderlessFullscreen = true) { null }
        val fullscreen = FullscreenState(windowState, effects).apply { attachWindow(window) }

        fullscreen.toggle()
        assertTrue(fullscreen.isFullscreen)
        assertFalse(fullscreen.isBorderlessFullscreen)
        assertEquals(WindowPlacement.Fullscreen, windowState.placement)
        assertEquals(1, effects.enterCalls)

        fullscreen.exit()
        assertFalse(fullscreen.isFullscreen)
        assertEquals(WindowPlacement.Maximized, windowState.placement)
        assertEquals(0, effects.exitCalls)
    }

    @Test
    fun `解绑窗口后清空无边框全屏状态`() {
        val window = testWindow(displayable = true)
        val windowState = WindowState(placement = WindowPlacement.Floating)
        val effects = FakeWindowEffects(supportsBorderlessFullscreen = true) { sampleRestore(wasMaximized = false) }
        val fullscreen = FullscreenState(windowState, effects).apply { attachWindow(window) }

        fullscreen.toggle()
        assertTrue(fullscreen.isFullscreen)

        fullscreen.detachWindow(window)
        assertFalse(fullscreen.isFullscreen)
        assertFalse(fullscreen.isBorderlessFullscreen)
    }

    @Test
    fun `Win32结构体符合64位ABI字节大小`() {
        val rect = WinRect()
        val monitorInfo = MonitorInfo()
        val placement = WindowPlacementInfo()
        assertEquals(16, rect.size())
        assertEquals(40, monitorInfo.size())
        assertEquals(40, monitorInfo.cbSize)
        assertEquals(44, placement.size())
        assertEquals(44, placement.length)
    }
}

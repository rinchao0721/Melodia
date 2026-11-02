package com.lin0721.linmusic.desktop.platform.windows.taskbar

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TaskbarModelTest {

    // 无曲目时三个按钮都隐藏
    @Test
    fun hidesAllButtonsWithoutTrack() {
        val specs = thumbButtonSpecs(hasTrack = false, playing = false)
        assertEquals(3, specs.size)
        assertTrue(specs.all { it.hidden })
    }

    // 暂停时按钮仍显示，中间按钮是“播放”
    @Test
    fun showsPlayIconWhenPaused() {
        val specs = thumbButtonSpecs(hasTrack = true, playing = false)
        assertTrue(specs.none { it.hidden })
        assertEquals(listOf(ThumbIcon.PREVIOUS, ThumbIcon.PLAY, ThumbIcon.NEXT), specs.map { it.icon })
        assertEquals("播放", specs[1].tooltip)
    }

    @Test
    fun showsPauseIconWhenPlaying() {
        val specs = thumbButtonSpecs(hasTrack = true, playing = true)
        assertEquals(ThumbIcon.PAUSE, specs[1].icon)
        assertEquals("暂停", specs[1].tooltip)
    }

    // 上一首、下一首不因队列边界置灰，按钮始终可点
    @Test
    fun buttonIdsAreStableAndMapToCommands() {
        val specs = thumbButtonSpecs(hasTrack = true, playing = true)
        assertEquals(listOf(BUTTON_PREVIOUS, BUTTON_TOGGLE, BUTTON_NEXT), specs.map { it.id })
        assertEquals(ThumbCommand.PREVIOUS, commandForButton(BUTTON_PREVIOUS))
        assertEquals(ThumbCommand.TOGGLE, commandForButton(BUTTON_TOGGLE))
        assertEquals(ThumbCommand.NEXT, commandForButton(BUTTON_NEXT))
        assertNull(commandForButton(99))
    }

    @Test
    fun squareSizeUsesShorterSideAndStaysPositive() {
        assertEquals(150, squareSizeWithin(200, 150))
        assertEquals(120, squareSizeWithin(120, 300))
        assertEquals(1, squareSizeWithin(0, 0))
    }

    private fun personalize(value: String?) = buildString {
        appendLine()
        appendLine("HKEY_CURRENT_USER\\Software\\Microsoft\\Windows\\CurrentVersion\\Themes\\Personalize")
        appendLine("    AppsUseLightTheme    REG_DWORD    0x1")
        if (value != null) appendLine("    SystemUsesLightTheme    REG_DWORD    $value")
        appendLine("    EnableTransparency    REG_DWORD    0x1")
    }

    @Test
    fun parsesSystemTheme() {
        assertEquals(true, parseSystemUsesLightTheme(personalize("0x1")))
        assertEquals(false, parseSystemUsesLightTheme(personalize("0x0")))
    }

    // 应用主题与任务栏主题是两个独立的值
    @Test
    fun parsesAppsThemeIndependently() {
        assertEquals(true, parseAppsUseLightTheme(personalize("0x0")))
        assertEquals(false, parseAppsUseLightTheme(personalize("0x1").replace("AppsUseLightTheme    REG_DWORD    0x1", "AppsUseLightTheme    REG_DWORD    0x0")))
        assertNull(parseAppsUseLightTheme(""))
    }

    @Test
    fun returnsNullWhenThemeValueMissing() {
        assertNull(parseSystemUsesLightTheme(personalize(null)))
        assertFalse(parseSystemUsesLightTheme("").let { it == true })
    }
}

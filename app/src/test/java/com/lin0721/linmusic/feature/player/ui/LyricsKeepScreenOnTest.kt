package com.lin0721.linmusic.feature.player.ui

import com.lin0721.linmusic.feature.settings.ui.SettingsSearchIndex
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LyricsKeepScreenOnTest {

    @Test
    fun keepScreenOnActiveOnlyWhenEnabledVisibleOnLyricsAndPlaying() {
        assertTrue(
            shouldKeepScreenOn(
                keepScreenOnEnabled = true,
                isPlayerVisible = true,
                isLyricsScreenVisible = true,
                isPlaying = true
            )
        )
        assertFalse(
            shouldKeepScreenOn(
                keepScreenOnEnabled = false,
                isPlayerVisible = true,
                isLyricsScreenVisible = true,
                isPlaying = true
            )
        )
        assertFalse(
            shouldKeepScreenOn(
                keepScreenOnEnabled = true,
                isPlayerVisible = false,
                isLyricsScreenVisible = true,
                isPlaying = true
            )
        )
        assertFalse(
            shouldKeepScreenOn(
                keepScreenOnEnabled = true,
                isPlayerVisible = true,
                isLyricsScreenVisible = false,
                isPlaying = true
            )
        )
        assertFalse(
            shouldKeepScreenOn(
                keepScreenOnEnabled = true,
                isPlayerVisible = true,
                isLyricsScreenVisible = true,
                isPlaying = false
            )
        )
    }

    @Test
    fun settingsSearchFindsKeepScreenOnEntry() {
        assertTrue(SettingsSearchIndex.search("常亮").any { it.title == "歌词界面保持屏幕常亮" })
        assertTrue(SettingsSearchIndex.search("息屏").any { it.title == "歌词界面保持屏幕常亮" })
    }
}

package com.lin0721.linmusic.desktop.data

import com.lin0721.linmusic.core.player.floatinglyric.FloatingLyricAlign
import com.lin0721.linmusic.core.player.floatinglyric.FloatingLyricSecondary
import com.lin0721.linmusic.core.preferences.PreferencesStores
import com.lin0721.linmusic.desktop.app.WindowBounds
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import java.io.File
import java.nio.file.Files

class MiniPlayerPrefsTest {

    private lateinit var dir: File
    private lateinit var preferences: DesktopPreferences

    @Before
    fun setUp() {
        dir = Files.createTempDirectory("melodia-mini-player-prefs-test").toFile()
        preferences = DesktopPreferences(PreferencesStores.get(File(dir, "desktop.preferences_pb")))
    }

    @After
    fun tearDown() {
        dir.deleteRecursively()
    }

    @Test
    fun styleCyclesThroughAllEntries() {
        assertEquals(MiniPlayerStyle.CARD, MiniPlayerStyle.BAR.next())
        assertEquals(MiniPlayerStyle.LYRIC, MiniPlayerStyle.CARD.next())
        assertEquals(MiniPlayerStyle.BAR, MiniPlayerStyle.LYRIC.next())
    }

    @Test
    fun unknownStyleNameFallsBackToDefault() {
        assertEquals(MiniPlayerStyle.DEFAULT, MiniPlayerStyle.fromName(null))
        assertEquals(MiniPlayerStyle.DEFAULT, MiniPlayerStyle.fromName("REMOVED"))
        assertEquals(MiniPlayerStyle.LYRIC, MiniPlayerStyle.fromName("LYRIC"))
    }

    @Test
    fun positionRoundTripsAndRejectsMalformed() {
        assertEquals(MiniPlayerPosition(-120, 840), MiniPlayerPosition.decode(MiniPlayerPosition(-120, 840).encode()))
        assertNull(MiniPlayerPosition.decode(""))
        assertNull(MiniPlayerPosition.decode("10"))
        assertNull(MiniPlayerPosition.decode("10,20,30"))
        assertNull(MiniPlayerPosition.decode("a,20"))
    }

    @Test
    fun fitIntoPullsWindowBackInsideArea() {
        val area = WindowBounds(0, 0, 1920, 1040)

        assertEquals(MiniPlayerPosition(100, 200), MiniPlayerPosition(100, 200).fitInto(360, 72, area))
        assertEquals(MiniPlayerPosition(1560, 820), MiniPlayerPosition(1800, 1000).fitInto(360, 220, area))
        assertEquals(MiniPlayerPosition(0, 0), MiniPlayerPosition(-50, -10).fitInto(360, 72, area))
        assertEquals(MiniPlayerPosition(0, 0), MiniPlayerPosition(30, 30).fitInto(4000, 3000, area))
    }

    @Test
    fun lyricSettingsDefaultAndRoundTrip() = runBlocking {
        assertEquals(MiniLyricSettings(), preferences.miniLyricSettings.first())

        val custom = MiniLyricSettings(
            MiniLyricFontSize.LARGE, FloatingLyricSecondary.ROMA, karaoke = false, showSideLines = false, align = FloatingLyricAlign.END
        )
        preferences.saveMiniLyricSettings(custom)

        assertEquals(custom, preferences.miniLyricSettings.first())
    }

    @Test
    fun unsupportedSecondaryFallsBackToDefault() {
        assertEquals(FloatingLyricSecondary.TRANSLATION, MiniLyricSettings.secondaryFromKey(FloatingLyricSecondary.NEXT.key))
        assertEquals(FloatingLyricSecondary.NONE, MiniLyricSettings.secondaryFromKey(FloatingLyricSecondary.NONE.key))
        assertEquals(MiniLyricFontSize.MEDIUM, MiniLyricFontSize.fromName("HUGE"))
        assertEquals(FloatingLyricAlign.START, MiniLyricSettings.alignFromKey(null))
        assertEquals(FloatingLyricAlign.CENTER, MiniLyricSettings.alignFromKey(FloatingLyricAlign.CENTER.key))
    }

    @Test
    fun sizeDecodeClampsToStyleLimits() {
        assertEquals(MiniPlayerSize(500, 300, 1.2f), MiniPlayerSize.decode("500,300,1.2", MiniPlayerStyle.LYRIC))
        // 横条高度、方卡宽高不可调，存了别的值也回到默认
        assertEquals(MiniPlayerSize(500, 72, 1f), MiniPlayerSize.decode("500,300,1", MiniPlayerStyle.BAR))
        assertEquals(MiniPlayerSize(240, 240, 9f), MiniPlayerSize.decode("400,100,9", MiniPlayerStyle.CARD))
        assertEquals(MiniPlayerSize(1500, 900, 3f), MiniPlayerSize.decode("1500,900,3", MiniPlayerStyle.LYRIC))
        assertEquals(MiniPlayerSize(288, 176, 0.75f), MiniPlayerSize.decode("1,1,0.1", MiniPlayerStyle.LYRIC))
        assertNull(MiniPlayerSize.decode("500,300", MiniPlayerStyle.LYRIC))
        assertNull(MiniPlayerSize.decode("500,300,NaN", MiniPlayerStyle.LYRIC))
    }

    @Test
    fun sizesAreStoredPerStyle() = runBlocking {
        assertEquals(MiniPlayerStyle.entries.associateWith { it.defaultSize }, preferences.miniPlayerSizes.first())

        preferences.saveMiniPlayerSize(MiniPlayerStyle.LYRIC, MiniPlayerSize(420, 300, 1.25f))
        val sizes = preferences.miniPlayerSizes.first()

        assertEquals(MiniPlayerSize(420, 300, 1.25f), sizes[MiniPlayerStyle.LYRIC])
        assertEquals(MiniPlayerStyle.BAR.defaultSize, sizes[MiniPlayerStyle.BAR])
    }

    @Test
    fun defaultsBeforeAnythingIsSaved() = runBlocking {
        assertEquals(MiniPlayerStyle.DEFAULT, preferences.miniPlayerStyle.first())
        assertEquals(true, preferences.miniPlayerAlwaysOnTop.first())
        assertNull(preferences.loadMiniPlayerPosition())
    }

    @Test
    fun savedValuesAreReadBack() = runBlocking {
        preferences.saveMiniPlayerStyle(MiniPlayerStyle.CARD)
        preferences.saveMiniPlayerAlwaysOnTop(false)
        preferences.saveMiniPlayerPosition(MiniPlayerPosition(300, 400))

        assertEquals(MiniPlayerStyle.CARD, preferences.miniPlayerStyle.first())
        assertEquals(false, preferences.miniPlayerAlwaysOnTop.first())
        assertEquals(MiniPlayerPosition(300, 400), preferences.loadMiniPlayerPosition())
    }
}

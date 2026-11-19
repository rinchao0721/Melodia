package com.lin0721.linmusic.core.preferences

import com.lin0721.linmusic.core.player.PersonalFmMode
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Test
import java.io.File
import java.nio.file.Files

class SettingsPreferencesTest {

    private lateinit var dir: File
    private lateinit var settings: SettingsPreferences

    @Before
    fun setUp() {
        dir = Files.createTempDirectory("melodia-prefs-test").toFile()
        settings = SettingsPreferences(PreferencesStores.get(File(dir, "settings.preferences_pb")))
    }

    @After
    fun tearDown() {
        dir.deleteRecursively()
    }

    @Test
    fun fullScreenLyricKeepScreenOnDefaultsToFalseAndPersists() = runBlocking {
        assertFalse(settings.fullScreenLyricKeepScreenOn.first())

        settings.saveFullScreenLyricKeepScreenOn(true)
        assertEquals(true, settings.fullScreenLyricKeepScreenOn.first())

        settings.saveFullScreenLyricKeepScreenOn(false)
        assertEquals(false, settings.fullScreenLyricKeepScreenOn.first())
    }

    @Test
    fun bottomBarGradientDefaultsToFalseAndPersists() = runBlocking {
        assertFalse(settings.bottomBarGradient.first())

        settings.saveBottomBarGradient(true)
        assertEquals(true, settings.bottomBarGradient.first())

        settings.saveBottomBarGradient(false)
        assertEquals(false, settings.bottomBarGradient.first())
    }

    @Test
    fun personalFmModeDefaultsToDefaultAndPersists() = runBlocking {
        assertEquals(PersonalFmMode.DEFAULT, settings.personalFmMode.first())

        settings.savePersonalFmMode(PersonalFmMode.NIGHT_EMO)
        assertEquals(PersonalFmMode.NIGHT_EMO, settings.personalFmMode.first())

        settings.savePersonalFmMode(PersonalFmMode.EXPLORE)
        assertEquals(PersonalFmMode.EXPLORE, settings.personalFmMode.first())
    }

    @Test
    fun alwaysShowLikedBadgeDefaultsToTrueAndPersists() = runBlocking {
        assertEquals(true, settings.alwaysShowLikedBadge.first())

        settings.saveAlwaysShowLikedBadge(false)
        assertFalse(settings.alwaysShowLikedBadge.first())

        settings.saveAlwaysShowLikedBadge(true)
        assertEquals(true, settings.alwaysShowLikedBadge.first())
    }

    @Test
    fun homeSearchButtonAndDiscoveryTogglesDefaultToTrueAndPersist() = runBlocking {
        assertEquals(true, settings.homeSearchButtonEnabled.first())
        assertEquals(true, settings.hotSearchEnabled.first())
        assertEquals(true, settings.playlistTagsEnabled.first())

        settings.saveHomeSearchButtonEnabled(false)
        settings.saveHotSearchEnabled(false)
        settings.savePlaylistTagsEnabled(false)

        assertFalse(settings.homeSearchButtonEnabled.first())
        assertFalse(settings.hotSearchEnabled.first())
        assertFalse(settings.playlistTagsEnabled.first())

        settings.saveHomeSearchButtonEnabled(true)
        assertEquals(true, settings.homeSearchButtonEnabled.first())
    }

    @Test
    fun sidebarMessageDefaultsToTrueAndPersists() = runBlocking {
        assertEquals(true, settings.sidebarMessageEnabled.first())

        settings.saveSidebarMessageEnabled(false)
        assertFalse(settings.sidebarMessageEnabled.first())

        settings.saveSidebarMessageEnabled(true)
        assertEquals(true, settings.sidebarMessageEnabled.first())
    }
}

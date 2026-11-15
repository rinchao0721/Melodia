package com.lin0721.linmusic.core.preferences

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
}

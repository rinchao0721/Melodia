package com.lin0721.linmusic.desktop.data

import com.lin0721.linmusic.core.preferences.PreferencesStores
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import java.io.File
import java.nio.file.Files

class DesktopPreferencesVolumeTest {

    private lateinit var dir: File
    private lateinit var preferences: DesktopPreferences

    @Before
    fun setUp() {
        dir = Files.createTempDirectory("melodia-volume-prefs-test").toFile()
        preferences = DesktopPreferences(PreferencesStores.get(File(dir, "desktop.preferences_pb")))
    }

    @After
    fun tearDown() {
        dir.deleteRecursively()
    }

    @Test
    fun defaultsToFullVolume() = runBlocking {
        assertEquals(100, preferences.volume.first())
        assertEquals(100, preferences.lastAudibleVolume.first())
    }

    @Test
    fun savedVolumeAndMuteStateAreReadBack() = runBlocking {
        preferences.saveVolume(0, 35)

        assertEquals(0, preferences.volume.first())
        assertEquals(35, preferences.lastAudibleVolume.first())
    }

    @Test
    fun outOfRangeValuesAreClamped() = runBlocking {
        preferences.saveVolume(150, 0)

        assertEquals(100, preferences.volume.first())
        assertEquals(1, preferences.lastAudibleVolume.first())
    }
}

package com.lin0721.linmusic.core.preferences

import java.io.File
import java.nio.file.Files
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class ExclusiveOutputPreferencesTest {

    private lateinit var dir: File
    private lateinit var settings: SettingsPreferences

    @Before
    fun setUp() {
        dir = Files.createTempDirectory("melodia-exclusive-prefs").toFile()
        settings = SettingsPreferences(PreferencesStores.get(File(dir, "settings.preferences_pb")))
    }

    @After
    fun tearDown() {
        dir.deleteRecursively()
    }

    @Test
    fun `默认关闭`() = runBlocking {
        assertFalse(settings.exclusiveOutput.first())
    }

    @Test
    fun `开启独占会关闭交叉淡化与音频卸载`() = runBlocking {
        settings.saveCrossfadeEnabled(true)
        settings.saveExclusiveOutput(true)
        assertTrue(settings.exclusiveOutput.first())
        assertFalse(settings.crossfadeEnabled.first())
        assertFalse(settings.audioOffload.first())
    }

    @Test
    fun `开启交叉淡化或音频卸载会关闭独占`() = runBlocking {
        settings.saveExclusiveOutput(true)
        settings.saveCrossfadeEnabled(true)
        assertFalse(settings.exclusiveOutput.first())

        settings.saveExclusiveOutput(true)
        settings.saveAudioOffload(true)
        assertFalse(settings.exclusiveOutput.first())
    }

    @Test
    fun `关闭独占不恢复交叉淡化`() = runBlocking {
        settings.saveCrossfadeEnabled(true)
        settings.saveExclusiveOutput(true)
        settings.saveExclusiveOutput(false)
        assertFalse(settings.crossfadeEnabled.first())
    }
}

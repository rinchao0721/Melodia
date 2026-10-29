package com.lin0721.linmusic.feature.settings.ui

import com.lin0721.linmusic.core.log.AppLogger
import com.lin0721.linmusic.core.preferences.FullPlayerCardLayout
import com.lin0721.linmusic.core.preferences.PreferencesStores
import com.lin0721.linmusic.core.preferences.SettingsPreferences
import com.lin0721.linmusic.core.source.SourcePreferences
import com.lin0721.linmusic.core.ui.skin.SkinRepository
import com.lin0721.linmusic.core.ui.skin.SkinStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import java.io.File
import java.nio.file.Files

@OptIn(ExperimentalCoroutinesApi::class)
class CommonSettingsViewModelTest {

    private lateinit var dir: File
    private lateinit var settings: SettingsPreferences
    private lateinit var source: SourcePreferences
    private lateinit var vm: CommonSettingsViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        dir = Files.createTempDirectory("melodia-settings-test").toFile()
        settings = SettingsPreferences(PreferencesStores.get(File(dir, "settings.preferences_pb")))
        source = SourcePreferences(PreferencesStores.get(File(dir, "source.preferences_pb")))
        vm = CommonSettingsViewModel(settings, source, SkinRepository(SkinStore(File(dir, "skins")), settings))
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        AppLogger.setLevel(AppLogger.LogLevel.WARN)
        dir.deleteRecursively()
    }

    // 写入在 DataStore 自己的线程完成，等到读回期望值为止
    private fun <T> Flow<T>.awaitValue(expected: T) = runBlocking {
        withTimeoutOrNull(5_000) { first { it == expected } }
            ?: assertEquals(expected, first())
    }

    @Test
    fun playbackSettingsArePersisted() {
        vm.updateWifiQuality("hires")
        vm.updateCrossfadeEnabled(true)
        vm.updateCrossfadeDurationMs(6_000)

        settings.wifiQuality.awaitValue("hires")
        settings.crossfadeEnabled.awaitValue(true)
        settings.crossfadeDurationMs.awaitValue(6_000)
    }

    @Test
    fun uiScaleIsPersistedAndClamped() {
        settings.uiScalePercent.awaitValue(100)

        vm.updateUiScalePercent(225)
        settings.uiScalePercent.awaitValue(225)

        vm.updateUiScalePercent(900)
        settings.uiScalePercent.awaitValue(250)
    }

    @Test
    fun uiAnimationsDefaultOnAndPersisted() {
        settings.uiAnimationsEnabled.awaitValue(true)

        vm.updateUiAnimationsEnabled(false)
        settings.uiAnimationsEnabled.awaitValue(false)
    }

    @Test
    fun sourceSettingsArePersisted() {
        vm.updateFallbackEnabled(true)
        vm.updateCommunityPriority(true)
        vm.updateUnmRemoteFallbackEnabled(true)
        vm.updateUnmAutoMatch(false)
        vm.updateUnmServerUrl("https://unm.example.com")

        source.fallbackEnabled.awaitValue(true)
        source.communityPriority.awaitValue(true)
        source.unmRemoteFallbackEnabled.awaitValue(true)
        source.unmAutoMatch.awaitValue(false)
        source.unmServerUrl.awaitValue("https://unm.example.com")
    }

    @Test
    fun resetUnmServerUrlRestoresDefault() {
        val default = runBlocking { source.unmServerUrl.first() }
        vm.updateUnmServerUrl("https://unm.example.com")
        source.unmServerUrl.awaitValue("https://unm.example.com")

        vm.resetUnmServerUrl()

        source.unmServerUrl.awaitValue(default)
    }

    @Test
    fun cacheAndDownloadSettingsArePersisted() {
        vm.updateStreamCacheEnabled(true)
        vm.updateAudioCacheMaxSize(2048L * 1024 * 1024)
        vm.updateDownloadFolderUri("D:/Music")
        vm.updateDownloadLyricsEnabled(false)

        settings.streamCacheEnabled.awaitValue(true)
        settings.audioCacheMaxSize.awaitValue(2048L * 1024 * 1024)
        settings.downloadFolderUri.awaitValue("D:/Music")
        settings.downloadLyricsEnabled.awaitValue(false)

        vm.updateDownloadFolderUri(null)
        settings.downloadFolderUri.awaitValue(null)
    }

    @Test
    fun displaySettingsArePersisted() {
        val reversed = FullPlayerCardLayout.DEFAULT.reversed()
        vm.updateDefaultPlaylistPrivate(true)
        vm.updateShowDesktopLrc(true)
        vm.updateFullPlayerCardLayout(reversed)

        settings.defaultPlaylistPrivate.awaitValue(true)
        settings.showDesktopLrc.awaitValue(true)
        settings.fullPlayerCardLayout.awaitValue(reversed)
    }

    @Test
    fun logLevelIsPersistedByName() {
        vm.updateLogLevel(AppLogger.LogLevel.DEBUG)

        settings.logLevel.awaitValue("DEBUG")
    }

    @Test
    fun stateFollowsPreferencesWhileSubscribed() = runBlocking {
        settings.saveWifiQuality("exhigh")

        assertEquals("exhigh", withTimeout(5_000) { vm.wifiQuality.first { it == "exhigh" } })
    }
}

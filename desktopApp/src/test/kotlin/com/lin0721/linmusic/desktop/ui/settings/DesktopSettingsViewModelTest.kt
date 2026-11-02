package com.lin0721.linmusic.desktop.ui.settings

import com.lin0721.linmusic.core.player.PlaybackController
import com.lin0721.linmusic.core.player.data.LyricsCache
import com.lin0721.linmusic.core.preferences.PreferencesStores
import com.lin0721.linmusic.desktop.data.CacheRelocator
import com.lin0721.linmusic.desktop.data.CloseAction
import com.lin0721.linmusic.desktop.data.DesktopCacheManager
import com.lin0721.linmusic.desktop.data.DesktopPreferences
import com.lin0721.linmusic.desktop.platform.AutoStartService
import com.lin0721.linmusic.desktop.platform.GlobalHotkeyService
import com.lin0721.linmusic.desktop.platform.HotkeyAction
import com.lin0721.linmusic.desktop.platform.HotkeyCombo
import com.lin0721.linmusic.desktop.platform.MediaSessionService
import com.lin0721.linmusic.feature.cloud.domain.formatFileSize
import com.lin0721.linmusic.desktop.player.cache.AudioCache
import com.lin0721.linmusic.feature.player.ui.PlayerViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.nio.file.Files
import java.util.concurrent.CopyOnWriteArrayList

@OptIn(ExperimentalCoroutinesApi::class)
class DesktopSettingsViewModelTest {

    private class FakeHotkeys : GlobalHotkeyService {
        var pauses = 0
        var resumes = 0
        override val isSupported = true
        override var onAction: ((HotkeyAction) -> Unit)? = null
        override val failed: StateFlow<Set<HotkeyAction>> = MutableStateFlow(setOf(HotkeyAction.Next))
        override fun start() = Unit
        override fun stop() = Unit
        override fun apply(custom: Map<HotkeyAction, HotkeyCombo?>, mediaKeys: Boolean) = Unit
        override fun pause() {
            pauses++
        }
        override fun resume() {
            resumes++
        }
    }

    private class FakeMediaSession : MediaSessionService {
        override val available: StateFlow<Boolean> = MutableStateFlow(true)
        override fun start() = true
        override fun setEnabled(value: Boolean) = Unit
        override suspend fun bind(controller: PlaybackController, playerViewModel: PlayerViewModel) = Unit
        override fun shutdown() = Unit
    }

    private class FakeAutoStart(override val unavailableReason: String? = null) : AutoStartService {
        var enabled = false
        var succeed = true
        var setCalls = 0
        override fun isEnabled() = enabled
        override fun setEnabled(enabled: Boolean): Boolean {
            setCalls++
            if (succeed) this.enabled = enabled
            return succeed
        }
    }

    private lateinit var dir: File
    private lateinit var cacheDir: File
    private lateinit var preferences: DesktopPreferences
    private val hotkeys = FakeHotkeys()
    private var imageBytes = 300L
    private var logBytes = 40L

    private val storage = LocalStorageOps(
        logsSize = { logBytes },
        clearLogs = { logBytes = 0L }
    )

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        dir = Files.createTempDirectory("melodia-desktop-settings-test").toFile()
        cacheDir = File(dir, "audio").apply { mkdirs() }
        preferences = DesktopPreferences(PreferencesStores.get(File(dir, "desktop.preferences_pb")))
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        dir.deleteRecursively()
    }

    private fun viewModel(autoStart: AutoStartService = FakeAutoStart()) = DesktopSettingsViewModel(
        desktopPreferences = preferences,
        hotkeys = hotkeys,
        mediaSession = FakeMediaSession(),
        autoStart = autoStart,
        cacheManager = DesktopCacheManager(
            audioCache = AudioCache(cacheDir),
            lyricsCache = LyricsCache(File(dir, "cache")),
            lyricsDir = File(dir, "cache/lyrics"),
            metadataDir = File(dir, "meta"),
            imageCacheSize = { imageBytes },
            clearImageCache = { imageBytes = 0L }
        ),
        relocator = relocator(),
        storage = storage,
        ioDispatcher = UnconfinedTestDispatcher()
    )

    private fun relocator() = CacheRelocator(
        preferences,
        effectiveRoot = File(dir, "cache_in_use"),
        defaultRoot = File(dir, "cache_in_use"),
        ioDispatcher = UnconfinedTestDispatcher()
    )

    // 修改时间依次递增，淘汰时先删旧的
    private fun cacheFile(songId: Long, size: Int, modified: Long) {
        File(cacheDir, "${songId}_standard.mka").apply {
            writeBytes(ByteArray(size))
            setLastModified(modified)
        }
    }

    // 偏好读写在 DataStore 自己的线程完成，消息稍后才会到
    private fun awaitNotEmpty(messages: List<String>) = awaitUntil { messages.isNotEmpty() }

    private fun awaitUntil(condition: () -> Boolean) = runBlocking {
        withTimeoutOrNull(5_000) {
            while (!condition()) delay(20)
        }
        Unit
    }

    // 写入在 DataStore 自己的线程完成，等到读回期望值为止
    private fun <T> Flow<T>.awaitValue(expected: T) = runBlocking {
        withTimeoutOrNull(5_000) { first { it == expected } }
            ?: assertEquals(expected, first())
    }

    @Test
    fun refreshReadsStorageAndAutoStart() {
        cacheFile(1, 100, 1_000_000L)
        val autoStart = FakeAutoStart().apply { enabled = true }
        val vm = viewModel(autoStart)
        assertNull(vm.autoStartEnabled.value)

        vm.refresh()

        assertEquals(true, vm.autoStartEnabled.value)
        assertEquals(100L, vm.cacheUsage.value.audio)
        assertEquals(300L, vm.cacheUsage.value.image)
        assertEquals(40L, vm.logBytes.value)
    }

    @Test
    fun enforceCacheLimitEvictsOldestAndRefreshesUsage() {
        cacheFile(1, 100, 1_000_000L)
        cacheFile(2, 100, 2_000_000L)
        val vm = viewModel()

        // 音频 200 + 图片 300 = 500，超出 450 后淘汰最旧的一首
        vm.enforceCacheLimit(450L)

        assertEquals(100L, vm.cacheUsage.value.audio)
        assertEquals(400L, vm.cacheUsage.value.total)
        assertFalse(File(cacheDir, "1_standard.mka").exists())
        assertTrue(File(cacheDir, "2_standard.mka").exists())
    }

    @Test
    fun clearActionsResetSizes() {
        cacheFile(1, 100, 1_000_000L)
        val vm = viewModel()
        vm.refresh()

        vm.clearCaches()
        vm.clearLogs()

        assertEquals(0L, vm.cacheUsage.value.audio)
        assertEquals(0L, vm.cacheUsage.value.image)
        assertEquals(0L, vm.logBytes.value)
    }

    @Test
    fun changeCacheDirReportsPendingRestart() = runBlocking {
        val chosen = File(dir, "chosen").apply { mkdirs() }
        val vm = viewModel()
        val messages = CopyOnWriteArrayList<String>()
        val job = launch(UnconfinedTestDispatcher()) { vm.messages.collect { messages += it } }

        vm.changeCacheDir(chosen)
        awaitNotEmpty(messages)

        assertEquals(listOf("缓存目录已更改，重启后生效"), messages.toList())
        assertEquals(chosen.absolutePath, preferences.cacheRoot.first())
        awaitUntil { vm.relocation.value == null }
        assertNull(vm.relocation.value)
        job.cancel()
    }

    @Test
    fun changeCacheDirReportsFailureAndKeepsSetting() = runBlocking {
        val vm = viewModel()
        val messages = CopyOnWriteArrayList<String>()
        val job = launch(UnconfinedTestDispatcher()) { vm.messages.collect { messages += it } }

        vm.changeCacheDir(File(dir, "missing"))
        awaitNotEmpty(messages)

        assertEquals(listOf("更改缓存目录失败：所选目录不存在"), messages.toList())
        assertNull(preferences.cacheRoot.first())
        job.cancel()
    }

    @Test
    fun clearCachesReportsFreedSpace() = runBlocking {
        cacheFile(1, 100, 1_000_000L)
        val vm = viewModel()
        val messages = mutableListOf<String>()
        val job = launch(UnconfinedTestDispatcher()) { vm.messages.collect { messages += it } }

        vm.clearCaches()

        assertEquals(listOf("已清除缓存，释放 ${formatFileSize(400L)}"), messages)
        job.cancel()
    }

    @Test
    fun clearCachesWithNothingToClearSaysSo() = runBlocking {
        imageBytes = 0L
        val vm = viewModel()
        val messages = mutableListOf<String>()
        val job = launch(UnconfinedTestDispatcher()) { vm.messages.collect { messages += it } }

        vm.clearCaches()

        assertEquals(listOf("没有可清除的缓存"), messages)
        job.cancel()
    }

    @Test
    fun autoStartFailureKeepsStateAndReportsMessage() = runBlocking {
        val autoStart = FakeAutoStart().apply { succeed = false }
        val vm = viewModel(autoStart)
        vm.refresh()
        val messages = mutableListOf<String>()
        val job = launch(UnconfinedTestDispatcher()) { vm.messages.collect { messages += it } }

        vm.updateAutoStart(true)

        assertEquals(false, vm.autoStartEnabled.value)
        assertEquals(listOf("修改开机启动失败"), messages)
        job.cancel()
    }

    @Test
    fun autoStartSuccessUpdatesState() {
        val autoStart = FakeAutoStart()
        val vm = viewModel(autoStart)
        vm.refresh()

        vm.updateAutoStart(true)

        assertEquals(true, vm.autoStartEnabled.value)
        assertTrue(autoStart.enabled)
    }

    @Test
    fun unavailableAutoStartIsNeverTouched() {
        val autoStart = FakeAutoStart(unavailableReason = "仅安装版可用")
        val vm = viewModel(autoStart)

        vm.updateAutoStart(true)

        assertEquals("仅安装版可用", vm.autoStartUnavailableReason)
        assertEquals(0, autoStart.setCalls)
    }

    @Test
    fun hotkeyRecordingPausesAndResumesService() {
        val vm = viewModel()

        vm.pauseHotkeys()
        vm.resumeHotkeys()

        assertEquals(1, hotkeys.pauses)
        assertEquals(1, hotkeys.resumes)
        assertEquals(setOf(HotkeyAction.Next), vm.failedHotkeys.value)
        assertTrue(vm.hotkeysSupported)
        assertTrue(vm.mediaSessionAvailable.value)
    }

    @Test
    fun windowAndHotkeySettingsArePersisted() {
        val vm = viewModel()
        val custom = HotkeyCombo.defaults + (HotkeyAction.Next to null)

        vm.updateCloseAction(CloseAction.EXIT)
        vm.updateMediaKeysEnabled(false)
        vm.updateHotkeys(custom)

        preferences.closeAction.awaitValue(CloseAction.EXIT)
        preferences.mediaKeysEnabled.awaitValue(false)
        preferences.hotkeys.awaitValue(custom)
    }
}

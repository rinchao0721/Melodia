package com.lin0721.linmusic.desktop.player

import com.lin0721.linmusic.core.player.PlaybackPreferences
import com.lin0721.linmusic.core.player.data.PlaybackRepository
import com.lin0721.linmusic.core.preferences.PreferencesStores
import com.lin0721.linmusic.core.preferences.SettingsPreferences
import com.lin0721.linmusic.desktop.data.DesktopPreferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.lang.reflect.Proxy
import java.nio.file.Files
import java.util.concurrent.Executors

// 真实 libmpv 验证音量与静音状态在重启后恢复
class MpvPlaybackControllerVolumeTest {

    private lateinit var dir: File
    private lateinit var scope: CoroutineScope
    private lateinit var preferences: DesktopPreferences
    private val controllers = mutableListOf<MpvPlaybackController>()

    @Before
    fun setUp() {
        val dll = File("native/libmpv-2.dll")
        assumeTrue("缺少 libmpv-2.dll", dll.isFile)
        System.setProperty("compose.application.resources.dir", dll.parentFile.absolutePath)
        dir = Files.createTempDirectory("melodia-volume-test").toFile()
        val dispatcher = Executors.newSingleThreadExecutor { Thread(it, "test-main").apply { isDaemon = true } }.asCoroutineDispatcher()
        scope = CoroutineScope(SupervisorJob() + dispatcher)
        preferences = DesktopPreferences(PreferencesStores.get(File(dir, "desktop.preferences_pb")))
    }

    @After
    fun tearDown() {
        controllers.forEach { runCatching { it.release() } }
        if (::scope.isInitialized) scope.cancel()
        if (::dir.isInitialized) dir.deleteRecursively()
    }

    private fun newController(): MpvPlaybackController {
        val repository = Proxy.newProxyInstance(
            PlaybackRepository::class.java.classLoader,
            arrayOf(PlaybackRepository::class.java)
        ) { _, method, _ ->
            if (Flow::class.java.isAssignableFrom(method.returnType)) emptyFlow<Any>() else throw UnsupportedOperationException(method.name)
        } as PlaybackRepository
        return MpvPlaybackController(
            repository,
            SettingsPreferences(PreferencesStores.get(File(dir, "settings.preferences_pb"))),
            PlaybackPreferences(PreferencesStores.get(File(dir, "playback.preferences_pb"))),
            preferences,
            localAudioOf = { null },
            scope = scope,
            engineOptions = mapOf("ao" to "null")
        ).also { controllers += it }
    }

    private fun release(controller: MpvPlaybackController) {
        controllers.remove(controller)
        controller.release()
    }

    private fun await(timeoutMs: Long, condition: () -> Boolean): Boolean {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            if (condition()) return true
            Thread.sleep(20)
        }
        return condition()
    }

    private fun savedVolume(): Int = runBlocking { preferences.volume.first() }

    @Test
    fun startsAtFullVolumeWhenNothingSaved() {
        val controller = newController()
        Thread.sleep(500)

        assertEquals(100, controller.volume.value)
        assertEquals(100, controller.lastAudibleVolume.value)
    }

    @Test
    fun volumeAndMuteStateSurviveRestart() {
        val first = newController()
        Thread.sleep(300)
        first.setVolume(30)
        first.setVolume(0)
        release(first)

        val second = newController()

        assertTrue("应恢复为静音", await(5_000) { second.volume.value == 0 })
        assertEquals("应记住静音前的音量", 30, second.lastAudibleVolume.value)
    }

    @Test
    fun volumeChangeIsSavedWithoutWaitingForExit() {
        val controller = newController()
        Thread.sleep(300)

        controller.setVolume(55)

        assertTrue("停手后应自动保存", await(5_000) { savedVolume() == 55 })
    }

    @Test
    fun exitingBeforeRestoreDoesNotOverwriteSavedVolume() {
        runBlocking { preferences.saveVolume(40, 40) }

        release(newController())

        assertEquals(40, savedVolume())
    }
}

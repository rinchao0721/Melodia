package com.lin0721.linmusic.desktop.player

import com.lin0721.linmusic.core.player.PlaybackPreferences
import com.lin0721.linmusic.core.player.QueueItem
import com.lin0721.linmusic.core.player.data.PlaybackRepository
import com.lin0721.linmusic.core.preferences.PreferencesStores
import com.lin0721.linmusic.core.preferences.SettingsPreferences
import com.lin0721.linmusic.desktop.data.DesktopPreferences
import com.lin0721.linmusic.desktop.player.mpv.LibMpv
import com.lin0721.linmusic.desktop.player.mpv.MpvConst
import com.lin0721.linmusic.desktop.player.mpv.MpvEngine
import java.io.File
import java.lang.reflect.Proxy
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.file.Files
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.Executors
import kotlin.math.PI
import kotlin.math.sin
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test

// 真实 libmpv 验证独占输出：不建备用引擎、输出初始化失败时回退共享再暂停；缺少 DLL 时跳过
class MpvExclusiveOutputTest {

    private lateinit var dir: File
    private lateinit var scope: CoroutineScope
    private lateinit var settings: SettingsPreferences
    private var controller: MpvPlaybackController? = null
    private var pair: MpvEnginePair? = null

    private val idleListener = object : MpvEngine.Listener {
        override fun onPositionChanged(positionMs: Long) = Unit
        override fun onDurationChanged(durationMs: Long) = Unit
        override fun onPauseChanged(paused: Boolean) = Unit
        override fun onFileLoaded() = Unit
        override fun onEnded(isError: Boolean) = Unit
    }

    @Before
    fun setUp() {
        val dll = File("native/libmpv-2.dll")
        assumeTrue("缺少 libmpv-2.dll", dll.isFile)
        System.setProperty("compose.application.resources.dir", dll.parentFile.absolutePath)
        dir = Files.createTempDirectory("melodia-exclusive-test").toFile()
        val dispatcher = Executors.newSingleThreadExecutor { Thread(it, "test-main").apply { isDaemon = true } }.asCoroutineDispatcher()
        scope = CoroutineScope(SupervisorJob() + dispatcher)
        settings = SettingsPreferences(PreferencesStores.get(File(dir, "settings.preferences_pb")))
    }

    @After
    fun tearDown() {
        controller?.release()
        pair?.release()
        if (::scope.isInitialized) scope.cancel()
        if (::dir.isInitialized) dir.deleteRecursively()
    }

    private fun wav(name: String, seconds: Int): String {
        val rate = 22050
        val samples = ByteBuffer.allocate(rate * seconds * 2).order(ByteOrder.LITTLE_ENDIAN)
        repeat(rate * seconds) { samples.putShort((sin(2 * PI * 440.0 * it / rate) * 8000).toInt().toShort()) }
        val data = samples.array()
        val header = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN).apply {
            put("RIFF".toByteArray()); putInt(36 + data.size); put("WAVE".toByteArray())
            put("fmt ".toByteArray()); putInt(16); putShort(1); putShort(1); putInt(rate); putInt(rate * 2); putShort(2); putShort(16)
            put("data".toByteArray()); putInt(data.size)
        }.array()
        return File(dir, name).apply { writeBytes(header + data) }.absolutePath
    }

    private fun await(timeoutMs: Long, condition: () -> Boolean): Boolean {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            if (condition()) return true
            Thread.sleep(20)
        }
        return condition()
    }

    // 写入不存在的目录让 pcm 输出初始化失败，模拟设备被占用
    private fun brokenOutput(): Map<String, String> =
        mapOf("ao" to "pcm", "ao-pcm-file" to File(dir, "missing/out.wav").absolutePath)

    private fun newController(paths: Map<Long, String>, engineOptions: Map<String, String>): MpvPlaybackController {
        val repository = Proxy.newProxyInstance(
            PlaybackRepository::class.java.classLoader,
            arrayOf(PlaybackRepository::class.java)
        ) { _, method, _ ->
            if (Flow::class.java.isAssignableFrom(method.returnType)) emptyFlow<Any>() else throw UnsupportedOperationException(method.name)
        } as PlaybackRepository
        return MpvPlaybackController(
            repository,
            settings,
            PlaybackPreferences(PreferencesStores.get(File(dir, "playback.preferences_pb"))),
            DesktopPreferences(PreferencesStores.get(File(dir, "desktop.preferences_pb"))),
            localAudioOf = { songId -> paths[songId] },
            scope = scope,
            engineOptions = engineOptions
        ).also { controller = it }
    }

    private fun item(id: Long) = QueueItem(id, "歌$id", "歌手", "")

    @Test
    fun aoInitFailedConstantMatchesLibmpv() {
        assertEquals("audio output initialization failed", LibMpv.load().mpv_error_string(MpvConst.ERROR_AO_INIT_FAILED))
    }

    @Test
    fun exclusivePairRefusesAndReleasesSpare() {
        val pair = MpvEnginePair(idleListener, scope, mapOf("ao" to "null")).also { pair = it }
        pair.load(wav("a.wav", 4), 0L)
        assertTrue(pair.prepareSpare(wav("b.wav", 4)))
        assertTrue(await(5_000) { pair.spareReady })

        pair.setExclusive(true)
        assertFalse(pair.spareReady)
        assertEquals(null, pair.promote(500L))
        assertFalse(pair.prepareSpare(wav("c.wav", 4)))

        pair.setExclusive(false)
        assertTrue(pair.prepareSpare(wav("c.wav", 4)))
    }

    @Test
    fun exclusiveFailureFallsBackToSharedThenPausesWithoutSkipping() {
        runBlocking { settings.saveExclusiveOutput(true) }
        val controller = newController(mapOf(1L to wav("a.wav", 4), 2L to wav("b.wav", 4)), brokenOutput())
        val messages = CopyOnWriteArrayList<String>()
        scope.launch { controller.messages.collect { messages += it } }
        Thread.sleep(300)

        controller.playQueue(listOf(item(1), item(2)), 0, null, null, 0L)

        assertTrue("共享也失败后应提示设备不可用：$messages", await(5_000) { "输出设备被占用或不可用" in messages })
        assertEquals("应先回退共享再暂停", listOf("独占输出不可用，本次已改用共享模式", "输出设备被占用或不可用"), messages.toList())
        assertFalse(controller.playWhenReady.value)
        assertEquals("不应跳到下一首", 0, controller.currentIndex.value)
        assertEquals("1", controller.nowPlaying.value?.mediaId)
    }

    @Test
    fun sharedOutputFailurePausesInsteadOfSkipping() {
        val controller = newController(mapOf(1L to wav("a.wav", 4), 2L to wav("b.wav", 4)), brokenOutput())
        val messages = CopyOnWriteArrayList<String>()
        scope.launch { controller.messages.collect { messages += it } }
        Thread.sleep(300)

        controller.playQueue(listOf(item(1), item(2)), 0, null, null, 0L)

        assertTrue(await(5_000) { "输出设备被占用或不可用" in messages })
        assertEquals(listOf("输出设备被占用或不可用"), messages.toList())
        assertFalse(controller.playWhenReady.value)
        assertEquals(0, controller.currentIndex.value)
    }
}

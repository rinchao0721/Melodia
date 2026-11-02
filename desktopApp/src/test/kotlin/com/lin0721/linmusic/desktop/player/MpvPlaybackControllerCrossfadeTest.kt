package com.lin0721.linmusic.desktop.player

import com.lin0721.linmusic.core.player.PlaybackPreferences
import com.lin0721.linmusic.core.player.QueueItem
import com.lin0721.linmusic.core.player.data.PlaybackRepository
import com.lin0721.linmusic.core.preferences.PreferencesStores
import com.lin0721.linmusic.core.preferences.SettingsPreferences
import com.lin0721.linmusic.desktop.data.DesktopPreferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.lang.reflect.Proxy
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.file.Files
import java.util.concurrent.Executors
import kotlin.math.PI
import kotlin.math.sin

// 无声输出的真实 libmpv 端到端验证交叉淡化的预载、触发与状态交接；缺少 DLL 时跳过
class MpvPlaybackControllerCrossfadeTest {

    private lateinit var dir: File
    private lateinit var scope: CoroutineScope
    private lateinit var settings: SettingsPreferences
    private var controller: MpvPlaybackController? = null
    private val paths = HashMap<Long, String>()

    @Before
    fun setUp() {
        val dll = File("native/libmpv-2.dll")
        assumeTrue("缺少 libmpv-2.dll", dll.isFile)
        System.setProperty("compose.application.resources.dir", dll.parentFile.absolutePath)
        dir = Files.createTempDirectory("melodia-controller-test").toFile()
        val dispatcher = Executors.newSingleThreadExecutor { Thread(it, "test-main").apply { isDaemon = true } }.asCoroutineDispatcher()
        scope = CoroutineScope(SupervisorJob() + dispatcher)
        settings = SettingsPreferences(PreferencesStores.get(File(dir, "settings.preferences_pb")))
    }

    @After
    fun tearDown() {
        controller?.release()
        if (::scope.isInitialized) scope.cancel()
        if (::dir.isInitialized) dir.deleteRecursively()
    }

    // 末尾 silentSeconds 秒为静音
    private fun wav(name: String, seconds: Int, hz: Double, silentSeconds: Int = 0, amplitude: Double = 8000.0): String {
        val rate = 22050
        val samples = ByteBuffer.allocate(rate * seconds * 2).order(ByteOrder.LITTLE_ENDIAN)
        val audibleSamples = rate * (seconds - silentSeconds)
        repeat(rate * seconds) {
            val value = if (it < audibleSamples) sin(2 * PI * hz * it / rate) * amplitude else 0.0
            samples.putShort(value.toInt().toShort())
        }
        val data = samples.array()
        val header = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN).apply {
            put("RIFF".toByteArray()); putInt(36 + data.size); put("WAVE".toByteArray())
            put("fmt ".toByteArray()); putInt(16); putShort(1); putShort(1); putInt(rate); putInt(rate * 2); putShort(2); putShort(16)
            put("data".toByteArray()); putInt(data.size)
        }.array()
        return File(dir, name).apply { writeBytes(header + data) }.absolutePath
    }

    private fun newController(loudnessStoreFile: File? = null): MpvPlaybackController {
        val repository = Proxy.newProxyInstance(
            PlaybackRepository::class.java.classLoader,
            arrayOf(PlaybackRepository::class.java)
        ) { _, method, _ ->
            if (kotlinx.coroutines.flow.Flow::class.java.isAssignableFrom(method.returnType)) emptyFlow<Any>()
            else throw UnsupportedOperationException(method.name)
        } as PlaybackRepository
        return MpvPlaybackController(
            repository,
            settings,
            PlaybackPreferences(PreferencesStores.get(File(dir, "playback.preferences_pb"))),
            DesktopPreferences(PreferencesStores.get(File(dir, "desktop.preferences_pb"))),
            localAudioOf = { songId -> paths[songId] },
            scope = scope,
            engineOptions = mapOf("ao" to "null"),
            loudnessStoreFile = loudnessStoreFile
        ).also { controller = it }
    }

    private fun await(timeoutMs: Long, condition: () -> Boolean): Long {
        val start = System.currentTimeMillis()
        while (System.currentTimeMillis() - start < timeoutMs) {
            if (condition()) return System.currentTimeMillis() - start
            Thread.sleep(20)
        }
        return -1L
    }

    private fun item(id: Long) = QueueItem(id, "歌$id", "歌手", "")

    @Test
    fun switchesToNextTrackBeforeCurrentEnds() {
        runBlocking {
            settings.saveCrossfadeEnabled(true)
            settings.saveCrossfadeDurationMs(1_000)
        }
        paths[1] = wav("a.wav", 5, 440.0)
        paths[2] = wav("b.wav", 5, 660.0)
        val controller = newController()
        Thread.sleep(300)
        controller.playQueue(listOf(item(1), item(2)), 0, null, null, 0L)
        assertTrue(await(5_000) { controller.nowPlaying.value?.songId == 1L && controller.currentPosition.value > 300L } >= 0)

        // 5 秒曲目在 4 秒处开始淡化，自然播完再切歌最早也要到 5 秒之后；计时起点已播 0.3 秒以上
        val switchedAfter = await(5_000) { controller.nowPlaying.value?.songId == 2L }
        assertTrue("应在交叉淡化触发时切到第二首，实际 $switchedAfter", switchedAfter in 0..4_300)
        assertTrue("切歌时第二首应从头开始计时：${controller.currentPosition.value}", controller.currentPosition.value < 2_000L)
        assertEquals(1, controller.currentIndex.value)
        assertTrue(controller.isPlaying.value)
    }

    @Test
    fun smartCrossfadeSkipsSilentTail() {
        runBlocking {
            settings.saveCrossfadeEnabled(true)
            settings.saveCrossfadeDurationMs(1_000)
            settings.saveSmartCrossfadeEnabled(true)
        }
        paths[1] = wav("a.wav", 8, 440.0, silentSeconds = 4)
        paths[2] = wav("b.wav", 5, 660.0)
        val controller = newController()
        Thread.sleep(300)
        controller.playQueue(listOf(item(1), item(2)), 0, null, null, 0L)
        assertTrue(await(5_000) { controller.nowPlaying.value?.songId == 1L && controller.currentPosition.value > 300L } >= 0)

        // 有效结尾约 4.2 秒，应在 3.2 秒左右开始淡化；按时长会等到 7 秒
        val switchedAfter = await(7_000) { controller.nowPlaying.value?.songId == 2L }
        assertTrue("应按有效结尾提前切到第二首，实际 $switchedAfter", switchedAfter in 0..4_500)
    }

    @Test
    fun loudnessNormalizationMeasuresAndCachesGain() {
        runBlocking { settings.saveLoudnessNormalizationEnabled(true) }
        paths[1] = wav("loud.wav", 8, 440.0, amplitude = 16_000.0)
        val storeFile = File(dir, "loudness_gain.json")
        val controller = newController(loudnessStoreFile = storeFile)
        Thread.sleep(300)
        controller.playQueue(listOf(item(1)), 0, null, null, 0L)
        assertTrue(await(5_000) { controller.nowPlaying.value?.songId == 1L && controller.currentPosition.value > 300L } >= 0)
        // 幅度 16000 的正弦约 -9.2dBFS，按 -16 目标应压低约 6.8dB
        assertTrue("应写入响度缓存", await(10_000) { storeFile.isFile && storeFile.readText().contains("\"id\":1") } >= 0)
        val db = Regex("\"db\":(-?[0-9.]+)").find(storeFile.readText())!!.groupValues[1].toDouble()
        assertEquals(-6.8, db, 0.5)
    }

    @Test
    fun disabledCrossfadeWaitsForNaturalEnd() {
        paths[1] = wav("a.wav", 3, 440.0)
        paths[2] = wav("b.wav", 3, 660.0)
        val controller = newController()
        Thread.sleep(300)
        controller.playQueue(listOf(item(1), item(2)), 0, null, null, 0L)
        assertTrue(await(5_000) { controller.currentPosition.value > 300L } >= 0)

        Thread.sleep(1_500)
        assertEquals("未开启淡化时不应提前切歌", 1L, controller.nowPlaying.value?.songId)
        assertTrue(await(6_000) { controller.nowPlaying.value?.songId == 2L } >= 0)
    }

    @Test
    fun singleLoopNeverCrossfades() {
        runBlocking {
            settings.saveCrossfadeEnabled(true)
            settings.saveCrossfadeDurationMs(1_000)
        }
        paths[1] = wav("a.wav", 4, 440.0)
        paths[2] = wav("b.wav", 4, 660.0)
        val controller = newController()
        Thread.sleep(300)
        controller.toggleRepeat()
        controller.playQueue(listOf(item(1), item(2)), 0, null, null, 0L)
        assertTrue(await(5_000) { controller.currentPosition.value > 300L } >= 0)

        Thread.sleep(3_500)
        assertEquals(1L, controller.nowPlaying.value?.songId)
    }
}

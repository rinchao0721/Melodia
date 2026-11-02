package com.lin0721.linmusic.desktop.player

import com.lin0721.linmusic.core.player.PlaybackPreferences
import com.lin0721.linmusic.core.player.QueueItem
import com.lin0721.linmusic.core.player.data.PlaybackRepository
import com.lin0721.linmusic.core.player.data.SongPlaybackInfo
import com.lin0721.linmusic.core.preferences.PreferencesStores
import com.lin0721.linmusic.core.preferences.SettingsPreferences
import com.lin0721.linmusic.desktop.data.DesktopPreferences
import com.lin0721.linmusic.desktop.player.cache.AudioCache
import com.sun.net.httpserver.HttpServer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.lang.reflect.Proxy
import java.net.InetSocketAddress
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.file.Files
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.PI
import kotlin.math.sin

// 无声输出的真实 libmpv 端到端验证歌曲缓存：落缓存、命中缓存、联网失败时兜底
class MpvPlaybackControllerCacheTest {

    private lateinit var dir: File
    private lateinit var scope: CoroutineScope
    private lateinit var server: HttpServer
    private lateinit var settings: SettingsPreferences
    private lateinit var cache: AudioCache
    private var controller: MpvPlaybackController? = null
    private lateinit var wavBytes: ByteArray
    private val urlRequests = AtomicInteger(0)
    private var urlFails = false

    @Before
    fun setUp() {
        val dll = File("native/libmpv-2.dll")
        assumeTrue("缺少 libmpv-2.dll", dll.isFile)
        System.setProperty("compose.application.resources.dir", dll.parentFile.absolutePath)
        dir = Files.createTempDirectory("melodia-cache-test").toFile()
        val dispatcher = Executors.newSingleThreadExecutor { Thread(it, "test-main").apply { isDaemon = true } }.asCoroutineDispatcher()
        scope = CoroutineScope(SupervisorJob() + dispatcher)
        settings = SettingsPreferences(PreferencesStores.get(File(dir, "settings.preferences_pb")))
        cache = AudioCache(File(dir, "cache"))
        wavBytes = wav(3, 440.0)
        server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/") { ex ->
            val range = ex.requestHeaders.getFirst("Range")
            ex.responseHeaders.add("Accept-Ranges", "bytes")
            if (range != null && range.startsWith("bytes=")) {
                val start = range.removePrefix("bytes=").substringBefore('-').toInt()
                ex.responseHeaders.add("Content-Range", "bytes $start-${wavBytes.size - 1}/${wavBytes.size}")
                ex.sendResponseHeaders(206, (wavBytes.size - start).toLong())
                ex.responseBody.use { it.write(wavBytes, start, wavBytes.size - start) }
            } else {
                ex.sendResponseHeaders(200, wavBytes.size.toLong())
                ex.responseBody.use { it.write(wavBytes) }
            }
        }
        server.start()
    }

    @After
    fun tearDown() {
        controller?.release()
        if (::server.isInitialized) server.stop(0)
        if (::scope.isInitialized) scope.cancel()
        if (::dir.isInitialized) dir.deleteRecursively()
    }

    private fun wav(seconds: Int, hz: Double): ByteArray {
        val rate = 22050
        val samples = ByteBuffer.allocate(rate * seconds * 2).order(ByteOrder.LITTLE_ENDIAN)
        repeat(rate * seconds) { samples.putShort((sin(2 * PI * hz * it / rate) * 8000).toInt().toShort()) }
        val data = samples.array()
        val header = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN).apply {
            put("RIFF".toByteArray()); putInt(36 + data.size); put("WAVE".toByteArray())
            put("fmt ".toByteArray()); putInt(16); putShort(1); putShort(1); putInt(rate); putInt(rate * 2); putShort(2); putShort(16)
            put("data".toByteArray()); putInt(data.size)
        }.array()
        return header + data
    }

    private fun newController(): MpvPlaybackController {
        val url = "http://127.0.0.1:${server.address.port}/song.wav"
        val repository = Proxy.newProxyInstance(
            PlaybackRepository::class.java.classLoader,
            arrayOf(PlaybackRepository::class.java)
        ) { _, method, _ ->
            when {
                method.name == "getSongPlaybackInfo" -> {
                    urlRequests.incrementAndGet()
                    flowOf(if (urlFails) Result.failure<SongPlaybackInfo>(IllegalStateException("离线")) else Result.success(SongPlaybackInfo(url)))
                }
                Flow::class.java.isAssignableFrom(method.returnType) -> emptyFlow<Any>()
                else -> throw UnsupportedOperationException(method.name)
            }
        } as PlaybackRepository
        return MpvPlaybackController(
            repository,
            settings,
            PlaybackPreferences(PreferencesStores.get(File(dir, "playback.preferences_pb"))),
            DesktopPreferences(PreferencesStores.get(File(dir, "desktop.preferences_pb"))),
            localAudioOf = { null },
            scope = scope,
            engineOptions = mapOf("ao" to "null"),
            audioCache = cache
        ).also { controller = it }
    }

    private fun await(timeoutMs: Long, condition: () -> Boolean): Boolean {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            if (condition()) return true
            Thread.sleep(20)
        }
        return condition()
    }

    private fun play(controller: MpvPlaybackController) {
        controller.playQueue(listOf(QueueItem(1, "歌1", "歌手", "")), 0, null, null, 0L)
    }

    @Test
    fun playedTrackIsCachedThenServedWithoutFetchingUrl() {
        val controller = newController()
        Thread.sleep(300)
        play(controller)

        assertTrue("播完后应落缓存", await(15_000) { cache.playableIds(listOf(1)).isNotEmpty() })

        // 命中缓存：不再取地址，仍能完整播放（单曲队列播完会循环重播，计数以此刻为准）
        val requestsBefore = urlRequests.get()
        play(controller)
        assertTrue(await(5_000) { controller.currentPosition.value > 500L })
        assertEquals("命中缓存不应再取播放地址", requestsBefore, urlRequests.get())
        assertTrue(await(10_000) { !controller.isPlaying.value || controller.currentPosition.value > 2_500L })
    }

    @Test
    fun cachedFileIsUsedWhenUrlFetchFails() {
        val controller = newController()
        Thread.sleep(300)
        play(controller)
        assertTrue(await(15_000) { cache.playableIds(listOf(1)).isNotEmpty() })

        // 换一档音质使精确命中失效，联网又失败，只能退回已有缓存
        runBlocking { settings.saveWifiQuality("exhigh") }
        urlFails = true
        play(controller)
        assertTrue("联网失败时应退回缓存播放", await(5_000) { controller.currentPosition.value > 500L })
    }

    @Test
    fun externalTrackPlaysItsOwnUrlWithoutQueryingNeteaseOrCaching() {
        val controller = newController()
        Thread.sleep(300)
        val url = "http://127.0.0.1:${server.address.port}/song.wav"
        controller.playQueue(listOf(QueueItem(-7, "外部歌", "歌手", "", localUri = url)), 0, null, null, 0L)

        assertTrue("应直接播放自带直链", await(10_000) { controller.currentPosition.value > 500L })
        assertEquals("外部歌曲不应向网易云取播放地址", 0, urlRequests.get())
        Thread.sleep(1_500)
        assertTrue("外部歌曲不应写入缓存", cache.playableIds(listOf(-7)).isEmpty())
    }
}

package com.lin0721.linmusic.desktop.player

import com.lin0721.linmusic.core.platform.KugouTrackMeta
import com.lin0721.linmusic.core.platform.TrackId
import com.lin0721.linmusic.core.platform.UnifiedAudioQuality
import com.lin0721.linmusic.core.player.PlaybackPreferences
import com.lin0721.linmusic.core.player.PlaybackTrackMeta
import com.lin0721.linmusic.core.player.PlatformPlaybackProvider
import com.lin0721.linmusic.core.player.PlatformPlaybackRouter
import com.lin0721.linmusic.core.player.QueueItem
import com.lin0721.linmusic.core.player.data.PlaybackRepository
import com.lin0721.linmusic.core.player.data.SongPlaybackInfo
import com.lin0721.linmusic.core.player.domain.LyricLine
import com.lin0721.linmusic.core.preferences.PreferencesStores
import com.lin0721.linmusic.core.preferences.SettingsPreferences
import com.lin0721.linmusic.core.source.AudioSourceProvider
import com.lin0721.linmusic.core.source.ExternalTrack
import com.lin0721.linmusic.core.source.ExternalTrackResolver
import com.lin0721.linmusic.core.source.MusicPlatform
import com.lin0721.linmusic.core.source.SourceResult
import com.lin0721.linmusic.desktop.data.DesktopPreferences
import com.sun.net.httpserver.HttpServer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.launch
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
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.PI
import kotlin.math.sin

// 真实 libmpv（ao=null）验证桌面端酷狗官方播放路由、试听回退与播放上报分流
class MpvPlaybackControllerKugouTest {

    private lateinit var dir: File
    private lateinit var scope: CoroutineScope
    private lateinit var server: HttpServer
    private lateinit var settings: SettingsPreferences
    private var controller: MpvPlaybackController? = null
    private lateinit var wavBytes: ByteArray
    private val neteaseStartCalls = AtomicInteger(0)

    private class FakeKugouPlaybackProvider(
        private val urlProvider: () -> SongPlaybackInfo
    ) : PlatformPlaybackProvider {
        override val platform: MusicPlatform = MusicPlatform.KUGOU
        val resolvedIds = CopyOnWriteArrayList<TrackId>()
        val startReports = CopyOnWriteArrayList<TrackId>()

        override suspend fun resolvePlayback(
            trackId: TrackId,
            preferredQuality: UnifiedAudioQuality,
            meta: PlaybackTrackMeta?
        ): Result<SongPlaybackInfo> {
            resolvedIds += trackId
            return Result.success(urlProvider())
        }

        override suspend fun getLyrics(trackId: TrackId, meta: PlaybackTrackMeta?): Result<List<LyricLine>> =
            Result.success(emptyList())

        override suspend fun getRawLyrics(trackId: TrackId, meta: PlaybackTrackMeta?): Result<String> =
            Result.success("")

        override suspend fun reportPlayStart(trackId: TrackId, meta: PlaybackTrackMeta?): Result<Unit> {
            startReports += trackId
            return Result.success(Unit)
        }
    }

    @Before
    fun setUp() {
        val dll = File("native/libmpv-2.dll")
        assumeTrue("缺少 libmpv-2.dll", dll.isFile)
        System.setProperty("compose.application.resources.dir", dll.parentFile.absolutePath)
        dir = Files.createTempDirectory("melodia-kugou-test").toFile()
        val dispatcher = Executors.newSingleThreadExecutor { Thread(it, "test-kugou-main").apply { isDaemon = true } }.asCoroutineDispatcher()
        scope = CoroutineScope(SupervisorJob() + dispatcher)
        settings = SettingsPreferences(PreferencesStores.get(File(dir, "settings.preferences_pb")))
        wavBytes = wav(2, 440.0)
        server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/") { ex ->
            ex.responseHeaders.add("Accept-Ranges", "bytes")
            ex.sendResponseHeaders(200, wavBytes.size.toLong())
            ex.responseBody.use { it.write(wavBytes) }
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

    private fun newController(
        provider: FakeKugouPlaybackProvider,
        fallbackUrl: String? = null
    ): MpvPlaybackController {
        val repository = Proxy.newProxyInstance(
            PlaybackRepository::class.java.classLoader,
            arrayOf(PlaybackRepository::class.java)
        ) { _, method, _ ->
            if (method.name == "reportPlayStart") {
                neteaseStartCalls.incrementAndGet()
            }
            if (Flow::class.java.isAssignableFrom(method.returnType)) emptyFlow<Any>()
            else throw UnsupportedOperationException(method.name)
        } as PlaybackRepository

        val pluginProvider = object : AudioSourceProvider {
            override val platform: MusicPlatform = MusicPlatform.KUGOU
            override suspend fun resolveUrl(
                songId: String,
                songName: String,
                artists: String,
                albumName: String?,
                durationMs: Long,
                quality: String,
                origin: MusicPlatform
            ): SourceResult? = fallbackUrl?.let { SourceResult(url = it, platform = MusicPlatform.KUGOU, quality = "exhigh") }

            override suspend fun search(keyword: String, offset: Int, limit: Int): List<ExternalTrack> = emptyList()
        }

        return MpvPlaybackController(
            repository = repository,
            settingsPreferences = settings,
            preferences = PlaybackPreferences(PreferencesStores.get(File(dir, "playback.preferences_pb"))),
            desktopPreferences = DesktopPreferences(PreferencesStores.get(File(dir, "desktop.preferences_pb"))),
            localAudioOf = { null },
            scope = scope,
            engineOptions = mapOf("ao" to "null"),
            externalResolver = ExternalTrackResolver(listOf(pluginProvider), sourcePreferences = null),
            platformPlaybackRouter = PlatformPlaybackRouter(listOf(provider))
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

    @Test
    fun kugouTrackUsesOfficialRouterAndReportsToPlatformRouter() {
        val baseUrl = "http://127.0.0.1:${server.address.port}"
        val provider = FakeKugouPlaybackProvider {
            SongPlaybackInfo(url = "$baseUrl/official.wav", isFreeTrial = false, level = "exhigh")
        }
        val controller = newController(provider)
        Thread.sleep(300)

        val item = QueueItem(
            songId = -1001L,
            title = "酷狗曲目",
            artist = "测试歌手",
            coverUrl = "",
            trackId = TrackId.kugou("aabbccdd"),
            kugouMeta = KugouTrackMeta(hash = "aabbccdd", mixSongId = 123456L)
        )
        controller.playQueue(listOf(item), 0, null, null, 0L)

        assertTrue("酷狗曲目应起播", await(10_000) { controller.currentPosition.value > 200L })
        assertTrue(provider.resolvedIds.contains(TrackId.kugou("aabbccdd")))
        assertTrue(provider.startReports.contains(TrackId.kugou("aabbccdd")))
        assertEquals(0, neteaseStartCalls.get())
    }

    @Test
    fun kugouTrialFallsBackToExternalPluginAndEmitsHintWhenPluginFails() {
        val baseUrl = "http://127.0.0.1:${server.address.port}"
        val provider = FakeKugouPlaybackProvider {
            SongPlaybackInfo(url = "$baseUrl/trial.wav", isFreeTrial = true, level = "standard")
        }
        val controller = newController(provider, fallbackUrl = null)
        val messages = CopyOnWriteArrayList<String>()
        scope.launch { controller.messages.collect { messages += it } }
        Thread.sleep(300)

        val external = ExternalTrack(
            id = "11223344",
            name = "试听歌",
            artists = "歌手",
            albumName = "专辑",
            durationMs = 2000L,
            platform = MusicPlatform.KUGOU,
            hash = "11223344"
        )
        val item = QueueItem(
            songId = external.queueSongId,
            title = external.name,
            artist = external.artists,
            coverUrl = "",
            external = external,
            trackId = TrackId.kugou("11223344"),
            kugouMeta = KugouTrackMeta(hash = "11223344")
        )
        controller.playQueue(listOf(item), 0, null, null, 0L)

        assertTrue("换源失败时应兜底播放官方试听并提示", await(10_000) {
            controller.currentPosition.value > 200L && messages.any { it.contains("试听") }
        })
    }
}

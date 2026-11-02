package com.lin0721.linmusic.desktop.player

import com.lin0721.linmusic.desktop.player.cache.AudioCache
import com.lin0721.linmusic.desktop.player.mpv.MpvEngine
import com.sun.net.httpserver.HttpServer
import java.io.File
import java.net.InetSocketAddress
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.file.Files
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.PI
import kotlin.math.sin
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test

// 无声输出的真实 libmpv + 本地 HTTP 服务，验证 stream-record 边播边存；缺少 DLL 时跳过
class MpvRecordingTest {

    private lateinit var dir: File
    private lateinit var scope: CoroutineScope
    private lateinit var server: HttpServer
    private lateinit var cache: AudioCache
    private var pair: MpvEnginePair? = null
    private val ended = AtomicInteger(0)
    private val position = AtomicInteger(0)
    private val finished = CopyOnWriteArrayList<Pair<Long, String>>()
    private val audio = HashMap<String, ByteArray>()

    private val listener = object : MpvEngine.Listener {
        override fun onPositionChanged(positionMs: Long) { position.set(positionMs.toInt()) }
        override fun onDurationChanged(durationMs: Long) = Unit
        override fun onPauseChanged(paused: Boolean) = Unit
        override fun onFileLoaded() = Unit
        override fun onEnded(isError: Boolean) { ended.incrementAndGet() }
    }

    @Before
    fun setUp() {
        val dll = File("native/libmpv-2.dll")
        assumeTrue("缺少 libmpv-2.dll", dll.isFile)
        System.setProperty("compose.application.resources.dir", dll.parentFile.absolutePath)
        dir = Files.createTempDirectory("melodia-record-test").toFile()
        cache = AudioCache(File(dir, "cache"))
        scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/") { ex ->
            val bytes = audio.getValue(ex.requestURI.path.trimStart('/'))
            val range = ex.requestHeaders.getFirst("Range")
            ex.responseHeaders.add("Accept-Ranges", "bytes")
            if (range != null && range.startsWith("bytes=")) {
                val start = range.removePrefix("bytes=").substringBefore('-').toInt()
                ex.responseHeaders.add("Content-Range", "bytes $start-${bytes.size - 1}/${bytes.size}")
                ex.sendResponseHeaders(206, (bytes.size - start).toLong())
                ex.responseBody.use { it.write(bytes, start, bytes.size - start) }
            } else {
                ex.sendResponseHeaders(200, bytes.size.toLong())
                ex.responseBody.use { it.write(bytes) }
            }
        }
        server.start()
    }

    @After
    fun tearDown() {
        pair?.release()
        if (::server.isInitialized) server.stop(0)
        if (::scope.isInitialized) scope.cancel()
        if (::dir.isInitialized) dir.deleteRecursively()
    }

    private fun serve(name: String, seconds: Int, hz: Double): String {
        val rate = 22050
        val samples = ByteBuffer.allocate(rate * seconds * 2).order(ByteOrder.LITTLE_ENDIAN)
        repeat(rate * seconds) { samples.putShort((sin(2 * PI * hz * it / rate) * 8000).toInt().toShort()) }
        val data = samples.array()
        val header = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN).apply {
            put("RIFF".toByteArray()); putInt(36 + data.size); put("WAVE".toByteArray())
            put("fmt ".toByteArray()); putInt(16); putShort(1); putShort(1); putInt(rate); putInt(rate * 2); putShort(2); putShort(16)
            put("data".toByteArray()); putInt(data.size)
        }.array()
        audio[name] = header + data
        return "http://127.0.0.1:${server.address.port}/$name"
    }

    private fun await(timeoutMs: Long, condition: () -> Boolean): Boolean {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            if (condition()) return true
            Thread.sleep(20)
        }
        return condition()
    }

    private fun newPair(): MpvEnginePair =
        MpvEnginePair(listener, scope, mapOf("ao" to "null"), onRecordingFinished = { tag, path -> finished += tag to path })
            .also { pair = it }

    @Test
    fun sequentialPlayRecordsCommitsAndReplaysFromCache() {
        val pair = newPair()
        val temp = cache.tempFile(1, "standard")
        pair.load(serve("a.wav", 3, 440.0), 0L, RecordRequest(1, temp.absolutePath))

        assertTrue("播完应触发录制完成回调", await(10_000) { finished.isNotEmpty() })
        assertEquals(1L, finished.single().first)
        assertTrue(runBlocking { cache.commit(File(finished.single().second)) })
        val cached = cache.completeFile(1, "standard")
        assertTrue(cached != null && cached.length() > 0)

        // 缓存文件应能被 mpv 完整播放到结束
        val endedBefore = ended.get()
        pair.load(cached!!.absolutePath, 0L)
        assertTrue("缓存文件应能完整播放", await(10_000) { ended.get() > endedBefore })
        assertTrue("缓存文件应播到接近末尾：${position.get()}", position.get() > 2_500)
    }

    @Test
    fun seekingDiscardsRecording() {
        val pair = newPair()
        val temp = cache.tempFile(2, "standard")
        pair.load(serve("a.wav", 4, 440.0), 0L, RecordRequest(2, temp.absolutePath))
        assertTrue(await(5_000) { position.get() > 300 })

        pair.seekTo(2_000L)
        assertTrue(await(10_000) { ended.get() >= 1 })
        Thread.sleep(500)

        assertTrue("拖动过的录制不应回调", finished.isEmpty())
        assertTrue("未完成的临时文件应被清掉", await(5_000) { !temp.exists() })
    }

    @Test
    fun skippingMidTrackDiscardsRecording() {
        val pair = newPair()
        val temp = cache.tempFile(3, "standard")
        pair.load(serve("a.wav", 4, 440.0), 0L, RecordRequest(3, temp.absolutePath))
        assertTrue(await(5_000) { position.get() > 300 })

        pair.load(serve("b.wav", 2, 660.0), 0L)
        Thread.sleep(500)

        assertTrue(finished.isEmpty())
        assertTrue("切歌后未完成的临时文件应被清掉", await(5_000) { !temp.exists() })
    }

    @Test
    fun crossfadedOutgoingTrackStillFinishesItsRecording() {
        val pair = newPair()
        val tempA = cache.tempFile(10, "standard")
        val tempB = cache.tempFile(11, "standard")
        pair.load(serve("a.wav", 3, 440.0), 0L, RecordRequest(10, tempA.absolutePath))
        assertTrue(await(5_000) { position.get() > 500 })
        assertTrue(pair.prepareSpare(serve("b.wav", 3, 660.0), RecordRequest(11, tempB.absolutePath)))
        assertTrue(await(5_000) { pair.spareReady })
        pair.promote(500L)

        assertTrue("渐出曲目也应完成录制", await(10_000) { finished.any { it.first == 10L } })
        assertTrue("新主曲目播完后完成录制", await(10_000) { finished.any { it.first == 11L } })
        assertTrue(runBlocking { cache.commit(File(finished.first { it.first == 10L }.second)) })
        assertTrue(runBlocking { cache.commit(File(finished.first { it.first == 11L }.second)) })
        assertEquals(setOf(10L, 11L), cache.playableIds(listOf(10, 11)))
    }
}

package com.lin0721.linmusic.core.plugin

import com.lin0721.linmusic.core.player.NowPlaying
import com.lin0721.linmusic.feature.podcast.ui.FakePlaybackController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonPrimitive
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.file.Files
import java.util.concurrent.CopyOnWriteArrayList
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class PluginEventDispatcherTest {

    // 只记录宿主发往沙盒的调用，不执行脚本
    private class RecordingFactory : ScriptSandboxFactory {
        val calls = CopyOnWriteArrayList<Pair<String, List<JsonElement>>>()

        override suspend fun create(name: String, host: ScriptHost, prelude: String): ScriptSandbox = object : ScriptSandbox {
            override suspend fun evaluate(script: String): Result<Unit> = Result.success(Unit)
            override suspend fun call(function: String, vararg args: JsonElement) {
                calls += function to args.toList()
            }
            override fun close() = Unit
        }
    }

    private lateinit var root: File
    private lateinit var runtime: PluginRuntime
    private lateinit var store: PluginStore
    private val factory = RecordingFactory()
    private val controller = FakePlaybackController()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    @Before
    fun setUp() {
        root = Files.createTempDirectory("plugin-events").toFile()
        store = PluginStore(root)
        runtime = PluginRuntime(factory, PluginHostApi(store, mainDispatcher = Dispatchers.Unconfined), scope)
    }

    @After
    fun tearDown() {
        scope.cancel()
        root.deleteRecursively()
    }

    private fun installEnabled(events: String) {
        val manifest = """{"format":1,"id":"com.example.events","name":"事件","version":"1","permissions":["playback.read"],
            "contributes":{"events":[$events]}}"""
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { zip ->
            listOf(PluginPaths.MANIFEST to manifest, PluginPaths.SCRIPT to "1").forEach { (name, content) ->
                zip.putNextEntry(ZipEntry(name))
                zip.write(content.toByteArray())
                zip.closeEntry()
            }
        }
        val staged = (store.stage(ByteArrayInputStream(out.toByteArray())) as PluginStageResult.Success).staged
        store.install(staged, enabled = true)
        runBlocking { runtime.sync(store.list()) }
    }

    private fun emitted(event: PluginEvent) = factory.calls.filter {
        it.first == PluginPrelude.EMIT && (it.second.first() as JsonPrimitive).content == event.key
    }

    private suspend fun waitFor(condition: () -> Boolean) {
        withTimeout(3_000) { while (!condition()) delay(10) }
    }

    @Test
    fun `切歌与播放状态变化分发给订阅了的插件`() = runBlocking {
        installEnabled("\"songChanged\",\"playStateChanged\"")
        PluginEventDispatcher(runtime.registry, { controller }, scope, Dispatchers.Unconfined).start()
        delay(100)
        (controller.nowPlaying as MutableStateFlow).value = NowPlaying("186016", "晴天", "周杰伦", null)
        (controller.isPlaying as MutableStateFlow).value = true
        waitFor { emitted(PluginEvent.SONG_CHANGED).isNotEmpty() && emitted(PluginEvent.PLAY_STATE_CHANGED).isNotEmpty() }
        val song = emitted(PluginEvent.SONG_CHANGED).single().second[1] as JsonObject
        assertEquals("晴天", song["title"]!!.jsonPrimitive.content)
        assertEquals("186016", song["songId"]!!.jsonPrimitive.content)
        val state = emitted(PluginEvent.PLAY_STATE_CHANGED).single().second[1] as JsonObject
        assertEquals("true", state["playing"]!!.jsonPrimitive.content)
    }

    @Test
    fun `未订阅的事件不分发，播放中才发进度`() = runBlocking {
        installEnabled("\"progress\"")
        PluginEventDispatcher(runtime.registry, { controller }, scope, Dispatchers.Unconfined, progressIntervalMs = 20).start()
        (controller.nowPlaying as MutableStateFlow).value = NowPlaying("1", "a", "b", null)
        delay(150)
        assertTrue(emitted(PluginEvent.PROGRESS).isEmpty())
        (controller.isPlaying as MutableStateFlow).value = true
        (controller.currentPosition as MutableStateFlow).value = 5_000L
        waitFor { emitted(PluginEvent.PROGRESS).isNotEmpty() }
        assertTrue(emitted(PluginEvent.SONG_CHANGED).isEmpty())
        val progress = emitted(PluginEvent.PROGRESS).first().second[1] as JsonObject
        assertEquals("5000", progress["positionMs"]!!.jsonPrimitive.content)
    }

    @Test
    fun `没有订阅事件的插件时不连接播放器`() = runBlocking {
        var requested = 0
        PluginEventDispatcher(runtime.registry, { requested++; controller }, scope, Dispatchers.Unconfined).start()
        delay(100)
        assertEquals(0, requested)
        installEnabled("\"songChanged\"")
        waitFor { requested == 1 }
    }
}

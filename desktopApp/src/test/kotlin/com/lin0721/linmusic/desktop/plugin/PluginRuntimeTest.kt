package com.lin0721.linmusic.desktop.plugin

import com.lin0721.linmusic.core.plugin.InstalledPlugin
import com.lin0721.linmusic.core.plugin.PluginHostApi
import com.lin0721.linmusic.core.plugin.PluginHttpResponse
import com.lin0721.linmusic.core.plugin.PluginPaths
import com.lin0721.linmusic.core.plugin.PluginPrelude
import com.lin0721.linmusic.core.plugin.PluginRepository
import com.lin0721.linmusic.core.plugin.PluginRuntime
import com.lin0721.linmusic.core.plugin.PluginStageResult
import com.lin0721.linmusic.core.plugin.PluginStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.file.Files
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class PluginRuntimeTest {

    private lateinit var root: File
    private lateinit var store: PluginStore
    private lateinit var runtime: PluginRuntime
    private lateinit var repository: PluginRepository
    private val requestedUrls = mutableListOf<String>()

    @Before
    fun setUp() {
        root = Files.createTempDirectory("plugin-runtime").toFile()
        store = PluginStore(root)
        val api = PluginHostApi(
            store = store,
            http = { request ->
                requestedUrls += request.url
                PluginHttpResponse(200, emptyMap(), "{\"title\":\"晴天\"}")
            },
            mainDispatcher = Dispatchers.Unconfined
        )
        runtime = PluginRuntime(GraalScriptSandboxFactory(), api)
        repository = PluginRepository(store, runtime, api)
    }

    @After
    fun tearDown() {
        runBlocking { repository.installed.value.forEach { runtime.stop(it.id) } }
        root.deleteRecursively()
    }

    private fun install(script: String, permissions: String = "\"playback.read\",\"storage\",\"network\"") = runBlocking {
        val manifest = """
            {"format":1,"id":"com.example.demo","name":"示例","version":"1.0.0","permissions":[$permissions],
             "network":{"hosts":["api.example.com"]},
             "contributes":{"events":["songChanged"],"pages":[{"id":"main","title":"主页","ui":{"type":"column"}}]}}
        """.trimIndent()
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { zip ->
            zip.putNextEntry(ZipEntry(PluginPaths.MANIFEST))
            zip.write(manifest.toByteArray())
            zip.closeEntry()
            zip.putNextEntry(ZipEntry(PluginPaths.SCRIPT))
            zip.write(script.toByteArray())
            zip.closeEntry()
        }
        val staged = (repository.stage(ByteArrayInputStream(out.toByteArray())) as PluginStageResult.Success).staged
        repository.install(staged)
    }

    private fun plugin(): InstalledPlugin = repository.installed.value.single()

    private suspend fun waitFor(condition: () -> Boolean) {
        withTimeout(5_000) { while (!condition()) delay(20) }
    }

    private fun invokeAction(name: String, args: JsonObject = JsonObject(emptyMap())) = runBlocking {
        runtime.invoke("com.example.demo", PluginPrelude.KIND_ACTION, name, args, 3_000)
    }

    @Test
    fun `启用后运行脚本并登记注册的能力`() {
        install("melodia.register.action('ping', function(args) { return { pong: args.n + 1 }; });")
        assertTrue(runtime.isRunning("com.example.demo"))
        runBlocking { waitFor { repository.status.value["com.example.demo"]?.registrations?.contains("action:ping") == true } }
        val result = invokeAction("ping", buildJsonObject { put("n", 41) }) as JsonObject
        assertEquals(42, result["pong"]!!.jsonPrimitive.content.toInt())
    }

    @Test
    fun `脚本经宿主 API 读写数据与访问网络`() {
        install(
            """
            melodia.register.action('run', async function() {
                await melodia.storage.set('visits', 2);
                const visits = await melodia.storage.get('visits');
                const resp = await melodia.http.request({ url: 'https://api.example.com/song' });
                const song = JSON.parse(resp.body);
                let denied = '';
                try { await melodia.http.request({ url: 'https://evil.com/' }); } catch (e) { denied = e.message; }
                return { visits: visits, title: song.title, denied: denied };
            });
            """.trimIndent()
        )
        val result = invokeAction("run") as JsonObject
        assertEquals("2", result["visits"]!!.jsonPrimitive.content)
        assertEquals("晴天", result["title"]!!.jsonPrimitive.content)
        assertTrue(result["denied"]!!.jsonPrimitive.content.contains("evil.com"))
        assertEquals(listOf("https://api.example.com/song"), requestedUrls)
    }

    @Test
    fun `未申请的权限在脚本里调用会被拒绝`() {
        install(
            """
            melodia.register.action('run', async function() {
                try { await melodia.player.pause(); return 'ok'; } catch (e) { return e.message; }
            });
            """.trimIndent()
        )
        assertTrue((invokeAction("run") as JsonPrimitive).content.contains("playback.control"))
    }

    @Test
    fun `脚本加载出错时自动停用并记录原因`() {
        install("this is not javascript")
        runBlocking { waitFor { !plugin().state.enabled } }
        assertFalse(runtime.isRunning("com.example.demo"))
        assertTrue(plugin().state.disabledReason.orEmpty().startsWith("脚本加载失败"))
    }

    @Test
    fun `连续出错达到上限后自动停用，重新启用后恢复`() {
        install("melodia.register.action('boom', function() { throw new Error('坏了'); });")
        repeat(PluginRuntime.MAX_CONSECUTIVE_ERRORS) { assertNull(invokeAction("boom")) }
        runBlocking { waitFor { !plugin().state.enabled } }
        assertTrue(plugin().state.disabledReason.orEmpty().contains("坏了"))
        assertFalse(runtime.isRunning("com.example.demo"))

        runBlocking { repository.setEnabled("com.example.demo", true) }
        assertTrue(plugin().state.enabled)
        assertNull(plugin().state.disabledReason)
        assertTrue(runtime.isRunning("com.example.demo"))
    }

    @Test
    fun `成功调用会清零连续出错计数`() {
        install(
            """
            melodia.register.action('maybe', function(args) { if (args.fail) throw new Error('x'); return 1; });
            """.trimIndent()
        )
        repeat(10) { index ->
            invokeAction("maybe", buildJsonObject { put("fail", index % 2 == 0) })
        }
        assertTrue(plugin().state.enabled)
        assertTrue(runtime.isRunning("com.example.demo"))
    }

    @Test
    fun `调用超时按失败处理`() {
        install("melodia.register.action('slow', function() { return new Promise(function() {}); });")
        val result = runBlocking { runtime.invoke("com.example.demo", PluginPrelude.KIND_ACTION, "slow", JsonNull, 300) }
        assertNull(result)
        assertTrue(repository.status.value["com.example.demo"]?.lastError.orEmpty().contains("没有返回"))
    }

    @Test
    fun `停用与删除插件会关闭沙盒并清理数据`() {
        install("melodia.register.action('save', function() { return melodia.storage.set('a', 1); });")
        invokeAction("save")
        runBlocking { repository.setEnabled("com.example.demo", false) }
        assertFalse(runtime.isRunning("com.example.demo"))
        runBlocking { repository.delete("com.example.demo") }
        assertTrue(repository.installed.value.isEmpty())
        assertFalse(store.dataDirectoryOf("com.example.demo")!!.exists())
    }

    @Test
    fun `沙盒内无法访问宿主类与文件`() {
        install(
            """
            melodia.register.action('probe', function() {
                var result = [];
                try { Java.type('java.io.File'); result.push('java'); } catch (e) {}
                try { if (typeof require === 'function') { require('fs'); result.push('fs'); } } catch (e) {}
                return result.join(',');
            });
            """.trimIndent()
        )
        assertEquals("", (invokeAction("probe") as JsonPrimitive).content)
    }
}

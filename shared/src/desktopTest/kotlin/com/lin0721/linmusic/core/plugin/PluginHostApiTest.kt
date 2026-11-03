package com.lin0721.linmusic.core.plugin

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import java.io.File
import java.nio.file.Files

class PluginHostApiTest {

    private lateinit var root: File
    private lateinit var store: PluginStore
    private val requests = mutableListOf<PluginHttpRequest>()
    private val responses = ArrayDeque<PluginHttpResponse>()
    private val confirmations = PluginConfirmations()
    private val links = PluginLinks()
    private val opened = mutableListOf<String>()
    private var clock = 1_000_000L

    @Before
    fun setUp() {
        root = Files.createTempDirectory("plugin-api").toFile()
        store = PluginStore(root)
    }

    @After
    fun tearDown() {
        root.deleteRecursively()
    }

    private val api by lazy {
        PluginHostApi(
            store = store,
            http = { request ->
                requests += request
                responses.removeFirstOrNull() ?: PluginHttpResponse(200, emptyMap(), "ok")
            },
            mainDispatcher = Dispatchers.Unconfined,
            confirmations = confirmations,
            links = links,
            now = { clock }
        )
    }

    private fun manifest(permissions: String, hosts: String = "", pages: String = "") = (PluginParser.parse(
        """{"format":1,"id":"com.example.demo","name":"示例","version":"1","permissions":[$permissions],
            "network":{"hosts":[$hosts]},
            "contributes":{"events":["songChanged"]${if (pages.isEmpty()) "" else ",\"pages\":[$pages]"}}}"""
    ) as PluginParseResult.Success).manifest

    private fun call(manifest: PluginManifest, method: String, args: JsonElement = JsonNull): JsonElement =
        runBlocking { api.handle(manifest, method, args) }

    private fun expectError(message: String, block: () -> Unit) {
        try {
            block()
            fail("应抛出 PluginCallException")
        } catch (e: PluginCallException) {
            assertTrue("实际：${e.message}", e.message.orEmpty().contains(message))
        }
    }

    private fun request(url: String, method: String = "GET", body: String? = null) = buildJsonObject {
        put("url", url)
        put("method", method)
        body?.let { put("body", it) }
    }

    @Test
    fun `没有申请的权限一律拒绝`() {
        val readOnly = manifest("\"playback.read\"")
        expectError("network") { call(readOnly, "http.request", request("https://api.example.com")) }
        expectError("storage") { call(readOnly, "storage.get", buildJsonObject { put("key", "a") }) }
        expectError("playback.control") { call(readOnly, "player.pause") }
        expectError("不支持") { call(readOnly, "fs.read") }
    }

    @Test
    fun `只能访问清单列出的域名与 http(s)`() {
        val net = manifest("\"playback.read\",\"network\"", "\"api.example.com\"")
        val result = call(net, "http.request", request("https://api.example.com/v1?q=1")) as JsonObject
        assertEquals(200, result["status"]!!.jsonPrimitive.int)
        assertEquals("ok", result["body"]!!.jsonPrimitive.content)
        expectError("不允许访问") { call(net, "http.request", request("https://evil.com/")) }
        expectError("http") { call(net, "http.request", request("file:///etc/passwd")) }
        expectError("请求方法") { call(net, "http.request", request("https://api.example.com", method = "TRACE")) }
        assertEquals(1, requests.size)
    }

    @Test
    fun `重定向逐跳核对域名`() {
        val net = manifest("\"playback.read\",\"network\"", "\"api.example.com\",\"*.cdn.example.com\"")
        responses += PluginHttpResponse(302, mapOf("Location" to "https://a.cdn.example.com/file"), "")
        val ok = call(net, "http.request", request("https://api.example.com/start")) as JsonObject
        assertEquals(200, ok["status"]!!.jsonPrimitive.int)
        assertEquals("https://a.cdn.example.com/file", requests.last().url)

        responses += PluginHttpResponse(301, mapOf("location" to "https://evil.com/steal"), "")
        expectError("evil.com") { call(net, "http.request", request("https://api.example.com/start")) }
    }

    @Test
    fun `POST 遇到 303 改为不带请求体的 GET`() {
        val net = manifest("\"playback.read\",\"network\"", "\"api.example.com\"")
        responses += PluginHttpResponse(303, mapOf("Location" to "/done"), "")
        call(net, "http.request", request("https://api.example.com/submit", method = "POST", body = "{\"a\":1}"))
        assertEquals("POST", requests[0].method)
        assertEquals("{\"a\":1}", requests[0].body)
        assertEquals("GET", requests[1].method)
        assertEquals(null, requests[1].body)
        assertEquals("https://api.example.com/done", requests[1].url)
    }

    @Test
    fun `插件数据按插件隔离并持久保存`() {
        val storage = manifest("\"playback.read\",\"storage\"")
        val key = buildJsonObject { put("key", "count") }
        assertEquals(JsonNull, call(storage, "storage.get", key))
        call(storage, "storage.set", buildJsonObject { put("key", "count"); put("value", 3) })
        assertEquals(JsonPrimitive(3), call(storage, "storage.get", key))
        assertTrue(File(store.dataDirectoryOf("com.example.demo"), "storage.json").isFile)
        call(storage, "storage.remove", key)
        assertEquals(JsonNull, call(storage, "storage.get", key))
    }

    @Test
    fun `插件数据超过上限时拒绝写入`() {
        val storage = manifest("\"playback.read\",\"storage\"")
        val big = "x".repeat(1024 * 1024)
        expectError("1MB") { call(storage, "storage.set", buildJsonObject { put("key", "big"); put("value", big) }) }
        assertEquals(JsonNull, call(storage, "storage.get", buildJsonObject { put("key", "big") }))
    }

    @Test
    fun `页面数据只能写清单里声明的页面并按浅合并`() {
        val withPage = manifest("\"playback.read\"", pages = """{"id":"stats","title":"统计","ui":"ui/stats.json"}""")
        expectError("清单里没有页面") {
            call(withPage, "ui.setState", buildJsonObject { put("page", "other"); put("patch", JsonObject(emptyMap())) })
        }
        call(withPage, "ui.setState", buildJsonObject { put("page", "stats"); put("patch", buildJsonObject { put("a", 1) }) })
        call(withPage, "ui.setState", buildJsonObject { put("page", "stats"); put("patch", buildJsonObject { put("b", 2) }) })
        val state = runBlocking { api.uiStates.first() }["com.example.demo/stats"]
        assertEquals(buildJsonObject { put("a", 1); put("b", 2) }, state)
        api.clearUiState("com.example.demo")
        assertTrue(runBlocking { api.uiStates.first() }.isEmpty())
    }

    @Test
    fun `播放器未就绪时返回错误`() {
        val player = manifest("\"playback.read\",\"playback.control\"")
        expectError("播放器") { call(player, "player.current") }
        expectError("songId") { call(player, "player.enqueue", buildJsonObject { put("songId", -1) }) }
    }

    private fun url(value: String) = buildJsonObject { put("url", value) }

    // 收集被放行的链接，替代各端的浏览器
    private fun CoroutineScope.collectLinks() = launch(Dispatchers.Unconfined) {
        links.opens.collect { opened += it }
    }

    @Test
    fun `打开网页需要权限且只接受 https`() {
        expectError("ui.openUrl") { call(manifest("\"playback.read\",\"ui.notify\""), "ui.openUrl", url("https://www.last.fm")) }
        val opener = manifest("\"playback.read\",\"ui.openUrl\"")
        expectError("https") { call(opener, "ui.openUrl", url("http://www.last.fm")) }
        expectError("https") { call(opener, "ui.openUrl", url("file:///C:/a.txt")) }
        expectError("网址无效") { call(opener, "ui.openUrl", url("https:///nohost")) }
        expectError("网址过长") { call(opener, "ui.openUrl", url("https://a.com/" + "x".repeat(5000))) }
        assertEquals(null, confirmations.pending.value)
    }

    @Test
    fun `确认后打开且弹窗只显示域名`() = runBlocking {
        val collector = collectLinks()
        val opener = manifest("\"playback.read\",\"ui.openUrl\"")
        val target = "https://www.last.fm/api/auth/?api_key=k&token=secret-token"
        val result = async(Dispatchers.Default) { api.handle(opener, "ui.openUrl", url(target)) }
        val request = withTimeout(2_000) { confirmations.pending.filterNotNull().first() }
        expectError("等待确认") { call(opener, "ui.openUrl", url("https://www.last.fm/other")) }
        assertTrue(request.message.contains("www.last.fm"))
        assertTrue(!request.message.contains("secret-token"))
        confirmations.respond(true)
        result.await()
        assertEquals(listOf(target), opened)
        collector.cancel()
    }

    @Test
    fun `拒绝后不打开且冷却期内不再弹窗`() = runBlocking {
        val collector = collectLinks()
        val opener = manifest("\"playback.read\",\"ui.openUrl\"")
        val first = async(Dispatchers.Default) { runCatching { api.handle(opener, "ui.openUrl", url("https://www.last.fm")) } }
        withTimeout(2_000) { confirmations.pending.filterNotNull().first() }
        confirmations.respond(false)
        assertEquals("用户拒绝了打开网页", first.await().exceptionOrNull()?.message)

        expectError("请稍后再试") { call(opener, "ui.openUrl", url("https://www.last.fm")) }
        assertEquals(null, confirmations.pending.value)

        clock += 30_001
        val retry = async(Dispatchers.Default) { runCatching { api.handle(opener, "ui.openUrl", url("https://www.last.fm")) } }
        withTimeout(2_000) { confirmations.pending.filterNotNull().first() }
        confirmations.respond(true)
        assertTrue(retry.await().isSuccess)
        assertEquals(listOf("https://www.last.fm"), opened)
        collector.cancel()
    }

    @Test
    fun `确认被超时取消也进入冷却`() = runBlocking {
        val opener = manifest("\"playback.read\",\"ui.openUrl\"")
        val first = async(Dispatchers.Default) { api.handle(opener, "ui.openUrl", url("https://www.last.fm")) }
        withTimeout(2_000) { confirmations.pending.filterNotNull().first() }
        first.cancel()
        first.join()
        assertEquals(null, confirmations.pending.value)

        expectError("请稍后再试") { call(opener, "ui.openUrl", url("https://www.last.fm")) }
        assertEquals(null, confirmations.pending.value)
    }

    @Test
    fun `没有收集方时链接先存着`() = runBlocking {
        val opener = manifest("\"playback.read\",\"ui.openUrl\"")
        val result = async(Dispatchers.Default) { api.handle(opener, "ui.openUrl", url("https://www.last.fm")) }
        withTimeout(2_000) { confirmations.pending.filterNotNull().first() }
        confirmations.respond(true)
        result.await()

        assertEquals("https://www.last.fm", withTimeout(2_000) { links.opens.first() })
    }
}

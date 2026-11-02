package com.lin0721.linmusic.core.plugin

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.job
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.security.MessageDigest
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class PluginCatalogFlowTest {
    private object NoSandbox : ScriptSandboxFactory {
        override suspend fun create(name: String, host: ScriptHost, prelude: String): ScriptSandbox =
            error("布局插件不应创建沙盒")
    }

    private class FakeHttp : PluginCatalogHttp {
        var index = ""
        val files = HashMap<String, ByteArray>()
        var downloads = 0

        override suspend fun fetchText(url: String, maxBytes: Long): String = index

        override suspend fun download(url: String, maxBytes: Long, target: File, onProgress: (Long) -> Unit) {
            downloads++
            target.writeBytes(files[url] ?: throw IOException("404"))
        }
    }

    private val id = "com.example.demo"
    private val http = FakeHttp()
    private lateinit var root: File
    private lateinit var store: PluginStore
    private lateinit var repository: PluginRepository

    @Before
    fun setUp() {
        root = Files.createTempDirectory("catalog-flow").toFile()
        store = PluginStore(root)
        val api = PluginHostApi(store)
        repository = PluginRepository(store, PluginRuntime(NoSandbox, api), api, catalog = PluginCatalogService(store, http, PluginPlatform.DESKTOP))
    }

    @After
    fun tearDown() {
        root.deleteRecursively()
    }

    private fun zip(vararg entries: Pair<String, String>): ByteArray {
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { zip ->
            entries.forEach { (name, content) ->
                zip.putNextEntry(ZipEntry(name))
                zip.write(content.toByteArray())
                zip.closeEntry()
            }
        }
        return out.toByteArray()
    }

    private fun sandboxPackage(version: String, packageId: String = id) = zip(
        PluginPaths.MANIFEST to """{"format":1,"id":"$packageId","name":"示例","version":"$version",
            "contributes":{"playerLayouts":{"desktop.playerBar":"ui/bar.json"}}}""",
        "ui/bar.json" to """{"type":"row","children":[{"type":"controls"}]}"""
    )

    private fun nativePackage(version: String) = zip(
        PluginPaths.MANIFEST to """{"format":2,"kind":"native","id":"$id","name":"示例","version":"$version","apiVersion":"1.0",
            "main":"com.example.Entry","permissions":[],"contributes":{"commands":[{"id":"run","title":"运行"}]}}""",
        PluginPaths.JAR to "jar"
    )

    private fun sha256(bytes: ByteArray) = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

    private fun publish(version: String, bytes: ByteArray, kind: String = "sandbox", declaredHash: String = sha256(bytes)) {
        val url = "https://example.com/$version.melodiaplugin"
        http.files[url] = bytes
        http.index = """{"format":1,"plugins":[{"id":"$id","name":"示例","version":"$version","kind":"$kind",
            "source":"https://github.com/a/b","download":{"url":"$url","sha256":"$declaredHash","size":${bytes.size}}}]}"""
    }

    private fun flow(block: suspend (PluginSettingsController, CoroutineScope) -> Unit) = runBlocking(Dispatchers.Default) {
        val scope = CoroutineScope(coroutineContext + Job(coroutineContext.job))
        block(PluginSettingsController(repository, scope), scope)
        scope.coroutineContext.job.children.toList().joinAll()
        scope.coroutineContext.job.cancel()
    }

    private suspend fun CoroutineScope.idle() {
        coroutineContext.job.children.toList().joinAll()
    }

    private suspend fun install(controller: PluginSettingsController, scope: CoroutineScope): PluginImportStep {
        controller.loadCatalog()
        scope.idle()
        controller.installFromCatalog(controller.catalog.value.entries.single())
        scope.idle()
        return controller.step
    }

    private fun stagingEntries() = File(root, ".staging").listFiles().orEmpty().toList()

    @Test
    fun `从目录安装、升级再回退`() = flow { controller, scope ->
        publish("1.0.0", sandboxPackage("1.0.0"))
        val first = install(controller, scope) as PluginImportStep.Preview
        assertNull(first.existing)
        assertFalse(first.unreviewed)
        controller.confirmImport()
        scope.idle()
        assertEquals("1.0.0", repository.installed.value.single().manifest?.version)
        assertTrue(pluginUpdates(repository.installed.value, controller.catalog.value.entries).isEmpty())

        publish("2.0.0", sandboxPackage("2.0.0"))
        controller.loadCatalog()
        scope.idle()
        assertEquals("2.0.0", pluginUpdates(repository.installed.value, controller.catalog.value.entries)[id]?.version)

        val second = install(controller, scope) as PluginImportStep.Preview
        assertEquals("1.0.0", second.existing?.manifest?.version)
        controller.confirmImport()
        scope.idle()
        val upgraded = repository.installed.value.single()
        assertEquals("2.0.0", upgraded.manifest?.version)
        assertEquals("1.0.0", upgraded.previousVersion)

        controller.rollback(upgraded)
        scope.idle()
        val restored = repository.installed.value.single()
        assertEquals("1.0.0", restored.manifest?.version)
        assertNull(restored.previousVersion)
        assertEquals("已回退到 v1.0.0", controller.message)
        assertTrue(stagingEntries().isEmpty())
    }

    @Test
    fun `内容被改动时不进入预览`() = flow { controller, scope ->
        publish("1.0.0", sandboxPackage("1.0.0"), declaredHash = "a".repeat(64))
        val step = install(controller, scope) as PluginImportStep.Failed
        assertTrue(step.message, step.message.contains("校验"))
        assertTrue(repository.installed.value.isEmpty())
        assertTrue(stagingEntries().isEmpty())
    }

    @Test
    fun `包内 id 或版本与目录不一致时拒绝`() = flow { controller, scope ->
        publish("1.0.0", sandboxPackage("1.0.0", "com.example.other"))
        assertTrue((install(controller, scope) as PluginImportStep.Failed).message.contains("id"))
        assertTrue(stagingEntries().isEmpty())

        controller.dismissFailure()
        publish("1.0.0", sandboxPackage("9.9.9"))
        assertTrue((install(controller, scope) as PluginImportStep.Failed).message.contains("版本"))
        assertTrue(stagingEntries().isEmpty())
    }

    @Test
    fun `原生插件没开总开关时不下载`() = flow { controller, scope ->
        publish("1.0.0", nativePackage("1.0.0"), kind = "native")
        val step = install(controller, scope) as PluginImportStep.Failed
        assertTrue(step.message.contains("允许原生插件"))
        assertEquals(0, http.downloads)
    }

    @Test
    fun `默认目录的原生插件不带自定义目录警告`() = flow { controller, scope ->
        repository.setNativeAllowed(true)
        publish("1.0.0", nativePackage("1.0.0"), kind = "native")
        val preview = install(controller, scope) as PluginImportStep.Preview
        assertNull(pluginCatalogWarning(preview))
        controller.cancelImport()
    }

    @Test
    fun `自定义目录的原生插件带警告`() = flow { controller, scope ->
        repository.setNativeAllowed(true)
        publish("1.0.0", nativePackage("1.0.0"), kind = "native")
        controller.setCatalogUrl("https://mirror.example.com/index.json")
        scope.idle()
        val preview = install(controller, scope) as PluginImportStep.Preview
        assertTrue(preview.unreviewed)
        assertNotNull(pluginCatalogWarning(preview))
        controller.cancelImport()
    }

    @Test
    fun `自定义目录的沙盒插件不弹风险警告`() = flow { controller, scope ->
        publish("1.0.0", sandboxPackage("1.0.0"))
        controller.setCatalogUrl("https://mirror.example.com/index.json")
        scope.idle()
        val preview = install(controller, scope) as PluginImportStep.Preview
        assertTrue(preview.unreviewed)
        assertNull(pluginCatalogWarning(preview))
        controller.cancelImport()
    }
}

class OkHttpPluginCatalogHttpTest {
    private lateinit var directory: File

    @Before
    fun setUp() {
        directory = Files.createTempDirectory("catalog-http").toFile()
    }

    @After
    fun tearDown() {
        directory.deleteRecursively()
    }

    private fun http(code: Int = 200, body: ByteArray = ByteArray(0), url: String? = null) = OkHttpPluginCatalogHttp(
        OkHttpClient.Builder().addInterceptor(Interceptor { chain ->
            val request = chain.request().let { original -> url?.let { original.newBuilder().url(it).build() } ?: original }
            Response.Builder()
                .request(request)
                .protocol(Protocol.HTTP_1_1)
                .code(code)
                .message("x")
                .body(body.toResponseBody("application/octet-stream".toMediaType()))
                .build()
        }).build()
    )

    private fun assertFails(expected: String, block: suspend () -> Unit) = runBlocking {
        try {
            block()
            fail("应该失败")
        } catch (e: IOException) {
            assertTrue(e.message, e.message!!.contains(expected))
        }
    }

    private val url = "https://example.com/a"

    @Test
    fun `读取文本`() = runBlocking {
        assertEquals("你好", http(body = "你好".toByteArray()).fetchText(url, 100))
    }

    @Test
    fun `服务器报错时失败`() = assertFails("404") { http(code = 404).fetchText(url, 100) }

    @Test
    fun `响应超限时失败`() = assertFails("大小") { http(body = ByteArray(101)).fetchText(url, 100) }

    @Test
    fun `重定向到 http 时失败`() = assertFails("https") { http(body = ByteArray(1), url = "http://example.com/a").fetchText(url, 100) }

    @Test
    fun `地址无效时失败`() = assertFails("地址无效") { http().fetchText("不是地址", 100) }

    @Test
    fun `下载写入文件并报告进度`() = runBlocking {
        val target = File(directory, "a")
        var progress = 0L
        http(body = ByteArray(100_000) { 7 }).download(url, 100_000, target) { progress = it }
        assertEquals(100_000L, target.length())
        assertEquals(100_000L, progress)
    }

    @Test
    fun `下载超出声明大小时失败`() = assertFails("大小") {
        http(body = ByteArray(101)).download(url, 100, File(directory, "a")) {}
    }
}

package com.lin0721.linmusic.core.plugin

import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.security.MessageDigest
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class PluginCatalogTest {
    private val hash = "a".repeat(64)

    private fun entry(
        id: String = "com.example.demo",
        version: String = "1.0.0",
        kind: String = "sandbox",
        extra: String = "",
        url: String = "https://example.com/demo.melodiaplugin",
        sha256: String = hash,
        size: Long = 100
    ) = """{"id":"$id","name":"示例","version":"$version","kind":"$kind"$extra,
        "download":{"url":"$url","sha256":"$sha256","size":$size}}"""

    private fun index(vararg entries: String) = """{"format":1,"updatedAt":"2026-11-02","plugins":[${entries.joinToString(",")}]}"""

    private fun parse(vararg entries: String) = PluginCatalogParser.parse(index(*entries), PluginPlatform.DESKTOP)!!

    @Test
    fun `解析合规条目`() {
        val catalog = parse(entry(extra = ""","author":"作者","platforms":["desktop"],"apiVersion":"1.0","permissions":["storage"]"""))
        assertEquals(0, catalog.skipped)
        assertEquals("2026-11-02", catalog.updatedAt)
        val item = catalog.entries.single()
        assertEquals("作者", item.author)
        assertEquals(100L, item.download.size)
        assertFalse(item.isNative)
    }

    @Test
    fun `整体格式不对返回 null`() {
        assertNull(PluginCatalogParser.parse("不是 JSON"))
        assertNull(PluginCatalogParser.parse("[]"))
        assertNull(PluginCatalogParser.parse("""{"format":2,"plugins":[]}"""))
        assertNull(PluginCatalogParser.parse("""{"format":1}"""))
    }

    @Test
    fun `不合规条目被跳过，其他条目不受影响`() {
        val catalog = parse(
            entry(),
            entry(id = "bad id"),
            entry(id = "com.example.http", url = "http://example.com/a"),
            entry(id = "com.example.hash", sha256 = "abc"),
            entry(id = "com.example.upper", sha256 = "A".repeat(64)),
            entry(id = "com.example.empty", size = 0),
            entry(id = "com.example.huge", size = 11L * 1024 * 1024),
            entry(id = "com.example.kind", kind = "other"),
            entry(id = "com.example.native", kind = "native"),
            """{"id":"com.example.nodownload","name":"x","version":"1"}""",
            "42",
            entry()
        )
        assertEquals(listOf("com.example.demo"), catalog.entries.map { it.id })
        assertEquals(11, catalog.skipped)
    }

    @Test
    fun `原生插件必须有 https 源码地址`() {
        val source = ""","source":"https://github.com/a/b""""
        val catalog = parse(
            entry(kind = "native", extra = source, size = 150L * 1024 * 1024),
            entry(id = "com.example.other", kind = "native", extra = ""","source":"http://github.com/a/b"""")
        )
        assertEquals(1, catalog.entries.size)
        assertTrue(catalog.entries.single().isNative)
    }

    @Test
    fun `Android 不显示原生插件`() {
        val text = index(entry(kind = "native", extra = ""","source":"https://github.com/a/b""""), entry(id = "com.example.sandbox"))
        val catalog = PluginCatalogParser.parse(text, PluginPlatform.ANDROID)!!
        assertEquals(listOf("com.example.sandbox"), catalog.entries.map { it.id })
        assertEquals(0, catalog.skipped)
    }

    @Test
    fun `不兼容原因`() {
        val desktopOnly = parse(entry(extra = ""","platforms":["desktop"]""")).entries.single()
        assertNull(PluginCatalogParser.incompatibility(desktopOnly, PluginPlatform.DESKTOP))
        assertNotNull(PluginCatalogParser.incompatibility(desktopOnly, PluginPlatform.ANDROID))

        val future = parse(entry(extra = ""","apiVersion":"1.99"""")).entries.single()
        assertNotNull(PluginCatalogParser.incompatibility(future, PluginPlatform.DESKTOP))
    }

    @Test
    fun `版本比较`() {
        assertTrue(comparePluginVersions("1.10.0", "1.9.9") > 0)
        assertTrue(comparePluginVersions("2", "1.9") > 0)
        assertEquals(0, comparePluginVersions("1.0", "1.0.0"))
        assertEquals(0, comparePluginVersions("v1.0.0", "1.0.0"))
        assertTrue(comparePluginVersions("1.0.0", "1.0.1") < 0)
        assertTrue(comparePluginVersions("1.0.0-beta", "1.0.0-alpha") > 0)
        assertTrue(comparePluginVersions("1.0.0", "1.0.0-beta") > 0)
        assertTrue(comparePluginVersions("1.0.0-beta.2", "1.0.0-beta.10") < 0)
        assertEquals(0, comparePluginVersions("1.0.0+build.5", "1.0.0"))
    }

    @Test
    fun `https 地址判断`() {
        assertTrue(isHttps("https://example.com/a.json"))
        assertFalse(isHttps("http://example.com/a.json"))
        assertFalse(isHttps("https://"))
        assertFalse(isHttps("file:///etc/passwd"))
        assertFalse(isHttps("https://exa mple.com"))
        assertFalse(isHttps("https://example.com/" + "a".repeat(3000)))
    }
}

class PluginCatalogServiceTest {
    private lateinit var root: File
    private lateinit var store: PluginStore
    private val http = FakeHttp()
    private var clock = System.currentTimeMillis()

    private class FakeHttp : PluginCatalogHttp {
        var text: String? = null
        var bytes: ByteArray = ByteArray(0)
        var failure: IOException? = null
        val fetched = mutableListOf<String>()

        override suspend fun fetchText(url: String, maxBytes: Long): String {
            fetched += url
            failure?.let { throw it }
            return text ?: throw IOException("没有内容")
        }

        override suspend fun download(url: String, maxBytes: Long, target: File, onProgress: (Long) -> Unit) {
            failure?.let { throw it }
            target.writeBytes(bytes)
            onProgress(bytes.size.toLong())
        }
    }

    @Before
    fun setUp() {
        root = Files.createTempDirectory("catalog").toFile()
        store = PluginStore(File(root, "plugins"))
    }

    @After
    fun tearDown() {
        root.deleteRecursively()
    }

    private fun service() = PluginCatalogService(store, http, PluginPlatform.DESKTOP) { clock }

    private fun sha256(bytes: ByteArray) = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

    private fun catalogText(version: String = "1.0.0", bytes: ByteArray = ByteArray(10)) =
        """{"format":1,"plugins":[{"id":"com.example.demo","name":"示例","version":"$version",
            "download":{"url":"https://example.com/a","sha256":"${sha256(bytes)}","size":${bytes.size}}}]}"""

    @Test
    fun `读取成功后写入缓存`() = runBlocking {
        http.text = catalogText()
        val service = service()
        service.load()

        assertEquals(1, service.state.value.entries.size)
        assertFalse(service.state.value.loading)
        assertNull(service.state.value.error)
        assertEquals(http.text, store.catalogCache())
    }

    @Test
    fun `读取失败时保留缓存并给出提示`() = runBlocking {
        http.text = catalogText()
        service().load()
        http.failure = IOException("断网")

        val service = service()
        service.load()
        assertEquals(1, service.state.value.entries.size)
        assertTrue(service.state.value.error!!.contains("断网"))
        assertFalse(service.state.value.loading)
    }

    @Test
    fun `格式无效时不覆盖缓存`() = runBlocking {
        http.text = catalogText()
        val service = service()
        service.load()
        http.text = "{}"
        service.load()

        assertEquals("目录格式无效", service.state.value.error)
        assertEquals(1, service.state.value.entries.size)
        assertEquals(catalogText(), store.catalogCache())
    }

    @Test
    fun `启动检查每天最多联网一次`() = runBlocking {
        http.text = catalogText()
        val service = service()
        assertEquals(1, service.entriesForStartup().size)
        assertEquals(1, http.fetched.size)

        assertEquals(1, service.entriesForStartup().size)
        assertEquals(1, http.fetched.size)

        clock += 25 * 60 * 60 * 1000L
        service.entriesForStartup()
        assertEquals(2, http.fetched.size)
    }

    @Test
    fun `修改目录地址`() = runBlocking {
        http.text = catalogText()
        val service = service()
        service.load()
        assertTrue(service.state.value.isDefault)

        assertNotNull(service.setUrl("http://mirror.example.com/index.json"))
        assertTrue(service.state.value.isDefault)

        http.text = """{"format":1,"plugins":[]}"""
        assertNull(service.setUrl(" https://mirror.example.com/index.json "))
        assertEquals("https://mirror.example.com/index.json", service.state.value.url)
        assertFalse(service.state.value.isDefault)
        assertTrue(service.state.value.entries.isEmpty())
        assertEquals("https://mirror.example.com/index.json", http.fetched.last())

        http.text = catalogText()
        assertNull(service.setUrl(""))
        assertTrue(service.state.value.isDefault)
        assertEquals(DEFAULT_PLUGIN_CATALOG_URL, http.fetched.last())
        assertNull(store.catalogUrl())
    }

    private suspend fun entry(bytes: ByteArray, declared: ByteArray = bytes): PluginCatalogEntry {
        http.text = catalogText(bytes = declared)
        val service = service()
        service.load()
        http.bytes = bytes
        return service.state.value.entries.single()
    }

    private fun stagingFiles() = File(root, "plugins/.staging").listFiles().orEmpty().toList()

    @Test
    fun `校验通过后返回文件`() = runBlocking {
        val bytes = ByteArray(10) { it.toByte() }
        val target = entry(bytes)
        var progress = 0L
        val file = service().download(target) { progress = it }
        assertTrue(bytes.contentEquals(file.readBytes()))
        assertEquals(10L, progress)
    }

    private fun assertDownloadFails(block: suspend () -> Unit, expected: String) = runBlocking {
        try {
            block()
            fail("应该失败")
        } catch (e: PluginCatalogException) {
            assertTrue(e.message, e.message!!.contains(expected))
        }
        assertTrue(stagingFiles().isEmpty())
    }

    @Test
    fun `哈希不符时拒绝并清理`() {
        val target = runBlocking { entry(ByteArray(10) { 1 }, ByteArray(10) { 2 }) }
        assertDownloadFails({ service().download(target) {} }, "校验")
    }

    @Test
    fun `大小不符时拒绝并清理`() {
        val target = runBlocking { entry(ByteArray(9), ByteArray(10)) }
        assertDownloadFails({ service().download(target) {} }, "大小")
    }

    @Test
    fun `网络失败时拒绝并清理`() {
        val target = runBlocking { entry(ByteArray(10)) }
        http.failure = IOException("断网")
        assertDownloadFails({ service().download(target) {} }, "断网")
    }

    private fun plugin(version: String): InstalledPlugin {
        val manifest = """{"format":1,"id":"com.example.demo","name":"示例","version":"$version","permissions":["playback.read"],
            "contributes":{"events":["songChanged"]}}"""
        val zip = ByteArrayOutputStream()
        ZipOutputStream(zip).use {
            it.putNextEntry(ZipEntry(PluginPaths.MANIFEST))
            it.write(manifest.toByteArray())
            it.closeEntry()
            it.putNextEntry(ZipEntry(PluginPaths.SCRIPT))
            it.write("1".toByteArray())
            it.closeEntry()
        }
        val staged = (store.stage(ByteArrayInputStream(zip.toByteArray())) as PluginStageResult.Success).staged
        return store.install(staged, enabled = true)!!
    }

    @Test
    fun `更新只包含版本更高且能安装的插件`() {
        val installed = listOf(plugin("1.0.0"))
        val entries = { version: String, extra: String ->
            PluginCatalogParser.parse(
                """{"format":1,"plugins":[{"id":"com.example.demo","name":"示例","version":"$version"$extra,
                "download":{"url":"https://example.com/a","sha256":"${"a".repeat(64)}","size":1}}]}""",
                PluginPlatform.DESKTOP
            )!!.entries
        }
        assertEquals("1.1.0", pluginUpdates(installed, entries("1.1.0", ""))["com.example.demo"]?.version)
        assertTrue(pluginUpdates(installed, entries("1.0.0", "")).isEmpty())
        assertTrue(pluginUpdates(installed, entries("0.9.0", "")).isEmpty())
        assertTrue(pluginUpdates(installed, entries("2.0.0", ""","apiVersion":"1.99"""")).isEmpty())
        assertTrue(pluginUpdates(emptyList(), entries("2.0.0", "")).isEmpty())
    }

    @Test
    fun `包内信息和目录不一致时给出原因`() {
        val manifest = plugin("1.0.0").manifest!!
        val entry = PluginCatalogEntry(
            "com.example.demo", "示例", "1.0.0",
            download = PluginCatalogDownload("https://example.com/a", "a".repeat(64), 1)
        )
        assertNull(pluginCatalogMismatch(entry, manifest))
        assertNotNull(pluginCatalogMismatch(entry.copy(id = "com.example.other"), manifest))
        assertNotNull(pluginCatalogMismatch(entry.copy(version = "2.0.0"), manifest))
        assertNotNull(pluginCatalogMismatch(entry.copy(kind = "native"), manifest))
    }
}

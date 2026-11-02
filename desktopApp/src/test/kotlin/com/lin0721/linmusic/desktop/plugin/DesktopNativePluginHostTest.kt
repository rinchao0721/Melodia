package com.lin0721.linmusic.desktop.plugin

import com.lin0721.linmusic.core.plugin.Extension
import com.lin0721.linmusic.core.plugin.ExtensionPoints
import com.lin0721.linmusic.core.plugin.ExtensionRegistry
import com.lin0721.linmusic.core.plugin.InstalledPlugin
import com.lin0721.linmusic.core.plugin.PLUGIN_API_MAJOR
import com.lin0721.linmusic.core.plugin.PLUGIN_API_MINOR
import com.lin0721.linmusic.core.plugin.PluginHostApi
import com.lin0721.linmusic.core.plugin.PluginNotices
import com.lin0721.linmusic.core.plugin.PluginPackage
import com.lin0721.linmusic.core.plugin.PluginPaths
import com.lin0721.linmusic.core.plugin.PluginPlatform
import com.lin0721.linmusic.core.plugin.PluginRepository
import com.lin0721.linmusic.core.plugin.PluginRuntime
import com.lin0721.linmusic.core.plugin.PluginStageResult
import com.lin0721.linmusic.core.plugin.PluginStore
import com.lin0721.linmusic.plugin.api.PluginApiVersion
import com.lin0721.linmusic.plugin.api.SpectrumFrame
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.JsonNull
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.file.Files
import java.util.jar.JarEntry
import java.util.jar.JarOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class DesktopNativePluginHostTest {
    private lateinit var root: File
    private lateinit var store: PluginStore
    private val registry = ExtensionRegistry()
    private val notices = PluginNotices()
    private val failures = mutableListOf<Pair<String, String>>()

    @Before
    fun setUp() {
        root = Files.createTempDirectory("native-plugin").toFile()
        store = PluginStore(root)
    }

    @After
    fun tearDown() {
        root.deleteRecursively()
    }

    private fun host(safeMode: Boolean = false, loadTimeoutMs: Long = 5_000, spectrum: Flow<SpectrumFrame> = emptyFlow()) =
        DesktopNativePluginHost(store, PluginHostApi(store), registry, notices, safeMode, javaClass.classLoader, loadTimeoutMs, spectrum).also {
            it.setFailureHandler { id, reason -> synchronized(failures) { failures += id to reason } }
        }

    private fun pluginJar(): ByteArray {
        val output = ByteArrayOutputStream()
        val packageDirectory = File(NormalPluginLocator.location, "com/lin0721/linmusic/testplugin")
        JarOutputStream(output).use { jar ->
            packageDirectory.listFiles().orEmpty().filter { it.extension == "class" }.forEach { file ->
                jar.putNextEntry(JarEntry("com/lin0721/linmusic/testplugin/${file.name}"))
                file.inputStream().use { it.copyTo(jar) }
                jar.closeEntry()
            }
        }
        return output.toByteArray()
    }

    private fun install(
        mainClass: String,
        id: String = "com.example.native",
        extra: String = "",
        permissions: String = "\"storage\""
    ): InstalledPlugin {
        val manifest = """
            {"format":2,"kind":"native","id":"$id","name":"测试","version":"1.0.0","apiVersion":"1.0",
             "main":"com.lin0721.linmusic.testplugin.$mainClass","permissions":[$permissions],
             "contributes":{"commands":[{"id":"run","title":"运行"}]$extra}}
        """.trimIndent()
        val zip = ByteArrayOutputStream()
        ZipOutputStream(zip).use { out ->
            out.putNextEntry(ZipEntry(PluginPaths.MANIFEST))
            out.write(manifest.toByteArray())
            out.closeEntry()
            out.putNextEntry(ZipEntry(PluginPaths.JAR))
            out.write(pluginJar())
            out.closeEntry()
        }
        val staged = (store.stage(ByteArrayInputStream(zip.toByteArray()), "$mainClass.melodiaplugin") as PluginStageResult.Success).staged
        return assertNotNullValue(store.install(staged, enabled = true))
    }

    private fun assertNotNullValue(plugin: InstalledPlugin?): InstalledPlugin {
        assertNotNull(plugin)
        return plugin!!
    }

    private fun dataFile(id: String, name: String) = File(store.dataDirectoryOf(id), name)

    private fun commands() = registry.get(ExtensionPoints.COMMAND)

    @Test
    fun `API 版本与解析器一致`() {
        assertEquals(PLUGIN_API_MAJOR, PluginApiVersion.MAJOR)
        assertEquals(PLUGIN_API_MINOR, PluginApiVersion.MINOR)
    }

    @Test
    fun `有 playback read 权限的插件能收到频谱`() = runBlocking {
        val plugin = install("SpectrumProbePlugin", permissions = "\"playback.read\"")
        val host = host(spectrum = flowOf(SpectrumFrame(0, FloatArray(64), FloatArray(1024))))
        host.sync(listOf(plugin), allowed = true)

        assertTrue(commands().single().value.execute(JsonNull))
        assertEquals("bands=64", dataFile(plugin.id, "spectrum").readText())
        host.unload(plugin.id)
    }

    @Test
    fun `没有 playback read 权限时收集频谱失败`() = runBlocking {
        val plugin = install("SpectrumProbePlugin")
        val host = host(spectrum = flowOf(SpectrumFrame(0, FloatArray(64), FloatArray(1024))))
        host.sync(listOf(plugin), allowed = true)

        assertTrue(commands().single().value.execute(JsonNull))
        assertEquals("缺少权限 playback.read", dataFile(plugin.id, "spectrum").readText())
        host.unload(plugin.id)
    }

    @Test
    fun `加载后命令可用，卸载时回调并注销`() = runBlocking {
        val plugin = install("NormalPlugin")
        val host = host()
        host.sync(listOf(plugin), allowed = true)

        assertTrue(host.isActive(plugin.id))
        assertEquals(true, host.status.value[plugin.id]?.running)
        assertEquals(setOf("command:run"), host.status.value[plugin.id]?.registrations)
        assertTrue(commands().single().value.execute(JsonNull))
        assertEquals("yes", dataFile(plugin.id, "ran").readText())

        host.unload(plugin.id)
        assertFalse(host.isActive(plugin.id))
        assertTrue(commands().isEmpty())
        assertEquals("yes", dataFile(plugin.id, "unloaded").readText())
    }

    @Test
    fun `运行中可以删除安装目录`() = runBlocking {
        val plugin = install("NormalPlugin")
        val host = host()
        host.sync(listOf(plugin), allowed = true)
        assertTrue(store.delete(plugin.id))
        assertFalse(File(root, plugin.id).exists())
    }

    @Test
    fun `总开关关闭时不加载，并说明原因`() = runBlocking {
        val plugin = install("NormalPlugin")
        val host = host()
        host.sync(listOf(plugin), allowed = false)
        assertFalse(host.isActive(plugin.id))
        assertEquals("「允许原生插件」未开启", host.status.value[plugin.id]?.lastError)
    }

    @Test
    fun `总开关关闭后已加载的插件被卸载，再开启要等重启`() = runBlocking {
        val plugin = install("NormalPlugin")
        val host = host()
        host.sync(listOf(plugin), allowed = true)
        host.sync(listOf(plugin), allowed = false)
        assertFalse(host.isActive(plugin.id))
        host.sync(listOf(plugin), allowed = true)
        assertFalse(host.isActive(plugin.id))
        assertNull(host.status.value[plugin.id])
    }

    @Test
    fun `安全模式跳过所有原生插件`() = runBlocking {
        val plugin = install("NormalPlugin")
        val host = host(safeMode = true)
        host.sync(listOf(plugin), allowed = true)
        assertFalse(host.isActive(plugin.id))
        assertEquals("安全模式下未加载", host.status.value[plugin.id]?.lastError)
    }

    @Test
    fun `onLoad 抛异常时回滚已注册的扩展`() = runBlocking {
        val plugin = install("ThrowingPlugin")
        val host = host()
        host.sync(listOf(plugin), allowed = true)
        assertFalse(host.isActive(plugin.id))
        assertTrue(commands().isEmpty())
        assertTrue(failures.single().second.contains("加载炸了"))
    }

    @Test
    fun `onLoad 卡住时按超时失败`() = runBlocking {
        val plugin = install("HangingPlugin")
        val host = host(loadTimeoutMs = 100)
        host.sync(listOf(plugin), allowed = true)
        assertFalse(host.isActive(plugin.id))
        assertTrue(failures.single().second.contains("没有返回"))
    }

    @Test
    fun `onLoad 超时后迟到的注册被拒绝`() = runBlocking {
        val plugin = install("LateRegisterPlugin")
        host(loadTimeoutMs = 100).sync(listOf(plugin), allowed = true)
        delay(600)
        assertTrue(commands().isEmpty())
    }

    @Test
    fun `回调阻塞线程时调用方按超时返回`() = runBlocking {
        val plugin = install("BlockingCommandPlugin")
        val host = host()
        host.sync(listOf(plugin), allowed = true)
        val result = withTimeout(DesktopNativePluginHost.ACTION_TIMEOUT_MS + 2_000) { commands().single().value.execute(JsonNull) }
        assertFalse(result)
        host.unload(plugin.id)
    }

    @Test
    fun `读取不存在的存储项得到 null`() = runBlocking {
        val plugin = install("StorageProbePlugin")
        val host = host()
        host.sync(listOf(plugin), allowed = true)
        assertTrue(commands().single().value.execute(JsonNull))
        assertEquals("true", dataFile(plugin.id, "storage").readText())
        host.unload(plugin.id)
    }

    private fun repository(): PluginRepository {
        val api = PluginHostApi(store)
        store.setNativeAllowed(true)
        return PluginRepository(store, PluginRuntime(GraalScriptSandboxFactory(), api, registry = registry), api, native = host())
    }

    @Test
    fun `加载失败后列表显示已停用`() = runBlocking {
        install("ThrowingPlugin")
        val repository = repository()
        repository.refresh()
        val plugin = repository.installed.value.single()
        assertFalse(plugin.state.enabled)
        assertTrue(plugin.state.disabledReason.orEmpty().contains("加载炸了"))
    }

    @Test
    fun `启动后才导入的原生插件要等重启`() = runBlocking {
        val repository = repository()
        repository.refresh()
        install("NormalPlugin")
        repository.refresh()
        assertTrue(commands().isEmpty())
    }

    @Test
    fun `注册清单没声明的扩展时加载失败`() = runBlocking {
        val plugin = install("UndeclaredPlugin")
        val host = host()
        host.sync(listOf(plugin), allowed = true)
        assertFalse(host.isActive(plugin.id))
        assertTrue(failures.single().second.contains("ghost"))
    }

    @Test
    fun `Compose 页面要在清单声明，卸载时一并注销`() = runBlocking {
        val declared = install("ComposePagePlugin", extra = ""","pages":[{"id":"panel","title":"面板","compose":true}]""")
        val undeclared = install("ComposePagePlugin", id = "com.example.undeclared")
        val host = host()
        host.sync(listOf(declared, undeclared), allowed = true)
        assertTrue(host.isActive(declared.id))
        assertEquals("panel", registry.get(ExtensionPoints.COMPOSE_PAGE).single().localId)
        assertFalse(host.isActive(undeclared.id))
        assertTrue(failures.single().second.contains("panel"))
        host.unload(declared.id)
        assertTrue(registry.get(ExtensionPoints.COMPOSE_PAGE).isEmpty())
    }

    @Test
    fun `入口类无效时给出原因`() = runBlocking {
        val missing = install("Missing", id = "com.example.missing")
        val wrong = install("NoInterfacePlugin", id = "com.example.wrong")
        host().sync(listOf(missing, wrong), allowed = true)
        assertTrue(failures[0].second.contains("找不到入口类"))
        assertTrue(failures[1].second.contains("没有实现 MelodiaPlugin"))
    }

    @Test
    fun `JAR 与安装时不一致时拒绝加载`() = runBlocking {
        val plugin = install("NormalPlugin")
        File(root, "${plugin.id}/${PluginPaths.JAR}").appendBytes(byteArrayOf(1))
        host().sync(listOf(plugin), allowed = true)
        assertTrue(failures.single().second.contains("不一致"))
    }

    @Test
    fun `上次加载崩溃的插件被自动停用`() = runBlocking {
        val crashed = install("NormalPlugin")
        val other = install("NormalPlugin", id = "com.example.other")
        store.nativeRoot.mkdirs()
        File(store.nativeRoot, "loading").writeText(crashed.id)
        val host = host()
        host.sync(listOf(crashed, other), allowed = true)
        assertFalse(host.isActive(crashed.id))
        assertTrue(host.isActive(other.id))
        assertEquals(crashed.id, failures.single().first)
        assertFalse(File(store.nativeRoot, "loading").exists())
    }

    @Test
    fun `加载完成后不留标记`() = runBlocking {
        val plugin = install("NormalPlugin")
        host().sync(listOf(plugin), allowed = true)
        assertFalse(File(store.nativeRoot, "loading").exists())
    }

    @Test
    fun `回调连续出错后自动卸载`() = runBlocking {
        val plugin = install("FailingCommandPlugin")
        val host = host()
        host.sync(listOf(plugin), allowed = true)
        val command: Extension<*> = commands().single()
        repeat(5) { assertFalse(commands().single().value.execute(JsonNull)) }
        assertEquals(plugin.id, command.pluginId)
        withTimeout(3_000) {
            while (host.isActive(plugin.id) || failures.isEmpty()) delay(10)
        }
        assertTrue(commands().isEmpty())
        assertTrue(failures.single().second.contains("连续出错"))
    }

    @Test
    fun `插件看不到宿主内部类，只看到 API`() = runBlocking {
        val plugin = install("ProbePlugin")
        host().sync(listOf(plugin), allowed = true)
        assertEquals("false,true", dataFile(plugin.id, "probe").readText())
    }

    @Test
    fun `示例插件能在真实包里加载并执行命令`() = runBlocking {
        val path = System.getProperty("melodia.samplePlugin")
        val staged = File(path).inputStream().use { store.stage(it, "sample-native.melodiaplugin") } as PluginStageResult.Success
        val plugin = assertNotNullValue(store.install(staged.staged, enabled = true))
        val host = host()
        host.sync(listOf(plugin), allowed = true)
        assertTrue(failures.toString(), host.isActive(plugin.id))
        assertEquals("hello", commands().single().localId)
    }

    @Test
    fun `沙盒包不能带 JAR，Android 不解包原生文件`() {
        val zip = ByteArrayOutputStream()
        ZipOutputStream(zip).use { out ->
            out.putNextEntry(ZipEntry(PluginPaths.MANIFEST))
            out.write("""{"format":2,"id":"com.example.sandbox","name":"a","version":"1","contributes":{"playerLayouts":{"desktop.playerBar":{"type":"column"}}}}""".toByteArray())
            out.closeEntry()
            out.putNextEntry(ZipEntry(PluginPaths.JAR))
            out.write(byteArrayOf(1, 2, 3))
            out.closeEntry()
        }
        val desktop = PluginPackage.stage(ByteArrayInputStream(zip.toByteArray()), File(root, "a"), platform = PluginPlatform.DESKTOP)
        assertTrue(desktop.toString(), (desktop as PluginStageResult.Failure).reason.contains("沙盒插件不能包含"))
        val android = PluginPackage.stage(ByteArrayInputStream(zip.toByteArray()), File(root, "b"), platform = PluginPlatform.ANDROID)
        assertTrue(android.toString(), android is PluginStageResult.Success)
        assertFalse(File(root, "b/${PluginPaths.JAR}").exists())
    }
}

private object NormalPluginLocator {
    val location: File = File(com.lin0721.linmusic.testplugin.NormalPlugin::class.java.protectionDomain.codeSource.location.toURI())
}

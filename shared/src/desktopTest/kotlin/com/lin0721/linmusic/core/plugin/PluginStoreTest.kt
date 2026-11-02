package com.lin0721.linmusic.core.plugin

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
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

class PluginStoreTest {

    private lateinit var root: File
    private lateinit var store: PluginStore

    @Before
    fun setUp() {
        root = Files.createTempDirectory("plugins").toFile()
        store = PluginStore(File(root, "plugins"))
    }

    @After
    fun tearDown() {
        root.deleteRecursively()
    }

    private fun manifest(version: String = "1.0.0", permissions: String = "\"playback.read\"") = """
        {"format":1,"id":"com.example.demo","name":"示例","version":"$version","permissions":[$permissions],
         "contributes":{"events":["songChanged"],"playerLayouts":{"desktop.playerBar":"ui/bar.json"}}}
    """.trimIndent()

    private fun zip(vararg entries: Pair<String, Any>): ByteArray {
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { zip ->
            entries.forEach { (name, content) ->
                zip.putNextEntry(ZipEntry(name))
                zip.write(if (content is String) content.toByteArray() else content as ByteArray)
                zip.closeEntry()
            }
        }
        return out.toByteArray()
    }

    private fun validPackage(version: String = "1.0.0", permissions: String = "\"playback.read\"") = zip(
        PluginPaths.MANIFEST to manifest(version, permissions),
        PluginPaths.SCRIPT to "melodia.on('songChanged', function() {});",
        "ui/bar.json" to "{\"type\":\"row\"}"
    )

    private fun stage(bytes: ByteArray) = store.stage(ByteArrayInputStream(bytes))

    private fun staged(bytes: ByteArray) = (stage(bytes) as PluginStageResult.Success).staged

    @Test
    fun `原子写入可以反复覆盖已有文件`() {
        val file = File(root, "nested/data.json")
        writeFileAtomically(file, "第一次")
        writeFileAtomically(file, "第二次")
        assertEquals("第二次", file.readText())
        assertEquals(listOf("data.json"), file.parentFile.list().orEmpty().toList())
    }

    @Test
    fun `首次安装没有可回退的版本`() {
        val plugin = store.install(staged(validPackage()), enabled = true)
        assertNull(plugin?.previousVersion)
        assertNull(store.rollback(plugin!!.id))
        assertEquals("1.0.0", store.installed(plugin.id)?.manifest?.version)
    }

    @Test
    fun `升级后可以回退，状态和数据都恢复`() {
        val id = store.install(staged(validPackage()), enabled = true)!!.id
        store.updateState(id) { it.copy(enabled = false, disabledReason = "手动") }
        store.dataDirectoryOf(id)!!.apply { mkdirs() }.resolve("keep.txt").writeText("x")

        val upgraded = store.install(staged(validPackage("2.0.0", "\"playback.read\",\"storage\"")), enabled = true)
        assertEquals("1.0.0", upgraded?.previousVersion)
        assertEquals("2.0.0", upgraded?.manifest?.version)

        val restored = store.rollback(id)
        assertEquals("1.0.0", restored?.manifest?.version)
        assertNull(restored?.previousVersion)
        assertFalse(restored!!.state.enabled)
        assertEquals("手动", restored.state.disabledReason)
        assertEquals(setOf("playback.read"), restored.state.granted)
        assertEquals("x", File(store.dataDirectoryOf(id), "keep.txt").readText())
        assertNull(store.rollback(id))
    }

    @Test
    fun `替换失败时保留旧版本`() {
        val id = store.install(staged(validPackage()), enabled = true)!!.id
        val broken = staged(validPackage("2.0.0"))
        broken.directory.deleteRecursively()

        assertNull(store.install(broken, enabled = true))
        val plugin = store.installed(id)
        assertEquals("1.0.0", plugin?.manifest?.version)
        assertTrue(plugin!!.state.enabled)
    }

    @Test
    fun `多次升级只保留上一版本`() {
        val id = store.install(staged(validPackage()), enabled = true)!!.id
        store.install(staged(validPackage("2.0.0")), enabled = true)
        assertEquals("2.0.0", store.install(staged(validPackage("3.0.0")), enabled = true)?.previousVersion)
        assertEquals("2.0.0", store.rollback(id)?.manifest?.version)
    }

    @Test
    fun `删除插件时清掉上一版本`() {
        val id = store.install(staged(validPackage()), enabled = true)!!.id
        store.install(staged(validPackage("2.0.0")), enabled = true)
        store.delete(id)

        assertNull(store.install(staged(validPackage()), enabled = true)?.previousVersion)
    }

    @Test
    fun `备份状态损坏时不能回退`() {
        val id = store.install(staged(validPackage()), enabled = true)!!.id
        store.install(staged(validPackage("2.0.0")), enabled = true)
        File(root, "plugins/.previous/$id.json").writeText("{坏")

        assertNull(store.rollback(id))
        assertEquals("2.0.0", store.installed(id)?.manifest?.version)
    }

    @Test
    fun `解包成功并忽略白名单外的文件`() {
        val result = stage(
            zip(
                PluginPaths.MANIFEST to manifest(),
                PluginPaths.SCRIPT to "1",
                "ui/bar.json" to "{}",
                "run.sh" to "rm -rf /",
                "assets/x.exe" to ByteArray(4)
            )
        )
        val staged = (result as PluginStageResult.Success).staged
        assertEquals("com.example.demo", staged.manifest.id)
        assertEquals(2, staged.warnings.count { it.startsWith("已忽略") })
        assertFalse(File(staged.directory, "run.sh").exists())
    }

    @Test
    fun `缺少脚本或界面文件、路径穿越与超限都会失败`() {
        assertTrue(stage(zip(PluginPaths.MANIFEST to manifest(), "ui/bar.json" to "{}")) is PluginStageResult.Failure)
        assertTrue(stage(zip(PluginPaths.MANIFEST to manifest(), PluginPaths.SCRIPT to "1")) is PluginStageResult.Failure)
        assertTrue(stage(zip(PluginPaths.MANIFEST to manifest(), "../evil.js" to "1")) is PluginStageResult.Failure)
        assertTrue(stage(zip(PluginPaths.MANIFEST to manifest(), PluginPaths.SCRIPT to ByteArray(3 * 1024 * 1024))) is PluginStageResult.Failure)
        assertTrue(stage(zip(PluginPaths.SCRIPT to "1")) is PluginStageResult.Failure)
        assertTrue(stage("not a zip".toByteArray()) is PluginStageResult.Failure)
        assertFalse(File(root, "evil.js").exists())
    }

    @Test
    fun `安装后记录已确认的权限并按状态列出`() {
        val plugin = store.install(staged(validPackage()), enabled = true)
        assertNotNull(plugin)
        assertTrue(plugin!!.state.enabled)
        assertTrue(plugin.canRun)
        assertEquals(setOf("playback.read"), plugin.state.granted)

        store.updateState(plugin.id) { it.copy(enabled = false, disabledReason = "出错") }
        val reloaded = PluginStore(File(root, "plugins")).list().single()
        assertFalse(reloaded.state.enabled)
        assertEquals("出错", reloaded.state.disabledReason)
    }

    @Test
    fun `清单权限多于已确认的权限时不能运行`() {
        store.install(staged(validPackage()), enabled = true)
        val directory = File(root, "plugins/com.example.demo")
        File(directory, PluginPaths.MANIFEST).writeText(manifest(permissions = "\"playback.read\",\"storage\""))
        val plugin = store.list().single()
        assertEquals(setOf(PluginPermission.STORAGE), plugin.missingPermissions)
        assertFalse(plugin.canRun)
    }

    @Test
    fun `覆盖安装即升级，插件数据保留`() {
        store.install(staged(validPackage()), enabled = true)
        val data = File(store.dataDirectoryOf("com.example.demo"), "storage.json")
        data.parentFile.mkdirs()
        data.writeText("{\"k\":1}")
        val upgraded = store.install(staged(validPackage(version = "2.0.0")), enabled = true)
        assertEquals("2.0.0", upgraded?.manifest?.version)
        assertTrue(data.isFile)
        assertEquals(1, store.list().size)
    }

    @Test
    fun `清单损坏的插件仍列出并可删除`() {
        store.install(staged(validPackage()), enabled = true)
        File(root, "plugins/com.example.demo/${PluginPaths.MANIFEST}").writeText("{broken")
        val plugin = store.list().single()
        assertNull(plugin.manifest)
        assertNotNull(plugin.loadError)
        assertFalse(plugin.canRun)
        assertTrue(store.delete(plugin.id))
        assertTrue(store.list().isEmpty())
    }

    @Test
    fun `删除插件同时删除插件数据与状态`() {
        store.install(staged(validPackage()), enabled = true)
        val dataDir = store.dataDirectoryOf("com.example.demo")!!
        dataDir.mkdirs()
        File(dataDir, "storage.json").writeText("{}")
        assertTrue(store.delete("com.example.demo"))
        assertFalse(dataDir.exists())
        assertNull(store.installed("com.example.demo"))
        store.install(staged(validPackage()), enabled = false)
        assertFalse(store.list().single().state.enabled)
    }

    @Test
    fun `导出只包含安装文件，导出后可再次导入`() {
        store.install(staged(validPackage()), enabled = true)
        val out = ByteArrayOutputStream()
        assertTrue(store.export("com.example.demo", out))
        val names = ZipInputStream(ByteArrayInputStream(out.toByteArray())).use { zip ->
            generateSequence { zip.nextEntry?.name }.toList()
        }
        assertEquals(listOf(PluginPaths.SCRIPT, PluginPaths.MANIFEST, "ui/bar.json"), names)
        assertEquals("com.example.demo", staged(out.toByteArray()).manifest.id)
    }

    @Test
    fun `非法 id 不会拼出插件目录外的路径`() {
        assertNull(store.installed("../plugins"))
        assertNull(store.dataDirectoryOf("../x"))
        assertFalse(store.delete(".."))
    }
}

package com.lin0721.linmusic.core.source

import com.lin0721.linmusic.feature.podcast.ui.InMemoryPreferencesStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LxPluginManagerTest {

    private class FakeRuntime : LxPluginRuntime {
        var verifyResult: Result<LxPluginInfo> = Result.success(LxPluginInfo(name = "插件A", version = "1.0.0", sources = listOf("kw")))
        var downloadResult: Result<String> = Result.success("downloaded script")
        val activated = mutableListOf<String>()
        val deactivated = mutableListOf<String>()
        val synced = mutableListOf<List<String>>()
        var resetCount = 0

        override suspend fun downloadScript(url: String) = downloadResult
        override suspend fun loadAndVerifyScript(scriptText: String) = verifyResult
        override suspend fun activatePlugin(item: LxPluginItem): Boolean {
            activated += item.id
            return true
        }

        override fun deactivatePlugin(pluginId: String) {
            deactivated += pluginId
        }

        override suspend fun syncActivePlugins(items: List<LxPluginItem>) {
            synced += items.map { it.id }
        }

        override fun getSupportedSources(pluginId: String): List<String> = emptyList()
        override suspend fun resolveMusicUrl(
            pluginId: String,
            source: String,
            songId: String,
            songName: String,
            singer: String,
            albumName: String,
            durationMs: Long,
            quality: String,
            timeoutMs: Long
        ): String? = null

        override fun reset() {
            resetCount++
        }
    }

    private val runtime = FakeRuntime()
    private val prefs = SourcePreferences(InMemoryPreferencesStore())
    private val manager = LxPluginManager(runtime, prefs)

    @Test
    fun `无重名时直接入库并在总开关打开时激活`() = runBlocking {
        prefs.saveLxPluginEnabled(true)
        val result = manager.precheckAndImport("script")
        assertTrue(result is LxPluginManager.ImportCheckResult.Success)
        val saved = prefs.lxPlugins.first()
        assertEquals(1, saved.size)
        assertEquals("插件A", saved[0].name)
        assertEquals(listOf(saved[0].id), runtime.activated)
    }

    @Test
    fun `总开关关闭时导入不激活沙盒`() = runBlocking {
        manager.precheckAndImport("script")
        assertTrue(runtime.activated.isEmpty())
    }

    @Test
    fun `同名插件返回重名结果且不入库`() = runBlocking {
        manager.precheckAndImport("script")
        val second = manager.precheckAndImport("script2")
        assertTrue(second is LxPluginManager.ImportCheckResult.Duplicate)
        assertEquals(1, prefs.lxPlugins.first().size)
    }

    @Test
    fun `覆盖导入保持原 id，副本导入生成新 id`() = runBlocking {
        val first = (manager.precheckAndImport("v1") as LxPluginManager.ImportCheckResult.Success).item
        val dup = manager.precheckAndImport("v2") as LxPluginManager.ImportCheckResult.Duplicate

        manager.confirmImport(dup.newInfo, "v2", overwriteExistingId = first.id)
        assertEquals(listOf(first.id), prefs.lxPlugins.first().map { it.id })
        assertEquals("v2", prefs.lxPlugins.first()[0].rawScript)

        manager.confirmImport(dup.newInfo, "v3", overwriteExistingId = null)
        assertEquals(2, prefs.lxPlugins.first().size)
    }

    @Test
    fun `脚本校验失败时返回失败信息`() = runBlocking {
        runtime.verifyResult = Result.failure(Exception("语法错误"))
        val result = manager.precheckAndImport("bad")
        assertEquals("语法错误", (result as LxPluginManager.ImportCheckResult.Failure).message)
        assertTrue(prefs.lxPlugins.first().isEmpty())
    }

    @Test
    fun `链接输入走下载，文本输入原样返回`() = runBlocking {
        assertEquals("downloaded script", manager.resolveScriptContent(" https://a.b/c.js ").getOrThrow())
        assertEquals("plain", manager.resolveScriptContent("  plain ").getOrThrow())
        runtime.downloadResult = Result.failure(Exception("404"))
        assertTrue(manager.resolveScriptContent("http://a.b/c.js").isFailure)
    }

    @Test
    fun `总开关开关会同步或重置沙盒`() = runBlocking {
        manager.precheckAndImport("script")
        manager.setEnabled(true)
        assertEquals(1, runtime.synced.size)
        manager.setEnabled(false)
        assertEquals(1, runtime.resetCount)
        assertFalse(prefs.lxPluginEnabled.first())
    }

    @Test
    fun `卸载插件会注销沙盒并删除记录`() = runBlocking {
        val item = (manager.precheckAndImport("script") as LxPluginManager.ImportCheckResult.Success).item
        manager.remove(item.id)
        assertTrue(prefs.lxPlugins.first().isEmpty())
        assertEquals(listOf(item.id), runtime.deactivated)
    }

    @Test
    fun `停用单个插件后同步只带已启用项`() = runBlocking {
        val item = (manager.precheckAndImport("script") as LxPluginManager.ImportCheckResult.Success).item
        prefs.saveLxPluginEnabled(true)
        manager.toggle(item.id)
        assertEquals(listOf(emptyList<String>()), runtime.synced)
    }

    @Test
    fun `批量导入汇总成功与重名`() = runBlocking {
        val result = manager.importScripts(listOf(ArchiveScript("a.js", "s1"), ArchiveScript("b.js", "s2")))
        assertEquals(1, result.imported.size)
        assertEquals(1, result.duplicates.size)
        assertTrue(result.failures.isEmpty())
        assertEquals("导入 1 个，重名 1 个待确认", result.summary())
    }

    @Test
    fun `批量导入时失败项带上文件路径`() = runBlocking {
        runtime.verifyResult = Result.failure(Exception("语法错误"))
        val result = manager.importScripts(listOf(ArchiveScript("lib/bad.js", "x")))
        assertEquals(listOf("lib/bad.js：语法错误"), result.failures)
        assertEquals("导入 0 个，失败 1 个", result.summary())
    }
}

package com.lin0721.linmusic.desktop.plugin

import com.lin0721.linmusic.core.source.LxPluginItem
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GraalLxPluginRuntimeTest {

    private val requestedUrls = mutableListOf<String>()
    private val runtime = GraalLxPluginRuntime(fetcher = { url, _, _, _ ->
        requestedUrls += url
        "{\"url\":\"http://cdn.example/real.mp3\"}"
    })

    @After
    fun tearDown() {
        runtime.reset()
    }

    private fun script(handlerBody: String, sources: String = "{ kw: {}, wy: {} }") = """
        /**
         * @name 测试插件
         * @version 1.2.3
         * @author tester
         * @description 单元测试脚本
         */
        const { EVENT_NAMES, on, send } = globalThis.lx
        on(EVENT_NAMES.request, ({ source, action, info }) => {
            $handlerBody
        })
        send(EVENT_NAMES.inited, { sources: $sources })
    """.trimIndent()

    private fun item(raw: String) = LxPluginItem(id = "p1", name = "测试插件", rawScript = raw)

    private suspend fun resolve(sourceKey: String = "kw") = runtime.resolveMusicUrl(
        pluginId = "p1",
        source = sourceKey,
        songId = "123",
        songName = "晴天",
        singer = "周杰伦",
        albumName = "叶惠美",
        durationMs = 269_000,
        quality = "lossless"
    )

    @Test
    fun `校验脚本时提取头信息与支持平台`() = runBlocking {
        val info = runtime.loadAndVerifyScript(script("return 'x'")).getOrThrow()
        assertEquals("测试插件", info.name)
        assertEquals("1.2.3", info.version)
        assertEquals("tester", info.author)
        assertEquals(setOf("kw", "wy"), info.sources.toSet())
    }

    @Test
    fun `语法错误的脚本校验失败`() = runBlocking {
        val result = runtime.loadAndVerifyScript("this is not javascript {{{")
        assertTrue(result.isFailure)
    }

    @Test
    fun `同步返回直链并带上歌曲信息`() = runBlocking {
        val raw = script("return 'http://x/' + source + '/' + info.musicInfo.name + '/' + info.type")
        assertTrue(runtime.activatePlugin(item(raw)))
        assertEquals("http://x/kw/晴天/flac", resolve())
        assertEquals(setOf("kw", "wy"), runtime.getSupportedSources("p1").toSet())
    }

    @Test
    fun `通过 lx request 异步取得直链`() = runBlocking {
        val raw = script(
            """
            return new Promise((resolve, reject) => {
                globalThis.lx.request('http://api.example/song', { method: 'GET' }, (err, resp, body) => {
                    if (err) reject(err); else resolve(body.url);
                });
            })
            """.trimIndent()
        )
        assertTrue(runtime.activatePlugin(item(raw)))
        assertEquals("http://cdn.example/real.mp3", resolve())
        assertEquals(listOf("http://api.example/song"), requestedUrls)
    }

    @Test
    fun `脚本可以使用 setTimeout`() = runBlocking {
        val raw = script("return new Promise(r => setTimeout(() => r('http://late/ok'), 50))")
        assertTrue(runtime.activatePlugin(item(raw)))
        assertEquals("http://late/ok", resolve())
    }

    @Test
    fun `处理器抛错时解析返回空`() = runBlocking {
        val raw = script("throw new Error('no copyright')")
        assertTrue(runtime.activatePlugin(item(raw)))
        assertNull(resolve())
    }

    @Test
    fun `沙盒内无法访问宿主文件与类`() = runBlocking {
        val raw = script("return typeof Java === 'undefined' && typeof require === 'undefined' ? 'http://safe/ok' : 'leak'")
        assertTrue(runtime.activatePlugin(item(raw)))
        assertEquals("http://safe/ok", resolve())
    }

    @Test
    fun `同步插件列表会卸载未启用的插件`() = runBlocking {
        val raw = script("return 'http://x/ok'")
        runtime.syncActivePlugins(listOf(item(raw)))
        assertEquals("http://x/ok", resolve())

        runtime.syncActivePlugins(listOf(item(raw).copy(isEnabled = false)))
        assertNull(resolve())
        assertTrue(runtime.getSupportedSources("p1").isEmpty())
    }

    @Test
    fun `重置后所有插件失效`() = runBlocking {
        assertTrue(runtime.activatePlugin(item(script("return 'http://x/ok'"))))
        runtime.reset()
        assertNull(resolve())
        assertFalse(runtime.getSupportedSources("p1").isNotEmpty())
    }
}

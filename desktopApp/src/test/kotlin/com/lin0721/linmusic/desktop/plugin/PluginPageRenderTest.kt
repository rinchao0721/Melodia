package com.lin0721.linmusic.desktop.plugin

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.lin0721.linmusic.core.plugin.ActivePluginPage
import com.lin0721.linmusic.core.plugin.PlayerLayoutNode
import com.lin0721.linmusic.core.plugin.PlayerLayoutParseResult
import com.lin0721.linmusic.core.plugin.PlayerLayoutParser
import com.lin0721.linmusic.core.plugin.PluginHostApi
import com.lin0721.linmusic.core.plugin.PluginImageSource
import com.lin0721.linmusic.core.plugin.PluginPageHost
import com.lin0721.linmusic.core.plugin.PluginPageUi
import com.lin0721.linmusic.core.plugin.PluginPageView
import com.lin0721.linmusic.core.plugin.PluginPaths
import com.lin0721.linmusic.core.plugin.PluginRuntime
import com.lin0721.linmusic.core.plugin.PluginStageResult
import com.lin0721.linmusic.core.plugin.PluginStore
import com.lin0721.linmusic.core.plugin.PluginTrack
import com.lin0721.linmusic.desktop.ui.plugin.DesktopPluginPageUi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.jetbrains.skia.EncodedImageFormat
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.file.Files
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

// 离屏渲染页面：真实脚本 + 真实绑定，组件换成记录回调的探针，验证交互链路
class PluginPageRenderTest {

    private class Probe : PluginPageUi {
        val buttons = LinkedHashMap<String, () -> Unit>()
        var busyLabels = emptySet<String>()
        var toggle: ((Boolean) -> Unit)? = null
        var toggleChecked = false
        var input: Pair<String, (String) -> Unit>? = null
        var commit: (() -> Unit)? = null

        fun reset() {
            buttons.clear()
            busyLabels = emptySet()
        }

        @Composable override fun Image(source: PluginImageSource, node: PlayerLayoutNode, modifier: Modifier) = Unit
        @Composable override fun Tracks(tracks: List<PluginTrack>, onPlay: (Int) -> Unit, modifier: Modifier) = Unit
        @Composable override fun Button(text: String, busy: Boolean, onClick: () -> Unit, modifier: Modifier) {
            buttons[text] = onClick
            if (busy) busyLabels = busyLabels + text
        }
        @Composable override fun Toggle(label: String, checked: Boolean, onChange: (Boolean) -> Unit, modifier: Modifier) {
            toggle = onChange
            toggleChecked = checked
        }
        @Composable override fun Input(label: String, value: String, onChange: (String) -> Unit, onCommit: () -> Unit, modifier: Modifier) {
            input = value to onChange
            commit = onCommit
        }
        @Composable override fun Loading(modifier: Modifier) = Unit
    }

    private val page = """
        {"type":"column","spacing":12,"children":[
            {"type":"heading","text":"${'$'}state.title"},
            {"type":"text","text":"${'$'}state.count"},
            {"type":"button","text":"加一","action":"increment"},
            {"type":"toggle","text":"显示列表","bind":"${'$'}state.showList"},
            {"type":"input","text":"新条目","bind":"${'$'}state.draft","action":"add"},
            {"type":"column","visible":"${'$'}state.showList","spacing":6,"children":[
                {"type":"list","bind":"${'$'}state.items","spacing":6,"children":[
                    {"type":"row","spacing":8,"children":[
                        {"type":"text","text":"${'$'}item.name","weight":1},
                        {"type":"button","text":"删除","action":"remove"}
                    ]}
                ]}
            ]}
        ]}
    """.trimIndent()

    private val script = """
        var state = { title: '示例页面', count: '点击次数：0', showList: true, draft: '', items: [{ name: '第一条' }, { name: '第二条' }] };
        var clicks = 0;
        function sync() { return melodia.ui.setState('main', state); }
        melodia.register.action('open:main', function() { state.title = '已打开'; return sync(); });
        melodia.register.action('increment', function() { clicks++; state.count = '点击次数：' + clicks; return sync(); });
        melodia.register.action('add', function(args) { state.items = state.items.concat([{ name: args.value }]); return sync(); });
        melodia.register.action('remove', function(item) {
            state.items = state.items.filter(function(i) { return i.name !== item.name; });
            return sync();
        });
        sync();
    """.trimIndent()

    private lateinit var root: File
    private lateinit var store: PluginStore
    private lateinit var api: PluginHostApi
    private lateinit var runtime: PluginRuntime
    private lateinit var host: PluginPageHost
    private lateinit var active: ActivePluginPage

    @Before
    fun setUp() = runBlocking<Unit> {
        root = Files.createTempDirectory("plugin-page").toFile()
        store = PluginStore(root)
        api = PluginHostApi(store, mainDispatcher = Dispatchers.Unconfined)
        runtime = PluginRuntime(GraalScriptSandboxFactory(), api)
        host = PluginPageHost(runtime, api)
        val manifestText = """{"format":1,"id":"com.example.page","name":"页面","version":"1",
            "contributes":{"pages":[{"id":"main","title":"主页","ui":"ui/main.json"}]}}"""
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { zip ->
            listOf(PluginPaths.MANIFEST to manifestText, PluginPaths.SCRIPT to script, "ui/main.json" to page).forEach { (name, content) ->
                zip.putNextEntry(ZipEntry(name))
                zip.write(content.toByteArray())
                zip.closeEntry()
            }
        }
        val staged = (store.stage(ByteArrayInputStream(out.toByteArray())) as PluginStageResult.Success).staged
        store.install(staged, enabled = true)
        runtime.sync(store.list())
        val manifest = store.list().single().manifest!!
        val parsed = PlayerLayoutParser.loadPage(manifest, manifest.contributes.pages.single()) as PlayerLayoutParseResult.Success
        active = ActivePluginPage(manifest, manifest.contributes.pages.single(), parsed.root, null)
        waitFor { host.state("com.example.page", "main").isNotEmpty() }
        waitFor { "action:open:main" in runtime.status.value["com.example.page"]?.registrations.orEmpty() }
    }

    @After
    fun tearDown() {
        runBlocking { runtime.stop("com.example.page") }
        root.deleteRecursively()
    }

    private suspend fun waitFor(condition: () -> Boolean) {
        withTimeout(5_000) { while (!condition()) delay(20) }
    }

    private fun stateText(key: String): String =
        host.state("com.example.page", "main")[key].toString().trim('"')

    private inline fun withScene(noinline content: @Composable () -> Unit, block: (ImageComposeScene) -> Unit) {
        val scene = ImageComposeScene(720, 560, content = content)
        try {
            block(scene)
        } finally {
            scene.close()
        }
    }

    @Test
    fun `打开页面调用 open 动作，按钮、开关、输入框都走到脚本`() = runBlocking<Unit> {
        val probe = Probe()
        withScene({ PluginPageView(active, host, probe, Modifier.fillMaxSize().padding(24.dp)) }) { scene ->
            scene.render()
            waitFor { stateText("title") == "已打开" }

            scene.render()
            // 列表两项各有一个删除按钮，同名只记最后一个
            assertEquals(setOf("加一", "删除"), probe.buttons.keys)
            probe.buttons.getValue("加一")()
            waitFor { stateText("count") == "点击次数：1" }

            probe.reset()
            scene.render()
            probe.buttons.getValue("删除")()
            waitFor { !stateText("items").contains("第二条") }

            assertTrue(probe.toggleChecked)
            probe.toggle!!(false)
            scene.render()
            assertFalse(probe.toggleChecked)
            assertEquals("false", stateText("showList"))
            probe.reset()
            scene.render()
            assertFalse("列表隐藏后不再渲染删除按钮", "删除" in probe.buttons)

            probe.input!!.second("第三条")
            scene.render()
            assertEquals("第三条", probe.input!!.first)
            probe.commit!!()
            waitFor { stateText("items").contains("第三条") }
        }
    }

    // 用桌面端真实组件渲染，确认能出图；产物放 build 目录便于查看
    @Test
    fun `桌面组件能渲染完整页面`() = runBlocking<Unit> {
        withScene({
            Box(Modifier.fillMaxSize().background(Color(0xFF121212))) {
                PluginPageView(active, host, DesktopPluginPageUi, Modifier.padding(24.dp))
            }
        }) { scene ->
            scene.render()
            waitFor { stateText("title") == "已打开" }
            delay(100)
            val image = scene.render(nanoTime = 1_000_000_000L)
            val png = image.encodeToData(EncodedImageFormat.PNG)!!.bytes
            File("build/tmp/plugin-page.png").apply { parentFile.mkdirs() }.writeBytes(png)
            assertTrue(png.size > 1000)
        }
    }
}

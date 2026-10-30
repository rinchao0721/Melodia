package com.lin0721.linmusic.desktop.plugin

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import com.lin0721.linmusic.core.plugin.PluginParseResult
import com.lin0721.linmusic.core.plugin.PluginParser
import com.lin0721.linmusic.core.plugin.PluginPlatform
import com.lin0721.linmusic.core.plugin.PluginSettingsView
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.MutableStateFlow
import com.lin0721.linmusic.core.preferences.PreferencesStores
import com.lin0721.linmusic.core.preferences.SettingsPreferences
import com.lin0721.linmusic.core.plugin.cardKey
import com.lin0721.linmusic.core.plugin.PluginCardView
import com.lin0721.linmusic.core.plugin.PluginCardContext
import com.lin0721.linmusic.core.plugin.PlayerCardSource
import com.lin0721.linmusic.core.plugin.LocalPluginCards
import androidx.compose.runtime.CompositionLocalProvider
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
import com.lin0721.linmusic.core.plugin.PluginOption
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
        @Composable override fun Select(label: String, options: List<PluginOption>, selected: String, onSelect: (String) -> Unit, modifier: Modifier) = Unit
        @Composable override fun Slider(label: String, value: Float, min: Float, max: Float, step: Float, onChange: (Float) -> Unit, onCommit: () -> Unit, modifier: Modifier) = Unit
        @Composable override fun ProgressBar(fraction: Float, modifier: Modifier) = Unit
        @Composable override fun Badge(text: String, modifier: Modifier) = Unit
        @Composable override fun Tabs(labels: List<String>, selected: Int, onSelect: (Int) -> Unit, modifier: Modifier) = Unit
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
    private lateinit var card: ActivePluginPage

    @Before
    fun setUp() = runBlocking<Unit> {
        root = Files.createTempDirectory("plugin-page").toFile()
        store = PluginStore(root)
        api = PluginHostApi(store, mainDispatcher = Dispatchers.Unconfined)
        runtime = PluginRuntime(GraalScriptSandboxFactory(), api)
        host = PluginPageHost(runtime, api)
        val manifestText = """{"format":1,"id":"com.example.page","name":"页面","version":"1",
            "contributes":{"pages":[{"id":"main","title":"主页","ui":"ui/main.json"},
                {"id":"card","title":"卡片","placement":"nowPlayingCard",
                 "ui":{"type":"column","children":[{"type":"button","text":"卡片按钮","action":"increment"}]}}]}}"""
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
        fun pageOf(index: Int): ActivePluginPage {
            val page = manifest.contributes.pages[index]
            val parsed = PlayerLayoutParser.loadPage(manifest, page) as PlayerLayoutParseResult.Success
            return ActivePluginPage(manifest, page, parsed.root, null)
        }
        active = pageOf(0)
        card = pageOf(1)
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

    @Test
    fun `插件卡片按配置排序、保存后保留，并能触发脚本`() = runBlocking<Unit> {
        val settings = SettingsPreferences(PreferencesStores.get(File(root, "settings.preferences_pb")))
        val source = PlayerCardSource(settings, MutableStateFlow(listOf(active, card)))
        val entries = source.entries.first()
        assertEquals(card.cardKey, entries.last().key)
        assertFalse("普通页面不进卡片列表", entries.any { it.title == "主页" })

        source.save(listOf(entries.last()) + entries.dropLast(1))
        val saved = source.entries.first { it.first().key == card.cardKey }
        assertEquals(entries.size, saved.size)

        val probe = Probe()
        val context = PluginCardContext(source, host, probe)
        withScene({
            CompositionLocalProvider(LocalPluginCards provides context) { PluginCardView(card.cardKey) }
        }) { scene ->
            scene.render()
            probe.buttons.getValue("卡片按钮")()
            waitFor { stateText("count") == "点击次数：1" }
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

    @Test
    fun `新增组件与设置页可以离屏渲染`() = runBlocking {
        val manifest = (PluginParser.parse(
            """
            {"format":2,"id":"com.example.kit","name":"组件","version":"1.0.0",
             "contributes":{
               "pages":[{"id":"kit","title":"组件","ui":{"type":"column"}}],
               "settings":[
                 {"id":"on","type":"toggle","title":"启用","default":true},
                 {"id":"name","type":"text","title":"名称"},
                 {"id":"mode","type":"select","title":"模式","options":[{"value":"fast","label":"快速"},{"value":"safe","label":"安全"}]},
                 {"id":"level","type":"number","title":"等级","min":0,"max":10,"step":2,"default":6}
               ]}}
            """.trimIndent(),
            platform = PluginPlatform.DESKTOP
        ) as PluginParseResult.Success).manifest
        val tree = """
            {"type":"column","spacing":12,"children":[
                {"type":"badge","text":"实验性"},
                {"type":"select","text":"音质","bind":"${'$'}state.quality","options":[{"value":"hi","label":"高"},{"value":"lo","label":"低"}]},
                {"type":"slider","text":"音量","bind":"${'$'}state.volume","min":0,"max":100,"step":5},
                {"type":"progressBar","bind":"${'$'}state.progress"},
                {"type":"tabs","spacing":8,"children":[
                    {"type":"text","text":"第一页"},
                    {"type":"text","text":"第二页"}
                ]},
                {"type":"list","bind":"${'$'}state.items","columns":3,"spacing":8,"children":[
                    {"type":"text","text":"${'$'}item.name"}
                ]}
            ]}
        """.trimIndent()
        val root = (PlayerLayoutParser.parsePage(Json.parseToJsonElement(tree) as JsonObject) as PlayerLayoutParseResult.Success).root
        assertEquals(6, root.children.size)
        api.setUiField(manifest.id, "kit", "quality", JsonPrimitive("hi"))
        api.setUiField(manifest.id, "kit", "volume", JsonPrimitive(35))
        api.setUiField(manifest.id, "kit", "progress", JsonPrimitive(0.4))
        api.setUiField(manifest.id, "kit", "items", Json.parseToJsonElement("""[{"name":"一"},{"name":"二"},{"name":"三"},{"name":"四"}]"""))
        val kit = ActivePluginPage(manifest, manifest.contributes.pages.single(), root, null)
        withScene({
            Column(Modifier.fillMaxSize().background(Color(0xFF121212)).padding(24.dp)) {
                PluginPageView(kit, host, DesktopPluginPageUi, Modifier.fillMaxWidth())
                PluginSettingsView(manifest, host, DesktopPluginPageUi, Modifier.fillMaxWidth().padding(top = 24.dp))
            }
        }) { scene ->
            scene.render()
            delay(200)
            scene.render(nanoTime = 500_000_000L)
            val image = scene.render(nanoTime = 1_000_000_000L)
            val png = image.encodeToData(EncodedImageFormat.PNG)!!.bytes
            File("build/tmp/plugin-kit.png").apply { parentFile.mkdirs() }.writeBytes(png)
            assertTrue(png.size > 1000)
        }
    }
}

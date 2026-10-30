package com.lin0721.linmusic.core.plugin

import com.lin0721.linmusic.core.ui.skin.PaletteToken
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import org.junit.After
import org.junit.Assert.assertEquals
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

class PlayerLayoutTest {

    private fun json(text: String) = Json.parseToJsonElement(text) as JsonObject

    private fun parse(area: PlayerLayoutArea, text: String) = PlayerLayoutParser.parse(area, json(text))

    private val validBar = """
        {"type":"row","padding":[0,16],"spacing":12,"align":"center","height":96,"children":[
            {"type":"cover","shape":"circle","maxSize":64},
            {"type":"songInfo","weight":1,"lines":2},
            {"type":"column","weight":2,"align":"center","children":[
                {"type":"controls","buttons":["previous","playPause","next"],"size":"large"},
                {"type":"progress","showTime":false}
            ]},
            {"type":"actions","buttons":["queue","volume"]}
        ]}
    """.trimIndent()

    @Test
    fun `合法的播放栏布局解析出完整的树`() {
        val result = parse(PlayerLayoutArea.DESKTOP_PLAYER_BAR, validBar) as PlayerLayoutParseResult.Success
        val root = result.root
        assertEquals(LayoutNodeType.ROW, root.type)
        assertEquals(LayoutPadding(0f, 16f, 0f, 16f), root.padding)
        assertEquals(96f, root.height)
        assertEquals(LayoutShape.CIRCLE, root.children[0].shape)
        assertEquals(2, root.children[1].maxLines)
        val controls = root.children[2].children[0]
        assertEquals(listOf("previous", "playPause", "next"), controls.buttons)
        assertEquals(LayoutSize.LARGE, controls.size)
        assertEquals(false, root.children[2].children[1].showTime)
        assertTrue(result.warnings.isEmpty())
    }

    @Test
    fun `缺少必需组件时整个区域无效`() {
        val noProgress = """{"type":"row","children":[{"type":"controls"}]}"""
        assertTrue((parse(PlayerLayoutArea.DESKTOP_PLAYER_BAR, noProgress) as PlayerLayoutParseResult.Failure).reason.contains("进度条"))
        val noPlay = """{"type":"row","children":[{"type":"controls","buttons":["next"]},{"type":"progress"}]}"""
        assertTrue((parse(PlayerLayoutArea.DESKTOP_PLAYER_BAR, noPlay) as PlayerLayoutParseResult.Failure).reason.contains("播放/暂停"))
        assertTrue(parse(PlayerLayoutArea.ANDROID_MINI_PLAYER, """{"type":"row","children":[{"type":"cover"}]}""") is PlayerLayoutParseResult.Failure)
        assertTrue(parse(PlayerLayoutArea.DESKTOP_NOW_PLAYING, """{"type":"column","children":[{"type":"cover"}]}""") is PlayerLayoutParseResult.Success)
    }

    @Test
    fun `层级与节点数超限时无效`() {
        var deep = """{"type":"controls"}"""
        repeat(PlayerLayoutParser.MAX_DEPTH) { deep = """{"type":"column","children":[$deep]}""" }
        assertTrue((parse(PlayerLayoutArea.ANDROID_FULL_PLAYER, deep) as PlayerLayoutParseResult.Failure).reason.contains("层级"))
        val many = (1..PlayerLayoutParser.MAX_NODES).joinToString(",") { """{"type":"spacer"}""" }
        val wide = """{"type":"column","children":[{"type":"controls"},$many]}"""
        assertTrue((parse(PlayerLayoutArea.ANDROID_FULL_PLAYER, wide) as PlayerLayoutParseResult.Failure).reason.contains("节点"))
    }

    @Test
    fun `不支持的组件、按钮与参数忽略并提示，数值收到边界内`() {
        val result = parse(
            PlayerLayoutArea.DESKTOP_PLAYER_BAR,
            """{"type":"row","spacing":500,"padding":[1,2,3],"background":"pane","align":"middle","children":[
                {"type":"lyricsCard"},
                {"type":"hologram"},
                {"type":"controls","buttons":["playPause","rewind"],"size":"huge"},
                {"type":"actions","buttons":["share"]},
                {"type":"progress"},
                {"type":"text","text":"${"字".repeat(60)}","lines":9,"background":"neon"}
            ]}"""
        ) as PlayerLayoutParseResult.Success
        val root = result.root
        assertEquals(64f, root.spacing)
        assertEquals(LayoutPadding.Zero, root.padding)
        assertEquals(PaletteToken.PANE, root.background)
        assertEquals(LayoutAlign.START, root.align)
        assertEquals(listOf(LayoutNodeType.CONTROLS, LayoutNodeType.PROGRESS, LayoutNodeType.TEXT), root.children.map { it.type })
        assertEquals(listOf("playPause"), root.children[0].buttons)
        assertEquals(40, root.children[2].text.length)
        assertEquals(4, root.children[2].maxLines)
        assertNull(root.children[2].background)
        assertEquals(9, result.warnings.size)
    }

    @Test
    fun `未写按钮时使用本区域的全部按钮`() {
        val result = parse(
            PlayerLayoutArea.DESKTOP_PLAYER_BAR,
            """{"type":"row","children":[{"type":"controls"},{"type":"progress"},{"type":"actions"}]}"""
        ) as PlayerLayoutParseResult.Success
        assertEquals(CONTROL_BUTTONS, result.root.children[0].buttons)
        assertEquals(PlayerLayoutArea.DESKTOP_PLAYER_BAR.actionButtons, result.root.children[2].buttons)
    }

    // 只提供布局的插件不运行脚本，运行时不会创建沙盒
    private object NoSandbox : ScriptSandboxFactory {
        override suspend fun create(name: String, host: ScriptHost, prelude: String): ScriptSandbox =
            error("布局插件不应创建沙盒")
    }

    private lateinit var root: File
    private lateinit var store: PluginStore
    private lateinit var repository: PluginRepository

    @Before
    fun setUp() {
        root = Files.createTempDirectory("player-layout").toFile()
        store = PluginStore(root)
        val api = PluginHostApi(store)
        repository = PluginRepository(store, PluginRuntime(NoSandbox, api), api)
    }

    @After
    fun tearDown() {
        root.deleteRecursively()
    }

    private fun layoutPackage(id: String, layout: String): ByteArray {
        val manifest = """{"format":1,"id":"$id","name":"$id","version":"1",
            "contributes":{"playerLayouts":{"desktop.playerBar":"ui/bar.json"}}}"""
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { zip ->
            listOf(PluginPaths.MANIFEST to manifest, "ui/bar.json" to layout).forEach { (name, content) ->
                zip.putNextEntry(ZipEntry(name))
                zip.write(content.toByteArray())
                zip.closeEntry()
            }
        }
        return out.toByteArray()
    }

    private fun install(id: String, layout: String = validBar): StagedPlugin = runBlocking {
        val staged = (repository.stage(ByteArrayInputStream(layoutPackage(id, layout))) as PluginStageResult.Success).staged
        repository.install(staged)
        Thread.sleep(5)
        staged
    }

    private fun activeBar(): String? = repository.layouts.value.active[PlayerLayoutArea.DESKTOP_PLAYER_BAR]?.pluginId

    @Test
    fun `同一区域默认取最近启用的插件，可手动选择或改回内置布局`() = runBlocking {
        install("com.example.first")
        install("com.example.second")
        assertEquals("com.example.second", activeBar())
        assertEquals(2, repository.layouts.value.candidates[PlayerLayoutArea.DESKTOP_PLAYER_BAR]?.size)

        repository.setEnabled("com.example.first", false)
        repository.setEnabled("com.example.first", true)
        assertEquals("com.example.first", activeBar())

        repository.chooseLayout(PlayerLayoutArea.DESKTOP_PLAYER_BAR, "com.example.second")
        assertEquals("com.example.second", activeBar())
        repository.chooseLayout(PlayerLayoutArea.DESKTOP_PLAYER_BAR, LAYOUT_CHOICE_DEFAULT)
        assertNull(activeBar())
        repository.chooseLayout(PlayerLayoutArea.DESKTOP_PLAYER_BAR, null)
        assertEquals("com.example.first", activeBar())

        repository.setEnabled("com.example.first", false)
        repository.setEnabled("com.example.second", false)
        assertNull(activeBar())
    }

    @Test
    fun `选中的插件被删除后回到最近启用的插件`() = runBlocking {
        install("com.example.first")
        install("com.example.second")
        repository.chooseLayout(PlayerLayoutArea.DESKTOP_PLAYER_BAR, "com.example.first")
        repository.delete("com.example.first")
        assertEquals("com.example.second", activeBar())
    }

    @Test
    fun `无效布局导入时提示并且不生效`() {
        val staged = install("com.example.broken", """{"type":"row","children":[{"type":"cover"}]}""")
        assertTrue(staged.warnings.any { it.contains("布局无效") && it.contains("将使用默认布局") })
        assertNull(activeBar())
        assertTrue(repository.layouts.value.candidates[PlayerLayoutArea.DESKTOP_PLAYER_BAR].isNullOrEmpty())
    }

    @Test
    fun `内联布局与文件布局都能读取`() {
        val manifest = (PluginParser.parse(
            """{"format":1,"id":"com.example.inline","name":"内联","version":"1",
                "contributes":{"playerLayouts":{"desktop.nowPlaying":{"type":"column","children":[{"type":"cover"},{"type":"songInfo"}]}}}}"""
        ) as PluginParseResult.Success).manifest
        val result = PlayerLayoutParser.load(manifest, PlayerLayoutArea.DESKTOP_NOW_PLAYING) as PlayerLayoutParseResult.Success
        assertEquals(2, result.root.children.size)
        assertTrue(PlayerLayoutParser.load(manifest, PlayerLayoutArea.DESKTOP_PLAYER_BAR) is PlayerLayoutParseResult.Failure)
    }
}

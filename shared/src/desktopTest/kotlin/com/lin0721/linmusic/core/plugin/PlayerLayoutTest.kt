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

    @Test
    fun `迷你播放器只支持切歌与播放暂停`() {
        val result = parse(
            PlayerLayoutArea.ANDROID_MINI_PLAYER,
            """{"type":"row","children":[{"type":"controls","buttons":["shuffle","playPause","next"]},{"type":"lyricsCard"}]}"""
        ) as PlayerLayoutParseResult.Success
        assertEquals(listOf("playPause", "next"), result.root.children.single().buttons)
        assertEquals(2, result.warnings.size)
        val defaults = parse(PlayerLayoutArea.ANDROID_MINI_PLAYER, """{"type":"row","children":[{"type":"controls"}]}""") as PlayerLayoutParseResult.Success
        assertEquals(listOf("previous", "playPause", "next"), defaults.root.children.single().buttons)
    }

    private val minimalFull = """{"type":"column","children":[{"type":"controls","buttons":["playPause"]}]}"""

    @Test
    fun `外壳对象解析外框与变体，旧写法使用默认外框`() {
        val result = parse(
            PlayerLayoutArea.ANDROID_FULL_PLAYER,
            """{"frame":{"header":"none","cards":false},"layout":$minimalFull,
                "variants":[{"when":{"orientation":"landscape","minWidth":600},"layout":{"type":"row","children":[{"type":"controls"}]}}]}"""
        ) as PlayerLayoutParseResult.Success
        assertEquals(LayoutHeader.NONE, result.frame.header)
        assertEquals(false, result.frame.cards)
        assertTrue(result.warnings.isEmpty())
        val spec = result.spec
        assertEquals(LayoutNodeType.ROW, spec.select(700f, landscape = true).type)
        assertEquals(LayoutNodeType.COLUMN, spec.select(500f, landscape = true).type)
        assertEquals(LayoutNodeType.COLUMN, spec.select(700f, landscape = false).type)

        val legacy = parse(PlayerLayoutArea.ANDROID_FULL_PLAYER, minimalFull) as PlayerLayoutParseResult.Success
        assertEquals(PlayerLayoutFrame.Default, legacy.frame)
        assertTrue(legacy.variants.isEmpty())
    }

    @Test
    fun `外框字段按区域过滤，取值收到边界内`() {
        val wide = parse(
            PlayerLayoutArea.ANDROID_FULL_PLAYER_WIDE,
            """{"frame":{"split":0.9,"cards":false},"layout":$minimalFull}"""
        ) as PlayerLayoutParseResult.Success
        assertEquals(0.7f, wide.frame.split)
        assertTrue(wide.frame.cards)
        assertEquals(1, wide.warnings.size)

        val mini = parse(
            PlayerLayoutArea.ANDROID_MINI_PLAYER,
            """{"frame":{"background":"cardSurface","radius":[12,12,0,0],"shadow":false,"border":"no"},
                "layout":{"type":"row","children":[{"type":"controls"}]}}"""
        ) as PlayerLayoutParseResult.Success
        assertEquals(FrameBackground.Token(PaletteToken.CARD_SURFACE), mini.frame.background)
        assertEquals(LayoutCorners(12f, 12f, 0f, 0f), mini.frame.corners)
        assertEquals(false, mini.frame.shadow)
        assertTrue(mini.frame.border)
        assertEquals(1, mini.warnings.size)

        val bar = parse(PlayerLayoutArea.DESKTOP_PLAYER_BAR, """{"frame":{"header":"none"},"layout":$validBar}""") as PlayerLayoutParseResult.Success
        assertEquals(PlayerLayoutFrame.Default, bar.frame)
        assertEquals(1, bar.warnings.size)
    }

    @Test
    fun `无效的变体只跳过这一条`() {
        val result = parse(
            PlayerLayoutArea.ANDROID_FULL_PLAYER,
            """{"layout":$minimalFull,"variants":[
                {"when":{"minWidth":600},"layout":{"type":"column","children":[{"type":"cover"}]}},
                {"when":{"tilt":1},"layout":$minimalFull},
                {"layout":$minimalFull},
                {"when":{"maxWidth":400},"layout":$minimalFull},
                {"when":{"minWidth":1},"layout":$minimalFull}
            ]}"""
        ) as PlayerLayoutParseResult.Success
        assertEquals(1, result.variants.size)
        assertEquals(400f, result.variants.single().condition.maxWidth)
        // 第一条缺少播放键、第二条条件无效（两条提示）、第三条缺少条件、超出条数
        assertEquals(5, result.warnings.size)
    }

    @Test
    fun `外壳缺少有效 layout 时区域无效，预留字段提示暂不支持`() {
        assertTrue(parse(PlayerLayoutArea.ANDROID_FULL_PLAYER, """{"layout":"ui/a.json"}""") is PlayerLayoutParseResult.Failure)
        val result = parse(
            PlayerLayoutArea.ANDROID_FULL_PLAYER,
            """{"styles":{},"theme":1,"layout":{"type":"column","children":[{"type":"controls","animation":"spin","onTap":"next"}]}}"""
        ) as PlayerLayoutParseResult.Success
        assertEquals(4, result.warnings.size)
        assertTrue(result.warnings.any { it.contains("styles") })
    }

    @Test
    fun `分页器不能嵌套，页数与初始页收到边界内`() {
        val pages = (1..6).joinToString(",") { """{"type":"spacer"}""" }
        val result = parse(
            PlayerLayoutArea.ANDROID_FULL_PLAYER,
            """{"type":"column","children":[
                {"type":"pager","indicator":"dots","initialPage":9,"children":[$pages,{"type":"pager","children":[{"type":"spacer"}]}]},
                {"type":"pager","children":[]},
                {"type":"controls"}
            ]}"""
        ) as PlayerLayoutParseResult.Success
        val pager = result.root.children.first()
        assertEquals(LayoutNodeType.PAGER, pager.type)
        assertEquals(5, pager.children.size)
        assertEquals(PagerIndicator.DOTS, pager.indicator)
        assertEquals(4, pager.initialPage)
        assertEquals(listOf(LayoutNodeType.PAGER, LayoutNodeType.CONTROLS), result.root.children.map { it.type })
        assertEquals(3, result.warnings.size)

        val mini = parse(PlayerLayoutArea.ANDROID_MINI_PLAYER, """{"type":"row","children":[{"type":"pager","children":[{"type":"spacer"}]},{"type":"controls"}]}""")
        assertEquals(1, (mini as PlayerLayoutParseResult.Success).warnings.size)
    }

    @Test
    fun `尺寸、排列、位置与分角圆角`() {
        val result = parse(
            PlayerLayoutArea.ANDROID_FULL_PLAYER,
            """{"type":"box","children":[
                {"type":"row","width":"45%","height":"150%","maxWidth":2000,"minHeight":20,"arrange":"spaceEvenly","children":[{"type":"controls"}]},
                {"type":"cover","alignSelf":"bottomEnd","offset":[300,-8],"shape":"rounded","radius":[16,16,0,0]},
                {"type":"spacer","width":"wide","offset":[1],"radius":[1,2],"arrange":"around","alignSelf":"middle"}
            ]}"""
        ) as PlayerLayoutParseResult.Success
        val row = result.root.children[0]
        assertEquals(0.45f, row.widthFraction)
        assertEquals(1f, row.heightFraction)
        assertNull(row.width)
        assertEquals(1000f, row.maxWidth)
        assertEquals(20f, row.minHeight)
        assertEquals(LayoutArrange.SPACE_EVENLY, row.arrange)
        val cover = result.root.children[1]
        assertEquals(LayoutPosition.BOTTOM_END, cover.alignSelf)
        assertEquals(200f, cover.offsetX)
        assertEquals(-8f, cover.offsetY)
        assertEquals(LayoutCorners(16f, 16f, 0f, 0f), cover.corners)
        val spacer = result.root.children[2]
        assertNull(spacer.widthFraction)
        assertNull(spacer.corners)
        assertEquals(5, result.warnings.size)
    }

    @Test
    fun `时间、歌词视图与迷你条歌词按区域开放`() {
        val full = parse(
            PlayerLayoutArea.ANDROID_FULL_PLAYER,
            """{"type":"column","children":[{"type":"time","format":"both","style":"caption"},{"type":"time","format":"elapsed"},{"type":"lyricsView"},{"type":"controls"}]}"""
        ) as PlayerLayoutParseResult.Success
        assertEquals(TimeFormat.BOTH, full.root.children[0].timeFormat)
        assertEquals(LayoutTextStyle.CAPTION, full.root.children[0].textStyle)
        assertEquals(TimeFormat.POSITION, full.root.children[1].timeFormat)
        assertEquals(LayoutNodeType.LYRICS_VIEW, full.root.children[2].type)
        assertEquals(1, full.warnings.size)

        val wide = parse(PlayerLayoutArea.ANDROID_FULL_PLAYER_WIDE, """{"type":"column","children":[{"type":"lyricsView"},{"type":"time"},{"type":"controls"}]}""") as PlayerLayoutParseResult.Success
        assertEquals(listOf(LayoutNodeType.TIME, LayoutNodeType.CONTROLS), wide.root.children.map { it.type })
        val mini = parse(PlayerLayoutArea.ANDROID_MINI_PLAYER, """{"type":"row","children":[{"type":"miniLyric"},{"type":"time"},{"type":"controls"}]}""") as PlayerLayoutParseResult.Success
        assertTrue(mini.warnings.isEmpty())
        val bar = parse(PlayerLayoutArea.DESKTOP_PLAYER_BAR, """{"type":"row","children":[{"type":"time"},{"type":"controls"},{"type":"progress"}]}""") as PlayerLayoutParseResult.Success
        assertEquals(1, bar.warnings.size)
    }

    @Test
    fun `新增按钮需要显式写出，没写 buttons 时保持原来的按钮`() {
        val result = parse(
            PlayerLayoutArea.ANDROID_FULL_PLAYER,
            """{"type":"column","children":[
                {"type":"controls"},{"type":"controls","buttons":["playMode"]},
                {"type":"actions"},{"type":"actions","buttons":["sleepTimer","comments","lyrics"]}
            ]}"""
        ) as PlayerLayoutParseResult.Success
        val (defaults, playMode, actions, extra) = result.root.children
        assertEquals(CONTROL_BUTTONS, defaults.buttons)
        assertEquals(listOf(CONTROL_PLAY_MODE), playMode.buttons)
        assertEquals(listOf("device", "queue", "share", "quality", "more"), actions.buttons)
        assertEquals(listOf("sleepTimer", "comments", "lyrics"), extra.buttons)
        assertTrue(result.warnings.isEmpty())

        val desktop = parse(PlayerLayoutArea.DESKTOP_PLAYER_BAR, """{"type":"row","children":[{"type":"controls","buttons":["playPause","playMode"]},{"type":"progress"}]}""") as PlayerLayoutParseResult.Success
        assertEquals(listOf(CONTROL_PLAY_PAUSE), desktop.root.children[0].buttons)
        assertEquals(1, desktop.warnings.size)
    }

    @Test
    fun `播放器区域只认 player 绑定，页面不认`() {
        val result = parse(
            PlayerLayoutArea.ANDROID_FULL_PLAYER,
            """{"type":"column","children":[
                {"type":"text","text":"${'$'}player.quality","visible":"${'$'}player.quality"},
                {"type":"text","text":"${'$'}player.mood"},
                {"type":"text","text":"${'$'}state.title"},
                {"type":"text","text":"${'$'}9.99"},
                {"type":"lyricsView","visible":"${'$'}state.show"},
                {"type":"controls","visible":"${'$'}player.isPodcast"}
            ]}"""
        ) as PlayerLayoutParseResult.Success
        val children = result.root.children
        assertEquals(listOf("\$player.quality", "\$9.99", "", ""), children.map { it.text })
        assertEquals("\$player.quality", children[0].visible)
        assertNull(children[2].visible)
        assertEquals(3, result.warnings.size)

        val page = PlayerLayoutParser.parsePage(json("""{"type":"column","children":[{"type":"text","text":"a","visible":"${'$'}player.playing"}]}""")) as PlayerLayoutParseResult.Success
        assertNull(page.root.children.single().visible)
        assertEquals(1, page.warnings.size)
    }

    @Test
    fun `绑定状态替换文字并按 visible 隐藏节点`() {
        val state = PlayerBindingState(title = "歌", quality = "无损", playing = true, playMode = com.lin0721.linmusic.core.player.PlayMode.SINGLE_LOOP)
        val text = PlayerLayoutNode(LayoutNodeType.TEXT, text = "\$player.quality")
        assertEquals("无损", state.prepare(text)?.text)
        assertEquals("singleLoop", state.prepare(text.copy(text = "\$player.playMode"))?.text)
        assertEquals("固定", state.prepare(text.copy(text = "固定"))?.text)
        assertNull(state.prepare(PlayerLayoutNode(LayoutNodeType.LYRICS_VIEW, visible = "\$player.hasLyrics")))
        assertEquals(LayoutNodeType.COVER, state.prepare(PlayerLayoutNode(LayoutNodeType.COVER, visible = "\$player.playing"))?.type)
        assertNull(state.prepare(PlayerLayoutNode(LayoutNodeType.COVER, visible = "\$player.album")))
    }

    @Test
    fun `桌面播放栏可设背景，正在播放面板可用分页`() {
        val bar = parse(
            PlayerLayoutArea.DESKTOP_PLAYER_BAR,
            """{"frame":{"background":"pane"},"layout":{"type":"row","children":[{"type":"pager","children":[{"type":"cover"}]},{"type":"controls"},{"type":"progress"}]},
                "variants":[{"when":{"maxWidth":900},"layout":$validBar}]}"""
        ) as PlayerLayoutParseResult.Success
        assertEquals(FrameBackground.Token(PaletteToken.PANE), bar.frame.background)
        assertEquals(1, bar.variants.size)
        assertEquals(1, bar.warnings.size)
        val nowPlaying = parse(
            PlayerLayoutArea.DESKTOP_NOW_PLAYING,
            """{"type":"pager","indicator":"bar","children":[{"type":"cover"},{"type":"songInfo"}]}"""
        ) as PlayerLayoutParseResult.Success
        assertEquals(LayoutNodeType.PAGER, nowPlaying.root.type)
        assertTrue(nowPlaying.warnings.isEmpty())
    }

    @Test
    fun `变体里的组件也计入区域是否用到`() {
        val spec = (parse(
            PlayerLayoutArea.ANDROID_FULL_PLAYER,
            """{"layout":$minimalFull,"variants":[{"when":{"orientation":"portrait"},"layout":{"type":"column","children":[{"type":"lyricsCard"},{"type":"controls"}]}}]}"""
        ) as PlayerLayoutParseResult.Success).spec
        assertTrue(spec.contains(LayoutNodeType.LYRICS_CARD))
        assertTrue(!spec.root.contains(LayoutNodeType.LYRICS_CARD))
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

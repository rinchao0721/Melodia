package com.lin0721.linmusic.core.plugin

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PluginPageTest {

    private fun json(text: String) = Json.parseToJsonElement(text) as JsonObject

    private fun manifest(permissions: String = "", hosts: String = "", ui: String) = (PluginParser.parse(
        """{"format":1,"id":"com.example.page","name":"页面","version":"1","permissions":[$permissions],
            "network":{"hosts":[$hosts]},
            "contributes":{"pages":[{"id":"main","title":"主页","ui":$ui}]}}"""
    ) as PluginParseResult.Success).manifest

    @Test
    fun `页面描述解析出绑定字段与列表模板`() {
        val result = PlayerLayoutParser.parsePage(
            json(
                """{"type":"column","spacing":8,"children":[
                    {"type":"heading","text":"${'$'}state.title"},
                    {"type":"loading","visible":"${'$'}state.loading"},
                    {"type":"list","bind":"${'$'}state.items","children":[
                        {"type":"row","children":[{"type":"text","text":"${'$'}item.name"},{"type":"button","text":"删除","action":"remove"}]}
                    ]},
                    {"type":"toggle","text":"开启","bind":"${'$'}state.enabled","action":"toggled"},
                    {"type":"input","text":"昵称","bind":"${'$'}state.nick"},
                    {"type":"image","bind":"assets/a.png"},
                    {"type":"empty","text":"没有内容","visible":"${'$'}state.empty"}
                ]}"""
            )
        ) as PlayerLayoutParseResult.Success
        val root = result.root
        assertEquals(7, root.children.size)
        assertEquals("\$state.loading", root.children[1].visible)
        assertEquals(LayoutNodeType.ROW, root.children[2].children.single().type)
        assertEquals("toggled", root.children[3].action)
        assertTrue(result.warnings.isEmpty())
    }

    @Test
    fun `缺少绑定或动作的节点被忽略，播放器组件不能用于页面`() {
        val result = PlayerLayoutParser.parsePage(
            json(
                """{"type":"column","children":[
                    {"type":"button","text":"没有动作"},
                    {"type":"button","text":"非法动作","action":"a b"},
                    {"type":"list","bind":"items"},
                    {"type":"toggle","bind":"${'$'}item.on"},
                    {"type":"image"},
                    {"type":"controls"},
                    {"type":"text","text":"留下","visible":"state.show"}
                ]}"""
            )
        ) as PlayerLayoutParseResult.Success
        assertEquals(listOf(LayoutNodeType.TEXT), result.root.children.map { it.type })
        assertNull(result.root.children.single().visible)
        assertEquals(7, result.warnings.size)
    }

    @Test
    fun `没有 playback control 权限时去掉歌曲列表`() {
        val ui = """{"type":"column","children":[{"type":"trackList","bind":"${'$'}state.songs"},{"type":"text","text":"留下"}]}"""
        val denied = manifest(ui = ui)
        val stripped = PlayerLayoutParser.loadPage(denied, denied.contributes.pages.single()) as PlayerLayoutParseResult.Success
        assertEquals(listOf(LayoutNodeType.TEXT), stripped.root.children.map { it.type })
        assertTrue(stripped.warnings.any { it.contains("playback.control") })

        val allowed = manifest("\"playback.control\"", ui = ui)
        val kept = PlayerLayoutParser.loadPage(allowed, allowed.contributes.pages.single()) as PlayerLayoutParseResult.Success
        assertTrue(kept.root.contains(LayoutNodeType.TRACK_LIST))
    }

    @Test
    fun `绑定按路径取值，缺失时为空`() {
        val state = json("""{"user":{"name":"小林"},"count":3}""")
        val item = json("""{"title":"晴天"}""")
        assertEquals("小林", PluginBinding.text(PluginBinding.resolve("\$state.user.name", state, null)))
        assertEquals("晴天", PluginBinding.text(PluginBinding.resolve("\$item.title", state, item)))
        assertEquals("3", PluginBinding.text(PluginBinding.resolve("\$state.count", state, null)))
        assertEquals("", PluginBinding.text(PluginBinding.resolve("\$state.user.age", state, null)))
        assertEquals("", PluginBinding.text(PluginBinding.resolve("\$item.title", state, null)))
        assertFalse(PluginBinding.isBinding("state.x"))
        assertFalse(PluginBinding.isBinding("\$state.a b"))
    }

    @Test
    fun `真假判断`() {
        assertTrue(PluginBinding.truthy(JsonPrimitive(true)))
        assertTrue(PluginBinding.truthy(JsonPrimitive(1)))
        assertTrue(PluginBinding.truthy(JsonPrimitive("x")))
        assertTrue(PluginBinding.truthy(Json.parseToJsonElement("[1]")))
        assertFalse(PluginBinding.truthy(JsonPrimitive(false)))
        assertFalse(PluginBinding.truthy(JsonPrimitive(0)))
        assertFalse(PluginBinding.truthy(JsonPrimitive("")))
        assertFalse(PluginBinding.truthy(Json.parseToJsonElement("[]")))
        assertFalse(PluginBinding.truthy(JsonNull))
        assertFalse(PluginBinding.truthy(null))
    }

    @Test
    fun `图片只放行清单允许的域名与包内资源`() {
        val page = manifest("\"network\",\"playback.read\"", "\"img.example.com\"", """{"type":"column"}""")
        assertTrue(PluginBinding.image(page, "https://img.example.com/a.png") is PluginImageSource.Remote)
        assertNull(PluginBinding.image(page, "https://evil.com/a.png"))
        assertNull(PluginBinding.image(page, "file:///etc/passwd"))
        assertNull(PluginBinding.image(page, "assets/../plugin.json"))
        assertNull(PluginBinding.image(page, "assets/missing.png"))
    }

    @Test
    fun `歌曲数组只保留有效项`() {
        val tracks = PluginBinding.tracks(
            Json.parseToJsonElement("""[{"songId":1,"title":"a","artist":"b","cover":"c"},{"songId":0},{"title":"无 id"},"x"]""")
        )
        assertEquals(listOf(PluginTrack(1, "a", "b", "c")), tracks)
        assertTrue(PluginBinding.tracks(JsonPrimitive("x")).isEmpty())
    }
}

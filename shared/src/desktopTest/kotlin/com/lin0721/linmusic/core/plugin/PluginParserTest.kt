package com.lin0721.linmusic.core.plugin

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PluginParserTest {

    private fun parse(body: String): PluginParseResult =
        PluginParser.parse("""{"format":1,"id":"com.example.demo","name":"示例","version":"1.0.0"$body}""")

    private fun success(body: String) = parse(body) as PluginParseResult.Success

    private fun failure(body: String) = parse(body) as PluginParseResult.Failure

    private fun parseV2(body: String, platform: PluginPlatform = PluginPlatform.DESKTOP): PluginParseResult = PluginParser.parse(
        """{"format":2,"id":"com.example.demo","name":"示例","version":"1.0.0","contributes":{"playerLayouts":{"desktop.playerBar":"ui/bar.json"}}$body}""",
        platform = platform
    )

    private fun reasonV2(body: String, platform: PluginPlatform = PluginPlatform.DESKTOP): String =
        (parseV2(body, platform) as PluginParseResult.Failure).reason

    private fun contributesV2(body: String) = (PluginParser.parse(
        """{"format":2,"id":"com.example.demo","name":"示例","version":"1.0.0","contributes":{$body}}""",
        platform = PluginPlatform.DESKTOP
    ) as PluginParseResult.Success)

    @Test
    fun `解析命令与菜单`() {
        val result = contributesV2(
            """"commands":[{"id":"share","title":"分享"},{"id":"bad"}],
               "menus":[{"command":"share","location":"song"},{"command":"share","location":"nowhere"},{"command":"ghost","location":"song"}]"""
        )
        val contributes = result.manifest.contributes
        assertEquals(listOf(PluginCommandEntry("share", "分享")), contributes.commands)
        assertEquals(listOf(PluginMenuEntry("share", PluginMenuLocation.SONG)), contributes.menus)
        assertTrue(contributes.needsScript)
        assertEquals(3, result.warnings.size)
    }

    @Test
    fun `解析设置项并校验默认值`() {
        val result = contributesV2(
            """"settings":[
                {"id":"on","type":"toggle","title":"开关","default":true},
                {"id":"name","type":"text","title":"名称"},
                {"id":"mode","type":"select","title":"模式","options":[{"value":"a"},{"value":"b","label":"乙"}],"default":"z"},
                {"id":"level","type":"number","title":"等级","min":1,"max":5,"step":2,"default":99},
                {"id":"bad","type":"number","title":"无效","min":5,"max":1},
                {"id":"empty","type":"select","title":"空","options":[]},
                {"id":"odd","type":"color","title":"未知"}]"""
        )
        val settings = result.manifest.contributes.settings.associateBy { it.id }
        assertEquals(setOf("on", "name", "mode", "level"), settings.keys)
        assertEquals("true", settings.getValue("on").default.toString())
        assertEquals("\"a\"", settings.getValue("mode").default.toString())
        assertEquals("5.0", settings.getValue("level").default.toString())
        assertEquals(3, result.warnings.size)
    }

    @Test
    fun `format 1 忽略命令与设置`() {
        val contributes = success(
            ""","contributes":{"events":["songChanged"],"commands":[{"id":"a","title":"甲"}],"settings":[{"id":"b","type":"text","title":"乙"}]},"permissions":["playback.read"]"""
        ).manifest.contributes
        assertTrue(contributes.commands.isEmpty())
        assertTrue(contributes.settings.isEmpty())
    }

    @Test
    fun `format 2 默认是沙盒插件且不限平台`() {
        val manifest = (parseV2("") as PluginParseResult.Success).manifest
        assertEquals(PluginKind.SANDBOX, manifest.kind)
        assertTrue(manifest.platforms.isEmpty())
        assertEquals(null, manifest.apiVersion)
    }

    @Test
    fun `format 2 校验平台`() {
        val body = ""","platforms":["android"]"""
        assertEquals(setOf(PluginPlatform.ANDROID), (parseV2(body, PluginPlatform.ANDROID) as PluginParseResult.Success).manifest.platforms)
        assertEquals("插件不支持当前平台", reasonV2(body, PluginPlatform.DESKTOP))
    }

    @Test
    fun `未知平台被忽略并提示`() {
        val result = parseV2(""","platforms":["ios","desktop"]""") as PluginParseResult.Success
        assertEquals(setOf(PluginPlatform.DESKTOP), result.manifest.platforms)
        assertTrue(result.warnings.single().contains("ios"))
    }

    @Test
    fun `原生插件暂不能导入且 Android 明确拒绝`() {
        assertEquals("Android 不支持原生插件", reasonV2(""","kind":"native"""", PluginPlatform.ANDROID))
        assertEquals("当前版本还不支持原生插件", reasonV2(""","kind":"native"""", PluginPlatform.DESKTOP))
    }

    @Test
    fun `未知插件类型直接失败`() {
        assertTrue(reasonV2(""","kind":"wasm"""").contains("wasm"))
    }

    @Test
    fun `apiVersion 按主版本和次版本校验`() {
        assertEquals("1.0", (parseV2(""","apiVersion":"1.0"""") as PluginParseResult.Success).manifest.apiVersion)
        assertTrue(reasonV2(""","apiVersion":"2.0"""").contains("2.0"))
        assertTrue(reasonV2(""","apiVersion":"1.9"""").contains("1.9"))
        assertTrue(reasonV2(""","apiVersion":"latest"""").contains("apiVersion"))
    }

    @Test
    fun `format 1 忽略 format 2 的字段`() {
        val manifest = success(
            ""","kind":"native","platforms":["android"],"apiVersion":"9.0","contributes":{"playerLayouts":{"desktop.playerBar":"ui/bar.json"}}"""
        ).manifest
        assertEquals(PluginKind.SANDBOX, manifest.kind)
        assertTrue(manifest.platforms.isEmpty())
    }

    @Test
    fun `完整清单解析出权限、域名与各项能力`() {
        val result = success(
            ""","author":"tester","permissions":["network","playback.read","storage","audioSource","lyricsSource"],
            "network":{"hosts":["api.example.com","*.cdn.example.com"]},
            "contributes":{
                "playerLayouts":{"desktop.playerBar":"ui/bar.json","android.miniPlayer":{"type":"row"}},
                "pages":[{"id":"stats","title":"统计","ui":"ui/stats.json","placement":"nowPlayingCard"}],
                "audioSources":[{"id":"main","name":"示例音源"}],
                "lyricsSources":[{"id":"lrc"}],
                "events":["songChanged","playStateChanged"]}"""
        )
        val manifest = result.manifest
        assertEquals(5, manifest.permissions.size)
        assertEquals(listOf("api.example.com", "*.cdn.example.com"), manifest.networkHosts)
        assertEquals(PluginUiRef.File("ui/bar.json"), manifest.contributes.playerLayouts["desktop.playerBar"])
        assertTrue(manifest.contributes.playerLayouts["android.miniPlayer"] is PluginUiRef.Inline)
        assertEquals(PluginPagePlacement.NOW_PLAYING_CARD, manifest.contributes.pages.single().placement)
        assertEquals("lrc", manifest.contributes.lyricsSources.single().name)
        assertEquals(listOf("ui/bar.json", "ui/stats.json"), manifest.contributes.uiFiles)
        assertTrue(manifest.contributes.needsScript)
        assertTrue(result.warnings.isEmpty())
    }

    @Test
    fun `结构错误直接失败`() {
        assertTrue(PluginParser.parse("not json") is PluginParseResult.Failure)
        assertTrue(PluginParser.parse("""{"format":3,"id":"com.example.demo","name":"a","version":"1"}""") is PluginParseResult.Failure)
        assertTrue(PluginParser.parse("""{"format":1,"id":"demo","name":"a","version":"1"}""") is PluginParseResult.Failure)
        assertTrue(PluginParser.parse("""{"format":1,"id":"com.Example.demo","name":"a","version":"1"}""") is PluginParseResult.Failure)
        assertTrue(PluginParser.parse("""{"format":1,"id":"com.example.demo","version":"1"}""") is PluginParseResult.Failure)
    }

    @Test
    fun `没有任何能力的插件无效`() {
        assertTrue(failure(""","permissions":["storage"]""").reason.contains("没有提供任何功能"))
    }

    @Test
    fun `能力缺少对应权限时整个插件无效`() {
        assertTrue(failure(""","contributes":{"audioSources":[{"id":"main"}]}""").reason.contains("audioSource"))
        assertTrue(failure(""","contributes":{"lyricsSources":[{"id":"main"}]}""").reason.contains("lyricsSource"))
        assertTrue(failure(""","contributes":{"events":["songChanged"]}""").reason.contains("playback.read"))
    }

    @Test
    fun `申请网络权限必须列出域名`() {
        val reason = failure(""","permissions":["network"],"contributes":{"events":["songChanged"]}""").reason
        assertTrue(reason.contains("network.hosts"))
    }

    @Test
    fun `无效单项忽略并给出提示`() {
        val result = success(
            ""","permissions":["playback.read","fly"],
            "network":{"hosts":["evil.com"]},
            "contributes":{
                "playerLayouts":{"desktop.sidebar":"ui/a.json","desktop.playerBar":"../escape.json"},
                "pages":[{"id":"Bad Id","title":"x","ui":"ui/a.json"},{"id":"p","ui":"ui/a.json"},{"id":"q","title":"q","ui":"ui/q.json","placement":"top"}],
                "events":["songChanged","explode"]}"""
        )
        val manifest = result.manifest
        assertEquals(setOf(PluginPermission.PLAYBACK_READ), manifest.permissions)
        assertTrue(manifest.networkHosts.isEmpty())
        assertTrue(manifest.contributes.playerLayouts.isEmpty())
        assertEquals(listOf("q"), manifest.contributes.pages.map { it.id })
        assertEquals(PluginPagePlacement.SIDEBAR, manifest.contributes.pages.single().placement)
        assertEquals(setOf(PluginEvent.SONG_CHANGED), manifest.contributes.events)
        assertEquals(8, result.warnings.size)
    }

    @Test
    fun `通配域名只匹配子域名`() {
        val hosts = listOf("api.example.com", "*.cdn.example.com")
        assertTrue(PluginHostApi.isHostAllowed(hosts, "api.example.com"))
        assertTrue(PluginHostApi.isHostAllowed(hosts, "a.cdn.example.com"))
        assertTrue(!PluginHostApi.isHostAllowed(hosts, "cdn.example.com"))
        assertTrue(!PluginHostApi.isHostAllowed(hosts, "evilcdn.example.com"))
        assertTrue(!PluginHostApi.isHostAllowed(hosts, "api.example.com.evil.net"))
    }
}

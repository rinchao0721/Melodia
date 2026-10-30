package com.lin0721.linmusic.core.plugin

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PluginParserTest {

    private fun parse(body: String): PluginParseResult =
        PluginParser.parse("""{"format":1,"id":"com.example.demo","name":"示例","version":"1.0.0"$body}""")

    private fun success(body: String) = parse(body) as PluginParseResult.Success

    private fun failure(body: String) = parse(body) as PluginParseResult.Failure

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
        assertTrue(PluginParser.parse("""{"format":2,"id":"com.example.demo","name":"a","version":"1"}""") is PluginParseResult.Failure)
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

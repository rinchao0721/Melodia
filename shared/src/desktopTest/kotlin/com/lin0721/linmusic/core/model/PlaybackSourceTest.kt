package com.lin0721.linmusic.core.model

import com.lin0721.linmusic.core.source.MusicPlatform
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

// 播放来源文案与来源/音质拼接单测
class PlaybackSourceTest {

    private fun source(kind: PlaybackSourceKind, platform: MusicPlatform? = null, name: String? = null) =
        PlaybackSourceInfo("1", kind, platform, name)

    @Test
    fun `官方来源显示平台名`() {
        assertEquals("网易", playbackSourceLabel(source(PlaybackSourceKind.OFFICIAL, MusicPlatform.NETEASE)))
        assertEquals("酷狗", playbackSourceLabel(source(PlaybackSourceKind.OFFICIAL, MusicPlatform.KUGOU)))
        assertEquals("QQ", playbackSourceLabel(source(PlaybackSourceKind.OFFICIAL, MusicPlatform.QQ)))
    }

    @Test
    fun `社区来源带插件短名并保守截断`() {
        assertEquals("社区 · 星澜", playbackSourceLabel(source(PlaybackSourceKind.COMMUNITY, name = "星澜")))
        assertEquals("社区 · 一二三四五六", playbackSourceLabel(source(PlaybackSourceKind.COMMUNITY, name = "一二三四五六七八")))
        assertEquals("社区", playbackSourceLabel(source(PlaybackSourceKind.COMMUNITY, name = "  ")))
        assertEquals("社区", playbackSourceLabel(source(PlaybackSourceKind.COMMUNITY)))
    }

    @Test
    fun `本地与缓存显示固定词`() {
        assertEquals("本地", playbackSourceLabel(source(PlaybackSourceKind.LOCAL)))
        assertEquals("缓存", playbackSourceLabel(source(PlaybackSourceKind.CACHE)))
    }

    @Test
    fun `两个开关组合决定播放页文案`() {
        assertEquals("网易 · 极高", composePlaybackInfoLabel("网易", "极高", showSource = true, showQuality = true))
        assertEquals("极高", composePlaybackInfoLabel("网易", "极高", showSource = false, showQuality = true))
        assertEquals("网易", composePlaybackInfoLabel("网易", "极高", showSource = true, showQuality = false))
        assertNull(composePlaybackInfoLabel("网易", "极高", showSource = false, showQuality = false))
    }

    @Test
    fun `某一部分缺失时只显示另一部分`() {
        assertEquals("网易", composePlaybackInfoLabel("网易", null, showSource = true, showQuality = true))
        assertEquals("极高", composePlaybackInfoLabel(null, "极高", showSource = true, showQuality = true))
        assertNull(composePlaybackInfoLabel(null, null, showSource = true, showQuality = true))
        assertNull(composePlaybackInfoLabel("网易", "", showSource = false, showQuality = true))
    }
}

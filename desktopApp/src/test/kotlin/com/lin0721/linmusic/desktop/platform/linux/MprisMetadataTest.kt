package com.lin0721.linmusic.desktop.platform.linux

import com.lin0721.linmusic.desktop.platform.native.linux.mpris.MprisLoopStatus
import com.lin0721.linmusic.desktop.platform.native.linux.mpris.MprisMetadata
import com.lin0721.linmusic.desktop.platform.native.linux.mpris.MprisPlaybackStatus
import com.lin0721.linmusic.desktop.platform.native.linux.mpris.MprisPlayer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.freedesktop.dbus.types.Variant

// MPRIS 元数据与常量契约的单元测试。
//
// 覆盖构建期踩过的坑：dbus-java 对 Variant 的包装要求严格，
// 用错集合类型（MapBuilder / SingletonList）会在运行时才抛异常，
// 因此这里显式校验签名与容器类型。
class MprisMetadataTest {

    @Test
    fun `元数据包含 MPRIS 规范要求的必需字段`() {
        val metadata = MprisMetadata.build(
            songId = 42L,
            title = "标题",
            artist = "歌手",
            album = "专辑",
            coverUrl = "https://example.com/c.jpg",
            durationMs = 210_000L,
        )

        assertTrue(metadata.containsKey(MprisMetadata.KEY_TRACK_ID))
        assertTrue(metadata.containsKey(MprisMetadata.KEY_TITLE))
        assertTrue(metadata.containsKey(MprisMetadata.KEY_ARTIST))
        assertEquals("专辑", metadata.getValue(MprisMetadata.KEY_ALBUM).value)
        assertEquals("标题", metadata.getValue(MprisMetadata.KEY_TITLE).value)
    }

    @Test
    fun `trackid 为对象路径且带 o 签名`() {
        val metadata = MprisMetadata.build(42L, "t", "a", "", null, 0L)
        val trackId = metadata.getValue(MprisMetadata.KEY_TRACK_ID)

        assertEquals("o", trackId.sig)
        assertTrue((trackId.value as String).startsWith("/"))
        assertEquals(MprisMetadata.trackPath(42L), trackId.value)
    }

    @Test
    fun `artist 为字符串数组且带 as 签名`() {
        val metadata = MprisMetadata.build(1L, "t", "某歌手", "", null, 0L)
        val artist = metadata.getValue(MprisMetadata.KEY_ARTIST)

        // MPRIS 规定 xesam:artist 是数组，不能用单个字符串
        assertEquals("as", artist.sig)
        val values = artist.value as Array<*>
        assertEquals(1, values.size)
        assertEquals("某歌手", values[0])
    }

    @Test
    fun `时长为微秒且带 int64 签名`() {
        val metadata = MprisMetadata.build(1L, "t", "a", "", null, 210_000L)
        val length = metadata.getValue(MprisMetadata.KEY_LENGTH)

        assertEquals("x", length.sig)
        assertEquals(210_000_000L, length.value)
    }

    @Test
    fun `空专辑与空封面不写入元数据`() {
        val metadata = MprisMetadata.build(1L, "t", "a", "", null, 0L)

        assertTrue(!metadata.containsKey(MprisMetadata.KEY_ALBUM))
        assertTrue(!metadata.containsKey(MprisMetadata.KEY_ART_URL))
        assertTrue(!metadata.containsKey(MprisMetadata.KEY_LENGTH))
    }

    @Test
    fun `容器为标准 Map 而非 Kotlin 构建器`() {
        val metadata = MprisMetadata.build(1L, "t", "a", "al", "u", 1L)

        // dbus-java 无法包装 kotlin 的 MapBuilder，必须是标准实现
        val implClass = metadata.javaClass.name
        assertTrue("实际实现：$implClass", !implClass.contains("builders"))
    }

    @Test
    fun `空元数据携带 NoTrack 占位`() {
        val empty = MprisMetadata.empty()
        val trackId = empty.getValue(MprisMetadata.KEY_TRACK_ID)

        assertEquals(MprisMetadata.NO_TRACK_PATH, trackId.value)
        assertEquals("o", trackId.sig)
    }

    @Test
    fun `常量与 MPRIS 规范取值一致`() {
        assertEquals("org.mpris.MediaPlayer2.melodia", MprisPlayer.BUS_NAME)
        assertEquals("/org/mpris/MediaPlayer2", MprisPlayer.OBJECT_PATH)
        assertEquals("Playing", MprisPlaybackStatus.PLAYING)
        assertEquals("Paused", MprisPlaybackStatus.PAUSED)
        assertEquals("Stopped", MprisPlaybackStatus.STOPPED)
        assertEquals("Track", MprisLoopStatus.TRACK)
        assertEquals("Playlist", MprisLoopStatus.PLAYLIST)
    }

    @Test
    fun `PropertiesChanged 信号可构造且携带正确接口名`() {
        val changed = linkedMapOf<String, Variant<*>>(
            MprisPlayer.PROP_PLAYBACK_STATUS to Variant(MprisPlaybackStatus.PLAYING, "s"),
        )
        val signal = MprisPlayer.PropertiesChanged(MprisPlayer.OBJECT_PATH, changed)

        assertEquals(MprisPlayer.OBJECT_PATH, signal.getPath())
        assertEquals("org.freedesktop.DBus.Properties", signal.getInterface())
        assertEquals("PropertiesChanged", signal.getName())
        assertNotNull(signal)
    }
}

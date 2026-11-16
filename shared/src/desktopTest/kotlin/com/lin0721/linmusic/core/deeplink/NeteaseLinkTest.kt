package com.lin0721.linmusic.core.deeplink

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class NeteaseLinkTest {

    @Test
    fun `parses standard and hash and mobile song urls`() {
        assertEquals(SongLinkTarget.Song(2023954033L), NeteaseLink.target("https://music.163.com/song?id=2023954033"))
        assertEquals(SongLinkTarget.Song(2023954033L), NeteaseLink.target("https://music.163.com/#/song?id=2023954033"))
        assertEquals(SongLinkTarget.Song(2023954033L), NeteaseLink.target("https://music.163.com/#/m/song?id=2023954033"))
        assertEquals(SongLinkTarget.Song(2023954033L), NeteaseLink.target("https://y.music.163.com/m/song?id=2023954033"))
        assertEquals(
            SongLinkTarget.Song(411314657L),
            NeteaseLink.target("https://y.music.163.com/m/song?app_version=9.0&id=411314657&uct2=abc")
        )
    }

    @Test
    fun `parses restful path urls`() {
        assertEquals(SongLinkTarget.Song(12345L), NeteaseLink.target("https://music.163.com/song/12345"))
        assertEquals(SongLinkTarget.Song(12345L), NeteaseLink.target("https://music.163.com/m/song/12345?from=share"))
        assertEquals(SongLinkTarget.Playlist(67890L, isAlbum = false), NeteaseLink.target("https://music.163.com/playlist/67890"))
        assertEquals(SongLinkTarget.Playlist(24680L, isAlbum = true), NeteaseLink.target("https://music.163.com/#/album/24680"))
        assertEquals(SongLinkTarget.Artist(13579L), NeteaseLink.target("https://y.music.163.com/m/artist/13579"))
    }

    @Test
    fun `does not cross query boundary when matching path type`() {
        assertEquals(
            SongLinkTarget.Playlist(123L, isAlbum = false),
            NeteaseLink.target("https://music.163.com/playlist?from=song&id=123")
        )
        assertEquals(
            SongLinkTarget.Playlist(456L, isAlbum = true),
            NeteaseLink.target("https://music.163.com/album?from=song&id=456")
        )
        assertEquals(
            SongLinkTarget.Artist(789L),
            NeteaseLink.target("https://music.163.com/artist?tab=album&id=789")
        )
    }

    @Test
    fun `parses orpheus scheme and share copy`() {
        assertEquals(SongLinkTarget.Song(411314657L), NeteaseLink.target("orpheus://song/411314657"))
        assertEquals(SongLinkTarget.Playlist(9988L, isAlbum = false), NeteaseLink.target("orpheus://playlist/9988"))
        assertEquals(SongLinkTarget.Playlist(7766L, isAlbum = true), NeteaseLink.target("orpheus://album/7766"))
        assertEquals(SongLinkTarget.Artist(5544L), NeteaseLink.target("orpheus://artist/5544"))

        val shareCopy = "分享陈奕迅的单曲《十年》: https://music.163.com/song?id=65528&userid=12345 (来自@网易云音乐)"
        assertEquals(SongLinkTarget.Song(65528L), NeteaseLink.target(shareCopy))
    }

    @Test
    fun `extracts short links and rejects invalid schemes`() {
        assertEquals("https://163cn.tv/aBc123", NeteaseLink.shortLink("打开网易云音乐：https://163cn.tv/aBc123。"))
        assertEquals("http://163cn.tv/XYZ", NeteaseLink.shortLink("http://163cn.tv/XYZ"))

        assertNull(NeteaseLink.target("javascript:music.163.com/song?id=123"))
        assertNull(NeteaseLink.target("ftp://music.163.com/song?id=123"))
        assertNull(NeteaseLink.target("https://music.163.com/playlist?userid=999"))
        assertNull(NeteaseLink.target("普通搜索词"))
    }
}

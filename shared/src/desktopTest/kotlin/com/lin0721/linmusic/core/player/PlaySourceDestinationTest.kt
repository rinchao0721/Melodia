package com.lin0721.linmusic.core.player

import com.lin0721.linmusic.core.platform.AlbumId
import com.lin0721.linmusic.core.platform.PlaylistId
import com.lin0721.linmusic.core.source.MusicPlatform
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PlaySourceDestinationTest {

    @Test
    fun `resolves netease playlist album rank and radio`() {
        assertEquals(
            QueueSourceDestination.Playlist(10L, isAlbum = false, label = "歌单A"),
            resolveQueueSourceDestination(
                PlaySource(PlaySource.Kind.PLAYLIST, id = 10L, name = "歌单A"),
                playContext = "歌单A",
                currentItem = null
            )
        )
        assertEquals(
            QueueSourceDestination.Playlist(20L, isAlbum = true, label = "专辑B"),
            resolveQueueSourceDestination(
                PlaySource(PlaySource.Kind.ALBUM, id = 20L, name = "专辑B"),
                playContext = "专辑B",
                currentItem = null
            )
        )
        assertEquals(
            QueueSourceDestination.Playlist(30L, isAlbum = false, label = "飙升榜"),
            resolveQueueSourceDestination(
                PlaySource(PlaySource.Kind.RANK, id = 30L, name = "飙升榜"),
                playContext = "飙升榜",
                currentItem = null
            )
        )
        assertEquals(
            QueueSourceDestination.Radio(40L, label = "电台C"),
            resolveQueueSourceDestination(
                PlaySource(PlaySource.Kind.RADIO, id = 40L, name = "电台C"),
                playContext = PlaybackController.CONTEXT_PODCAST,
                currentItem = null
            )
        )
    }

    @Test
    fun `resolves non-netease playlist and album via entityId`() {
        val kugouPlaylist = PlaylistId.kugou("k123").canonical
        assertEquals(
            QueueSourceDestination.PlaylistEntity(kugouPlaylist, isAlbum = false, label = "酷狗歌单"),
            resolveQueueSourceDestination(
                PlaySource(PlaySource.Kind.PLAYLIST, id = 0L, name = "酷狗歌单", platform = MusicPlatform.KUGOU, rawId = "k123"),
                playContext = "酷狗歌单",
                currentItem = null
            )
        )

        val qqAlbum = AlbumId.of(MusicPlatform.QQ, "q999").canonical
        assertEquals(
            QueueSourceDestination.PlaylistEntity(qqAlbum, isAlbum = true, label = "QQ专辑"),
            resolveQueueSourceDestination(
                PlaySource(PlaySource.Kind.ALBUM, id = 0L, name = "QQ专辑", platform = MusicPlatform.QQ, rawId = "q999"),
                playContext = "QQ专辑",
                currentItem = null
            )
        )
    }

    @Test
    fun `falls back to currentItem radioId when playSource is null`() {
        val item = QueueItem(songId = 1L, title = "第一期", artist = "夜航西飞 · 主播", coverUrl = "", radioId = 555L)
        assertEquals(
            QueueSourceDestination.Radio(555L, label = "夜航西飞"),
            resolveQueueSourceDestination(null, PlaybackController.CONTEXT_PODCAST, item)
        )

        assertNull(resolveQueueSourceDestination(null, "每日推荐", QueueItem(songId = 2L, title = "歌", artist = "歌手", coverUrl = "")))
    }
}

package com.lin0721.linmusic.core.kugou

import com.lin0721.linmusic.core.platform.KugouTrackMeta
import com.lin0721.linmusic.core.platform.TrackId
import com.lin0721.linmusic.core.player.KugouQueueArtist
import com.lin0721.linmusic.core.player.QueueItem
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class KugouTrackLinkResolverTest {

    private fun item(
        artist: String = "周杰伦",
        albumId: String? = "200",
        singerId: String? = null,
        artists: List<KugouQueueArtist> = emptyList()
    ) = QueueItem(
        songId = -1L,
        title = "发如雪",
        artist = artist,
        coverUrl = "",
        trackId = TrackId.kugou("abc"),
        kugouMeta = KugouTrackMeta(hash = "abc", albumId = albumId),
        kugouSingerId = singerId,
        kugouArtists = artists
    )

    @Test
    fun `队列项带歌手 id 时不再请求网络`() = runTest {
        val resolver = KugouTrackLinkResolver(
            albumIdOfHash = { error("不应请求") },
            authorsOfAlbum = { error("不应请求") }
        )
        val links = resolver.resolve(
            item(artists = listOf(KugouQueueArtist("11", "周杰伦"), KugouQueueArtist("22", "方文山")))
        )
        assertEquals("200", links.albumId)
        assertEquals(listOf(KugouArtistLink("11", "周杰伦"), KugouArtistLink("22", "方文山")), links.artists)
    }

    @Test
    fun `只有 kugouSingerId 时用它作为歌手`() = runTest {
        val resolver = KugouTrackLinkResolver({ null }, { error("不应请求") })
        val links = resolver.resolve(item(artist = "周杰伦 / 方文山", singerId = "11"))
        assertEquals(listOf(KugouArtistLink("11", "周杰伦")), links.artists)
    }

    @Test
    fun `歌手 id 为空时用专辑作者补查并按歌手名对上`() = runTest {
        val resolver = KugouTrackLinkResolver(
            albumIdOfHash = { error("已有专辑 id 不应请求") },
            authorsOfAlbum = { albumId ->
                assertEquals("200", albumId)
                listOf(KugouArtistLink("11", "周杰伦"), KugouArtistLink("33", "别人"))
            }
        )
        val links = resolver.resolve(item())
        assertEquals("200", links.albumId)
        assertEquals(listOf(KugouArtistLink("11", "周杰伦")), links.artists)
    }

    @Test
    fun `专辑 id 缺失时先按 hash 补查专辑再补查歌手`() = runTest {
        val resolver = KugouTrackLinkResolver(
            albumIdOfHash = { hash -> if (hash == "abc") "300" else null },
            authorsOfAlbum = { albumId -> if (albumId == "300") listOf(KugouArtistLink("11", "JJ")) else emptyList() }
        )
        val links = resolver.resolve(item(albumId = null))
        assertEquals("300", links.albumId)
        assertEquals(listOf(KugouArtistLink("11", "JJ")), links.artists)
    }

    @Test
    fun `专辑 id 为 0 视为缺失`() = runTest {
        val resolver = KugouTrackLinkResolver({ null }, { emptyList() })
        val links = resolver.resolve(item(albumId = "0"))
        assertNull(links.albumId)
        assertEquals(emptyList<KugouArtistLink>(), links.artists)
    }

    @Test
    fun `名字对不上且作者不唯一时不猜歌手`() = runTest {
        val resolver = KugouTrackLinkResolver(
            { null },
            { listOf(KugouArtistLink("1", "甲"), KugouArtistLink("2", "乙")) }
        )
        assertEquals(emptyList<KugouArtistLink>(), resolver.resolve(item(artist = "丙")).artists)
    }

    @Test
    fun `补查抛异常时返回已知部分`() = runTest {
        val resolver = KugouTrackLinkResolver(
            albumIdOfHash = { throw IllegalStateException("网络失败") },
            authorsOfAlbum = { throw IllegalStateException("网络失败") }
        )
        val links = resolver.resolve(item(albumId = null))
        assertNull(links.albumId)
        assertEquals(emptyList<KugouArtistLink>(), links.artists)
    }
}

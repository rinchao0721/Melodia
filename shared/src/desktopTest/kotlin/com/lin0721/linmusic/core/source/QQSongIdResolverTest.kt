package com.lin0721.linmusic.core.source

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class QQSongIdResolverTest {

    private class FakeQQSearch(private val results: List<ExternalTrack>) : PlatformSearchProvider {
        override val platform = MusicPlatform.QQ
        var calls = 0
        override suspend fun search(keyword: String, offset: Int, limit: Int): List<ExternalTrack> {
            calls++
            return results
        }
    }

    private fun qq(id: Long, name: String, artist: String, durationMs: Long) = ExternalTrack(
        id = "mid$id", name = name, artists = artist, artistList = listOf(artist),
        durationMs = durationMs, platform = MusicPlatform.QQ, qqSongId = id
    )

    @Test
    fun qqTrackUsesOwnIdWithoutSearching() = runBlocking {
        val search = FakeQQSearch(emptyList())
        val resolver = QQSongIdResolver(search)

        assertEquals(97773L, resolver.resolve(qq(97773, "晴天", "周杰伦", 269_000)))
        assertEquals(0, search.calls)
    }

    @Test
    fun otherPlatformMatchesOnQQAndCachesResult() = runBlocking {
        val search = FakeQQSearch(listOf(qq(1, "晴天 (Live)", "某人", 300_000), qq(97773, "晴天", "周杰伦", 269_000)))
        val resolver = QQSongIdResolver(search)
        val kugou = ExternalTrack(
            id = "k1", name = "晴天", artists = "周杰伦", artistList = listOf("周杰伦"),
            durationMs = 269_500, platform = MusicPlatform.KUGOU
        )

        assertEquals(97773L, resolver.resolve(kugou))
        assertEquals(97773L, resolver.resolve(kugou))
        assertEquals(1, search.calls)
    }

    @Test
    fun emptySearchIsNotCached() = runBlocking {
        val search = FakeQQSearch(emptyList())
        val resolver = QQSongIdResolver(search)
        val kuwo = ExternalTrack(id = "w1", name = "歌", artists = "人", platform = MusicPlatform.KUWO)

        assertNull(resolver.resolve(kuwo))
        assertNull(resolver.resolve(kuwo))
        assertEquals(2, search.calls)
    }
}

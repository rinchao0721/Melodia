package com.lin0721.linmusic.core.player.data

import com.lin0721.linmusic.core.model.Artist
import com.lin0721.linmusic.core.model.Track
import java.lang.reflect.Proxy
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.*
import org.junit.Test

class QqLyricsSourceTest {
    private val target = Track(id = 1, name = "测试", ar = listOf(Artist(name = "歌手")), dt = 100000)
    private val emptySearch = """{"search":{"code":0,"data":{"body":{"item_song":[]}}}}"""

    private class Repo(val detail: Result<Track>) {
        var detailCalls = 0
        val proxy: PlaybackRepository = Proxy.newProxyInstance(
            PlaybackRepository::class.java.classLoader, arrayOf(PlaybackRepository::class.java)
        ) { _, method, _ ->
            check(method.name == "getSongDetail") { "Unexpected repository call: ${method.name}" }
            detailCalls++
            flowOf(detail)
        } as PlaybackRepository
    }

    @Test fun songDetailIsFetchedOncePerSong() = runTest {
        MockWebServer().use { server ->
            repeat(2) { server.enqueue(MockResponse().setBody(emptySearch)) }
            val repo = Repo(Result.success(target))
            val source = QqLyricsSource(repo.proxy, QqLyricsClient(OkHttpClient(), server.url("/").toString()))
            assertTrue(source.fetch(1).isEmpty())
            assertTrue(source.fetch(1).isEmpty())
            assertEquals(1, repo.detailCalls)
        }
    }

    @Test fun failedSongDetailReturnsEmptyAndIsNotCached() = runTest {
        MockWebServer().use { server ->
            val repo = Repo(Result.failure(IllegalStateException("offline")))
            val source = QqLyricsSource(repo.proxy, QqLyricsClient(OkHttpClient(), server.url("/").toString()))
            assertTrue(source.fetch(1).isEmpty())
            assertTrue(source.fetch(1).isEmpty())
            assertEquals(2, repo.detailCalls)
            assertEquals(0, server.requestCount)
        }
    }
}

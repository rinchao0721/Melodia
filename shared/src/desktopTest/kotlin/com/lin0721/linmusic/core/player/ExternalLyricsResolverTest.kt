package com.lin0721.linmusic.core.player

import com.lin0721.linmusic.core.player.data.PlaybackRepository
import com.lin0721.linmusic.core.player.domain.LyricLine
import com.lin0721.linmusic.core.player.domain.WordInfo
import com.lin0721.linmusic.core.source.ExternalTrack
import com.lin0721.linmusic.core.source.MusicPlatform
import java.lang.reflect.Proxy
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class ExternalLyricsResolverTest {
    private val plain = listOf(LyricLine(1000, 1000, "网易歌词"))
    private val word = listOf(LyricLine(1000, 1000, "QQ逐字", words = listOf(WordInfo("QQ逐字", 0, 1000))))
    private val track = ExternalTrack(id = "k1", name = "发如雪", artists = "周杰伦", durationMs = 299_000, platform = MusicPlatform.KUGOU)
    private val songId = track.queueSongId

    // 记录网易歌词按哪个 id 请求
    private fun repo(requested: MutableList<Long>, result: Result<List<LyricLine>>): PlaybackRepository = Proxy.newProxyInstance(
        PlaybackRepository::class.java.classLoader, arrayOf(PlaybackRepository::class.java)
    ) { _, method, args ->
        check(method.name == "getLyrics") { "Unexpected repository call: ${method.name}" }
        requested += args[0] as Long
        flowOf(result)
    } as PlaybackRepository

    @Test fun matchedNeteaseIdIsUsedForNeteaseAndAmll() = runTest {
        val requested = mutableListOf<Long>()
        val amllIds = mutableListOf<Long>()
        var matches = 0
        val resolver = LyricsResolver(
            repo(requested, Result.success(plain)), { null }, { null },
            readAmllLyrics = { amllIds += it; null },
            processingDispatcher = StandardTestDispatcher(testScheduler),
            isQqEnabled = { false },
            externalOf = { if (it == songId) track else null },
            matchNeteaseId = { matches++; 186_001L }
        )

        val resolved = resolver.lyricsWithSourceFor(songId).toList().last().getOrThrow()

        assertEquals(LyricsSource.NETEASE, resolved.source)
        assertEquals(listOf(186_001L), requested)
        assertEquals(listOf(186_001L), amllIds)
        assertEquals(1, matches)
    }

    @Test fun qqUsesExternalInfoWhenNeteaseHasNoMatch() = runTest {
        val requested = mutableListOf<Long>()
        val resolver = LyricsResolver(
            repo(requested, Result.success(plain)), { null }, { null },
            isAmllEnabled = { false },
            processingDispatcher = StandardTestDispatcher(testScheduler),
            externalOf = { track },
            matchNeteaseId = { null },
            readExternalQqLyrics = { if (it == track) word else emptyList() }
        )

        val resolved = resolver.lyricsWithSourceFor(songId).toList().last().getOrThrow()

        assertEquals(LyricsSource.QQ, resolved.source)
        assertEquals("QQ逐字", resolved.lines.single().text)
        assertTrue(requested.isEmpty())
    }

    @Test fun noMatchAndNoQqIsEmptyNotFailure() = runTest {
        val resolver = LyricsResolver(
            repo(mutableListOf(), Result.success(plain)), { null }, { "http://direct-url" },
            isAmllEnabled = { false },
            processingDispatcher = StandardTestDispatcher(testScheduler),
            externalOf = { track },
            matchNeteaseId = { throw IllegalStateException("search failed") }
        )

        val result = resolver.lyricsWithSourceFor(songId).toList().last()

        assertTrue(result.isSuccess)
        assertTrue(result.getOrThrow().lines.isEmpty())
    }
}

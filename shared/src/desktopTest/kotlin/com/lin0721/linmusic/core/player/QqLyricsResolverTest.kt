package com.lin0721.linmusic.core.player

import com.lin0721.linmusic.core.player.data.PlaybackRepository
import com.lin0721.linmusic.core.player.domain.LyricLine
import com.lin0721.linmusic.core.player.domain.WordInfo
import java.lang.reflect.Proxy
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class QqLyricsResolverTest {
    private val plain = listOf(LyricLine(1000, 1000, "普通歌词"))
    private val word = listOf(LyricLine(1000, 1000, "逐字", words = listOf(WordInfo("逐字", 0, 1000))))

    private fun repo(result: Result<List<LyricLine>>): PlaybackRepository = Proxy.newProxyInstance(
        PlaybackRepository::class.java.classLoader, arrayOf(PlaybackRepository::class.java)
    ) { _, method, _ ->
        check(method.name == "getLyrics") { "Unexpected repository call: ${method.name}" }
        flowOf(result)
    } as PlaybackRepository

    @Test fun qqWordLyricsReplacePlainNeteaseAndCarrySource() = runTest {
        val resolver = LyricsResolver(repo(Result.success(plain)), { null }, { null },
            isAmllEnabled = { false }, processingDispatcher = StandardTestDispatcher(testScheduler), readQqLyrics = { word })
        val resolved = resolver.lyricsWithSourceFor(1).toList().last().getOrThrow()
        assertEquals(LyricsSource.QQ, resolved.source)
        assertEquals("逐字", resolved.lines.single().text)
    }

    @Test fun neteaseWordTimingWinsButDoesNotPreventParallelQqQuery() = runTest {
        var qqReads = 0
        val resolver = LyricsResolver(repo(Result.success(word)), { null }, { null },
            isAmllEnabled = { false }, processingDispatcher = StandardTestDispatcher(testScheduler),
            readQqLyrics = { qqReads++; emptyList() })
        assertEquals(LyricsSource.NETEASE, resolver.lyricsWithSourceFor(1).toList().last().getOrThrow().source)
        assertEquals(1, qqReads)
    }

    @Test fun qqRescuesNeteaseFailure() = runTest {
        val resolver = LyricsResolver(repo(Result.failure(IllegalStateException("offline"))), { null }, { null },
            isAmllEnabled = { false }, processingDispatcher = StandardTestDispatcher(testScheduler), readQqLyrics = { word })
        assertEquals(LyricsSource.QQ, resolver.lyricsWithSourceFor(1).toList().last().getOrThrow().source)
    }

    @Test fun disablingQqSkipsReaderAndReenablingReselectsTheSameSong() = runTest {
        var enabled = false
        var reads = 0
        val resolver = LyricsResolver(repo(Result.success(plain)), { null }, { null },
            isAmllEnabled = { false }, processingDispatcher = StandardTestDispatcher(testScheduler),
            readQqLyrics = { reads++; word }, isQqEnabled = { enabled })
        assertEquals(LyricsSource.NETEASE, resolver.lyricsWithSourceFor(1).toList().last().getOrThrow().source)
        assertEquals(0, reads)
        enabled = true
        assertEquals(LyricsSource.QQ, resolver.lyricsWithSourceFor(1).toList().last().getOrThrow().source)
        assertEquals(1, reads)
        enabled = false
        assertEquals(LyricsSource.NETEASE, resolver.lyricsWithSourceFor(1).toList().last().getOrThrow().source)
        assertEquals(1, reads)
    }

    @Test fun qqPlainLyricsDoNotReplaceExistingPlainLyrics() = runTest {
        val resolver = LyricsResolver(repo(Result.success(plain)), { null }, { null },
            isAmllEnabled = { false }, processingDispatcher = StandardTestDispatcher(testScheduler), readQqLyrics = { plain })
        assertEquals(LyricsSource.NETEASE, resolver.lyricsWithSourceFor(1).toList().last().getOrThrow().source)
    }

    @Test fun qqTimeoutPreservesOriginalErrorAndLocalFallback() = runTest {
        val failure = IllegalStateException("offline")
        val resolver = LyricsResolver(repo(Result.failure(failure)), { "[00:01.00]本地" }, { if (it == 1L) "local" else null },
            isAmllEnabled = { false }, processingDispatcher = StandardTestDispatcher(testScheduler),
            readQqLyrics = { delay(9000); word })
        assertEquals(LyricsSource.LOCAL, resolver.lyricsWithSourceFor(1).toList().last().getOrThrow().source)
        assertSame(failure, resolver.lyricsFor(2).first().exceptionOrNull())
        assertTrue(testScheduler.currentTime in 16000L..16500L)
    }

    @Test fun cancellationIsPropagated() = runTest {
        val resolver = LyricsResolver(repo(Result.success(plain)), { null }, { null },
            isAmllEnabled = { false }, processingDispatcher = StandardTestDispatcher(testScheduler),
            readQqLyrics = { throw CancellationException("cancel") })
        try {
            resolver.lyricsFor(1).toList().last()
            fail("Cancellation should propagate")
        } catch (_: CancellationException) { }
    }
}

package com.lin0721.linmusic.core.player

import com.lin0721.linmusic.core.player.data.PlaybackRepository
import com.lin0721.linmusic.core.player.domain.LyricLine
import com.lin0721.linmusic.core.player.domain.WordInfo
import java.lang.reflect.Proxy
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class ProgressiveLyricsResolverTest {
    private val plain = listOf(LyricLine(1000, 1000, "网易歌词"))
    private val word = listOf(LyricLine(1000, 1000, "逐字", words = listOf(WordInfo("逐字", 0, 1000))))
    private val ttml = """<tt xmlns="http://www.w3.org/ns/ttml"><body><div><p begin="1s" end="2s"><span begin="1s" end="2s">AMLL</span></p></div></body></tt>"""

    private fun repo(result: Result<List<LyricLine>>, waitMs: Long = 0, started: () -> Unit = {}): PlaybackRepository = Proxy.newProxyInstance(
        PlaybackRepository::class.java.classLoader, arrayOf(PlaybackRepository::class.java)
    ) { _, method, _ ->
        check(method.name == "getLyrics")
        flow { started(); delay(waitMs); emit(result) }
    } as PlaybackRepository

    @Test fun allEnabledSourcesStartTogetherWithoutWaitingForNetease() = runTest {
        val starts = mutableListOf<Pair<String, Long>>()
        val resolver = LyricsResolver(repo(Result.success(word), 3000) { starts += "netease" to testScheduler.currentTime }, { null }, { null },
            readAmllLyrics = { starts += "amll" to testScheduler.currentTime; delay(1500); ttml },
            readQqLyrics = { starts += "qq" to testScheduler.currentTime; delay(500); word },
            processingDispatcher = StandardTestDispatcher(testScheduler))
        val received = mutableListOf<Pair<Long, LyricsSource>>()
        launch { resolver.lyricsWithSourceFor(1).collect { received += testScheduler.currentTime to it.getOrThrow().source } }
        runCurrent()
        assertEquals(setOf("netease", "amll", "qq"), starts.map { it.first }.toSet())
        assertTrue(starts.all { it.second == 0L })
        advanceTimeBy(750); runCurrent()
        assertEquals(LyricsSource.QQ, received.first().second)
        assertTrue(received.first().first < 3000)
        advanceUntilIdle()
        assertEquals(LyricsSource.AMLL, received.last().second)
    }

    @Test fun firstResultIsImmediateAndLaterResultsInsideOneWindowAreCoalesced() = runTest {
        val resolver = LyricsResolver(repo(Result.success(plain), 20), { null }, { null },
            readAmllLyrics = { delay(180); ttml }, readQqLyrics = { delay(90); word },
            processingDispatcher = StandardTestDispatcher(testScheduler))
        val received = mutableListOf<Pair<Long, LyricsSource>>()
        launch { resolver.lyricsWithSourceFor(1).collect { received += testScheduler.currentTime to it.getOrThrow().source } }
        advanceTimeBy(249); runCurrent()
        assertEquals(listOf(20L to LyricsSource.NETEASE), received)
        advanceUntilIdle()
        assertEquals(listOf(20L to LyricsSource.NETEASE, 250L to LyricsSource.AMLL), received)
    }

    @Test fun updatesFollowFixed250MillisecondWindows() = runTest {
        val resolver = LyricsResolver(repo(Result.success(plain), 10), { null }, { null },
            readAmllLyrics = { delay(510); ttml }, readQqLyrics = { delay(260); word },
            processingDispatcher = StandardTestDispatcher(testScheduler))
        val received = mutableListOf<Pair<Long, LyricsSource>>()
        launch { resolver.lyricsWithSourceFor(1).collect { received += testScheduler.currentTime to it.getOrThrow().source } }
        advanceUntilIdle()
        assertEquals(listOf(10L to LyricsSource.NETEASE, 500L to LyricsSource.QQ, 750L to LyricsSource.AMLL), received)
    }

    @Test fun fastAmllDoesNotWaitForSlowNeteaseOrQqAndIsNotDowngraded() = runTest {
        val resolver = LyricsResolver(repo(Result.success(word), 3000), { null }, { null },
            readAmllLyrics = { delay(10); ttml }, readQqLyrics = { delay(2000); word },
            processingDispatcher = StandardTestDispatcher(testScheduler))
        val received = mutableListOf<Pair<Long, LyricsSource>>()
        launch { resolver.lyricsWithSourceFor(1).collect { received += testScheduler.currentTime to it.getOrThrow().source } }
        advanceTimeBy(250); runCurrent()
        assertEquals(listOf(10L to LyricsSource.AMLL), received)
        advanceUntilIdle()
        assertEquals(1, received.size)
    }

    @Test fun amllFailureDoesNotClearOrRepeatDisplayedLyrics() = runTest {
        val resolver = LyricsResolver(repo(Result.success(plain)), { null }, { null },
            readAmllLyrics = { delay(1000); error("offline") }, readQqLyrics = { delay(2000); emptyList() },
            processingDispatcher = StandardTestDispatcher(testScheduler))
        val results = resolver.lyricsWithSourceFor(1).toList()
        assertEquals(1, results.size)
        assertEquals(LyricsSource.NETEASE, results.single().getOrThrow().source)
    }

    @Test fun emptyResultsWaitUntilAllQueriesFinishAndAreFlushed() = runTest {
        val resolver = LyricsResolver(repo(Result.success(emptyList())), { null }, { null },
            readAmllLyrics = { delay(1000); null }, readQqLyrics = { delay(1510); emptyList() },
            processingDispatcher = StandardTestDispatcher(testScheduler))
        val received = mutableListOf<Long>()
        launch { resolver.lyricsFor(1).collect { received += testScheduler.currentTime; assertTrue(it.getOrThrow().isEmpty()) } }
        advanceTimeBy(1500); runCurrent()
        assertTrue(received.isEmpty())
        advanceUntilIdle()
        assertEquals(listOf(1510L), received)
    }

    @Test fun allFailuresKeepOriginalNeteaseErrorAfterTimeout() = runTest {
        val failure = IllegalStateException("offline")
        val resolver = LyricsResolver(repo(Result.failure(failure)), { null }, { null },
            readAmllLyrics = { delay(20000); null }, readQqLyrics = { delay(20000); word },
            processingDispatcher = StandardTestDispatcher(testScheduler))
        assertSame(failure, resolver.lyricsFor(1).toList().single().exceptionOrNull())
        assertTrue(testScheduler.currentTime in 8000L..8250L)
    }

    @Test fun emptyNeteaseAndQqCancelPendingAmll() = runTest {
        var cancelledAt: Long? = null
        val resolver = LyricsResolver(repo(Result.success(emptyList()), 100), { null }, { null },
            readAmllLyrics = { try { delay(2000); ttml } finally { cancelledAt = testScheduler.currentTime } },
            readQqLyrics = { delay(510); emptyList() },
            processingDispatcher = StandardTestDispatcher(testScheduler))
        val results = resolver.lyricsFor(1).toList()
        assertTrue(results.single().getOrThrow().isEmpty())
        assertEquals(510L, cancelledAt)
        assertEquals(510L, testScheduler.currentTime)
    }

    @Test fun disabledQqDoesNotCountAsEmptyQqResult() = runTest {
        val resolver = LyricsResolver(repo(Result.success(emptyList())), { null }, { null },
            readAmllLyrics = { delay(510); ttml }, isQqEnabled = { false },
            processingDispatcher = StandardTestDispatcher(testScheduler))
        assertEquals(LyricsSource.AMLL, resolver.lyricsWithSourceFor(1).toList().single().getOrThrow().source)
    }

    @Test fun completedAmllIsPreservedWhenOtherSourcesAreEmpty() = runTest {
        val resolver = LyricsResolver(repo(Result.success(emptyList()), 510), { null }, { null },
            readAmllLyrics = { ttml }, readQqLyrics = { delay(510); emptyList() },
            processingDispatcher = StandardTestDispatcher(testScheduler))
        assertEquals(LyricsSource.AMLL, resolver.lyricsWithSourceFor(1).toList().single().getOrThrow().source)
    }

    @Test fun disabledSourcesAreNotRead() = runTest {
        var reads = 0
        val resolver = LyricsResolver(repo(Result.success(plain)), { null }, { null },
            readAmllLyrics = { reads++; ttml }, readQqLyrics = { reads++; word },
            isAmllEnabled = { false }, isQqEnabled = { false }, processingDispatcher = StandardTestDispatcher(testScheduler))
        assertEquals(LyricsSource.NETEASE, resolver.lyricsWithSourceFor(1).toList().single().getOrThrow().source)
        assertEquals(0, reads)
        assertEquals(0L, testScheduler.currentTime)
    }

    @Test fun matchedLocalSongShowsLocalFirstThenUpgrades() = runTest {
        var localReads = 0
        val resolver = LyricsResolver(repo(Result.success(plain)), { localReads++; "[00:01.00]本地" }, { "local" },
            readAmllLyrics = { delay(510); ttml }, isQqEnabled = { false },
            processingDispatcher = StandardTestDispatcher(testScheduler))
        val results = resolver.lyricsWithSourceFor(1).toList().map { it.getOrThrow().source }
        assertEquals(listOf(LyricsSource.LOCAL, LyricsSource.NETEASE, LyricsSource.AMLL), results)
        assertEquals(1, localReads)
    }

    @Test fun firstResultCancellationStopsBothOtherSources() = runTest {
        val cancelled = mutableSetOf<String>()
        val resolver = LyricsResolver(repo(Result.success(plain)), { null }, { null },
            readAmllLyrics = { try { delay(3000); ttml } finally { cancelled += "amll" } },
            readQqLyrics = { try { delay(3000); word } finally { cancelled += "qq" } },
            processingDispatcher = StandardTestDispatcher(testScheduler))
        assertEquals(LyricsSource.NETEASE, resolver.lyricsWithSourceFor(1).first().getOrThrow().source)
        runCurrent()
        assertEquals(setOf("amll", "qq"), cancelled)
        assertEquals(0L, testScheduler.currentTime)
    }

    @Test fun amllTimeoutAfterSixSecondsKeepsEarlierResult() = runTest {
        val resolver = LyricsResolver(repo(Result.success(word)), { null }, { null },
            readAmllLyrics = { delay(7000); ttml }, readQqLyrics = { word },
            processingDispatcher = StandardTestDispatcher(testScheduler))
        val received = mutableListOf<Pair<Long, LyricsSource>>()
        launch { resolver.lyricsWithSourceFor(1).collect { received += testScheduler.currentTime to it.getOrThrow().source } }
        advanceTimeBy(5999); runCurrent()
        assertEquals(listOf(0L to LyricsSource.NETEASE), received)
        advanceUntilIdle()
        assertEquals(listOf(0L to LyricsSource.NETEASE), received)
        assertTrue(testScheduler.currentTime in 6000L..6250L)
    }

    @Test fun disabledNeteaseIsNotQueriedAndOtherSourcesStillWork() = runTest {
        var neteaseCalls = 0
        val resolver = LyricsResolver(repo(Result.success(plain)) { neteaseCalls++ }, { null }, { null },
            readAmllLyrics = { null }, readQqLyrics = { word },
            isNeteaseEnabled = { false },
            processingDispatcher = StandardTestDispatcher(testScheduler))
        val results = resolver.lyricsWithSourceFor(1).toList()
        assertEquals(0, neteaseCalls)
        assertEquals(LyricsSource.QQ, results.last().getOrThrow().source)
    }

    @Test fun disabledNeteaseDoesNotCancelPendingAmll() = runTest {
        val resolver = LyricsResolver(repo(Result.success(plain)), { null }, { null },
            readAmllLyrics = { delay(1000); ttml }, readQqLyrics = { delay(100); emptyList() },
            isNeteaseEnabled = { false },
            processingDispatcher = StandardTestDispatcher(testScheduler))
        assertEquals(LyricsSource.AMLL, resolver.lyricsWithSourceFor(1).toList().last().getOrThrow().source)
        assertEquals(1000L, testScheduler.currentTime)
    }

    @Test fun allSourcesDisabledEmitsOneEmptyResultWithoutQueries() = runTest {
        var reads = 0
        val resolver = LyricsResolver(repo(Result.success(plain)) { reads++ }, { null }, { null },
            readAmllLyrics = { reads++; ttml }, readQqLyrics = { reads++; word },
            isAmllEnabled = { false }, isQqEnabled = { false }, isNeteaseEnabled = { false },
            processingDispatcher = StandardTestDispatcher(testScheduler))
        val results = resolver.lyricsWithSourceFor(1).toList().map { it.getOrThrow() }
        assertEquals(1, results.size)
        assertTrue(results.single().lines.isEmpty())
        assertEquals(0, reads)
        assertEquals(0L, testScheduler.currentTime)
    }

    @Test fun neteaseFailureDoesNotCancelPendingAmll() = runTest {
        val resolver = LyricsResolver(repo(Result.failure(IllegalStateException("offline"))), { null }, { null },
            readAmllLyrics = { delay(1000); ttml }, readQqLyrics = { delay(100); emptyList() },
            processingDispatcher = StandardTestDispatcher(testScheduler))
        val results = resolver.lyricsWithSourceFor(1).toList()
        assertEquals(LyricsSource.AMLL, results.last().getOrThrow().source)
        assertEquals(1000L, testScheduler.currentTime)
    }

    @Test fun localLyricsAppearBeforeSlowOnlineSourcesFinish() = runTest {
        val resolver = LyricsResolver(repo(Result.success(emptyList())), { "[00:01.00]本地" }, { "local" },
            readAmllLyrics = { delay(5000); null }, readQqLyrics = { delay(3000); emptyList() },
            processingDispatcher = StandardTestDispatcher(testScheduler))
        val received = mutableListOf<Pair<Long, LyricsSource>>()
        launch { resolver.lyricsWithSourceFor(1).collect { received += testScheduler.currentTime to it.getOrThrow().source } }
        runCurrent()
        assertEquals(listOf(0L to LyricsSource.LOCAL), received)
        advanceUntilIdle()
        assertEquals(1, received.size)
    }

    @Test fun matchedLocalSongWithoutAnyLyricsEmitsOneEmptyLocalResult() = runTest {
        val resolver = LyricsResolver(repo(Result.success(emptyList())), { null }, { "local" },
            readAmllLyrics = { null }, readQqLyrics = { emptyList() },
            processingDispatcher = StandardTestDispatcher(testScheduler))
        val results = resolver.lyricsWithSourceFor(1).toList().map { it.getOrThrow() }
        assertEquals(1, results.size)
        assertEquals(LyricsSource.LOCAL, results.single().source)
        assertTrue(results.single().lines.isEmpty())
    }
}

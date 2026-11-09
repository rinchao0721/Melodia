package com.lin0721.linmusic.core.player

import com.lin0721.linmusic.core.player.data.PlaybackRepository
import com.lin0721.linmusic.core.player.domain.LyricFilterConfig
import com.lin0721.linmusic.core.player.domain.LyricLine
import java.lang.reflect.Proxy
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class LyricFilterResolverTest {
    private val credited = listOf(
        LyricLine(0, 1000, "作词：张三"),
        LyricLine(1000, 1000, "第一句"),
        LyricLine(2000, 1000, "第二句")
    )
    private val localLrc = "[00:00.00]作词：张三\n[00:01.00]第一句\n[00:02.00]第二句"

    private fun repo(result: Result<List<LyricLine>>): PlaybackRepository = Proxy.newProxyInstance(
        PlaybackRepository::class.java.classLoader, arrayOf(PlaybackRepository::class.java)
    ) { _, method, _ ->
        check(method.name == "getLyrics") { "Unexpected repository call: ${method.name}" }
        flowOf(result)
    } as PlaybackRepository

    @Test fun neteaseCreditsAreStrippedWhenEnabled() = runTest {
        val resolver = LyricsResolver(repo(Result.success(credited)), { null }, { null },
            isAmllEnabled = { false }, isQqEnabled = { false },
            processingDispatcher = StandardTestDispatcher(testScheduler),
            lyricFilterConfig = { LyricFilterConfig() })
        val resolved = resolver.lyricsWithSourceFor(1).toList().last().getOrThrow()
        assertEquals(listOf("第一句", "第二句"), resolved.lines.map { it.text })
    }

    @Test fun filterDisabledKeepsCredits() = runTest {
        val resolver = LyricsResolver(repo(Result.success(credited)), { null }, { null },
            isAmllEnabled = { false }, isQqEnabled = { false },
            processingDispatcher = StandardTestDispatcher(testScheduler),
            lyricFilterConfig = { LyricFilterConfig(enabled = false) })
        val resolved = resolver.lyricsWithSourceFor(1).toList().last().getOrThrow()
        assertEquals(3, resolved.lines.size)
    }

    @Test fun amllIsNotFilteredByDefault() = runTest {
        val ttml = """<tt xmlns="http://www.w3.org/ns/ttml"><body><div>""" +
            """<p begin="00:00.000" end="00:01.000"><span begin="00:00.000" end="00:01.000">作词：张三</span></p>""" +
            """<p begin="00:01.000" end="00:02.000"><span begin="00:01.000" end="00:02.000">第一句</span></p>""" +
            """</div></body></tt>"""
        val resolver = LyricsResolver(repo(Result.success(emptyList())), { null }, { null },
            readAmllLyrics = { ttml }, isQqEnabled = { false },
            processingDispatcher = StandardTestDispatcher(testScheduler),
            lyricFilterConfig = { LyricFilterConfig() })
        val resolved = resolver.lyricsWithSourceFor(1).toList().last().getOrThrow()
        assertEquals(LyricsSource.AMLL, resolved.source)
        assertEquals(2, resolved.lines.size)
    }

    @Test fun localLyricsFollowLocalSwitch() = runTest {
        suspend fun resolve(filterLocal: Boolean): List<String> {
            val resolver = LyricsResolver(repo(Result.success(emptyList())), { localLrc }, { "file:///a.flac" },
                processingDispatcher = StandardTestDispatcher(testScheduler),
                lyricFilterConfig = { LyricFilterConfig(filterLocal = filterLocal) })
            return resolver.lyricsWithSourceFor(-5).toList().last().getOrThrow().lines.map { it.text }
        }
        assertEquals(3, resolve(false).size)
        assertEquals(listOf("第一句", "第二句"), resolve(true))
    }
}

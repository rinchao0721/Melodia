package com.lin0721.linmusic.core.player

import com.lin0721.linmusic.core.localmusic.LocalMusicApi
import com.lin0721.linmusic.core.model.Track
import com.lin0721.linmusic.core.player.data.PlaybackRepository
import com.lin0721.linmusic.core.player.data.LyricsContent
import com.lin0721.linmusic.core.player.domain.LyricLine
import com.lin0721.linmusic.core.player.domain.LyricsKind
import com.lin0721.linmusic.core.player.domain.lyricsKind
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.Executors

class LyricsResolverTest {

    private val onlineLines = listOf(LyricLine(timeMs = 0, text = "网易歌词"))
    private val localLrc = "[00:01.00]本地歌词"
    private val amllTtml = """<tt xmlns="http://www.w3.org/ns/ttml"><body><div><p begin="1s" end="2s"><span begin="1s" end="2s">AMLL歌词</span></p></div></body></tt>"""

    @Test
    fun `来源随实际选择更新并在AMLL失败时标记网易`() = runTest {
        var enabled = true
        var amllText: String? = amllTtml
        val resolver = LyricsResolver(
            FakePlaybackRepository(Result.success(onlineLines)),
            readLocalLyrics = { null }, localUriOf = { null },
            readAmllLyrics = { amllText }, isAmllEnabled = { enabled }
        )
        val amll = resolver.lyricsWithSourceFor(1).first().getOrThrow()
        assertEquals(LyricsSource.AMLL, amll.source)
        assertEquals("AMLL歌词", amll.lines.single().text)
        enabled = false
        assertEquals(LyricsSource.NETEASE, resolver.lyricsWithSourceFor(1).first().getOrThrow().source)
        enabled = true
        amllText = null
        assertEquals(LyricsSource.NETEASE, resolver.lyricsWithSourceFor(1).first().getOrThrow().source)
    }

    @Test
    fun `本地回退保留本地来源`() = runTest {
        val resolver = LyricsResolver(
            FakePlaybackRepository(Result.success(emptyList())),
            readLocalLyrics = { localLrc }, localUriOf = { "content://a" },
            isAmllEnabled = { false }
        )
        val result = resolver.lyricsWithSourceFor(1).first().getOrThrow()
        assertEquals(LyricsSource.LOCAL, result.source)
        assertEquals("本地歌词", result.lines.single().text)
    }

    @Test
    fun `网易纯音乐状态无需歌词标识行也能保留`() = runTest {
        val result = LyricsResolver(
            FakePlaybackRepository(Result.success(emptyList()), LyricsKind.PURE_MUSIC),
            readLocalLyrics = { null }, localUriOf = { null },
            isAmllEnabled = { false }
        ).lyricsWithSourceFor(1).first().getOrThrow()

        assertEquals(LyricsKind.PURE_MUSIC, result.kind)
        assertTrue(result.lines.isEmpty())
    }

    @Test
    fun `AMLL读取解析在后台执行而结果交回调用线程`() = runBlocking {
        Executors.newSingleThreadExecutor { runnable -> Thread(runnable, "lyrics-worker") }
            .asCoroutineDispatcher().use { dispatcher ->
                val callerThread = Thread.currentThread()
                var readThread: Thread? = null
                val resolver = LyricsResolver(
                    FakePlaybackRepository(Result.success(onlineLines)),
                    readLocalLyrics = { null }, localUriOf = { null },
                    readAmllLyrics = { readThread = Thread.currentThread(); amllTtml },
                    processingDispatcher = dispatcher
                )
                val result = resolver.lyricsFor(1).first().getOrThrow()
                assertEquals("AMLL歌词", result.single().text)
                assertEquals("lyrics-worker", readThread?.name)
                assertEquals(callerThread, Thread.currentThread())
            }
    }

    @Test
    fun `关闭AMLL不读取镜像或缓存并立即使用网易`() = runTest {
        val repo = FakePlaybackRepository(Result.success(onlineLines))
        var amllReads = 0
        val resolver = LyricsResolver(
            repo, readLocalLyrics = { null }, localUriOf = { null },
            readAmllLyrics = { amllReads++; amllTtml }, isAmllEnabled = { false }
        )
        assertEquals("网易歌词", resolver.lyricsFor(1).first().getOrThrow().single().text)
        assertEquals(0, amllReads)
        assertEquals(1, repo.lyricRequests)
        assertEquals(0L, testScheduler.currentTime)
    }

    @Test
    fun `切换AMLL开关后同一首歌重新选择歌词源`() = runTest {
        var enabled = true
        var amllReads = 0
        val resolver = LyricsResolver(
            FakePlaybackRepository(Result.success(onlineLines)),
            readLocalLyrics = { null }, localUriOf = { null },
            readAmllLyrics = { amllReads++; amllTtml }, isAmllEnabled = { enabled }
        )
        assertEquals("AMLL歌词", resolver.lyricsFor(1).first().getOrThrow().single().text)
        enabled = false
        assertEquals("网易歌词", resolver.lyricsFor(1).first().getOrThrow().single().text)
        enabled = true
        assertEquals("AMLL歌词", resolver.lyricsFor(1).first().getOrThrow().single().text)
        assertEquals(2, amllReads)
    }

    @Test
    fun `关闭AMLL后仍可回退本地歌词并保留网易失败`() = runTest {
        val failure = IllegalStateException("offline")
        val resolver = LyricsResolver(
            FakePlaybackRepository(Result.failure(failure)),
            readLocalLyrics = { localLrc }, localUriOf = { if (it == 1L) "content://a" else null },
            readAmllLyrics = { error("AMLL disabled") }, isAmllEnabled = { false }
        )
        assertEquals("本地歌词", resolver.lyricsFor(1).first().getOrThrow().single().text)
        assertEquals(failure, resolver.lyricsFor(2).first().exceptionOrNull())
    }

    private class FakePlaybackRepository(
        private val lyrics: Result<List<LyricLine>>,
        private val kindOverride: LyricsKind? = null
    ) : PlaybackRepository {
        var lyricRequests = 0
        override fun getLyrics(songId: Long): Flow<Result<LyricsContent>> {
            lyricRequests++
            return flowOf(lyrics.map { LyricsContent(it, kindOverride ?: it.lyricsKind()) })
        }
        override fun getRawLyrics(songId: Long): Flow<Result<String>> = emptyFlow()
        override fun getSongUrl(songId: Long): Flow<Result<String>> = emptyFlow()
        override fun getSongPlaybackInfo(songId: Long): Flow<Result<com.lin0721.linmusic.core.player.data.SongPlaybackInfo>> = emptyFlow()
        override fun getSongDetail(songId: Long): Flow<Result<Track>> = emptyFlow()
        override fun getSimilarSongs(songId: Long): Flow<Result<List<Track>>> = emptyFlow()
        override fun getIntelligenceSongs(songId: Long, playlistId: Long): Flow<Result<List<Track>>> = emptyFlow()
        override fun reportStartPlay(songId: Long, source: PlaySource?): Flow<Result<Unit>> = emptyFlow()
        override fun reportPlayEnd(songId: Long, playedSeconds: Long, source: PlaySource?): Flow<Result<Unit>> = emptyFlow()
        override val playlistRecorded: SharedFlow<Long> = MutableSharedFlow()
    }

    private class FakeLocalMusicApi(private val lyrics: String?) : LocalMusicApi {
        var lyricReads = 0
        override suspend fun coverUriFor(sourceUri: android.net.Uri): android.net.Uri? = null
        override suspend fun readLyrics(sourceUri: String): String? {
            lyricReads++
            return lyrics
        }
    }

    private fun resolver(repo: PlaybackRepository, local: LocalMusicApi, localUri: String?) =
        LyricsResolver(repo, local::readLyrics, localUriOf = { localUri })

    @Test
    fun `未匹配的本地歌只读本地歌词不请求网易`() = runTest {
        val repo = FakePlaybackRepository(Result.success(onlineLines))
        val lines = resolver(repo, FakeLocalMusicApi(localLrc), "content://a").lyricsFor(-5L).first().getOrThrow()
        assertEquals("本地歌词", lines.single().text)
        assertEquals(0, repo.lyricRequests)
    }

    @Test
    fun `已匹配的本地歌优先网易歌词`() = runTest {
        val local = FakeLocalMusicApi(localLrc)
        val lines = resolver(FakePlaybackRepository(Result.success(onlineLines)), local, "content://a").lyricsFor(7L).first().getOrThrow()
        assertEquals("网易歌词", lines.single().text)
        assertEquals(0, local.lyricReads)
    }

    @Test
    fun `网易歌词为空时回退本地歌词`() = runTest {
        val lines = resolver(FakePlaybackRepository(Result.success(emptyList())), FakeLocalMusicApi(localLrc), "content://a")
            .lyricsFor(7L).first().getOrThrow()
        assertEquals("本地歌词", lines.single().text)
    }

    @Test
    fun `网易歌词请求失败时回退本地歌词`() = runTest {
        val lines = resolver(FakePlaybackRepository(Result.failure(RuntimeException())), FakeLocalMusicApi(localLrc), "content://a")
            .lyricsFor(7L).first().getOrThrow()
        assertEquals("本地歌词", lines.single().text)
    }

    @Test
    fun `在线歌曲直接透传网易结果`() = runTest {
        val failure = Result.failure<List<LyricLine>>(RuntimeException("网络错误"))
        val result = resolver(FakePlaybackRepository(failure), FakeLocalMusicApi(null), null).lyricsFor(7L).first()
        assertTrue(result.isFailure)
    }

    @Test
    fun `没有本地文件的占位 id 返回空歌词且不请求网易`() = runTest {
        val repo = FakePlaybackRepository(Result.success(onlineLines))
        val lines = resolver(repo, FakeLocalMusicApi(localLrc), null).lyricsFor(-5L).first().getOrThrow()
        assertTrue(lines.isEmpty())
        assertEquals(0, repo.lyricRequests)
    }

    @Test
    fun `本地找不到歌词时返回空列表`() = runTest {
        val lines = resolver(FakePlaybackRepository(Result.success(emptyList())), FakeLocalMusicApi(null), "content://a")
            .lyricsFor(-5L).first().getOrThrow()
        assertTrue(lines.isEmpty())
    }
}

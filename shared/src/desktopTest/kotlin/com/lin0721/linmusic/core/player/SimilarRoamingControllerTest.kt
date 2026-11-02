package com.lin0721.linmusic.core.player

import com.lin0721.linmusic.core.model.Album
import com.lin0721.linmusic.core.model.Artist
import com.lin0721.linmusic.core.model.Track
import com.lin0721.linmusic.core.player.data.PlaybackRepository
import com.lin0721.linmusic.core.player.data.SongPlaybackInfo
import com.lin0721.linmusic.core.player.domain.LyricLine
import com.lin0721.linmusic.core.preferences.PreferencesStores
import com.lin0721.linmusic.core.preferences.SettingsPreferences
import java.io.File
import java.nio.file.Files
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

// 漫游续接的回归测试：相似列表高度重叠，若不去重/不保序就会退化成两三首循环（#132）
class SimilarRoamingControllerTest {

    private fun item(id: Long) = QueueItem(songId = id, title = "歌曲$id", artist = "歌手$id", coverUrl = "cover/$id")

    private fun track(id: Long) = Track(
        id = id,
        name = "歌曲$id",
        ar = listOf(Artist(id = 1, name = "歌手$id")),
        al = Album(id = 1, name = "专辑$id", picUrl = "cover/$id")
    )

    // 相似歌曲接口按歌曲 id 返回预设列表，并记录调用次数
    private class FakePlaybackRepository(
        private val simi: (Long) -> List<Track>
    ) : PlaybackRepository {
        var simiCalls = 0

        override fun getSongUrl(songId: Long): Flow<Result<String>> = emptyFlow()

        override fun getSongPlaybackInfo(songId: Long): Flow<Result<SongPlaybackInfo>> = emptyFlow()

        override fun getLyrics(songId: Long): Flow<Result<List<LyricLine>>> = emptyFlow()

        override fun getRawLyrics(songId: Long): Flow<Result<String>> = emptyFlow()

        override fun getSongDetail(songId: Long): Flow<Result<Track>> = emptyFlow()

        override fun getSimilarSongs(songId: Long): Flow<Result<List<Track>>> = flow {
            simiCalls++
            emit(Result.success(simi(songId)))
        }

        override fun getIntelligenceSongs(songId: Long, playlistId: Long): Flow<Result<List<Track>>> = emptyFlow()

        override fun reportStartPlay(songId: Long, source: PlaySource?): Flow<Result<Unit>> = emptyFlow()

        override fun reportPlayEnd(songId: Long, playedSeconds: Long, source: PlaySource?): Flow<Result<Unit>> = emptyFlow()

        override val playlistRecorded: SharedFlow<Long> = MutableSharedFlow()
    }

    private class Harness(
        val controller: SimilarRoamingController,
        val queue: PlaybackQueue,
        val repository: FakePlaybackRepository
    )

    private fun harness(simi: (Long) -> List<Track>, ids: List<Long>, startIndex: Int = 0): Harness {
        val dir: File = Files.createTempDirectory("melodia-roaming-test").toFile()
        val store = PreferencesStores.get(File(dir, "playback.preferences_pb"))
        val settings = SettingsPreferences(PreferencesStores.get(File(dir, "settings.preferences_pb")))
        val playbackPreferences = PlaybackPreferences(store)
        val scope = CoroutineScope(Dispatchers.Unconfined)
        val stateStore = PlaybackStateStore(scope, playbackPreferences)
        val queue = PlaybackQueue().apply { replaceAll(ids.map { item(it) }, startIndex) }
        val repository = FakePlaybackRepository(simi)
        val controller = SimilarRoamingController(scope, repository, settings, queue, stateStore)
        return Harness(controller, queue, repository)
    }

    private fun PlaybackQueue.ids(): List<Long> = items.value.map { it.songId }

    @Test
    fun `续接只追加队尾_不截断未播曲目且不出重复`() {
        val h = harness(
            simi = { listOf(track(1), track(2), track(4), track(5)) },
            ids = listOf(1, 2, 3)
        )
        h.queue.setPlayContext(SimilarRoamingController.CONTEXT_ROAMING)

        h.controller.prefetchOnPlay(1)

        // 还没播的 2、3 必须保留，种子曲自荐与新歌一起去重后接在后面
        assertEquals(listOf(1L, 2L, 3L, 4L, 5L), h.queue.ids())
        assertEquals(h.queue.ids().size, h.queue.ids().distinct().size)
    }

    @Test
    fun `队尾未播曲目足够时不再请求相似歌曲`() {
        val h = harness(simi = { listOf(track(9)) }, ids = listOf(1, 2, 3, 4, 5, 6), startIndex = 0)
        h.queue.setPlayContext(SimilarRoamingController.CONTEXT_ROAMING)
        assertEquals(5, h.queue.upcomingCount)

        h.controller.prefetchOnPlay(1)

        assertEquals(0, h.repository.simiCalls)
        assertEquals(listOf(1L, 2L, 3L, 4L, 5L, 6L), h.queue.ids())
    }

    @Test
    fun `相似列表被播完时不会原地循环`() {
        val h = harness(simi = { listOf(track(1), track(2), track(3)) }, ids = listOf(1, 2, 3))
        h.queue.setPlayContext(SimilarRoamingController.CONTEXT_ROAMING)

        h.controller.prefetchOnPlay(3)

        // 全是已在本队列的歌，宁可这一轮不补，也不能把队尾换成刚播过的几首
        assertEquals(listOf(1L, 2L, 3L), h.queue.ids())
    }

    @Test
    fun `播放到队列末尾时自动续接并进入漫游上下文`() = runBlocking {
        val h = harness(simi = { listOf(track(2), track(7), track(8)) }, ids = listOf(1, 2), startIndex = 1)

        h.controller.onProgressTick(songId = 2, remainingMs = 1000)

        assertEquals(SimilarRoamingController.CONTEXT_ROAMING, h.queue.playContext.value)
        assertEquals(listOf(1L, 2L, 7L, 8L), h.queue.ids())
        assertEquals(1, h.repository.simiCalls)
    }

    @Test
    fun `退出漫游还原进入前的队列`() {
        val h = harness(simi = { listOf(track(7), track(8)) }, ids = listOf(1, 2, 3), startIndex = 1)
        h.controller.prepare()
        h.queue.setPlayContext(SimilarRoamingController.CONTEXT_ROAMING)
        h.queue.setPlayMode(PlayMode.LIST_LOOP)

        h.controller.prefetchOnPlay(2)
        assertTrue(h.queue.ids().size > 3)

        h.controller.disable()

        assertEquals(listOf(1L, 2L, 3L), h.queue.ids())
        assertNull(h.queue.playContext.value)
        assertEquals(1, h.queue.currentIndex.value)
    }
}

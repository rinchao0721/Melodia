package com.lin0721.linmusic.core.player

import com.lin0721.linmusic.core.model.Album
import com.lin0721.linmusic.core.model.Artist
import com.lin0721.linmusic.core.model.Track
import com.lin0721.linmusic.core.player.data.PlaybackRepository
import com.lin0721.linmusic.core.player.data.SongPlaybackInfo
import com.lin0721.linmusic.core.player.domain.LyricLine
import com.lin0721.linmusic.core.preferences.SettingsPreferences
import com.lin0721.linmusic.feature.podcast.ui.InMemoryPreferencesStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PersonalFmControllerTest {

    private fun item(id: Long) = QueueItem(songId = id, title = "歌曲$id", artist = "歌手$id", coverUrl = "cover/$id")

    private fun track(id: Long) = Track(
        id = id,
        name = "歌曲$id",
        ar = listOf(Artist(id = 1, name = "歌手$id")),
        al = Album(id = 1, name = "专辑$id", picUrl = "cover/$id")
    )

    private class FakePlaybackRepository(
        private val fmHandler: (PersonalFmMode) -> Result<List<Track>> = { Result.success(emptyList()) },
        private val trashHandler: (Long, Long) -> Result<Unit> = { _, _ -> Result.success(Unit) }
    ) : PlaybackRepository {
        val fmCalls = mutableListOf<PersonalFmMode>()
        val trashCalls = mutableListOf<Pair<Long, Long>>()

        override fun getSongUrl(songId: Long): Flow<Result<String>> = emptyFlow()

        override fun getSongPlaybackInfo(songId: Long): Flow<Result<SongPlaybackInfo>> = emptyFlow()

        override fun getLyrics(songId: Long): Flow<Result<List<LyricLine>>> = emptyFlow()

        override fun getRawLyrics(songId: Long): Flow<Result<String>> = emptyFlow()

        override fun getSongDetail(songId: Long): Flow<Result<Track>> = emptyFlow()

        override fun getSimilarSongs(songId: Long): Flow<Result<List<Track>>> = emptyFlow()

        override fun getIntelligenceSongs(songId: Long, playlistId: Long): Flow<Result<List<Track>>> = emptyFlow()

        override fun getPersonalFmSongs(mode: PersonalFmMode): Flow<Result<List<Track>>> = flow {
            fmCalls += mode
            emit(fmHandler(mode))
        }

        override fun trashFmSong(songId: Long, playedSeconds: Long): Flow<Result<Unit>> = flow {
            trashCalls += songId to playedSeconds
            emit(trashHandler(songId, playedSeconds))
        }

        override fun reportStartPlay(songId: Long, source: PlaySource?): Flow<Result<Unit>> = emptyFlow()

        override fun reportPlayEnd(songId: Long, playedSeconds: Long, source: PlaySource?): Flow<Result<Unit>> = emptyFlow()

        override val playlistRecorded: SharedFlow<Long> = MutableSharedFlow()
    }

    private class Harness(
        val controller: PersonalFmController,
        val queue: PlaybackQueue,
        val repository: FakePlaybackRepository,
        val settings: SettingsPreferences,
        val playbackPreferences: PlaybackPreferences
    )

    private fun harness(
        ids: List<Long>,
        startIndex: Int = 0,
        fmHandler: (PersonalFmMode) -> Result<List<Track>> = { Result.success(emptyList()) },
        trashHandler: (Long, Long) -> Result<Unit> = { _, _ -> Result.success(Unit) }
    ): Harness {
        val settings = SettingsPreferences(InMemoryPreferencesStore())
        val playbackPreferences = PlaybackPreferences(InMemoryPreferencesStore())
        val scope = CoroutineScope(Dispatchers.Unconfined)
        val stateStore = PlaybackStateStore(scope, playbackPreferences)
        val queue = PlaybackQueue().apply {
            replaceAll(ids.map { item(it) }, startIndex)
        }
        val repository = FakePlaybackRepository(fmHandler, trashHandler)
        val controller = PersonalFmController(scope, repository, settings, queue, stateStore)
        return Harness(controller, queue, repository, settings, playbackPreferences)
    }

    private fun PlaybackQueue.ids(): List<Long> = items.value.map { it.songId }

    @Test
    fun `进入私人FM会备份原队列并强制列表循环`() {
        val h = harness(ids = listOf(10, 20), startIndex = 1)
        h.queue.setPlayContext("原歌单")
        h.queue.setPlayMode(PlayMode.SHUFFLE)

        h.controller.prepare()
        h.queue.setPlayContext(PersonalFmController.CONTEXT_PERSONAL_FM)
        h.queue.replaceAll(listOf(item(100), item(101)), 0)

        assertEquals(PlayMode.LIST_LOOP, h.queue.playMode.value)
        assertEquals(listOf(100L, 101L), h.queue.ids())

        h.controller.disable()

        assertEquals("原歌单", h.queue.playContext.value)
        assertEquals(listOf(10L, 20L), h.queue.ids())
    }

    @Test
    fun `未播曲目不足2首时自动拉取并过滤已入队歌曲`() {
        val h = harness(
            ids = listOf(1, 2, 3),
            startIndex = 1, // upcomingCount = 1 (< 2)
            fmHandler = { Result.success(listOf(track(1), track(3), track(4), track(4), track(5))) }
        )
        h.queue.setPlayContext(PersonalFmController.CONTEXT_PERSONAL_FM)

        h.controller.prefetchOnPlay(2)

        assertEquals(listOf(PersonalFmMode.DEFAULT), h.repository.fmCalls)
        assertEquals(listOf(1L, 2L, 3L, 4L, 5L), h.queue.ids())
    }

    @Test
    fun `未播曲目充足时不触发预取`() {
        val h = harness(
            ids = listOf(1, 2, 3, 4),
            startIndex = 1, // upcomingCount = 2 (>= 2)
            fmHandler = { Result.success(listOf(track(9))) }
        )
        h.queue.setPlayContext(PersonalFmController.CONTEXT_PERSONAL_FM)

        h.controller.prefetchOnPlay(2)

        assertEquals(0, h.repository.fmCalls.size)
        assertEquals(listOf(1L, 2L, 3L, 4L), h.queue.ids())
    }

    @Test
    fun `切换模式持久化偏好并清空未播曲目重新拉取且不打断当前曲`() = runBlocking {
        val h = harness(
            ids = listOf(1, 2, 3, 4),
            startIndex = 1,
            fmHandler = { mode ->
                if (mode == PersonalFmMode.NIGHT_EMO) {
                    Result.success(listOf(track(201), track(202)))
                } else {
                    Result.success(emptyList())
                }
            }
        )
        h.queue.setPlayContext(PersonalFmController.CONTEXT_PERSONAL_FM)

        h.controller.switchMode(PersonalFmMode.NIGHT_EMO)

        assertEquals(PersonalFmMode.NIGHT_EMO, h.settings.personalFmMode.first())
        assertEquals(PersonalFmMode.NIGHT_EMO, h.controller.currentMode.value)
        assertEquals(listOf(PersonalFmMode.NIGHT_EMO), h.repository.fmCalls)
        assertEquals(1, h.queue.currentIndex.value)
        assertEquals(2L, h.queue.currentItem()?.songId)
        assertEquals(listOf(1L, 2L, 201L, 202L), h.queue.ids())
    }

    @Test
    fun `不喜欢接口失败也立即切下一首并移出当前曲目且余量不足时补拉`() {
        val h = harness(
            ids = listOf(1, 2),
            startIndex = 0, // upcomingCount = 1, removeAt 后变为 0 -> 触发补拉
            fmHandler = { Result.success(listOf(track(3), track(4))) },
            trashHandler = { _, _ -> Result.failure(IllegalStateException("network error")) }
        )
        h.queue.setPlayContext(PersonalFmController.CONTEXT_PERSONAL_FM)
        var playedIndex = -1

        h.controller.trashCurrent(playedSeconds = 12L) { playedIndex = it }

        assertEquals(listOf(1L to 12L), h.repository.trashCalls)
        assertEquals(0, playedIndex)
        assertEquals(listOf(2L, 3L, 4L), h.queue.ids())
    }

    @Test
    fun `队尾无余量时不喜欢会先拉新一批再移除当前曲目`() {
        val h = harness(
            ids = listOf(1, 2),
            startIndex = 1, // upcomingCount = 0
            fmHandler = { Result.success(listOf(track(3), track(4), track(5))) }
        )
        h.queue.setPlayContext(PersonalFmController.CONTEXT_PERSONAL_FM)
        var playedIndex = -1

        h.controller.trashCurrent(playedSeconds = 25L) { playedIndex = it }

        assertEquals(listOf(2L to 25L), h.repository.trashCalls)
        assertEquals(1, playedIndex)
        assertEquals(listOf(1L, 3L, 4L, 5L), h.queue.ids())
        assertEquals(3L, h.queue.currentItem()?.songId)
    }

    @Test
    fun `队尾临近播完时按进度兜底拉取下一批`() = runBlocking {
        val h = harness(
            ids = listOf(1, 2),
            startIndex = 1,
            fmHandler = { Result.success(listOf(track(8), track(9))) }
        )
        h.queue.setPlayContext(PersonalFmController.CONTEXT_PERSONAL_FM)

        h.controller.onProgressTick(songId = 2L, remainingMs = 10_000L)

        assertEquals(listOf(1L, 2L, 8L, 9L), h.queue.ids())
        assertEquals(1, h.repository.fmCalls.size)
    }

    @Test
    fun `非FM上下文下退出与不喜欢不产生变化`() {
        val h = harness(ids = listOf(1, 2), startIndex = 0)
        h.queue.setPlayContext(null)
        var playedIndex = -1

        h.controller.trashCurrent(10L) { playedIndex = it }
        h.controller.disable()

        assertEquals(-1, playedIndex)
        assertEquals(0, h.repository.trashCalls.size)
        assertNull(h.queue.playContext.value)
        assertEquals(listOf(1L, 2L), h.queue.ids())
    }

    @Test
    fun `切歌与拉取新一批FM曲目时记录最近3首且不足3首按实际保存`() = runBlocking {
        val h = harness(
            ids = listOf(1, 2),
            startIndex = 0,
            fmHandler = { mode ->
                if (mode == PersonalFmMode.EXPLORE) {
                    Result.success(listOf(track(3), track(4)))
                } else {
                    Result.success(emptyList())
                }
            }
        )
        h.queue.setPlayContext(PersonalFmController.CONTEXT_PERSONAL_FM)

        // 余量只有 1 首但 fmHandler 返回空：保存仅有的 2 首
        h.controller.prefetchOnPlay(1)
        assertEquals(
            listOf(1L, 2L),
            h.playbackPreferences.recentFmTracks.first().map { it.songId }
        )

        // 切换模式拉取新一批：队列变成 [1, 3, 4]，更新为最近 3 首
        h.controller.switchMode(PersonalFmMode.EXPLORE)
        assertEquals(
            listOf(1L, 3L, 4L),
            h.playbackPreferences.recentFmTracks.first().map { it.songId }
        )

        // 切到第 3 首时顺延更新
        h.queue.setCurrentIndex(1)
        h.controller.prefetchOnPlay(3)
        assertEquals(
            listOf(3L, 4L, 1L),
            h.playbackPreferences.recentFmTracks.first().map { it.songId }
        )
    }
}

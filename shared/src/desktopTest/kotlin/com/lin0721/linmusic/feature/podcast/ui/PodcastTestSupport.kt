package com.lin0721.linmusic.feature.podcast.ui

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import com.lin0721.linmusic.core.network.AppString
import com.lin0721.linmusic.core.network.ResourceProvider
import com.lin0721.linmusic.core.player.NowPlaying
import com.lin0721.linmusic.core.player.PlayMode
import com.lin0721.linmusic.core.player.PlaySource
import com.lin0721.linmusic.core.player.PlaybackController
import com.lin0721.linmusic.core.player.QueueItem
import com.lin0721.linmusic.feature.podcast.data.PodcastRepository
import com.lin0721.linmusic.feature.podcast.domain.PodcastCategory
import com.lin0721.linmusic.feature.podcast.domain.PodcastCategoryGroup
import com.lin0721.linmusic.feature.podcast.domain.PodcastPage
import com.lin0721.linmusic.feature.podcast.domain.PodcastProgram
import com.lin0721.linmusic.feature.podcast.domain.PodcastProgressEntry
import com.lin0721.linmusic.feature.podcast.domain.PodcastRadio
import com.lin0721.linmusic.feature.podcast.domain.PodcastRadioDetail
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flow

// 同步的内存 DataStore，让依赖偏好的 ViewModel 测试不碰 IO 线程
class InMemoryPreferencesStore : DataStore<Preferences> {
    private val state = MutableStateFlow(emptyPreferences())

    override val data: Flow<Preferences> = state

    override suspend fun updateData(transform: suspend (t: Preferences) -> Preferences): Preferences {
        val updated = transform(state.value)
        state.value = updated
        return updated
    }
}

val testResourceProvider = object : ResourceProvider() {
    override fun getString(key: AppString): String = "错误"
}

class FakePlaybackController : PlaybackController {

    data class QueueCall(
        val items: List<QueueItem>,
        val startIndex: Int,
        val playContext: String?,
        val startPositionMs: Long
    )

    val queueCalls = mutableListOf<QueueCall>()

    override val isPlaying: StateFlow<Boolean> = MutableStateFlow(false)
    override val playWhenReady: StateFlow<Boolean> = MutableStateFlow(false)
    override val nowPlaying: StateFlow<NowPlaying?> = MutableStateFlow(null)
    override val currentPosition: StateFlow<Long> = MutableStateFlow(0L)
    override val duration: StateFlow<Long> = MutableStateFlow(0L)
    override val sleepTimerRemaining: StateFlow<Long> = MutableStateFlow(0L)
    // 测试里直接改写这两个，模拟播放器切歌与切换播放上下文
    val mutablePlayContext = MutableStateFlow<String?>(null)
    val mutableQueueItem = MutableStateFlow<QueueItem?>(null)

    override val playContext: StateFlow<String?> = mutablePlayContext
    override val playSource: StateFlow<PlaySource?> = MutableStateFlow(null)
    override val currentIndex: StateFlow<Int> = MutableStateFlow(0)
    override val playMode: StateFlow<PlayMode> = MutableStateFlow(PlayMode.LIST_LOOP)
    override val queue: StateFlow<List<QueueItem>> = MutableStateFlow(emptyList())
    override val currentQueueItem: StateFlow<QueueItem?> = mutableQueueItem
    override val previousQueueItem: StateFlow<QueueItem?> = MutableStateFlow(null)
    override val nextQueueItem: StateFlow<QueueItem?> = MutableStateFlow(null)

    override suspend fun initController() = Unit

    override fun playQueue(
        items: List<QueueItem>,
        startIndex: Int,
        playContext: String?,
        source: PlaySource?,
        startPositionMs: Long
    ) {
        queueCalls += QueueCall(items, startIndex, playContext, startPositionMs)
    }

    override fun playAudio(
        songId: Long,
        url: String,
        title: String,
        artist: String,
        coverUrl: String,
        startPosition: Long,
        playContext: String?
    ) = Unit

    val playNextCalls = mutableListOf<List<QueueItem>>()

    override fun addToPlayNext(items: List<QueueItem>) {
        playNextCalls += items
    }

    val appendCalls = mutableListOf<List<QueueItem>>()

    override fun appendToQueue(items: List<QueueItem>) {
        appendCalls += items
    }
    override fun extendQueue(source: PlaySource, items: List<QueueItem>) = Unit
    override fun playNext() = Unit
    override fun playPrevious() = Unit
    override fun skipToPrevious() = Unit
    override fun playAtIndex(index: Int) = Unit
    override fun removeFromQueue(index: Int) = Unit
    override fun moveInQueue(from: Int, to: Int) = Unit
    override fun clearQueue() = Unit
    override fun toggleShuffle() = Unit
    override fun toggleRepeat() = Unit
    override fun rotatePlayMode() = Unit
    override fun pause() = Unit
    override fun resume() = Unit
    override fun togglePlayPause() = Unit
    override fun seekTo(positionMs: Long) = Unit
    override fun reloadCurrentTrack() = Unit
    override fun setSleepTimer(minutes: Int) = Unit
    override fun setPositionUpdateInterval(intervalMs: Long) = Unit
    override fun cancelPendingSkip(): Boolean = false
    override fun disableRoaming() = Unit
    override fun disableIntelligence() = Unit
}

// 各接口的结果都可替换，且可在结果里挂起以模拟慢响应；calls 记录调用顺序供断言
class FakePodcastRepository : PodcastRepository {

    val calls = mutableListOf<String>()

    var categories: suspend () -> Result<List<PodcastCategory>> = { Result.success(emptyList()) }
    var recommendPrograms: suspend (Long?) -> Result<List<PodcastProgram>> = { Result.success(emptyList()) }
    var personalizedRadios: suspend () -> Result<List<PodcastRadio>> = { Result.success(emptyList()) }
    var recommendRadios: suspend () -> Result<List<PodcastRadio>> = { Result.success(emptyList()) }
    var toplistRadios: suspend () -> Result<List<PodcastRadio>> = { Result.success(emptyList()) }
    var subscribedRadios: suspend (Int) -> Result<PodcastPage<PodcastRadio>> =
        { Result.success(PodcastPage(emptyList(), false)) }
    var categoryGroups: suspend () -> Result<List<PodcastCategoryGroup>> = { Result.success(emptyList()) }
    var categoryHotRadios: suspend (Long, Int) -> Result<PodcastPage<PodcastRadio>> =
        { _, _ -> Result.success(PodcastPage(emptyList(), false)) }
    var programToplist: suspend (Int) -> Result<PodcastPage<PodcastProgram>> =
        { Result.success(PodcastPage(emptyList(), false)) }
    var radioDetail: suspend (Long) -> Result<PodcastRadioDetail> = { Result.success(testRadioDetail(it)) }
    var setSubscribed: suspend (Long, Boolean) -> Result<Unit> = { _, _ -> Result.success(Unit) }
    var radioPrograms: suspend (Long, Int, Boolean) -> Result<List<PodcastProgram>> =
        { _, _, _ -> Result.success(emptyList()) }

    fun count(prefix: String): Int = calls.count { it.startsWith(prefix) }

    override fun getCategories() = record("categories") { categories() }

    override fun getRecommendPrograms(cateId: Long?) = record("recommendPrograms:$cateId") { recommendPrograms(cateId) }

    override fun getPersonalizedRadios() = record("personalized") { personalizedRadios() }

    override fun getRecommendRadios() = record("recommendRadios") { recommendRadios() }

    override fun getToplistRadios() = record("toplistRadios") { toplistRadios() }

    override fun getSubscribedRadios(offset: Int) = record("subscribed:$offset") { subscribedRadios(offset) }

    override fun getCategoryGroups() = record("categoryGroups") { categoryGroups() }

    override fun getCategoryHotRadios(cateId: Long, offset: Int) =
        record("categoryHot:$cateId:$offset") { categoryHotRadios(cateId, offset) }

    override fun getProgramToplist(offset: Int) = record("programToplist:$offset") { programToplist(offset) }

    override fun getRadioDetail(radioId: Long) = record("detail:$radioId") { radioDetail(radioId) }

    override fun setRadioSubscribed(radioId: Long, subscribe: Boolean) =
        record("subscribe:$radioId:$subscribe") { setSubscribed(radioId, subscribe) }

    override fun getRadioPrograms(radioId: Long, offset: Int, asc: Boolean) =
        record("programs:$radioId:$offset:$asc") { radioPrograms(radioId, offset, asc) }

    private fun <T> record(call: String, block: suspend () -> Result<T>): Flow<Result<T>> = flow {
        calls += call
        emit(block())
    }
}

fun testRadio(id: Long, lastProgramAt: Long = 0, name: String = "电台$id") = PodcastRadio(
    id = id,
    name = name,
    picUrl = "http://p/$id.jpg",
    programCount = 10,
    subCount = 100,
    djName = "主播",
    lastProgramCreateTimeMs = lastProgramAt
)

fun testProgram(id: Long, createTimeMs: Long = 0, songId: Long = id * 10) = PodcastProgram(
    id = id,
    songId = songId,
    name = "节目$id",
    coverUrl = "http://p/$id.jpg",
    durationMs = 1_000_000,
    createTimeMs = createTimeMs,
    listenerCount = 0,
    serialNum = id.toInt(),
    radioId = 7,
    radioName = "电台",
    djName = "主播"
)

fun testRadioDetail(id: Long, subscribed: Boolean = false) = PodcastRadioDetail(
    id = id,
    name = "电台$id",
    picUrl = "http://p/$id.jpg",
    desc = "",
    category = "",
    programCount = 10,
    subCount = 100,
    djName = "主播",
    djAvatarUrl = "",
    subscribed = subscribed
)

fun testProgress(
    songId: Long,
    positionMs: Long = 100_000,
    durationMs: Long = 1_000_000,
    updatedAtMs: Long = 0
) = PodcastProgressEntry(
    songId = songId,
    title = "节目$songId",
    subtitle = "电台 · 主播",
    coverUrl = "http://p/$songId.jpg",
    durationMs = durationMs,
    positionMs = positionMs,
    updatedAtMs = updatedAtMs
)

fun failure(message: String = "失败"): Result<Nothing> = Result.failure(IllegalStateException(message))

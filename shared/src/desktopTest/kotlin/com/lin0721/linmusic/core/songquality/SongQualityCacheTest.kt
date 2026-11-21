package com.lin0721.linmusic.core.songquality

import com.lin0721.linmusic.core.model.SongPrivilege
import com.lin0721.linmusic.core.model.QualityBadge
import com.lin0721.linmusic.core.model.SongFileInfo
import com.lin0721.linmusic.core.model.Track
import com.lin0721.linmusic.core.model.mergePrivilege
import com.lin0721.linmusic.core.model.mergePrivileges
import com.lin0721.linmusic.core.model.qualityBadgeOf
import com.lin0721.linmusic.core.player.QueueItem
import com.lin0721.linmusic.feature.podcast.ui.InMemoryPreferencesStore
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SongQualityCacheTest {

    private class FakeFetcher(var levels: Map<Long, String>) {
        val calls = mutableListOf<List<Long>>()
        var failNext = false

        suspend fun fetch(ids: List<Long>): Map<Long, String> {
            calls += ids
            if (failNext) {
                failNext = false
                error("网络错误")
            }
            return ids.mapNotNull { id -> levels[id]?.let { id to it } }.toMap()
        }
    }

    private fun TestScope.cache(fetcher: FakeFetcher, enabled: MutableStateFlow<Boolean>) =
        SongQualityCache(enabled, fetcher::fetch, backgroundScope, batchDelayMs = 300).also { runCurrent() }

    @Test
    fun badgeMapsDolbyImmersiveAndSurroundIndependently() {
        assertEquals(QualityBadge.Master, qualityBadgeOf("jymaster"))
        assertEquals(QualityBadge.Dolby, qualityBadgeOf("dolby"))
        assertEquals(QualityBadge.Immersive, qualityBadgeOf("sky"))
        assertEquals(QualityBadge.Surround, qualityBadgeOf("jyeffect"))
        assertEquals(QualityBadge.HiRes, qualityBadgeOf("hires"))
        assertEquals(QualityBadge.Lossless, qualityBadgeOf("lossless"))
        assertEquals(QualityBadge.ExHigh, qualityBadgeOf("exhigh"))
        assertEquals(QualityBadge.Standard, qualityBadgeOf("higher"))
        assertEquals(QualityBadge.Standard, qualityBadgeOf("standard"))
        assertNull(qualityBadgeOf(null))
        assertNull(qualityBadgeOf("unknown"))
    }

    @Test
    fun downloadedQualityOverridesOnlineAndUnknownFallsBack() {
        assertEquals(
            QualityBadge.ExHigh,
            resolveSongQualityBadge(
                enabled = true,
                songId = 10L,
                exactLevel = "jymaster",
                downloadedLevel = "exhigh"
            )
        )
        assertFalse(
            needsSongQualityLookup(
                songId = 10L,
                exactLevel = null,
                downloadedLevel = "lossless"
            )
        )
        // 下载记录 quality 为未知值时回退线上逻辑与补查
        assertEquals(
            QualityBadge.Master,
            resolveSongQualityBadge(
                enabled = true,
                songId = 10L,
                exactLevel = null,
                downloadedLevel = "unexpected",
                cachedLevel = "jymaster"
            )
        )
        assertTrue(
            needsSongQualityLookup(
                songId = 10L,
                exactLevel = null,
                downloadedLevel = "unexpected"
            )
        )
    }

    @Test
    fun localQualityDisplaysWithoutNeteaseIdAndRespectsSwitch() {
        assertEquals(
            QualityBadge.HiRes,
            resolveSongQualityBadge(
                enabled = true,
                songId = null,
                exactLevel = null,
                localLevel = "hires"
            )
        )
        assertNull(
            resolveSongQualityBadge(
                enabled = true,
                songId = 0L,
                exactLevel = null,
                localLevel = null
            )
        )
        assertNull(
            resolveSongQualityBadge(
                enabled = false,
                songId = null,
                exactLevel = null,
                localLevel = "lossless"
            )
        )
        assertFalse(needsSongQualityLookup(songId = null, exactLevel = null))
    }

    @Test
    fun onlineBadgeHiddenUntilExactOrCachedLevel() {
        assertTrue(needsSongQualityLookup(songId = 10L, exactLevel = null))
        assertNull(
            resolveSongQualityBadge(
                enabled = true,
                songId = 10L,
                exactLevel = null,
                cachedLevel = null
            )
        )
        assertEquals(
            QualityBadge.Master,
            resolveSongQualityBadge(
                enabled = true,
                songId = 10L,
                exactLevel = null,
                cachedLevel = "jymaster"
            )
        )
        assertEquals(
            QualityBadge.Immersive,
            resolveSongQualityBadge(
                enabled = true,
                songId = 10L,
                exactLevel = "sky",
                cachedLevel = null
            )
        )
        assertFalse(needsSongQualityLookup(songId = 10L, exactLevel = "sky"))
    }

    @Test
    fun mergePrivilegesFillsMissingWithoutOverwritingExisting() {
        val existingPrivilege = SongPrivilege(id = 1L, maxBrLevel = "sky")
        val tracks = listOf(
            Track(id = 1L, name = "A", privilege = existingPrivilege),
            Track(id = 2L, name = "B", privilege = null),
            Track(id = 3L, name = "C", privilege = null),
            Track(id = 0L, name = "Local", privilege = null)
        )
        val privileges = listOf(
            SongPrivilege(id = 1L, maxBrLevel = "lossless"),
            SongPrivilege(id = 2L, maxBrLevel = "jymaster"),
            SongPrivilege(id = 0L, maxBrLevel = "standard")
        )

        val merged = tracks.mergePrivileges(privileges)
        assertEquals("sky", merged[0].privilege?.maxBrLevel)
        assertEquals("jymaster", merged[1].privilege?.maxBrLevel)
        assertNull(merged[2].privilege)
        assertNull(merged[3].privilege)

        // 单首合并同样保留已有 privilege 并补全缺失项
        assertEquals("sky", tracks[0].mergePrivilege(privileges).privilege?.maxBrLevel)
        assertEquals("jymaster", tracks[1].mergePrivilege(privileges).privilege?.maxBrLevel)
    }

    @Test
    fun requestsInOneWindowAreMergedAndDeduplicated() = runTest {
        val fetcher = FakeFetcher(mapOf(1L to "hires", 2L to "lossless"))
        val cache = cache(fetcher, MutableStateFlow(true))

        cache.request(1)
        cache.request(2)
        cache.request(1)
        cache.request(3)
        advanceTimeBy(301)
        runCurrent()

        assertEquals(listOf(listOf(1L, 2L, 3L)), fetcher.calls)
        assertEquals(mapOf(1L to "hires", 2L to "lossless"), cache.levels.value)

        // 查过的（含没查到的）不再请求
        cache.request(1)
        cache.request(3)
        advanceTimeBy(301)
        runCurrent()
        assertEquals(1, fetcher.calls.size)
    }

    @Test
    fun disabledCacheSendsNoRequest() = runTest {
        val fetcher = FakeFetcher(mapOf(1L to "hires"))
        val cache = cache(fetcher, MutableStateFlow(false))

        cache.request(1)
        advanceTimeBy(301)
        runCurrent()

        assertTrue(fetcher.calls.isEmpty())
        assertTrue(cache.levels.value.isEmpty())
    }

    @Test
    fun invalidIdsAreIgnored() = runTest {
        val fetcher = FakeFetcher(emptyMap())
        val cache = cache(fetcher, MutableStateFlow(true))

        cache.request(0)
        cache.request(-5)
        advanceTimeBy(301)
        runCurrent()

        assertTrue(fetcher.calls.isEmpty())
    }

    @Test
    fun failedBatchCanBeRequestedAgain() = runTest {
        val fetcher = FakeFetcher(mapOf(1L to "jymaster")).apply { failNext = true }
        val cache = cache(fetcher, MutableStateFlow(true))

        cache.request(1)
        advanceTimeBy(301)
        runCurrent()
        assertTrue(cache.levels.value.isEmpty())

        cache.request(1)
        advanceTimeBy(301)
        runCurrent()
        assertEquals(2, fetcher.calls.size)
        assertEquals("jymaster", cache.levels.value[1L])
    }

    @Test
    fun largeBatchIsSplitByServerLimit() = runTest {
        val fetcher = FakeFetcher(emptyMap())
        val cache = cache(fetcher, MutableStateFlow(true))

        (1L..1500L).forEach(cache::request)
        advanceTimeBy(301)
        runCurrent()

        assertEquals(listOf(1000, 500), fetcher.calls.map { it.size })
    }

    @Test
    fun tracksNeedingPrefetchIncludesAllOnlineTracksMissingMaxBrLevel() {
        val file = SongFileInfo(br = 128_000)
        val tracks = listOf(
            Track(id = 1L, privilege = SongPrivilege(maxBrLevel = "lossless")),
            Track(id = 2L, sq = file),
            Track(id = 3L, h = SongFileInfo(br = 320_000)),
            Track(id = 4L, hr = file),
            Track(id = 5L),
            Track(id = 4L, hr = file),
            Track(id = 6L, privilege = SongPrivilege(maxBrLevel = "")),
            Track(id = 0L),
            Track(id = -2L)
        )

        assertEquals(listOf(2L, 3L, 4L, 5L, 6L), tracksNeedingQualityPrefetch(tracks))
    }

    @Test
    fun prefetchResolvesBeforeReturningSplitsBatchesAndSkipsDuplicates() = runTest {
        val fetcher = FakeFetcher(mapOf(10L to "jymaster", 12L to "sky", 20L to "sky"))
        val cache = SongQualityCache(
            enabledFlow = MutableStateFlow(true),
            fetcher = fetcher::fetch,
            scope = backgroundScope,
            batchDelayMs = 300
        )

        val tracks = listOf(
            Track(id = 10L, hr = SongFileInfo(br = 999_000)),
            Track(id = 11L, privilege = SongPrivilege(maxBrLevel = "lossless")),
            Track(id = 12L, sq = SongFileInfo(br = 128_000)),
            Track(id = 20L),
            Track(id = 10L)
        )
        cache.prefetch(tracks)

        assertEquals(listOf(listOf(10L, 12L, 20L)), fetcher.calls)
        assertEquals("jymaster", cache.levels.value[10L])
        assertEquals("sky", cache.levels.value[12L])
        assertEquals("sky", cache.levels.value[20L])

        // 已有缓存且未过期：prefetch 与 request 均不再触发网络请求
        cache.prefetch(tracks)
        cache.request(10L)
        advanceTimeBy(301)
        runCurrent()
        assertEquals(1, fetcher.calls.size)

        // 超过 1000 首时分批同步预查
        cache.prefetchIds((1001L..2500L).toList())
        assertEquals(listOf(3, 1000, 500), fetcher.calls.map { it.size })
    }

    @Test
    fun prefetchSeedsExactLevelsWithoutNetworkAndWarmQueueSkipsNonNetease() = runTest {
        val fetcher = FakeFetcher(mapOf(31L to "hires"))
        val store = InMemoryPreferencesStore()
        val cache = SongQualityCache(
            enabledFlow = MutableStateFlow(true),
            fetcher = fetcher::fetch,
            scope = backgroundScope,
            batchDelayMs = 300,
            dataStore = store
        )

        // 接口自带等级直接写入缓存，不发请求
        cache.prefetch(listOf(Track(id = 30L, privilege = SongPrivilege(maxBrLevel = "sky"))))
        assertTrue(fetcher.calls.isEmpty())
        assertEquals("sky", cache.levels.value[30L])

        // 重启后从持久化读回
        val restored = SongQualityCache(MutableStateFlow(true), fetcher::fetch, backgroundScope, dataStore = store)
        runCurrent()
        assertEquals("sky", restored.levels.value[30L])

        val queue = MutableStateFlow(
            listOf(
                QueueItem(30L, "a", "", ""),
                QueueItem(31L, "b", "", ""),
                QueueItem(32L, "c", "", "", localUri = "content://x"),
                QueueItem(33L, "d", "", "", radioId = 9L)
            )
        )
        cache.warmQueue(queue, backgroundScope)
        advanceTimeBy(501)
        runCurrent()
        assertEquals(listOf(listOf(31L)), fetcher.calls)
        assertEquals("hires", cache.levels.value[31L])
    }

    @Test
    fun prefetchNeverBlocksOnErrorAndAllowsRetry() = runTest {
        val fetcher = FakeFetcher(mapOf(50L to "dolby")).apply { failNext = true }
        val cache = cache(fetcher, MutableStateFlow(true))

        cache.prefetchIds(listOf(50L))
        assertTrue(cache.levels.value.isEmpty())

        cache.prefetchIds(listOf(50L))
        assertEquals(2, fetcher.calls.size)
        assertEquals("dolby", cache.levels.value[50L])
    }

    @Test
    fun longUnrefreshedEntriesArePrunedOnStartup() = runTest {
        var now = 1_000_000L
        val store = InMemoryPreferencesStore()
        val cache1 = SongQualityCache(
            enabledFlow = MutableStateFlow(true),
            fetcher = FakeFetcher(mapOf(1L to "hires"))::fetch,
            scope = backgroundScope,
            batchDelayMs = 300,
            dataStore = store,
            nowMs = { now }
        )
        cache1.prefetchIds(listOf(1L))
        assertEquals("hires", cache1.levels.value[1L])

        now += SONG_QUALITY_TTL_MS * 4
        val cache2 = SongQualityCache(
            enabledFlow = MutableStateFlow(false),
            fetcher = FakeFetcher(emptyMap())::fetch,
            scope = backgroundScope,
            batchDelayMs = 300,
            dataStore = store,
            nowMs = { now }
        )
        runCurrent()
        assertNull(cache2.levels.value[1L])
        assertTrue(store.data.first().asMap().isEmpty())
    }

    @Test
    fun persistedCacheSurvivesRestartWithSevenDayTtl() = runTest {
        var now = 1_000_000L
        val store = InMemoryPreferencesStore()
        val fetcher = FakeFetcher(mapOf(1L to "hires", 2L to "jymaster"))

        // 首次预查并写入持久化存储
        val cache1 = SongQualityCache(
            enabledFlow = MutableStateFlow(true),
            fetcher = fetcher::fetch,
            scope = backgroundScope,
            batchDelayMs = 300,
            dataStore = store,
            nowMs = { now }
        )
        cache1.prefetchIds(listOf(1L, 2L))
        assertEquals(1, fetcher.calls.size)

        // 重启后 3 天内直接复用本地持久化，不发网络请求
        now += 3L * 24 * 60 * 60 * 1000
        val fetcher2 = FakeFetcher(mapOf(1L to "jymaster", 2L to "sky"))
        val cache2 = SongQualityCache(
            enabledFlow = MutableStateFlow(true),
            fetcher = fetcher2::fetch,
            scope = backgroundScope,
            batchDelayMs = 300,
            dataStore = store,
            nowMs = { now }
        )
        runCurrent()
        assertEquals("hires", cache2.levels.value[1L])
        assertEquals("jymaster", cache2.levels.value[2L])

        cache2.prefetchIds(listOf(1L, 2L))
        cache2.request(1L)
        advanceTimeBy(301)
        runCurrent()
        assertTrue(fetcher2.calls.isEmpty())

        // 超过 7 天 TTL 后再重启：立即复用旧值不阻塞，同时后台静默刷新并更新时间戳
        now += SONG_QUALITY_TTL_MS
        val fetcher3 = FakeFetcher(mapOf(1L to "jymaster", 2L to "sky"))
        val cache3 = SongQualityCache(
            enabledFlow = MutableStateFlow(true),
            fetcher = fetcher3::fetch,
            scope = backgroundScope,
            batchDelayMs = 300,
            dataStore = store,
            nowMs = { now }
        )
        cache3.prefetchIds(listOf(1L, 2L))
        // prefetchIds 不阻塞于过期刷新
        assertTrue(fetcher3.calls.isEmpty())
        assertEquals("hires", cache3.levels.value[1L])

        // 后台刷新窗口结束后写入最新等级
        advanceTimeBy(301)
        runCurrent()
        assertEquals(listOf(listOf(1L, 2L)), fetcher3.calls)
        assertEquals("jymaster", cache3.levels.value[1L])
        assertEquals("sky", cache3.levels.value[2L])

        // 再次重启已刷新为最新，不再请求
        val fetcher4 = FakeFetcher(emptyMap())
        val cache4 = SongQualityCache(
            enabledFlow = MutableStateFlow(true),
            fetcher = fetcher4::fetch,
            scope = backgroundScope,
            batchDelayMs = 300,
            dataStore = store,
            nowMs = { now }
        )
        cache4.prefetchIds(listOf(1L, 2L))
        advanceTimeBy(301)
        runCurrent()
        assertTrue(fetcher4.calls.isEmpty())
        assertEquals("jymaster", cache4.levels.value[1L])
        assertEquals("sky", cache4.levels.value[2L])
    }

    @Test
    fun offlineSkipsNetworkWhileServingPersistedCache() = runTest {
        var now = 1_000_000L
        val store = InMemoryPreferencesStore()
        val seedFetcher = FakeFetcher(mapOf(7L to "lossless"))
        val seed = SongQualityCache(
            enabledFlow = MutableStateFlow(true),
            fetcher = seedFetcher::fetch,
            scope = backgroundScope,
            batchDelayMs = 300,
            dataStore = store,
            nowMs = { now }
        )
        seed.prefetchIds(listOf(7L))

        now += SONG_QUALITY_TTL_MS + 1000L
        var online = false
        val offlineFetcher = FakeFetcher(mapOf(7L to "jymaster", 8L to "dolby"))
        val offlineCache = SongQualityCache(
            enabledFlow = MutableStateFlow(true),
            fetcher = offlineFetcher::fetch,
            scope = backgroundScope,
            batchDelayMs = 300,
            dataStore = store,
            nowMs = { now },
            isOnline = { online }
        )
        offlineCache.prefetchIds(listOf(7L, 8L))
        offlineCache.request(8L)
        advanceTimeBy(301)
        runCurrent()

        assertTrue(offlineFetcher.calls.isEmpty())
        assertEquals("lossless", offlineCache.levels.value[7L])

        // 恢复联网后可补查
        online = true
        offlineCache.prefetchIds(listOf(8L))
        assertEquals("dolby", offlineCache.levels.value[8L])
    }

    @Test
    fun concurrentPrefetchAndRequestDeduplicateSameId() = runTest {
        val gate = CompletableDeferred<Unit>()
        val calls = mutableListOf<List<Long>>()
        val cache = SongQualityCache(
            enabledFlow = MutableStateFlow(true),
            fetcher = { ids ->
                calls += ids
                gate.await()
                ids.associateWith { "jymaster" }
            },
            scope = backgroundScope,
            batchDelayMs = 300
        )
        runCurrent()

        val job = launch { cache.prefetchIds(listOf(99L)) }
        runCurrent()
        cache.request(99L)
        advanceTimeBy(301)
        runCurrent()

        gate.complete(Unit)
        job.join()
        assertEquals(listOf(listOf(99L)), calls)
        assertEquals("jymaster", cache.levels.value[99L])
    }

    @Test
    fun prefetchTimesOutWithoutBlockingAndAllowsRetry() = runTest {
        val gate = CompletableDeferred<Unit>()
        var slow = true
        val cache = SongQualityCache(
            enabledFlow = MutableStateFlow(true),
            fetcher = { ids ->
                if (slow) gate.await()
                ids.associateWith { "hires" }
            },
            scope = backgroundScope,
            batchDelayMs = 300,
            prefetchTimeoutMs = 3_000
        )
        runCurrent()

        cache.prefetchIds(listOf(5L))
        assertNull(cache.levels.value[5L])

        slow = false
        cache.prefetchIds(listOf(5L))
        assertEquals("hires", cache.levels.value[5L])
    }
}

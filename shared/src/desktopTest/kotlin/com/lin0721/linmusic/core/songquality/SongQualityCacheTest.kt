package com.lin0721.linmusic.core.songquality

import com.lin0721.linmusic.core.model.QualityBadge
import com.lin0721.linmusic.core.model.SongFileInfo
import com.lin0721.linmusic.core.model.Track
import com.lin0721.linmusic.core.model.inferredMaxBrLevel
import com.lin0721.linmusic.core.model.isFinalInferredLevel
import com.lin0721.linmusic.core.model.qualityBadgeOf
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
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

    private class FakeFetcher(private val levels: Map<Long, String>) {
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
    fun badgeDowngradesUnplayableLevels() {
        assertEquals(QualityBadge.Master, qualityBadgeOf("jymaster"))
        assertEquals(QualityBadge.Lossless, qualityBadgeOf("dolby"))
        assertEquals(QualityBadge.Lossless, qualityBadgeOf("sky"))
        assertEquals(QualityBadge.Lossless, qualityBadgeOf("jyeffect"))
        assertEquals(QualityBadge.HiRes, qualityBadgeOf("hires"))
        assertEquals(QualityBadge.Lossless, qualityBadgeOf("lossless"))
        assertEquals(QualityBadge.ExHigh, qualityBadgeOf("exhigh"))
        assertEquals(QualityBadge.Standard, qualityBadgeOf("higher"))
        assertEquals(QualityBadge.Standard, qualityBadgeOf("standard"))
        assertNull(qualityBadgeOf(null))
        assertNull(qualityBadgeOf("unknown"))
    }

    @Test
    fun levelIsInferredFromFileTiers() {
        val file = SongFileInfo(br = 128_000)
        assertEquals("hires", Track(hr = file, sq = file, h = file).inferredMaxBrLevel())
        assertEquals("lossless", Track(sq = file, h = file).inferredMaxBrLevel())
        assertEquals("exhigh", Track(h = SongFileInfo(br = 320_000), l = file).inferredMaxBrLevel())
        assertEquals("standard", Track(h = SongFileInfo(br = 192_000)).inferredMaxBrLevel())
        assertEquals("standard", Track(l = file).inferredMaxBrLevel())
        assertNull(Track().inferredMaxBrLevel())
    }

    @Test
    fun onlyHiResInferenceNeedsLookup() {
        assertFalse(isFinalInferredLevel("hires"))
        assertFalse(isFinalInferredLevel(null))
        assertTrue(isFinalInferredLevel("lossless"))
        assertTrue(isFinalInferredLevel("exhigh"))
        assertTrue(isFinalInferredLevel("standard"))
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
}

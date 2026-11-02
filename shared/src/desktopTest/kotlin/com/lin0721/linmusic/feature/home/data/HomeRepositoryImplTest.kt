package com.lin0721.linmusic.feature.home.data

import com.lin0721.linmusic.core.auth.UserPreferences
import com.lin0721.linmusic.core.cache.testOfflineFallback
import com.lin0721.linmusic.core.contentfilter.ContentFilter
import com.lin0721.linmusic.core.model.EmptyBody
import com.lin0721.linmusic.feature.podcast.ui.InMemoryPreferencesStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

private class FakeHomeApi(
    private val dislike: (DislikeDailySongRequest) -> DislikeDailySongResponse
) : HomeApi {
    override suspend fun getHomeBlockPage(body: HomeBlockPageRequest): HomeBlockPageResponse = error("not used")
    override suspend fun getPersonalizedPlaylists(body: PersonalizedRequest): PersonalizedResponse = error("not used")
    override suspend fun getDailyRecommendSongs(body: EmptyBody): DailyRecommendSongsResponse = error("not used")
    override suspend fun dislikeDailySong(body: DislikeDailySongRequest): DislikeDailySongResponse = dislike(body)
    override suspend fun getHistoryRecommendDates(body: EmptyBody): HistoryDatesResponse = error("not used")
    override suspend fun getHistoryRecommendDetail(body: HistoryDetailRequest): HistoryDetailResponse = error("not used")
    override suspend fun getToplistDetail(body: EmptyBody): ToplistDetailResponse = error("not used")
}

class HomeRepositoryImplTest {

    private fun repository(api: HomeApi) = HomeRepositoryImpl(
        api,
        ContentFilter(UserPreferences(InMemoryPreferencesStore())),
        testOfflineFallback()
    )

    @Test
    fun `减少推荐请求携带歌曲id并固定资源类型与场景`() = runBlocking {
        val requests = mutableListOf<DislikeDailySongRequest>()
        val api = FakeHomeApi { req ->
            requests += req
            DislikeDailySongResponse(code = 200)
        }

        val result = repository(api).dislikeDailySong(1234L).first()

        assertTrue(result.isSuccess)
        assertEquals(listOf(DislikeDailySongRequest(resId = 1234L, resType = 4, sceneType = 1)), requests)
    }

    @Test
    fun `减少推荐请求体包含参考实现要求的三个字段`() {
        val json = Json { encodeDefaults = true }

        val body = json.encodeToString(DislikeDailySongRequest.serializer(), DislikeDailySongRequest(resId = 7L))

        assertEquals("""{"resId":7,"resType":4,"sceneType":1}""", body)
    }

    @Test
    fun `业务码非200时减少推荐返回失败`() = runBlocking {
        val api = FakeHomeApi { DislikeDailySongResponse(code = 301) }

        val result = repository(api).dislikeDailySong(1L).first()

        assertTrue(result.isFailure)
    }

    @Test
    fun `接口异常时减少推荐返回失败`() = runBlocking {
        val api = FakeHomeApi { throw RuntimeException("网络异常") }

        val result = repository(api).dislikeDailySong(1L).first()

        assertTrue(result.isFailure)
    }
}

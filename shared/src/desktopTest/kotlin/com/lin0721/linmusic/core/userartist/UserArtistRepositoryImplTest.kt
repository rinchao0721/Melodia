package com.lin0721.linmusic.core.userartist

import com.lin0721.linmusic.core.model.Artist
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

// 手写 Fake 代替真实网络请求
private class FakeUserArtistApi(
    private val sublist: () -> ArtistSublistResponse = { error("not used in this test") },
    private val topArtists: () -> TopArtistsResponse = { error("not used in this test") }
) : UserArtistApi {
    override suspend fun getArtistSublist(body: ArtistSublistRequest): ArtistSublistResponse = sublist()
    override suspend fun getTopArtists(body: TopArtistsRequest): TopArtistsResponse = topArtists()
}

class UserArtistRepositoryImplTest {

    private fun artist(id: Long, name: String) = Artist(id = id, name = name, picUrl = "pic/$id", img1v1Url = "avatar/$id")

    @Test
    fun `已关注歌手非空时直接使用已关注列表，不回退到热门歌手`() = runBlocking {
        val api = FakeUserArtistApi(
            sublist = { ArtistSublistResponse(code = 200, data = listOf(artist(1, "已关注歌手"))) },
            topArtists = { error("不应该调用热门歌手接口") }
        )
        val repo = UserArtistRepositoryImpl(api)

        val result = repo.getFavoriteArtists().first()

        assertTrue(result.isSuccess)
        assertEquals(1, result.getOrNull()?.size)
        assertEquals("已关注歌手", result.getOrNull()?.first()?.name)
    }

    @Test
    fun `已关注歌手为空时回退到热门歌手榜单`() = runBlocking {
        val api = FakeUserArtistApi(
            sublist = { ArtistSublistResponse(code = 200, data = emptyList()) },
            topArtists = { TopArtistsResponse(code = 200, artists = listOf(artist(2, "热门歌手"))) }
        )
        val repo = UserArtistRepositoryImpl(api)

        val result = repo.getFavoriteArtists().first()

        assertTrue(result.isSuccess)
        assertEquals("热门歌手", result.getOrNull()?.first()?.name)
    }

    @Test
    fun `已关注歌手接口异常时静默捕获并回退到热门歌手榜单`() = runBlocking {
        val api = FakeUserArtistApi(
            sublist = { throw RuntimeException("网络异常") },
            topArtists = { TopArtistsResponse(code = 200, artists = listOf(artist(3, "备用歌手"))) }
        )
        val repo = UserArtistRepositoryImpl(api)

        val result = repo.getFavoriteArtists().first()

        assertTrue(result.isSuccess)
        assertEquals("备用歌手", result.getOrNull()?.first()?.name)
    }

    @Test
    fun `已关注与热门歌手均为空时返回失败结果`() = runBlocking {
        val api = FakeUserArtistApi(
            sublist = { ArtistSublistResponse(code = 200, data = emptyList()) },
            topArtists = { TopArtistsResponse(code = 200, artists = emptyList()) }
        )
        val repo = UserArtistRepositoryImpl(api)

        val result = repo.getFavoriteArtists().first()

        assertTrue(result.isFailure)
    }

    @Test
    fun `热门歌手接口返回非成功状态码时返回失败结果`() = runBlocking {
        val api = FakeUserArtistApi(
            sublist = { ArtistSublistResponse(code = 200, data = emptyList()) },
            topArtists = { TopArtistsResponse(code = 400, artists = emptyList()) }
        )
        val repo = UserArtistRepositoryImpl(api)

        val result = repo.getFavoriteArtists().first()

        assertTrue(result.isFailure)
    }

    @Test
    fun `优先使用已关注歌手的头像信息(img1v1Url优先于picUrl)`() = runBlocking {
        val api = FakeUserArtistApi(
            sublist = {
                ArtistSublistResponse(
                    code = 200,
                    data = listOf(Artist(id = 1, name = "歌手", picUrl = "fallback.jpg", img1v1Url = "primary.jpg"))
                )
            }
        )
        val repo = UserArtistRepositoryImpl(api)

        val result = repo.getFavoriteArtists().first()

        assertEquals("primary.jpg", result.getOrNull()?.first()?.avatarUrl)
    }

    @Test
    fun `已关注歌手头像为空时回退使用picUrl`() = runBlocking {
        val api = FakeUserArtistApi(
            sublist = {
                ArtistSublistResponse(
                    code = 200,
                    data = listOf(Artist(id = 1, name = "歌手", picUrl = "fallback.jpg", img1v1Url = ""))
                )
            }
        )
        val repo = UserArtistRepositoryImpl(api)

        val result = repo.getFavoriteArtists().first()

        assertEquals("fallback.jpg", result.getOrNull()?.first()?.avatarUrl)
    }
}

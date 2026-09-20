package com.lin0721.linmusic.core.songlike

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SongLikeRepositoryTest {

    private class FakeSongLikeApi : SongLikeApi {
        var shouldSucceed = true
        var likedIds = listOf(101L, 102L)

        override suspend fun likeSong(body: LikeSongRequest): LikeSongResponse {
            if (!shouldSucceed) {
                return LikeSongResponse(code = 500)
            }
            return LikeSongResponse(code = 200)
        }

        override suspend fun getLikedSongIds(body: LikeSongListRequest): LikeSongListResponse {
            if (!shouldSucceed) {
                return LikeSongListResponse(code = 500)
            }
            return LikeSongListResponse(code = 200, ids = likedIds)
        }
    }

    @Test
    fun `getLikedSongIds 成功后更新 likedSongIds StateFlow`() = runTest {
        val fakeApi = FakeSongLikeApi()
        val repository = SongLikeRepositoryImpl(fakeApi)

        assertEquals(emptySet<Long>(), repository.likedSongIds.value)

        val result = repository.getLikedSongIds(12345L).first()
        assertTrue(result.isSuccess)
        assertEquals(setOf(101L, 102L), repository.likedSongIds.value)
    }

    @Test
    fun `likeSong 乐观添加并在失败时回滚`() = runTest {
        val fakeApi = FakeSongLikeApi()
        val repository = SongLikeRepositoryImpl(fakeApi)
        repository.syncLikedSongIds(setOf(101L))

        // 成功添加
        val successResult = repository.likeSong(103L, true).first()
        assertTrue(successResult.isSuccess)
        assertTrue(repository.likedSongIds.value.contains(103L))

        // 失败回滚
        fakeApi.shouldSucceed = false
        val failureResult = repository.likeSong(104L, true).first()
        assertTrue(failureResult.isFailure)
        assertFalse(repository.likedSongIds.value.contains(104L))
        assertTrue(repository.likedSongIds.value.contains(103L))
    }

    @Test
    fun `syncLikedSongIds 直接同步新集合`() = runTest {
        val fakeApi = FakeSongLikeApi()
        val repository = SongLikeRepositoryImpl(fakeApi)

        repository.syncLikedSongIds(setOf(201L, 202L))
        assertEquals(setOf(201L, 202L), repository.likedSongIds.value)
    }
}

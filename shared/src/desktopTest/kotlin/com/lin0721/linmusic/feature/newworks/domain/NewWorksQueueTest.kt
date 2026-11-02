package com.lin0721.linmusic.feature.newworks.domain

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NewWorksQueueTest {

    private fun track(id: Long) = NewWorksTrack(id = id, title = "歌$id", artistName = "歌手", coverUrl = "")

    private fun single(id: Long) = NewWorksRelease(
        id = id, title = "歌$id", coverUrl = "", artistName = "歌手", isAlbum = false,
        trackCount = 1, publishTime = 0, tracks = listOf(track(id))
    )

    private fun album(id: Long, vararg trackIds: Long) = NewWorksRelease(
        id = id, title = "专辑$id", coverUrl = "", artistName = "歌手", isAlbum = true,
        trackCount = trackIds.size, publishTime = 0, tracks = trackIds.map { track(it) }
    )

    private fun ids(tracks: List<NewWorksTrack>) = tracks.map { it.id }

    @Test
    fun `按列表顺序展开单曲与专辑`() = runBlocking {
        val releases = listOf(single(1), album(100, 11, 12), single(2))

        val result = NewWorksQueue.build(releases, emptySet()) { it.tracks }

        assertEquals(listOf(1L, 11L, 12L, 2L), ids(result))
    }

    @Test
    fun `没有内联曲目的专辑通过回调补拉并保持顺序`() = runBlocking {
        val bare = NewWorksRelease(
            id = 200, title = "专辑200", coverUrl = "", artistName = "歌手", isAlbum = true,
            trackCount = 2, publishTime = 0
        )
        val releases = listOf(single(1), bare, single(2))

        val result = NewWorksQueue.build(releases, emptySet()) { release ->
            if (release.id == 200L) listOf(track(21), track(22)) else release.tracks
        }

        assertEquals(listOf(1L, 21L, 22L, 2L), ids(result))
    }

    @Test
    fun `批内重复的歌曲只保留首次出现`() = runBlocking {
        val releases = listOf(single(1), album(100, 1, 2), single(2))

        val result = NewWorksQueue.build(releases, emptySet()) { it.tracks }

        assertEquals(listOf(1L, 2L), ids(result))
    }

    @Test
    fun `已在队列里的歌曲不再追加`() = runBlocking {
        val releases = listOf(single(1), single(2), single(3))

        val result = NewWorksQueue.build(releases, alreadyQueued = setOf(2L)) { it.tracks }

        assertEquals(listOf(1L, 3L), ids(result))
    }

    @Test
    fun `补拉失败的发布返回空曲目时不影响其余`() = runBlocking {
        val releases = listOf(single(1), album(100, 5), single(2))

        val result = NewWorksQueue.build(releases, emptySet()) { release ->
            if (release.id == 100L) emptyList() else release.tracks
        }

        assertEquals(listOf(1L, 2L), ids(result))
    }

    @Test
    fun `超过并发上限的发布也按原顺序展开`() = runBlocking {
        val releases = (1L..12L).map { single(it) }

        val result = NewWorksQueue.build(releases, emptySet()) { it.tracks }

        assertEquals((1L..12L).toList(), ids(result))
    }

    @Test
    fun `剩余曲目不足阈值时需要追加`() {
        assertTrue(NewWorksQueue.shouldExtend(queueSize = 10, currentIndex = 9))
        assertTrue(NewWorksQueue.shouldExtend(queueSize = 10, currentIndex = 6))
        assertFalse(NewWorksQueue.shouldExtend(queueSize = 10, currentIndex = 5))
    }

    @Test
    fun `没有当前曲目时不追加`() {
        assertFalse(NewWorksQueue.shouldExtend(queueSize = 0, currentIndex = -1))
    }
}

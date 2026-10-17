package com.lin0721.linmusic.feature.localmusic.data.batch

import com.lin0721.linmusic.core.model.Album
import com.lin0721.linmusic.core.model.Artist
import com.lin0721.linmusic.core.model.Track
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

class LocalMatchCoverRepositoryTest {
    private fun song(id: Long, album: Long = id, url: String = "") = Track(id, "Song $id", listOf(Artist(name = "Artist")), Album(album, "Album", url), dt = 120_000L)

    @Test fun enrichesOnlyPictureAndBatchesSameAlbum() = runTest {
        val calls = mutableListOf<List<Long>>()
        val repo = LocalMatchCoverRepository { ids -> calls += ids; Result.success(ids.map { song(it, 10, "cover").copy(name = "different", dt = 9L) }) }
        val original = listOf(song(1, 10), song(2, 10))
        val filled = repo.fillCovers(original)
        assertEquals(listOf(listOf(1L)), calls)
        assertEquals(original.map { it.name }, filled.map { it.name })
        assertEquals(original.map { it.dt }, filled.map { it.dt })
        assertTrue(filled.all { it.al.picUrl == "cover" })
        repo.fillCovers(original)
        assertEquals(1, calls.size)
    }

    @Test fun failedArtworkPreservesCandidateAndDoesNotSpam() = runTest {
        var calls = 0
        val repo = LocalMatchCoverRepository { calls++; Result.failure(IllegalStateException("offline")) }
        val original = listOf(song(1))
        assertEquals(original, repo.fillCovers(original))
        assertEquals(original, repo.fillCovers(original))
        assertEquals(1, calls)
    }

    @Test fun existingArtworkNeedsNoRequestAndEnrichesSameAlbum() = runTest {
        val repo = LocalMatchCoverRepository { error("unexpected network") }
        val result = repo.fillCovers(listOf(song(1, 10, "existing"), song(2, 10)))
        assertTrue(result.all { it.al.picUrl == "existing" })
    }

    @Test fun failedAlbumCoolsDownEveryCandidateInTheBatch() = runTest {
        var calls = 0
        val repo = LocalMatchCoverRepository { calls++; Result.failure(IllegalStateException("offline")) }
        val original = listOf(song(1, 10), song(2, 10))
        assertEquals(original, repo.fillCovers(original))
        assertEquals(listOf(song(2, 10)), repo.fillCovers(listOf(song(2, 10))))
        assertEquals(1, calls)
    }

    @Test fun independentAlbumsStaySeparate() = runTest {
        val repo = LocalMatchCoverRepository { ids -> Result.success(ids.map { song(it, it, "cover-$it") }) }
        val result = repo.fillCovers(listOf(song(1), song(2)))
        assertEquals(listOf("cover-1", "cover-2"), result.map { it.al.picUrl })
    }
}

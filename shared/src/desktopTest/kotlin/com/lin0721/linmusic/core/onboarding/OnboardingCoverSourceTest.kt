package com.lin0721.linmusic.core.onboarding

import com.lin0721.linmusic.core.model.Album
import com.lin0721.linmusic.core.model.PlaylistDetail
import com.lin0721.linmusic.core.model.Track
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.IOException

class OnboardingCoverSourceTest {

    @get:Rule
    val temp = TemporaryFolder()

    private fun track(id: Long, pic: String) = Track(id = id, al = Album(picUrl = pic))

    @Test
    fun extractCoverUrlsDedupesNormalizesAndLimits() {
        val detail = PlaylistDetail(
            tracks = listOf(
                track(1, "http://p1.music.126.net/a.jpg"),
                track(2, "https://p1.music.126.net/a.jpg?param=300y300"),
                track(3, ""),
                track(4, "https://p2.music.126.net/b.jpg"),
                track(5, "https://p2.music.126.net/c.jpg")
            )
        )
        val urls = OnboardingCoverSource.extractCoverUrls(detail, limit = 2)
        assertEquals(
            listOf(
                "https://p1.music.126.net/a.jpg?param=150y150",
                "https://p2.music.126.net/b.jpg?param=150y150"
            ),
            urls
        )
    }

    @Test
    fun loadFetchesOnceThenServesCache() = runTest {
        val file = temp.root.resolve("covers.json")
        var calls = 0
        val source = OnboardingCoverSource(file) { id ->
            calls++
            assertEquals(OnboardingCoverSource.HOT_SONGS_PLAYLIST_ID, id)
            PlaylistDetail(tracks = listOf(track(1, "https://p1.music.126.net/a.jpg")))
        }
        val first = source.load()
        val second = source.load()
        assertEquals(listOf("https://p1.music.126.net/a.jpg?param=150y150"), first)
        assertEquals(first, second)
        assertEquals(1, calls)
        assertTrue(file.isFile)
    }

    @Test
    fun loadReturnsEmptyWhenOfflineWithoutCache() = runTest {
        val file = temp.root.resolve("covers.json")
        val failing = OnboardingCoverSource(file) { throw IOException("offline") }
        assertEquals(emptyList<String>(), failing.load())
        val nullResult = OnboardingCoverSource(file) { null }
        assertEquals(emptyList<String>(), nullResult.load())
    }
}

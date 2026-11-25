package com.lin0721.linmusic.core.localmusic

import com.lin0721.linmusic.core.localmusic.data.LocalSearch
import com.lin0721.linmusic.core.localmusic.domain.LocalTrack
import com.lin0721.linmusic.core.localmusic.domain.LocalTrackSource
import com.lin0721.linmusic.core.localmusic.domain.buildLocalLibraryIndex
import org.junit.Assert.assertEquals
import org.junit.Test

class LocalSearchTest {

    @Test
    fun matchesCaseAndFullWidthInsensitivelyAndGroupsResults() {
        val tracks = listOf(
            LocalTrack(
                location = "/1.flac",
                mediaStoreId = 1,
                songId = null,
                title = "ＡＢＣ Song",
                artist = "Jay Chou",
                album = "Fantasy",
                durationMs = 180_000,
                sizeBytes = 1000,
                path = "/1.flac",
                dateAddedMs = 1,
                dateModifiedMs = 1,
                source = LocalTrackSource.EXTERNAL
            )
        )
        val index = buildLocalLibraryIndex(tracks)

        val byTitle = LocalSearch.search("abc", index)
        assertEquals(1, byTitle.songs.size)

        val byAlbum = LocalSearch.search("ＦＡＮＴＡＳＹ", index)
        assertEquals(1, byAlbum.songs.size)
        assertEquals(1, byAlbum.albums.size)

        val byArtist = LocalSearch.search("jay", index)
        assertEquals(1, byArtist.artists.size)
    }
}

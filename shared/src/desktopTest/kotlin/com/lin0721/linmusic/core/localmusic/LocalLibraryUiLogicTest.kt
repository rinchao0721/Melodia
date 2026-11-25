package com.lin0721.linmusic.core.localmusic

import com.lin0721.linmusic.core.localmusic.data.LocalSearch
import com.lin0721.linmusic.core.localmusic.domain.LocalTrack
import com.lin0721.linmusic.core.localmusic.domain.LocalTrackSource
import com.lin0721.linmusic.core.localmusic.domain.buildLocalLibraryIndex
import com.lin0721.linmusic.core.localmusic.ui.LocalFolderTree
import com.lin0721.linmusic.core.localmusic.ui.LocalTrackSortOrder
import com.lin0721.linmusic.core.localmusic.ui.filterDownloadTracks
import com.lin0721.linmusic.core.localmusic.ui.resolveResumePlayback
import com.lin0721.linmusic.core.localmusic.ui.sortLocalTracks
import com.lin0721.linmusic.core.player.QueueItem
import com.lin0721.linmusic.core.player.QueueState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalLibraryUiLogicTest {

    private fun track(
        location: String,
        title: String,
        artist: String = "Artist",
        album: String? = "Album",
        folder: String? = "/music",
        dateAddedMs: Long = 100L,
        sizeBytes: Long = 1000L,
        source: LocalTrackSource = LocalTrackSource.EXTERNAL
    ) = LocalTrack(
        location = location,
        mediaStoreId = 0L,
        title = title,
        artist = artist,
        album = album,
        durationMs = 180_000L,
        sizeBytes = sizeBytes,
        path = "$folder/$title.mp3",
        folder = folder,
        dateAddedMs = dateAddedMs,
        dateModifiedMs = dateAddedMs,
        source = source,
        songId = null
    )

    @Test
    fun sortLocalTracksOrdersCorrectly() {
        val a = track("1", "Beta", dateAddedMs = 100, sizeBytes = 300)
        val b = track("2", "alpha", dateAddedMs = 300, sizeBytes = 100)
        val c = track("3", "Gamma", dateAddedMs = 200, sizeBytes = 200)

        assertEquals(listOf("2", "3", "1"), sortLocalTracks(listOf(a, b, c), LocalTrackSortOrder.DATE_DESC).map { it.location })
        assertEquals(listOf("2", "1", "3"), sortLocalTracks(listOf(a, b, c), LocalTrackSortOrder.NAME_ASC).map { it.location })
        assertEquals(listOf("1", "3", "2"), sortLocalTracks(listOf(a, b, c), LocalTrackSortOrder.SIZE_DESC).map { it.location })
    }

    @Test
    fun folderTreeBuildsHierarchyAndResolvesParent() {
        val t1 = track("1", "Song1", folder = "/storage/Music")
        val t2 = track("2", "Song2", folder = "/storage/Music/Pop")
        val t3 = track("3", "Song3", folder = "/storage/Music/Pop/2024")
        val t4 = track("4", "Song4", folder = "/storage/Podcasts")

        val root = LocalFolderTree.buildTree(listOf(t1, t2, t3, t4))
        assertEquals(4, root.totalTrackCount)
        assertEquals(2, root.childFolders.size)
        assertEquals("/storage/Music", root.childFolders[0].path)
        assertEquals("/storage/Podcasts", root.childFolders[1].path)

        val popNode = LocalFolderTree.findNode(root, "/storage/Music/Pop")!!
        assertNotNull(popNode)
        assertEquals(2, popNode.totalTrackCount)
        assertEquals(1, popNode.tracks.size)
        assertEquals(1, popNode.childFolders.size)

        assertEquals("/storage/Music", LocalFolderTree.parentPathOf(root, "/storage/Music/Pop"))
        assertEquals("", LocalFolderTree.parentPathOf(root, "/storage/Music"))
    }

    @Test
    fun resolveResumePlaybackHandlesEmptyAndValidQueue() {
        assertNull(resolveResumePlayback(QueueState(emptyList(), 0, "", null)))

        val queue = QueueState(
            queue = listOf(
                QueueItem(songId = 1L, title = "第一首", artist = "歌手A", coverUrl = ""),
                QueueItem(songId = 2L, title = "第二首", artist = "歌手B", coverUrl = "")
            ),
            currentIndex = 1,
            playContext = "本地音乐",
            playSource = null
        )
        val info = resolveResumePlayback(queue)!!
        assertNotNull(info)
        assertEquals("第二首", info.trackTitle)
        assertEquals("歌手B", info.artist)
        assertEquals(2, info.itemCount)
        assertEquals(1, info.currentIndex)
    }

    @Test
    fun localSearchGroupsSongsAlbumsAndArtists() {
        val tracks = listOf(
            track("1", "晴天", artist = "周杰伦", album = "叶惠美"),
            track("2", "七里香", artist = "周杰伦", album = "七里香"),
            track("3", "富士山下", artist = "陈奕迅", album = "What's Going On...?")
        )
        val index = buildLocalLibraryIndex(tracks)
        val result = LocalSearch.search("周杰伦", index)
        assertEquals(2, result.songs.size)
        assertEquals(2, result.albums.size)
        assertEquals(1, result.artists.size)
        assertTrue(LocalSearch.search("不存在", index).isEmpty)
    }

    @Test
    fun filterDownloadTracksMatchesDownloadSources() {
        val tracks = listOf(
            track("1", "Ext", source = LocalTrackSource.EXTERNAL),
            track("2", "Dl", source = LocalTrackSource.DOWNLOAD),
            track("3", "MelodiaDl", source = LocalTrackSource.MELODIA_DOWNLOAD)
        )
        assertEquals(listOf("2", "3"), filterDownloadTracks(tracks).map { it.location })
    }
}

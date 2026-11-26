package com.lin0721.linmusic.core.localmusic

import androidx.room.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import com.lin0721.linmusic.core.localmusic.data.LocalLibraryRepository
import com.lin0721.linmusic.core.localmusic.data.LocalPlaylistRepository
import com.lin0721.linmusic.core.localmusic.db.LocalMusicDatabase
import com.lin0721.linmusic.core.localmusic.domain.LocalPlaylist
import com.lin0721.linmusic.core.localmusic.domain.LocalTrack
import com.lin0721.linmusic.core.localmusic.domain.LocalTrackSource
import com.lin0721.linmusic.core.localmusic.domain.buildLocalLibraryIndex
import com.lin0721.linmusic.core.localmusic.platform.LocalMediaEntry
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalLibraryIndexAndPlaylistTest {

    @Test
    fun aggregatesAlbumsAndArtistsAccordingToRules() {
        val tracks = listOf(
            LocalTrack(
                location = "/music/1.flac",
                mediaStoreId = 1,
                songId = null,
                title = "Track 1",
                artist = "周杰伦 / 费玉清",
                album = "依然范特西",
                albumArtist = "周杰伦",
                durationMs = 200_000,
                sizeBytes = 1000,
                path = "/music/1.flac",
                dateAddedMs = 100,
                dateModifiedMs = 100,
                source = LocalTrackSource.EXTERNAL
            ),
            LocalTrack(
                location = "/music/2.flac",
                mediaStoreId = 2,
                songId = null,
                title = "Track 2",
                artist = "周杰伦",
                album = "依然范特西",
                albumArtist = "周杰伦",
                durationMs = 200_000,
                sizeBytes = 1000,
                path = "/music/2.flac",
                dateAddedMs = 100,
                dateModifiedMs = 100,
                source = LocalTrackSource.EXTERNAL
            )
        )

        val index = buildLocalLibraryIndex(tracks)
        assertEquals(1, index.albums.size)
        assertEquals("周杰伦", index.albums.first().artist)
        assertEquals(2, index.artists.size) // 周杰伦、费玉清
    }

    @Test
    fun synthesizesFolderSourceMinusRemovedPlusManualTracks() = runTest {
        val db = Room.inMemoryDatabaseBuilder<LocalMusicDatabase>()
            .setDriver(BundledSQLiteDriver())
            .build()
        try {
            val libRepo = LocalLibraryRepository(db.localTrackDao())
            val plRepo = LocalPlaylistRepository(db.localPlaylistDao(), libRepo)

            libRepo.syncEntries(
                listOf(
                    entry("/folderA/1.mp3", "A1"),
                    entry("/folderA/2.mp3", "A2"),
                    entry("/folderB/3.mp3", "B3")
                )
            )

            val pid = plRepo.createPlaylist("Folder Mix")
            plRepo.addFolderSource(pid, "/folderA")
            plRepo.removeTrack(pid, "/folderA/2.mp3")
            plRepo.addTracks(pid, listOf("/folderB/3.mp3"))

            val playlists = plRepo.observePlaylists().first()
            assertEquals(1, playlists.size)
            assertEquals(
                listOf("/folderA/1.mp3", "/folderB/3.mp3"),
                playlists.first().tracks.map { it.location }
            )
        } finally {
            db.close()
        }
    }

    @Test
    fun likedPlaylistLazyCreatedOnceNewestFirstAndProtected() = runTest {
        val db = Room.inMemoryDatabaseBuilder<LocalMusicDatabase>()
            .setDriver(BundledSQLiteDriver())
            .build()
        try {
            val libRepo = LocalLibraryRepository(db.localTrackDao())
            val plRepo = LocalPlaylistRepository(db.localPlaylistDao(), libRepo)

            libRepo.syncEntries(
                listOf(
                    entry("/music/1.mp3", "Song 1"),
                    entry("/music/2.mp3", "Song 2"),
                    entry("/music/3.mp3", "Song 3")
                )
            )

            // 并发调用 ensureLikedPlaylist 仅创建一条系统歌单
            val ids = listOf(
                async { plRepo.ensureLikedPlaylist() },
                async { plRepo.ensureLikedPlaylist() },
                async { plRepo.ensureLikedPlaylist() }
            ).awaitAll()
            assertEquals(1, ids.distinct().size)
            val likedId = ids.first()

            // 新喜欢排最前
            assertTrue(plRepo.toggleLiked("/music/1.mp3"))
            assertTrue(plRepo.toggleLiked("/music/2.mp3"))
            plRepo.setLiked("/music/3.mp3", true)
            assertEquals(setOf("/music/1.mp3", "/music/2.mp3", "/music/3.mp3"), plRepo.likedLocations.first())

            var likedPlaylist = plRepo.observePlaylists().first().first { it.id == likedId }
            assertEquals(LocalPlaylist.SYSTEM_KIND_LIKED, likedPlaylist.systemKind)
            assertTrue(likedPlaylist.isLiked)
            assertEquals(
                listOf("/music/3.mp3", "/music/2.mp3", "/music/1.mp3"),
                likedPlaylist.tracks.map { it.location }
            )

            assertFalse(plRepo.toggleLiked("/music/2.mp3"))
            assertEquals(setOf("/music/1.mp3", "/music/3.mp3"), plRepo.likedLocations.first())

            // 系统歌单禁止改名、删除、加文件夹来源
            plRepo.renamePlaylist(likedId, "被篡改的名字")
            plRepo.addFolderSource(likedId, "/music")
            plRepo.deletePlaylist(likedId)

            likedPlaylist = plRepo.observePlaylists().first().first { it.id == likedId }
            assertEquals(LocalPlaylist.LIKED_PLAYLIST_NAME, likedPlaylist.name)
            assertTrue(likedPlaylist.folderSources.isEmpty())
            assertEquals(
                listOf("/music/3.mp3", "/music/1.mp3"),
                likedPlaylist.tracks.map { it.location }
            )
        } finally {
            db.close()
        }
    }

    private fun entry(path: String, title: String) = LocalMediaEntry(
        location = path,
        mediaStoreId = null,
        title = title,
        artist = "Tester",
        album = "TestAlbum",
        albumArtist = null,
        year = 2024,
        trackNumber = 1,
        durationMs = 180_000,
        sizeBytes = 1024,
        path = path,
        dateAddedMs = 1000,
        dateModifiedMs = 1000,
        source = LocalTrackSource.EXTERNAL,
        qualityLevel = "lossless"
    )

    @Test
    fun synthesizesSafTreeUriFolderSourceWithSubdirectoriesRemovedAndReAdd() = runTest {
        val db = Room.inMemoryDatabaseBuilder<LocalMusicDatabase>()
            .setDriver(BundledSQLiteDriver())
            .build()
        try {
            val libRepo = LocalLibraryRepository(db.localTrackDao())
            val plRepo = LocalPlaylistRepository(db.localPlaylistDao(), libRepo)

            val treeA = "content://com.android.externalstorage.documents/tree/primary%3AMusic"
            val treeB = "content://com.android.externalstorage.documents/tree/primary%3ADownloads"
            val rootTrack = "$treeA/document/primary%3AMusic%2F1.flac"
            val subTrack = "$treeA/document/primary%3AMusic%2FSub%2F2.mp3"
            val otherTreeTrack = "$treeB/document/primary%3ADownloads%2F3.mp3"

            libRepo.syncEntries(
                listOf(
                    safEntry(rootTrack, "RootTrack"),
                    safEntry(subTrack, "SubTrack"),
                    safEntry(otherTreeTrack, "OtherTrack")
                )
            )

            val pid = plRepo.createPlaylist("SAF Folder")
            plRepo.addFolderSource(pid, treeA)
            plRepo.removeTrack(pid, subTrack)
            plRepo.addTracks(pid, listOf(otherTreeTrack, rootTrack))

            val firstSnapshot = plRepo.observePlaylists().first().single()
            assertEquals(listOf(treeA), firstSnapshot.folderSources)
            assertEquals(listOf(rootTrack, otherTreeTrack), firstSnapshot.tracks.map { it.location })

            // 手动加回曾被移除的文件夹曲目应解除 removed
            plRepo.addTracks(pid, listOf(subTrack))
            val reAddedSnapshot = plRepo.observePlaylists().first().single()
            assertEquals(
                listOf(rootTrack, subTrack, otherTreeTrack),
                reAddedSnapshot.tracks.map { it.location }
            )

            // 移除文件夹来源后仅保留手动加入的曲目
            plRepo.removeFolderSource(pid, treeA)
            val afterSourceRemoved = plRepo.observePlaylists().first().single()
            assertEquals(emptyList<String>(), afterSourceRemoved.folderSources)
            assertEquals(
                listOf(otherTreeTrack, rootTrack, subTrack),
                afterSourceRemoved.tracks.map { it.location }
            )
        } finally {
            db.close()
        }
    }

    private fun safEntry(uri: String, title: String) = LocalMediaEntry(
        location = uri,
        mediaStoreId = null,
        title = title,
        artist = "Tester",
        album = "TestAlbum",
        albumArtist = null,
        year = 2024,
        trackNumber = 1,
        durationMs = 180_000,
        sizeBytes = 1024,
        path = null,
        dateAddedMs = 1000,
        dateModifiedMs = 1000,
        source = LocalTrackSource.IMPORTED,
        qualityLevel = "lossless",
        folder = null
    )
}

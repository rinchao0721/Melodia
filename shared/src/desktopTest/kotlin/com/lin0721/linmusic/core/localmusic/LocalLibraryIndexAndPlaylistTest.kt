package com.lin0721.linmusic.core.localmusic

import androidx.room.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import com.lin0721.linmusic.core.localmusic.data.LocalLibraryRepository
import com.lin0721.linmusic.core.localmusic.data.LocalPlaylistRepository
import com.lin0721.linmusic.core.localmusic.db.LocalMusicDatabase
import com.lin0721.linmusic.core.localmusic.domain.LocalTrack
import com.lin0721.linmusic.core.localmusic.domain.LocalTrackSource
import com.lin0721.linmusic.core.localmusic.domain.buildLocalLibraryIndex
import com.lin0721.linmusic.core.localmusic.platform.LocalMediaEntry
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
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
}

package com.lin0721.linmusic.core.localmusic

import androidx.room.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import com.lin0721.linmusic.core.localmusic.data.DesktopJsonImporter
import com.lin0721.linmusic.core.localmusic.data.LocalLibraryRepository
import com.lin0721.linmusic.core.localmusic.data.LocalPlaylistRepository
import com.lin0721.linmusic.core.localmusic.db.LocalMusicDatabase
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import kotlin.io.path.createTempDirectory

class DesktopJsonImporterTest {

    @Test
    fun importsJsonAndRenamesToBak() = runTest {
        val tempDir = createTempDirectory("melodia-import-test").toFile()
        try {
            val jsonFile = File(tempDir, "local_library.json")
            jsonFile.writeText(
                """
                {
                  "tracks": [
                    {
                      "path": "C:/Music/A/song1.flac",
                      "title": "Song 1",
                      "artist": "Artist A",
                      "album": "Album 1",
                      "durationMs": 200000,
                      "size": 1024000,
                      "lastModified": 1000,
                      "hasCover": true,
                      "songId": 999,
                      "matchState": "AUTO",
                      "quality": "lossless"
                    },
                    {
                      "path": "C:/Music/A/song2.mp3",
                      "title": "Song 2",
                      "artist": "Artist A",
                      "durationMs": 180000,
                      "size": 512000,
                      "lastModified": 2000
                    }
                  ],
                  "playlists": [
                    {
                      "id": 10,
                      "name": "My Folder Mix",
                      "createdAt": 5000,
                      "isPinned": true,
                      "lastPlayedAt": 6000,
                      "folders": ["C:/Music/A"],
                      "files": [],
                      "removed": ["C:/Music/A/song2.mp3"]
                    }
                  ],
                  "filter": {
                    "minDurationSec": 30,
                    "excludedFolders": ["C:/Music/Ignored"]
                  }
                }
                """.trimIndent(),
                Charsets.UTF_8
            )

            val db = Room.inMemoryDatabaseBuilder<LocalMusicDatabase>()
                .setDriver(BundledSQLiteDriver())
                .build()
            try {
                val importer = DesktopJsonImporter(db.localTrackDao(), db.localPlaylistDao())
                val result = importer.importIfNeeded(jsonFile)

                assertTrue(result.imported)
                assertEquals(30, result.filter?.minDurationSec)
                assertEquals(setOf("C:/Music/Ignored"), result.filter?.excludedFolders)
                assertFalse(jsonFile.exists())
                assertTrue(File(tempDir, "local_library.json.bak").isFile)

                val libRepo = LocalLibraryRepository(db.localTrackDao())
                val plRepo = LocalPlaylistRepository(db.localPlaylistDao(), libRepo)
                val playlists = plRepo.observePlaylists().first()
                assertEquals(1, playlists.size)
                assertTrue(playlists[0].isPinned)
                assertEquals(6000L, playlists[0].lastPlayedAt)
                assertEquals(listOf("C:/Music/A/song1.flac"), playlists[0].tracks.map { it.location })
            } finally {
                db.close()
            }
        } finally {
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun keepsOriginalFileWhenJsonIsCorrupted() = runTest {
        val tempDir = createTempDirectory("melodia-import-fail").toFile()
        try {
            val jsonFile = File(tempDir, "local_library.json")
            jsonFile.writeText("{ invalid json", Charsets.UTF_8)

            val db = Room.inMemoryDatabaseBuilder<LocalMusicDatabase>()
                .setDriver(BundledSQLiteDriver())
                .build()
            try {
                val importer = DesktopJsonImporter(db.localTrackDao(), db.localPlaylistDao())
                val result = importer.importIfNeeded(jsonFile)

                assertFalse(result.imported)
                assertTrue(jsonFile.isFile)
                assertFalse(File(tempDir, "local_library.json.bak").exists())
            } finally {
                db.close()
            }
        } finally {
            tempDir.deleteRecursively()
        }
    }
}

package com.lin0721.linmusic.core.localmusic

import androidx.room.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.sqlite.execSQL
import com.lin0721.linmusic.core.localmusic.db.LocalMusicDatabase
import com.lin0721.linmusic.core.localmusic.db.LocalMusicDatabaseFactory
import java.io.File
import java.nio.file.Files
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalMusicMigrationTest {

    @Test
    fun migration6To7PreservesTracksAndPlaylists() = runTest {
        val driver = BundledSQLiteDriver()
        val conn = driver.open(":memory:")

        // 手工建立 v6 表结构并写入测试数据
        conn.execSQL(
            """
            CREATE TABLE local_track (
                uri TEXT NOT NULL PRIMARY KEY,
                mediaStoreId INTEGER,
                songId INTEGER,
                title TEXT NOT NULL,
                artist TEXT NOT NULL,
                album TEXT,
                durationMs INTEGER NOT NULL,
                sizeBytes INTEGER NOT NULL,
                path TEXT,
                dateAddedMs INTEGER NOT NULL,
                dateModifiedMs INTEGER NOT NULL,
                source TEXT NOT NULL,
                albumArtist TEXT,
                year INTEGER,
                trackNumber INTEGER,
                qualityLevel TEXT
            )
            """.trimIndent()
        )
        conn.execSQL(
            """
            CREATE TABLE local_playlist (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                name TEXT NOT NULL,
                createdAt INTEGER NOT NULL,
                updatedAt INTEGER NOT NULL
            )
            """.trimIndent()
        )
        conn.execSQL(
            """
            CREATE TABLE local_playlist_track (
                playlistId INTEGER NOT NULL,
                trackUri TEXT NOT NULL,
                position INTEGER NOT NULL,
                PRIMARY KEY(playlistId, trackUri)
            )
            """.trimIndent()
        )

        conn.execSQL(
            """
            INSERT INTO local_track (
                uri, mediaStoreId, songId, title, artist, album,
                durationMs, sizeBytes, path, dateAddedMs, dateModifiedMs,
                source, albumArtist, year, trackNumber, qualityLevel
            ) VALUES (
                'content://media/external/audio/media/42', 42, 1001, '稻香', '周杰伦', '魔杰座',
                223000, 5242880, '/storage/emulated/0/Music/daoxiang.flac', 1700000000, 1700000100,
                'EXTERNAL', '周杰伦', 2008, 1001, 'lossless'
            )
            """.trimIndent()
        )
        conn.execSQL("INSERT INTO local_playlist (id, name, createdAt, updatedAt) VALUES (1, '我喜欢的', 1000, 2000)")
        conn.execSQL("INSERT INTO local_playlist_track (playlistId, trackUri, position) VALUES (1, 'content://media/external/audio/media/42', 0)")

        LocalMusicDatabase.MIGRATION_6_7.migrate(conn)

        // 验证 local_track 数据保留且 uri 已迁为 location
        conn.prepare("SELECT location, title, matchedSongId, matchState FROM local_track").use { stmt ->
            assertTrue(stmt.step())
            assertEquals("content://media/external/audio/media/42", stmt.getText(0))
            assertEquals("稻香", stmt.getText(1))
            assertEquals(1001L, stmt.getLong(2))
            assertEquals("MANUAL", stmt.getText(3))
        }

        // 验证 local_playlist_track 数据保留
        conn.prepare("SELECT playlistId, trackLocation, position FROM local_playlist_track").use { stmt ->
            assertTrue(stmt.step())
            assertEquals(1L, stmt.getLong(0))
            assertEquals("content://media/external/audio/media/42", stmt.getText(1))
            assertEquals(0L, stmt.getLong(2))
        }

        // 验证新表存在
        conn.prepare("SELECT name FROM sqlite_master WHERE type='table' AND name IN ('local_playlist_source', 'local_playlist_removed')").use { stmt ->
            val tables = mutableListOf<String>()
            while (stmt.step()) tables += stmt.getText(0)
            assertEquals(setOf("local_playlist_source", "local_playlist_removed"), tables.toSet())
        }

        conn.close()
    }

    // 用 v6 的真实建表语句与 identity hash 建库，再经 Room 打开，覆盖迁移后的 schema 校验
    @Test
    fun roomOpensV6DatabaseAndValidatesMigratedSchema() = runTest {
        val dir = Files.createTempDirectory("local_music_v6").toFile()
        try {
            val conn = BundledSQLiteDriver().open(File(dir, LocalMusicDatabase.NAME).absolutePath)
            listOf(
                "CREATE TABLE IF NOT EXISTS `local_track` (`uri` TEXT NOT NULL, `mediaStoreId` INTEGER, `songId` INTEGER, `title` TEXT NOT NULL, `artist` TEXT NOT NULL, `album` TEXT, `durationMs` INTEGER NOT NULL, `sizeBytes` INTEGER NOT NULL, `path` TEXT, `dateAddedMs` INTEGER NOT NULL, `dateModifiedMs` INTEGER NOT NULL, `source` TEXT NOT NULL, `albumArtist` TEXT, `year` INTEGER, `trackNumber` INTEGER, `qualityLevel` TEXT, PRIMARY KEY(`uri`))",
                "CREATE INDEX IF NOT EXISTS `index_local_track_artist` ON `local_track` (`artist`)",
                "CREATE INDEX IF NOT EXISTS `index_local_track_album` ON `local_track` (`album`)",
                "CREATE TABLE IF NOT EXISTS `local_playlist` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `name` TEXT NOT NULL, `createdAt` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL)",
                "CREATE TABLE IF NOT EXISTS `local_playlist_track` (`playlistId` INTEGER NOT NULL, `trackUri` TEXT NOT NULL, `position` INTEGER NOT NULL, PRIMARY KEY(`playlistId`, `trackUri`), FOREIGN KEY(`playlistId`) REFERENCES `local_playlist`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE , FOREIGN KEY(`trackUri`) REFERENCES `local_track`(`uri`) ON UPDATE NO ACTION ON DELETE CASCADE )",
                "CREATE INDEX IF NOT EXISTS `index_local_playlist_track_trackUri` ON `local_playlist_track` (`trackUri`)",
                "CREATE TABLE IF NOT EXISTS room_master_table (id INTEGER PRIMARY KEY,identity_hash TEXT)",
                "INSERT OR REPLACE INTO room_master_table (id,identity_hash) VALUES(42, '9f23afb8536894db9cbd07231217182d')",
                "INSERT INTO local_track VALUES ('content://media/external/audio/media/42', 42, NULL, '稻香', '周杰伦', '魔杰座', 223000, 5242880, '/sdcard/Music/a.flac', 1, 2, 'MEDIA_STORE', NULL, 2008, 1001, 'lossless')",
                "INSERT INTO local_playlist (id, name, createdAt, updatedAt) VALUES (1, '开车听', 1000, 2000)",
                "INSERT INTO local_playlist_track VALUES (1, 'content://media/external/audio/media/42', 0)",
                "PRAGMA user_version = 6"
            ).forEach { conn.execSQL(it) }
            conn.close()

            val db = LocalMusicDatabase.build(LocalMusicDatabaseFactory(dir).createBuilder())
            try {
                val track = db.localTrackDao().getByLocation("content://media/external/audio/media/42")
                assertNotNull(track)
                assertEquals("稻香", track!!.title)
                assertEquals(listOf("content://media/external/audio/media/42"), db.localPlaylistDao().trackLocations(1))
            } finally {
                db.close()
            }
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun roomAutoMigratesV7ToV8PreservingDataAndNullSystemKind() = runTest {
        val dir = Files.createTempDirectory("local_music_v7").toFile()
        try {
            val conn = BundledSQLiteDriver().open(File(dir, LocalMusicDatabase.NAME).absolutePath)
            listOf(
                "CREATE TABLE IF NOT EXISTS `local_track` (`location` TEXT NOT NULL, `mediaStoreId` INTEGER, `songId` INTEGER, `title` TEXT NOT NULL, `artist` TEXT NOT NULL, `album` TEXT, `durationMs` INTEGER NOT NULL, `sizeBytes` INTEGER NOT NULL, `path` TEXT, `dateAddedMs` INTEGER NOT NULL, `dateModifiedMs` INTEGER NOT NULL, `source` TEXT NOT NULL, `albumArtist` TEXT, `year` INTEGER, `trackNumber` INTEGER, `qualityLevel` TEXT, `matchState` TEXT NOT NULL, `matchedSongId` INTEGER, `hasEmbeddedCover` INTEGER NOT NULL, `folder` TEXT, PRIMARY KEY(`location`))",
                "CREATE INDEX IF NOT EXISTS `index_local_track_artist` ON `local_track` (`artist`)",
                "CREATE INDEX IF NOT EXISTS `index_local_track_album` ON `local_track` (`album`)",
                "CREATE INDEX IF NOT EXISTS `index_local_track_folder` ON `local_track` (`folder`)",
                "CREATE TABLE IF NOT EXISTS `local_playlist` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `name` TEXT NOT NULL, `createdAt` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL, `isPinned` INTEGER NOT NULL, `lastPlayedAt` INTEGER NOT NULL)",
                "CREATE TABLE IF NOT EXISTS `local_playlist_track` (`playlistId` INTEGER NOT NULL, `trackLocation` TEXT NOT NULL, `position` INTEGER NOT NULL, PRIMARY KEY(`playlistId`, `trackLocation`), FOREIGN KEY(`playlistId`) REFERENCES `local_playlist`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE , FOREIGN KEY(`trackLocation`) REFERENCES `local_track`(`location`) ON UPDATE NO ACTION ON DELETE CASCADE )",
                "CREATE INDEX IF NOT EXISTS `index_local_playlist_track_trackLocation` ON `local_playlist_track` (`trackLocation`)",
                "CREATE TABLE IF NOT EXISTS `local_playlist_source` (`playlistId` INTEGER NOT NULL, `folderPath` TEXT NOT NULL, PRIMARY KEY(`playlistId`, `folderPath`), FOREIGN KEY(`playlistId`) REFERENCES `local_playlist`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )",
                "CREATE INDEX IF NOT EXISTS `index_local_playlist_source_playlistId` ON `local_playlist_source` (`playlistId`)",
                "CREATE TABLE IF NOT EXISTS `local_playlist_removed` (`playlistId` INTEGER NOT NULL, `trackLocation` TEXT NOT NULL, PRIMARY KEY(`playlistId`, `trackLocation`), FOREIGN KEY(`playlistId`) REFERENCES `local_playlist`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )",
                "CREATE INDEX IF NOT EXISTS `index_local_playlist_removed_playlistId` ON `local_playlist_removed` (`playlistId`)",
                "CREATE TABLE IF NOT EXISTS room_master_table (id INTEGER PRIMARY KEY,identity_hash TEXT)",
                "INSERT OR REPLACE INTO room_master_table (id,identity_hash) VALUES(42, '9d26a2f7754a5ea5529aa4470b307450')",
                "INSERT INTO local_track VALUES ('/music/a.flac', NULL, 1001, '稻香', '周杰伦', '魔杰座', 223000, 5242880, '/music/a.flac', 1, 2, 'EXTERNAL', NULL, 2008, 1, 'lossless', 'MANUAL', 1001, 1, '/music')",
                "INSERT INTO local_playlist (id, name, createdAt, updatedAt, isPinned, lastPlayedAt) VALUES (1, '开车听', 1000, 2000, 1, 3000)",
                "INSERT INTO local_playlist_track VALUES (1, '/music/a.flac', 0)",
                "PRAGMA user_version = 7"
            ).forEach { conn.execSQL(it) }
            conn.close()

            val db = LocalMusicDatabase.build(LocalMusicDatabaseFactory(dir).createBuilder())
            try {
                val playlists = db.localPlaylistDao().getPlaylists()
                assertEquals(1, playlists.size)
                assertEquals("开车听", playlists.first().name)
                assertEquals(null, playlists.first().systemKind)
                assertEquals(true, playlists.first().isPinned)
                assertEquals(listOf("/music/a.flac"), db.localPlaylistDao().trackLocations(1))
            } finally {
                db.close()
            }
        } finally {
            dir.deleteRecursively()
        }
    }
}

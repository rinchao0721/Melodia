package com.lin0721.linmusic.core.localmusic.db

import androidx.room.AutoMigration
import androidx.room.ConstructedBy
import androidx.room.Database
import androidx.room.DeleteColumn
import androidx.room.RoomDatabase
import androidx.room.RoomDatabaseConstructor
import androidx.room.migration.AutoMigrationSpec
import androidx.room.migration.Migration
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.sqlite.execSQL
import kotlinx.coroutines.Dispatchers

@Database(
    entities = [
        LocalTrackEntity::class,
        LocalPlaylistEntity::class,
        LocalPlaylistTrackEntity::class,
        LocalPlaylistSourceEntity::class,
        LocalPlaylistRemovedEntity::class
    ],
    version = 7,
    exportSchema = true,
    autoMigrations = [
        AutoMigration(from = 1, to = 2),
        AutoMigration(from = 2, to = 3, spec = LocalMusicDatabase.DropAlbumIdSpec::class),
        AutoMigration(from = 3, to = 4),
        AutoMigration(from = 4, to = 5)
    ]
)
@ConstructedBy(LocalMusicDatabaseConstructor::class)
abstract class LocalMusicDatabase : RoomDatabase() {
    abstract fun localTrackDao(): LocalTrackDao
    abstract fun localPlaylistDao(): LocalPlaylistDao

    @DeleteColumn(tableName = "local_track", columnName = "albumId")
    class DropAlbumIdSpec : AutoMigrationSpec

    companion object {
        const val NAME = "local_music.db"

        val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(connection: SQLiteConnection) {
                connection.execSQL(
                    """
                    UPDATE local_track SET qualityLevel = NULL
                    WHERE qualityLevel IN ('standard', 'higher', 'exhigh')
                      AND (
                        lower(COALESCE(path, '')) LIKE '%.m4a'
                        OR lower(COALESCE(path, '')) LIKE '%.mp4'
                        OR lower(COALESCE(path, '')) LIKE '%.m4b'
                        OR lower(COALESCE(path, '')) LIKE '%.m4r'
                        OR lower(uri) LIKE '%.m4a%'
                        OR lower(uri) LIKE '%.mp4%'
                        OR lower(uri) LIKE '%.m4b%'
                        OR lower(uri) LIKE '%.m4r%'
                        OR path IS NULL
                      )
                    """.trimIndent()
                )
            }
        }

        // 6->7 将主键 uri 改为 location，重建外键表与新增歌单来源/移除表
        val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(connection: SQLiteConnection) {
                connection.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS local_track_new (
                        location TEXT NOT NULL PRIMARY KEY,
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
                        qualityLevel TEXT,
                        matchState TEXT NOT NULL DEFAULT 'PENDING',
                        matchedSongId INTEGER,
                        hasEmbeddedCover INTEGER NOT NULL DEFAULT 0,
                        folder TEXT
                    )
                    """.trimIndent()
                )
                connection.execSQL(
                    """
                    INSERT INTO local_track_new (
                        location, mediaStoreId, songId, title, artist, album,
                        durationMs, sizeBytes, path, dateAddedMs, dateModifiedMs,
                        source, albumArtist, year, trackNumber, qualityLevel,
                        matchState, matchedSongId, hasEmbeddedCover, folder
                    )
                    SELECT
                        uri, mediaStoreId, songId, title, artist, album,
                        durationMs, sizeBytes, path, dateAddedMs, dateModifiedMs,
                        source, albumArtist, year, trackNumber, qualityLevel,
                        CASE WHEN songId IS NOT NULL THEN 'MANUAL' ELSE 'PENDING' END,
                        songId, 0, NULL
                    FROM local_track
                    """.trimIndent()
                )
                connection.execSQL("DROP TABLE local_track")
                connection.execSQL("ALTER TABLE local_track_new RENAME TO local_track")
                connection.execSQL("CREATE INDEX IF NOT EXISTS index_local_track_artist ON local_track(artist)")
                connection.execSQL("CREATE INDEX IF NOT EXISTS index_local_track_album ON local_track(album)")
                connection.execSQL("CREATE INDEX IF NOT EXISTS index_local_track_folder ON local_track(folder)")

                connection.execSQL("ALTER TABLE local_playlist ADD COLUMN isPinned INTEGER NOT NULL DEFAULT 0")
                connection.execSQL("ALTER TABLE local_playlist ADD COLUMN lastPlayedAt INTEGER NOT NULL DEFAULT 0")

                connection.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS local_playlist_track_new (
                        playlistId INTEGER NOT NULL,
                        trackLocation TEXT NOT NULL,
                        position INTEGER NOT NULL,
                        PRIMARY KEY(playlistId, trackLocation),
                        FOREIGN KEY(playlistId) REFERENCES local_playlist(id) ON DELETE CASCADE,
                        FOREIGN KEY(trackLocation) REFERENCES local_track(location) ON DELETE CASCADE
                    )
                    """.trimIndent()
                )
                connection.execSQL(
                    """
                    INSERT INTO local_playlist_track_new (playlistId, trackLocation, position)
                    SELECT playlistId, trackUri, position FROM local_playlist_track
                    """.trimIndent()
                )
                connection.execSQL("DROP TABLE local_playlist_track")
                connection.execSQL("ALTER TABLE local_playlist_track_new RENAME TO local_playlist_track")
                connection.execSQL("CREATE INDEX IF NOT EXISTS index_local_playlist_track_trackLocation ON local_playlist_track(trackLocation)")

                connection.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS local_playlist_source (
                        playlistId INTEGER NOT NULL,
                        folderPath TEXT NOT NULL,
                        PRIMARY KEY(playlistId, folderPath),
                        FOREIGN KEY(playlistId) REFERENCES local_playlist(id) ON DELETE CASCADE
                    )
                    """.trimIndent()
                )
                connection.execSQL("CREATE INDEX IF NOT EXISTS index_local_playlist_source_playlistId ON local_playlist_source(playlistId)")

                connection.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS local_playlist_removed (
                        playlistId INTEGER NOT NULL,
                        trackLocation TEXT NOT NULL,
                        PRIMARY KEY(playlistId, trackLocation),
                        FOREIGN KEY(playlistId) REFERENCES local_playlist(id) ON DELETE CASCADE
                    )
                    """.trimIndent()
                )
                connection.execSQL("CREATE INDEX IF NOT EXISTS index_local_playlist_removed_playlistId ON local_playlist_removed(playlistId)")
            }
        }

        fun build(builder: Builder<LocalMusicDatabase>): LocalMusicDatabase =
            builder
                .setDriver(BundledSQLiteDriver())
                .setQueryCoroutineContext(Dispatchers.IO)
                .addMigrations(MIGRATION_5_6, MIGRATION_6_7)
                .build()
    }
}

@Suppress("NO_ACTUAL_FOR_EXPECT")
expect object LocalMusicDatabaseConstructor : RoomDatabaseConstructor<LocalMusicDatabase> {
    override fun initialize(): LocalMusicDatabase
}

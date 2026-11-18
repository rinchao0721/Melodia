package com.lin0721.linmusic.feature.localmusic.data.db

import androidx.room.AutoMigration
import androidx.room.Database
import androidx.room.DeleteColumn
import androidx.room.migration.AutoMigrationSpec
import androidx.room.RoomDatabase

@Database(
    entities = [LocalTrackEntity::class, LocalPlaylistEntity::class, LocalPlaylistTrackEntity::class],
    version = 5,
    exportSchema = true,
    autoMigrations = [
        AutoMigration(from = 1, to = 2),
        AutoMigration(from = 2, to = 3, spec = LocalMusicDatabase.DropAlbumIdSpec::class),
        AutoMigration(from = 3, to = 4),
        AutoMigration(from = 4, to = 5)
    ]
)
abstract class LocalMusicDatabase : RoomDatabase() {
    abstract fun localTrackDao(): LocalTrackDao
    abstract fun localPlaylistDao(): LocalPlaylistDao

    // MediaStore 的 album_id 按"专辑名 + 所在目录"生成，同一张专辑分散在两个目录会得到两个 id，改按专辑名聚合后不再需要
    @DeleteColumn(tableName = "local_track", columnName = "albumId")
    class DropAlbumIdSpec : AutoMigrationSpec

    companion object {
        const val NAME = "local_music.db"
    }
}

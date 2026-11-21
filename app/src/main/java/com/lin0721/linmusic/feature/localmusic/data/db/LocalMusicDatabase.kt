package com.lin0721.linmusic.feature.localmusic.data.db

import androidx.room.AutoMigration
import androidx.room.Database
import androidx.room.DeleteColumn
import androidx.room.RoomDatabase
import androidx.room.migration.AutoMigrationSpec
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [LocalTrackEntity::class, LocalPlaylistEntity::class, LocalPlaylistTrackEntity::class],
    version = 6,
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

        // 旧版未探测容器编码，升级后清空 m4a/mp4 等可能被误判为有损的缓存档位以在下次同步时重扫
        val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
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
    }
}

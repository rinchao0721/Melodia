package com.lin0721.linmusic.core.localmusic.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

// SQLite 单条语句绑定参数上限 999，批量删除按此分片
private const val DELETE_CHUNK_SIZE = 500

@Dao
interface LocalTrackDao {

    @Query("SELECT * FROM local_track")
    fun observeAll(): Flow<List<LocalTrackEntity>>

    @Query("SELECT * FROM local_track")
    suspend fun getAll(): List<LocalTrackEntity>

    @Query("SELECT * FROM local_track WHERE location = :location")
    suspend fun getByLocation(location: String): LocalTrackEntity?

    @Query("SELECT location FROM local_track")
    suspend fun getAllLocations(): List<String>

    @Query("SELECT COUNT(*) FROM local_track")
    suspend fun count(): Int

    @Upsert
    suspend fun upsert(tracks: List<LocalTrackEntity>)

    @Upsert
    suspend fun upsertOne(track: LocalTrackEntity)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIgnoringExisting(tracks: List<LocalTrackEntity>): List<Long>

    // 用 substr 比较前缀：uri 里的 %xx 编码会被 LIKE 当成通配符
    @Query("SELECT COUNT(*) FROM local_track WHERE source = 'IMPORTED' AND substr(location, 1, length(:prefix)) = :prefix")
    suspend fun countImportedWithPrefix(prefix: String): Int

    @Query("DELETE FROM local_track WHERE source = 'IMPORTED' AND substr(location, 1, length(:prefix)) = :prefix")
    suspend fun deleteImportedWithPrefix(prefix: String)

    @Query("DELETE FROM local_track WHERE location IN (:locations)")
    suspend fun deleteByLocationsChunk(locations: List<String>)

    @Transaction
    suspend fun deleteByLocations(locations: Collection<String>) {
        locations.chunked(DELETE_CHUNK_SIZE).forEach { deleteByLocationsChunk(it) }
    }

    @Transaction
    suspend fun applySync(upserts: List<LocalTrackEntity>, deleteLocations: Collection<String>) {
        if (deleteLocations.isNotEmpty()) deleteByLocations(deleteLocations)
        if (upserts.isNotEmpty()) upsert(upserts)
    }

    @Query("UPDATE local_track SET matchState = :matchState, matchedSongId = :matchedSongId, songId = :matchedSongId WHERE location = :location")
    suspend fun updateMatch(location: String, matchState: String, matchedSongId: Long?)
}

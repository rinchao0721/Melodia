package com.lin0721.linmusic.core.localmusic.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface LocalPlaylistDao {

    @Query("SELECT * FROM local_playlist ORDER BY isPinned DESC, updatedAt DESC")
    fun observePlaylists(): Flow<List<LocalPlaylistEntity>>

    @Query("SELECT * FROM local_playlist ORDER BY isPinned DESC, updatedAt DESC")
    suspend fun getPlaylists(): List<LocalPlaylistEntity>

    @Query("SELECT * FROM local_playlist WHERE id = :id LIMIT 1")
    suspend fun getPlaylistById(id: Long): LocalPlaylistEntity?

    @Query("SELECT * FROM local_playlist WHERE systemKind = :systemKind LIMIT 1")
    suspend fun getBySystemKind(systemKind: String): LocalPlaylistEntity?

    @Query("SELECT * FROM local_playlist_track ORDER BY playlistId, position")
    fun observeEntries(): Flow<List<LocalPlaylistTrackEntity>>

    @Query("SELECT * FROM local_playlist_source ORDER BY playlistId")
    fun observeSources(): Flow<List<LocalPlaylistSourceEntity>>

    @Query("SELECT * FROM local_playlist_removed ORDER BY playlistId")
    fun observeRemoved(): Flow<List<LocalPlaylistRemovedEntity>>

    @Insert
    suspend fun insertPlaylist(playlist: LocalPlaylistEntity): Long

    @Query("UPDATE local_playlist SET name = :name, updatedAt = :now WHERE id = :id")
    suspend fun rename(id: Long, name: String, now: Long)

    @Query("UPDATE local_playlist SET updatedAt = :now WHERE id = :id")
    suspend fun touch(id: Long, now: Long)

    @Query("UPDATE local_playlist SET isPinned = :pinned, updatedAt = :now WHERE id = :id")
    suspend fun setPinned(id: Long, pinned: Boolean, now: Long)

    @Query("UPDATE local_playlist SET lastPlayedAt = :playedAt WHERE id = :id")
    suspend fun setLastPlayedAt(id: Long, playedAt: Long)

    @Query("DELETE FROM local_playlist WHERE id = :id")
    suspend fun deletePlaylist(id: Long)

    @Query("SELECT trackLocation FROM local_playlist_track WHERE playlistId = :playlistId")
    suspend fun trackLocations(playlistId: Long): List<String>

    @Query("SELECT COALESCE(MAX(position), -1) FROM local_playlist_track WHERE playlistId = :playlistId")
    suspend fun maxPosition(playlistId: Long): Int

    @Query("SELECT MIN(position) FROM local_playlist_track WHERE playlistId = :playlistId")
    suspend fun minPosition(playlistId: Long): Int?

    @Query("SELECT EXISTS(SELECT 1 FROM local_playlist_track WHERE playlistId = :playlistId AND trackLocation = :trackLocation)")
    suspend fun hasTrack(playlistId: Long, trackLocation: String): Boolean

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertEntries(entries: List<LocalPlaylistTrackEntity>)

    @Query("DELETE FROM local_playlist_track WHERE playlistId = :playlistId AND trackLocation = :trackLocation")
    suspend fun deleteEntry(playlistId: Long, trackLocation: String)

    @Query("DELETE FROM local_playlist_track WHERE playlistId = :playlistId")
    suspend fun clearEntries(playlistId: Long)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertSources(sources: List<LocalPlaylistSourceEntity>)

    @Query("DELETE FROM local_playlist_source WHERE playlistId = :playlistId AND folderPath = :folderPath")
    suspend fun deleteSource(playlistId: Long, folderPath: String)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertRemoved(removed: List<LocalPlaylistRemovedEntity>)

    @Query("DELETE FROM local_playlist_removed WHERE playlistId = :playlistId AND trackLocation = :trackLocation")
    suspend fun deleteRemoved(playlistId: Long, trackLocation: String)

    @Transaction
    suspend fun appendTracks(playlistId: Long, trackLocations: List<String>, now: Long): Int {
        val existing = trackLocations(playlistId).toHashSet()
        val toAdd = trackLocations.distinct().filterNot { it in existing }
        if (toAdd.isEmpty()) return 0
        val start = maxPosition(playlistId) + 1
        insertEntries(toAdd.mapIndexed { index, loc -> LocalPlaylistTrackEntity(playlistId, loc, start + index) })
        toAdd.forEach { deleteRemoved(playlistId, it) }
        touch(playlistId, now)
        return toAdd.size
    }

    @Transaction
    suspend fun removeTrack(playlistId: Long, trackLocation: String, now: Long) {
        deleteEntry(playlistId, trackLocation)
        insertRemoved(listOf(LocalPlaylistRemovedEntity(playlistId, trackLocation)))
        touch(playlistId, now)
    }

    @Transaction
    suspend fun prependTrack(playlistId: Long, trackLocation: String, now: Long): Boolean {
        if (hasTrack(playlistId, trackLocation)) return false
        val pos = (minPosition(playlistId) ?: 1) - 1
        insertEntries(listOf(LocalPlaylistTrackEntity(playlistId, trackLocation, pos)))
        deleteRemoved(playlistId, trackLocation)
        touch(playlistId, now)
        return true
    }

    @Transaction
    suspend fun removeEntryAndTouch(playlistId: Long, trackLocation: String, now: Long) {
        deleteEntry(playlistId, trackLocation)
        touch(playlistId, now)
    }

    @Transaction
    suspend fun replaceTracks(playlistId: Long, orderedLocations: List<String>, now: Long) {
        clearEntries(playlistId)
        insertEntries(orderedLocations.distinct().mapIndexed { index, loc -> LocalPlaylistTrackEntity(playlistId, loc, index) })
        touch(playlistId, now)
    }

    @Transaction
    suspend fun reorderPlaylists(orderedIds: List<Long>, now: Long) {
        orderedIds.forEachIndexed { index, id ->
            touch(id, now - index)
        }
    }
}

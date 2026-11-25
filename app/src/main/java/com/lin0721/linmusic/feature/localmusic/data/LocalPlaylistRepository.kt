package com.lin0721.linmusic.feature.localmusic.data

import com.lin0721.linmusic.core.localmusic.db.LocalPlaylistDao
import com.lin0721.linmusic.core.localmusic.db.LocalPlaylistEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

data class LocalPlaylistRecord(
    val id: Long,
    val name: String,
    val updatedAt: Long,
    val trackUris: List<String>
)

class LocalPlaylistRepository(private val dao: LocalPlaylistDao) {

    val playlists: Flow<List<LocalPlaylistRecord>> = combine(dao.observePlaylists(), dao.observeEntries()) { playlists, entries ->
        val urisByPlaylist = entries.groupBy({ it.playlistId }, { it.trackLocation })
        playlists.map { LocalPlaylistRecord(it.id, it.name, it.updatedAt, urisByPlaylist[it.id].orEmpty()) }
    }

    suspend fun create(name: String): Long {
        val now = System.currentTimeMillis()
        return dao.insertPlaylist(LocalPlaylistEntity(name = name.trim(), createdAt = now, updatedAt = now))
    }

    suspend fun rename(id: Long, name: String) = dao.rename(id, name.trim(), System.currentTimeMillis())

    suspend fun delete(id: Long) = dao.deletePlaylist(id)

    suspend fun addTracks(id: Long, trackUris: List<String>): Int = dao.appendTracks(id, trackUris, System.currentTimeMillis())

    suspend fun removeTrack(id: Long, trackUri: String) = dao.removeTrack(id, trackUri, System.currentTimeMillis())

    suspend fun replaceTracks(id: Long, orderedUris: List<String>) = dao.replaceTracks(id, orderedUris, System.currentTimeMillis())
}

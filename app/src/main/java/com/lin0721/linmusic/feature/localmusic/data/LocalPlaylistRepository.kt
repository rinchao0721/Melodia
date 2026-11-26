package com.lin0721.linmusic.feature.localmusic.data

import com.lin0721.linmusic.core.localmusic.data.LocalPlaylistRepository as SharedLocalPlaylistRepository
import com.lin0721.linmusic.core.localmusic.db.LocalPlaylistDao
import com.lin0721.linmusic.core.localmusic.db.LocalPlaylistEntity
import com.lin0721.linmusic.core.localmusic.domain.LocalPlaylist as SharedLocalPlaylist
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

data class LocalPlaylistRecord(
    val id: Long,
    val name: String,
    val updatedAt: Long,
    val trackUris: List<String>,
    val folderSources: List<String> = emptyList(),
    val removedUris: Set<String> = emptySet(),
    val isPinned: Boolean = false,
    val systemKind: String? = null
) {
    val isSystem: Boolean get() = systemKind != null
    val isLiked: Boolean get() = systemKind == SharedLocalPlaylist.SYSTEM_KIND_LIKED
}

class LocalPlaylistRepository(
    private val dao: LocalPlaylistDao,
    private val shared: SharedLocalPlaylistRepository
) {

    val likedLocations: Flow<Set<String>> = shared.likedLocations

    val playlists: Flow<List<LocalPlaylistRecord>> = combine(
        dao.observePlaylists(),
        dao.observeEntries(),
        dao.observeSources(),
        dao.observeRemoved()
    ) { playlists, entries, sources, removed ->
        val urisByPlaylist = entries.groupBy({ it.playlistId }, { it.trackLocation })
        val sourcesByPlaylist = sources.groupBy({ it.playlistId }, { it.folderPath })
        val removedByPlaylist = removed.groupBy({ it.playlistId }, { it.trackLocation })
            .mapValues { (_, locs) -> locs.toHashSet() }
        playlists.map {
            LocalPlaylistRecord(
                id = it.id,
                name = it.name,
                updatedAt = it.updatedAt,
                trackUris = urisByPlaylist[it.id].orEmpty(),
                folderSources = sourcesByPlaylist[it.id].orEmpty(),
                removedUris = removedByPlaylist[it.id].orEmpty(),
                isPinned = it.isPinned,
                systemKind = it.systemKind
            )
        }
    }

    suspend fun ensureLikedPlaylist(): Long = shared.ensureLikedPlaylist()

    suspend fun toggleLiked(location: String): Boolean = shared.toggleLiked(location)

    suspend fun create(name: String): Long {
        val now = System.currentTimeMillis()
        return dao.insertPlaylist(LocalPlaylistEntity(name = name.trim(), createdAt = now, updatedAt = now))
    }

    suspend fun rename(id: Long, name: String) = shared.renamePlaylist(id, name)

    suspend fun delete(id: Long) = shared.deletePlaylist(id)

    suspend fun addTracks(id: Long, trackUris: List<String>): Int = dao.appendTracks(id, trackUris, System.currentTimeMillis())

    suspend fun removeTrack(id: Long, trackUri: String) = dao.removeTrack(id, trackUri, System.currentTimeMillis())

    suspend fun replaceTracks(id: Long, orderedUris: List<String>) = dao.replaceTracks(id, orderedUris, System.currentTimeMillis())

    suspend fun addFolderSource(id: Long, folderUri: String) = shared.addFolderSource(id, folderUri)

    suspend fun removeFolderSource(id: Long, folderUri: String) = shared.removeFolderSource(id, folderUri)
}

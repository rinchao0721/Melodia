package com.lin0721.linmusic.core.localmusic.data

import com.lin0721.linmusic.core.localmusic.db.LocalPlaylistDao
import com.lin0721.linmusic.core.localmusic.db.LocalPlaylistEntity
import com.lin0721.linmusic.core.localmusic.db.LocalPlaylistSourceEntity
import com.lin0721.linmusic.core.localmusic.domain.LocalPlaylist
import com.lin0721.linmusic.core.localmusic.domain.LocalTrack
import com.lin0721.linmusic.core.localmusic.domain.folderPathOf
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

class LocalPlaylistRepository(
    private val playlistDao: LocalPlaylistDao,
    private val libraryRepository: LocalLibraryRepository,
    private val nowProvider: () -> Long = { System.currentTimeMillis() }
) {
    fun observePlaylists(): Flow<List<LocalPlaylist>> = combine(
        playlistDao.observePlaylists(),
        playlistDao.observeEntries(),
        playlistDao.observeSources(),
        playlistDao.observeRemoved(),
        libraryRepository.observeFilteredTracks()
    ) { playlists, entries, sources, removed, visibleTracks ->
        val tracksByLocation = visibleTracks.associateBy { it.location }
        val entriesByPlaylist = entries.groupBy { it.playlistId }
        val sourcesByPlaylist = sources.groupBy({ it.playlistId }, { it.folderPath })
        val removedByPlaylist = removed.groupBy({ it.playlistId }, { it.trackLocation })
            .mapValues { (_, locs) -> locs.toHashSet() }

        playlists.map { entity ->
            val folderPaths = sourcesByPlaylist[entity.id].orEmpty()
            val removedSet = removedByPlaylist[entity.id].orEmpty()
            val manualEntries = entriesByPlaylist[entity.id].orEmpty()

            val synthesized = ArrayList<LocalTrack>()
            val seen = HashSet<String>()

            if (folderPaths.isNotEmpty()) {
                val folderSet = folderPaths.toHashSet()
                for (track in visibleTracks) {
                    val trackFolder = track.folder ?: folderPathOf(track.path)
                    if (trackFolder != null && trackFolder in folderSet && track.location !in removedSet) {
                        if (seen.add(track.location)) {
                            synthesized += track
                        }
                    }
                }
            }

            for (entry in manualEntries) {
                val track = tracksByLocation[entry.trackLocation] ?: continue
                if (seen.add(track.location)) {
                    synthesized += track
                }
            }

            LocalPlaylist(
                id = entity.id,
                name = entity.name,
                createdAt = entity.createdAt,
                updatedAt = entity.updatedAt,
                isPinned = entity.isPinned,
                lastPlayedAt = entity.lastPlayedAt,
                folderSources = folderPaths,
                tracks = synthesized
            )
        }
    }

    suspend fun createPlaylist(name: String): Long {
        val now = nowProvider()
        return playlistDao.insertPlaylist(
            LocalPlaylistEntity(name = name.trim(), createdAt = now, updatedAt = now)
        )
    }

    suspend fun renamePlaylist(id: Long, name: String) {
        playlistDao.rename(id, name.trim(), nowProvider())
    }

    suspend fun deletePlaylist(id: Long) {
        playlistDao.deletePlaylist(id)
    }

    suspend fun setPinned(id: Long, pinned: Boolean) {
        playlistDao.setPinned(id, pinned, nowProvider())
    }

    suspend fun markPlayed(id: Long) {
        playlistDao.setLastPlayedAt(id, nowProvider())
    }

    suspend fun addTracks(playlistId: Long, locations: List<String>): Int =
        playlistDao.appendTracks(playlistId, locations, nowProvider())

    suspend fun removeTrack(playlistId: Long, location: String) {
        playlistDao.removeTrack(playlistId, location, nowProvider())
    }

    suspend fun replaceTracks(playlistId: Long, orderedLocations: List<String>) {
        playlistDao.replaceTracks(playlistId, orderedLocations, nowProvider())
    }

    suspend fun addFolderSource(playlistId: Long, folderPath: String) {
        playlistDao.insertSources(listOf(LocalPlaylistSourceEntity(playlistId, folderPath)))
        playlistDao.touch(playlistId, nowProvider())
    }

    suspend fun removeFolderSource(playlistId: Long, folderPath: String) {
        playlistDao.deleteSource(playlistId, folderPath)
        playlistDao.touch(playlistId, nowProvider())
    }
}

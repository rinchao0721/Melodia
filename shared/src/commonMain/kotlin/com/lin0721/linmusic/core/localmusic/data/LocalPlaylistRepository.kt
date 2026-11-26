package com.lin0721.linmusic.core.localmusic.data

import com.lin0721.linmusic.core.localmusic.db.LocalPlaylistDao
import com.lin0721.linmusic.core.localmusic.db.LocalPlaylistEntity
import com.lin0721.linmusic.core.localmusic.db.LocalPlaylistSourceEntity
import com.lin0721.linmusic.core.localmusic.domain.LocalPlaylist
import com.lin0721.linmusic.core.localmusic.domain.LocalTrack
import com.lin0721.linmusic.core.localmusic.domain.folderPathOf
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class LocalPlaylistRepository(
    private val playlistDao: LocalPlaylistDao,
    private val libraryRepository: LocalLibraryRepository,
    private val nowProvider: () -> Long = { System.currentTimeMillis() }
) {
    private val likedMutex = Mutex()

    val likedLocations: Flow<Set<String>> = combine(
        playlistDao.observePlaylists(),
        playlistDao.observeEntries()
    ) { playlists, entries ->
        val likedIds = playlists
            .filter { it.systemKind == LocalPlaylist.SYSTEM_KIND_LIKED }
            .mapTo(HashSet()) { it.id }
        if (likedIds.isEmpty()) {
            emptySet()
        } else {
            entries.asSequence()
                .filter { it.playlistId in likedIds }
                .mapTo(HashSet()) { it.trackLocation }
        }
    }.distinctUntilChanged()
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

            val synthesized = synthesizeLocalPlaylistTracks(
                visibleTracks = visibleTracks,
                tracksByLocation = tracksByLocation,
                folderSources = folderPaths,
                removedLocations = removedSet,
                manualTrackLocations = manualEntries.map { it.trackLocation }
            )

            LocalPlaylist(
                id = entity.id,
                name = entity.name,
                createdAt = entity.createdAt,
                updatedAt = entity.updatedAt,
                isPinned = entity.isPinned,
                lastPlayedAt = entity.lastPlayedAt,
                systemKind = entity.systemKind,
                folderSources = folderPaths,
                tracks = synthesized
            )
        }
    }

    suspend fun ensureLikedPlaylist(): Long = likedMutex.withLock {
        ensureLikedPlaylistLocked()
    }

    private suspend fun ensureLikedPlaylistLocked(): Long {
        playlistDao.getBySystemKind(LocalPlaylist.SYSTEM_KIND_LIKED)?.let { return it.id }
        val now = nowProvider()
        return playlistDao.insertPlaylist(
            LocalPlaylistEntity(
                name = LocalPlaylist.LIKED_PLAYLIST_NAME,
                createdAt = now,
                updatedAt = now,
                isPinned = true,
                systemKind = LocalPlaylist.SYSTEM_KIND_LIKED
            )
        )
    }

    suspend fun toggleLiked(location: String): Boolean = likedMutex.withLock {
        val likedId = ensureLikedPlaylistLocked()
        val now = nowProvider()
        if (playlistDao.hasTrack(likedId, location)) {
            playlistDao.removeEntryAndTouch(likedId, location, now)
            false
        } else {
            playlistDao.prependTrack(likedId, location, now)
            true
        }
    }

    suspend fun setLiked(location: String, liked: Boolean) = likedMutex.withLock {
        val likedId = ensureLikedPlaylistLocked()
        val now = nowProvider()
        if (liked) {
            playlistDao.prependTrack(likedId, location, now)
        } else if (playlistDao.hasTrack(likedId, location)) {
            playlistDao.removeEntryAndTouch(likedId, location, now)
        }
    }

    private suspend fun isSystemPlaylist(id: Long): Boolean =
        playlistDao.getPlaylistById(id)?.systemKind != null

    suspend fun createPlaylist(name: String): Long {
        val now = nowProvider()
        return playlistDao.insertPlaylist(
            LocalPlaylistEntity(name = name.trim(), createdAt = now, updatedAt = now)
        )
    }

    suspend fun renamePlaylist(id: Long, name: String) {
        if (isSystemPlaylist(id)) return
        playlistDao.rename(id, name.trim(), nowProvider())
    }

    suspend fun deletePlaylist(id: Long) {
        if (isSystemPlaylist(id)) return
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
        if (isSystemPlaylist(playlistId)) return
        playlistDao.insertSources(listOf(LocalPlaylistSourceEntity(playlistId, folderPath)))
        playlistDao.touch(playlistId, nowProvider())
    }

    suspend fun removeFolderSource(playlistId: Long, folderPath: String) {
        if (isSystemPlaylist(playlistId)) return
        playlistDao.deleteSource(playlistId, folderPath)
        playlistDao.touch(playlistId, nowProvider())
    }

    suspend fun reorderPlaylists(orderedIds: List<Long>) {
        if (orderedIds.isEmpty()) return
        playlistDao.reorderPlaylists(orderedIds, nowProvider())
    }
}

/**
 * 判断曲目是否归属于歌单的某个文件夹来源：
 * - SAF 树 URI（含 "/tree/"）：按 "{treeUri}/document/" 前缀匹配 track.location，覆盖树下全部子目录
 * - 本地目录路径（桌面端）：按规范化父目录路径精确匹配 track.folder
 */
fun matchesFolderSource(track: LocalTrack, folderSource: String): Boolean {
    val normalizedSource = folderSource.trimEnd('/', '\\')
    if (normalizedSource.startsWith("content://") && normalizedSource.contains("/tree/")) {
        return track.location.startsWith("$normalizedSource/document/")
    }
    val trackFolder = track.folder ?: folderPathOf(track.path) ?: return false
    return trackFolder.trimEnd('/', '\\') == normalizedSource
}

/**
 * 合成歌单曲目列表：先纳入文件夹来源下未在 removed 列表中的可见曲目，再按顺序追加手动加入的曲目并去重。
 */
fun synthesizeLocalPlaylistTracks(
    visibleTracks: List<LocalTrack>,
    tracksByLocation: Map<String, LocalTrack>,
    folderSources: List<String>,
    removedLocations: Set<String>,
    manualTrackLocations: List<String>
): List<LocalTrack> {
    val synthesized = ArrayList<LocalTrack>()
    val seen = HashSet<String>()

    if (folderSources.isNotEmpty()) {
        for (track in visibleTracks) {
            if (track.location !in removedLocations &&
                folderSources.any { source -> matchesFolderSource(track, source) }
            ) {
                if (seen.add(track.location)) {
                    synthesized += track
                }
            }
        }
    }

    for (location in manualTrackLocations) {
        val track = tracksByLocation[location] ?: continue
        if (seen.add(track.location)) {
            synthesized += track
        }
    }

    return synthesized
}

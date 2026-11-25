package com.lin0721.linmusic.core.localmusic.data

import com.lin0721.linmusic.core.localmusic.db.LocalTrackDao
import com.lin0721.linmusic.core.localmusic.db.LocalTrackEntity
import com.lin0721.linmusic.core.localmusic.db.toDomain
import com.lin0721.linmusic.core.localmusic.domain.LocalLibraryIndex
import com.lin0721.linmusic.core.localmusic.domain.LocalMatchState
import com.lin0721.linmusic.core.localmusic.domain.LocalScanFilter
import com.lin0721.linmusic.core.localmusic.domain.LocalTrack
import com.lin0721.linmusic.core.localmusic.domain.LocalTrackSource
import com.lin0721.linmusic.core.localmusic.domain.buildLocalLibraryIndex
import com.lin0721.linmusic.core.localmusic.domain.folderPathOf
import com.lin0721.linmusic.core.localmusic.platform.LocalMediaEntry
import com.lin0721.linmusic.core.localmusic.platform.LocalMediaSource
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update

class LocalLibraryRepository(
    private val trackDao: LocalTrackDao,
    private val mediaSource: LocalMediaSource? = null
) {
    private val _scanFilter = MutableStateFlow(LocalScanFilter())
    val scanFilter: StateFlow<LocalScanFilter> = _scanFilter.asStateFlow()

    fun updateScanFilter(transform: (LocalScanFilter) -> LocalScanFilter) {
        _scanFilter.update(transform)
    }

    fun observeAllTracks(): Flow<List<LocalTrack>> =
        trackDao.observeAll().map { entities -> entities.map { it.toDomain() } }

    fun observeFilteredTracks(): Flow<List<LocalTrack>> =
        combine(observeAllTracks(), _scanFilter) { tracks, filter ->
            tracks.filter { filter.accepts(it) }
        }

    fun observeIndex(): Flow<LocalLibraryIndex> =
        observeFilteredTracks().map { buildLocalLibraryIndex(it) }

    fun observeBySource(source: LocalTrackSource): Flow<List<LocalTrack>> =
        observeFilteredTracks().map { tracks -> tracks.filter { it.source == source } }

    suspend fun sync(): Int {
        val source = mediaSource ?: return 0
        val entries = source.scanAll()
        return syncEntries(entries)
    }

    suspend fun syncEntries(entries: List<LocalMediaEntry>): Int {
        val existingByLocation = trackDao.getAll().associateBy { it.location }
        val incomingLocations = HashSet<String>(entries.size)
        val upserts = ArrayList<LocalTrackEntity>(entries.size)

        for (entry in entries) {
            incomingLocations += entry.location
            val prev = existingByLocation[entry.location]
            val identityChanged = prev != null && (prev.title != entry.title || prev.artist != entry.artist)
            val prevState = prev?.matchState ?: LocalMatchState.PENDING.name
            val resetMatch = identityChanged && prevState in AUTO_MATCH_STATES

            val nextMatchState = if (resetMatch) LocalMatchState.PENDING.name else prevState
            val nextMatchedSongId = if (resetMatch) null else prev?.matchedSongId
            val nextSongId = if (resetMatch) null else prev?.songId

            val unchanged = prev != null &&
                prev.dateModifiedMs == entry.dateModifiedMs &&
                prev.sizeBytes == entry.sizeBytes &&
                prev.title == entry.title &&
                prev.artist == entry.artist &&
                prev.album == entry.album &&
                prev.qualityLevel == entry.qualityLevel &&
                prev.source == entry.source.name &&
                !resetMatch

            if (!unchanged) {
                upserts += LocalTrackEntity(
                    location = entry.location,
                    mediaStoreId = entry.mediaStoreId,
                    songId = nextSongId,
                    title = entry.title,
                    artist = entry.artist,
                    album = entry.album,
                    durationMs = entry.durationMs,
                    sizeBytes = entry.sizeBytes,
                    path = entry.path,
                    dateAddedMs = entry.dateAddedMs,
                    dateModifiedMs = entry.dateModifiedMs,
                    source = entry.source.name,
                    albumArtist = entry.albumArtist,
                    year = entry.year,
                    trackNumber = entry.trackNumber,
                    qualityLevel = entry.qualityLevel,
                    matchState = nextMatchState,
                    matchedSongId = nextMatchedSongId,
                    hasEmbeddedCover = entry.hasEmbeddedCover,
                    folder = entry.folder ?: folderPathOf(entry.path)
                )
            }
        }

        val toDelete = existingByLocation.keys.filter { it !in incomingLocations }
        trackDao.applySync(upserts, toDelete)
        return upserts.size
    }

    suspend fun updateMatch(location: String, matchState: LocalMatchState, matchedSongId: Long?) {
        trackDao.updateMatch(location, matchState.name, matchedSongId)
    }

    private companion object {
        val AUTO_MATCH_STATES = setOf(
            LocalMatchState.PENDING.name,
            LocalMatchState.AUTO.name,
            LocalMatchState.NOT_FOUND.name
        )
    }
}

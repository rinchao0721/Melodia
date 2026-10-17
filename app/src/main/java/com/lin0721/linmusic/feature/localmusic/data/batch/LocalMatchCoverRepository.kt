package com.lin0721.linmusic.feature.localmusic.data.batch

import com.lin0721.linmusic.core.model.Track
import com.lin0721.linmusic.feature.player.data.PlayerRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull

/** Batched, cached artwork enrichment. Artwork failure never changes a matching result. */
class LocalMatchCoverRepository(
    private val loadDetails: suspend (List<Long>) -> Result<List<Track>>
) {
    constructor(playerRepository: PlayerRepository) : this({ ids -> playerRepository.getSongDetails(ids).first() })

    private val mutex = Mutex()
    private val songCovers = LinkedHashMap<Long, String>()
    private val albumCovers = LinkedHashMap<Long, String>()
    private val failedUntil = LinkedHashMap<Long, Long>()

    suspend fun fillCovers(tracks: List<Track>): List<Track> = mutex.withLock {
        tracks.forEach { remember(it) }
        val missingTracks = tracks.filter { it.id > 0L && cover(it).isBlank() && (failedUntil[it.id] ?: 0L) <= System.currentTimeMillis() }
        val missing = missingTracks.distinctBy { if (it.al.id > 0L) "album:${it.al.id}" else "song:${it.id}" }
        for (part in missing.chunked(50)) {
            val details = try {
                withTimeoutOrNull(8_000L) { loadDetails(part.map { it.id }).getOrNull() }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { null }
            details.orEmpty().forEach { remember(it) }
            part.filter { cover(it).isBlank() }.forEach { failed ->
                missingTracks.filter { it.id == failed.id || (failed.al.id > 0L && it.al.id == failed.al.id) }
                    .forEach { failedUntil[it.id] = System.currentTimeMillis() + 60_000L }
            }
            while (failedUntil.size > 2_048) failedUntil.remove(failedUntil.keys.first())
        }
        // Use only the fetched picture. Candidate title, credits, album and duration remain authoritative.
        tracks.map { track -> cover(track).takeIf { it.isNotBlank() && it != track.al.picUrl }
            ?.let { track.copy(al = track.al.copy(picUrl = it)) } ?: track }
    }

    private fun cover(track: Track): String = track.al.picUrl.takeIf(String::isNotBlank)
        ?: songCovers[track.id] ?: albumCovers[track.al.id].orEmpty()

    private fun remember(track: Track) {
        val url = track.al.picUrl.takeIf(String::isNotBlank) ?: return
        songCovers[track.id] = url
        if (track.al.id > 0L) albumCovers[track.al.id] = url
        failedUntil.remove(track.id)
        while (songCovers.size > 1_024) songCovers.remove(songCovers.keys.first())
        while (albumCovers.size > 1_024) albumCovers.remove(albumCovers.keys.first())
        if (failedUntil.size > 2_048) failedUntil.entries.removeAll { it.value < System.currentTimeMillis() }
    }
}

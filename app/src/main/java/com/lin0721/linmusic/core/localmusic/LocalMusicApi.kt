package com.lin0721.linmusic.core.localmusic

import android.net.Uri
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

data class LocalSongMetadata(val title: String, val artist: String, val durationMs: Long, val album: String? = null)

// core 只依赖此接口，实现在 feature/localmusic
interface LocalMusicApi {

    /** Optional external sources; core does not depend on feature repositories or their storage. */
    val availabilityChanges: Flow<Unit> get() = flowOf(Unit)

    suspend fun knownPlayableIds(ids: Collection<Long>): Set<Long> = emptySet()

    suspend fun coverUriFor(sourceUri: Uri): Uri?

    suspend fun readLyrics(sourceUri: String): String?

    suspend fun preferredUri(songId: Long, title: String, artist: String, loadMetadata: suspend () -> LocalSongMetadata? = { null }): String? = null
}

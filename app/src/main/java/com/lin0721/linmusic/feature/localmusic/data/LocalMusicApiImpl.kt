package com.lin0721.linmusic.feature.localmusic.data

import android.net.Uri
import com.lin0721.linmusic.core.localmusic.LocalMusicApi
import com.lin0721.linmusic.core.localmusic.LocalSongMetadata
import com.lin0721.linmusic.feature.localmusic.data.lyrics.LocalLyricsReader

class LocalMusicApiImpl(
    private val coverArtCache: LocalCoverArtCache,
    private val lyricsReader: LocalLyricsReader,
    private val preferredAudio: PreferredLocalAudioRepository
) : LocalMusicApi {

    override val availabilityChanges = preferredAudio.changes

    override suspend fun knownPlayableIds(ids: Collection<Long>): Set<Long> = preferredAudio.knownPlayableIds(ids)

    override suspend fun coverUriFor(sourceUri: Uri): Uri? = coverArtCache.coverUriFor(sourceUri)

    override suspend fun readLyrics(sourceUri: String): String? = lyricsReader.read(sourceUri)

    override suspend fun preferredUri(songId: Long, title: String, artist: String, loadMetadata: suspend () -> LocalSongMetadata?): String? =
        preferredAudio.preferredUri(songId, title, artist, loadMetadata)
}

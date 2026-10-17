package com.lin0721.linmusic.feature.localmusic.domain

import com.lin0721.linmusic.core.model.Artist
import com.lin0721.linmusic.core.model.Track
import java.text.Normalizer
import java.util.Locale

/** Fill sparse player metadata only from a cached record for this exact online song. */
fun onlineAssociationMetadata(primary: Track, cached: Track?): Track {
    if (primary.id <= 0L || cached == null || cached.id != primary.id) return primary
    val primaryAlbum = primary.al
    val cachedAlbum = cached.al
    val album = primaryAlbum.copy(
        id = primaryAlbum.id.takeIf { it > 0L } ?: cachedAlbum.id.takeIf { it > 0L } ?: primaryAlbum.id,
        name = primaryAlbum.name.takeIf(::knownAssociationAlbum)
            ?: cachedAlbum.name.takeIf(::knownAssociationAlbum) ?: primaryAlbum.name,
        picUrl = primaryAlbum.picUrl.ifBlank { cachedAlbum.picUrl },
        artists = primaryAlbum.artists.takeIf(::hasAssociationArtist)
            ?: cachedAlbum.artists.takeIf(::hasAssociationArtist) ?: primaryAlbum.artists,
        publishTime = primaryAlbum.publishTime.takeIf { it > 0L }
            ?: cachedAlbum.publishTime.takeIf { it > 0L } ?: primaryAlbum.publishTime
    )
    return primary.copy(
        name = primary.name.ifBlank { cached.name },
        ar = primary.ar.takeIf(::hasAssociationArtist) ?: cached.ar.takeIf(::hasAssociationArtist) ?: primary.ar,
        al = album,
        dt = primary.dt.takeIf { it > 0L } ?: cached.dt.takeIf { it > 0L } ?: primary.dt
    )
}

private val associationUnknownAlbums = setOf("", "unknown", "<unknown>", "unknown album", "未知专辑")
private val associationUnknownArtists = setOf("", "unknown", "<unknown>", "unknown artist", "未知歌手", "未知艺术家")

private fun associationMetadataKey(value: String): String = Normalizer.normalize(value, Normalizer.Form.NFKC)
    .trim().lowercase(Locale.ROOT)

private fun knownAssociationAlbum(value: String): Boolean = associationMetadataKey(value) !in associationUnknownAlbums

private fun hasAssociationArtist(artists: List<Artist>): Boolean =
    artists.any { associationMetadataKey(it.name) !in associationUnknownArtists }

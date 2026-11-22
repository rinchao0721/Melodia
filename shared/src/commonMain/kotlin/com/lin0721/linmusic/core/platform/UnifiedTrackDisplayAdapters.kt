package com.lin0721.linmusic.core.platform

import com.lin0721.linmusic.core.model.Album
import com.lin0721.linmusic.core.model.Artist
import com.lin0721.linmusic.core.model.ArtistAlbum
import com.lin0721.linmusic.core.model.ArtistDetailInfo
import com.lin0721.linmusic.core.model.ArtistInfo
import com.lin0721.linmusic.core.model.PlaylistCreator
import com.lin0721.linmusic.core.model.PlaylistDetail
import com.lin0721.linmusic.core.model.PlaylistTrackId
import com.lin0721.linmusic.core.model.SongPrivilege
import com.lin0721.linmusic.core.model.Track
import com.lin0721.linmusic.core.platform.repository.UnifiedAlbumDetail
import com.lin0721.linmusic.core.platform.repository.UnifiedArtistDetail
import com.lin0721.linmusic.core.source.MusicPlatform
import kotlin.math.absoluteValue

fun PlatformEntityId.toDeterministicLegacyId(): Long =
    neteaseIdOrNull() ?: (-1L - canonical.hashCode().toLong().absoluteValue)

fun UnifiedTrack.toDisplayTrack(legacyIdOverride: Long? = null): Track {
    if (platform == MusicPlatform.NETEASE) {
        toLegacyNeteaseTrackOrNull()?.let { return it }
    }
    val legacyId = legacyIdOverride ?: id.toDeterministicLegacyId()
    val resolvedArtists = artists
        .filter { it.name.isNotBlank() }
        .map { artist ->
            Artist(
                id = artist.id?.toDeterministicLegacyId() ?: 0L,
                name = artist.name,
                picUrl = artist.picUrl,
                img1v1Url = artist.avatarUrl
            )
        }
        .ifEmpty {
            artistNames.split("、", "/", ",")
                .map { it.trim() }
                .filter { it.isNotEmpty() }
                .map { Artist(name = it) }
        }
    val resolvedCover = album?.coverUrl.orEmpty().ifBlank { coverUrl }
    val resolvedAlbum = Album(
        id = album?.id?.toDeterministicLegacyId() ?: 0L,
        name = album?.name.orEmpty(),
        picUrl = resolvedCover,
        artists = album?.artists?.map { a ->
            Artist(
                id = a.id?.toDeterministicLegacyId() ?: 0L,
                name = a.name,
                picUrl = a.picUrl,
                img1v1Url = a.avatarUrl
            )
        }.orEmpty(),
        publishTime = album?.publishTimeMs ?: publishTimeMs
    )
    val privilege = if (maxQuality != null || !playable) {
        SongPrivilege(
            id = legacyId,
            maxBrLevel = maxQuality?.neteaseCode,
            st = if (playable) 0 else -1
        )
    } else {
        null
    }
    return Track(
        id = legacyId,
        name = name,
        ar = resolvedArtists,
        al = resolvedAlbum,
        fee = fee,
        publishTime = publishTimeMs,
        dt = durationMs,
        privilege = privilege
    )
}

fun UnifiedPlaylist.toDisplayPlaylistDetail(legacyIdOverride: Long? = null): PlaylistDetail {
    if (platform == MusicPlatform.NETEASE) {
        toLegacyNeteasePlaylistOrNull()?.let { return it }
    }
    val legacyId = legacyIdOverride ?: id.toDeterministicLegacyId()
    val displayTracks = tracks.map { it.toDisplayTrack() }
    return PlaylistDetail(
        id = legacyId,
        name = name,
        coverImgUrl = coverUrl,
        description = description,
        playCount = playCount,
        subscribed = subscribed,
        creator = creator?.let {
            PlaylistCreator(
                userId = it.id?.toDeterministicLegacyId() ?: 0L,
                nickname = it.nickname,
                avatarUrl = it.avatarUrl
            )
        },
        tracks = displayTracks,
        trackCount = maxOf(trackCount, displayTracks.size),
        trackIds = displayTracks.map { PlaylistTrackId(id = it.id) },
        artists = artists.map {
            Artist(
                id = it.id?.toDeterministicLegacyId() ?: 0L,
                name = it.name,
                picUrl = it.picUrl,
                img1v1Url = it.avatarUrl
            )
        },
        privacy = if (isPublic) 0 else 10
    )
}

fun UnifiedAlbumDetail.toDisplayPlaylistDetail(legacyIdOverride: Long? = null): PlaylistDetail {
    val legacyId = legacyIdOverride ?: id.toDeterministicLegacyId()
    val displayTracks = tracks.map { it.toDisplayTrack() }
    return PlaylistDetail(
        id = legacyId,
        name = name,
        coverImgUrl = coverUrl,
        description = description.takeIf { it.isNotBlank() },
        playCount = 0L,
        subscribed = isSubscribed,
        tracks = displayTracks,
        trackCount = maxOf(trackCount, displayTracks.size),
        trackIds = displayTracks.map { PlaylistTrackId(id = it.id) },
        artists = artists.map {
            Artist(
                id = it.id?.toDeterministicLegacyId() ?: 0L,
                name = it.name,
                picUrl = it.picUrl,
                img1v1Url = it.avatarUrl
            )
        },
        privacy = 0
    )
}

fun UnifiedAlbum.toDisplayArtistAlbum(legacyIdOverride: Long? = null): ArtistAlbum = ArtistAlbum(
    id = legacyIdOverride ?: id?.toDeterministicLegacyId() ?: 0L,
    name = name,
    picUrl = coverUrl,
    publishTime = publishTimeMs
)

fun UnifiedArtist.toDisplayArtistInfo(legacyIdOverride: Long? = null): ArtistInfo = ArtistInfo(
    id = legacyIdOverride ?: id?.toDeterministicLegacyId() ?: 0L,
    name = name,
    avatarUrl = avatarUrl.ifBlank { picUrl }
)

fun UnifiedArtistDetail.toDisplayArtistDetailInfo(legacyIdOverride: Long? = null): ArtistDetailInfo = ArtistDetailInfo(
    id = legacyIdOverride ?: id.toDeterministicLegacyId(),
    name = name,
    cover = coverUrl.ifBlank { avatarUrl },
    avatar = avatarUrl.ifBlank { coverUrl },
    briefDesc = briefDesc,
    albumSize = albumSize,
    musicSize = musicSize,
    alias = alias.takeIf { it.isNotEmpty() }
)

package com.lin0721.linmusic.core.platform

import com.lin0721.linmusic.core.model.Album
import com.lin0721.linmusic.core.model.Artist
import com.lin0721.linmusic.core.model.BeRepliedComment
import com.lin0721.linmusic.core.model.CommentItem
import com.lin0721.linmusic.core.model.PlaylistCreator
import com.lin0721.linmusic.core.model.PlaylistDetail
import com.lin0721.linmusic.core.model.SongPrivilege
import com.lin0721.linmusic.core.model.Track
import com.lin0721.linmusic.core.player.PlaySource
import com.lin0721.linmusic.core.source.ExternalTrack
import com.lin0721.linmusic.core.source.MusicPlatform

// ======================= 歌手转换 =======================

fun Artist.toUnified(platform: MusicPlatform = MusicPlatform.NETEASE): UnifiedArtist =
    UnifiedArtist(
        id = if (id > 0L) ArtistId.of(platform, id.toString()) else null,
        name = name,
        avatarUrl = img1v1Url,
        picUrl = picUrl
    )

fun UnifiedArtist.toLegacyNeteaseArtist(): Artist =
    Artist(
        id = id?.neteaseIdOrNull() ?: 0L,
        name = name,
        picUrl = picUrl,
        img1v1Url = avatarUrl
    )

// ======================= 专辑转换 =======================

fun Album.toUnified(platform: MusicPlatform = MusicPlatform.NETEASE): UnifiedAlbum =
    UnifiedAlbum(
        id = if (id > 0L) AlbumId.of(platform, id.toString()) else null,
        name = name,
        coverUrl = picUrl,
        artists = artists.map { it.toUnified(platform) },
        publishTimeMs = publishTime
    )

fun UnifiedAlbum.toLegacyNeteaseAlbum(): Album =
    Album(
        id = id?.neteaseIdOrNull() ?: 0L,
        name = name,
        picUrl = coverUrl,
        artists = artists.map { it.toLegacyNeteaseArtist() },
        publishTime = publishTimeMs
    )

// ======================= 创建者转换 =======================

fun PlaylistCreator.toUnified(platform: MusicPlatform = MusicPlatform.NETEASE): UnifiedCreator =
    UnifiedCreator(
        id = if (userId > 0L) AccountId.of(platform, userId.toString()) else null,
        nickname = nickname,
        avatarUrl = avatarUrl
    )

fun UnifiedCreator.toLegacyNeteaseCreator(): PlaylistCreator =
    PlaylistCreator(
        userId = id?.neteaseIdOrNull() ?: 0L,
        nickname = nickname,
        avatarUrl = avatarUrl
    )

// ======================= 曲目转换 =======================

fun Track.toUnified(): UnifiedTrack {
    val unifiedArtists = ar.map { it.toUnified(MusicPlatform.NETEASE) }
    val unifiedAlbum = al.toUnified(MusicPlatform.NETEASE)
    val resolvedQuality = resolveMaxQuality(this)
    val isPlayable = privilege?.st?.let { it >= 0 } ?: true

    return UnifiedTrack(
        id = TrackId.netease(id),
        name = name,
        artists = unifiedArtists,
        artistNames = unifiedArtists.joinToString(" / ") { it.name },
        album = unifiedAlbum,
        durationMs = dt,
        coverUrl = al.picUrl,
        publishTimeMs = publishTime,
        maxQuality = resolvedQuality,
        fee = fee,
        playable = isPlayable
    )
}

fun UnifiedTrack.toLegacyNeteaseTrackOrNull(): Track? {
    val neteaseId = id.neteaseIdOrNull() ?: return null
    val legacyPrivilege = if (maxQuality != null || !playable) {
        SongPrivilege(
            id = neteaseId,
            maxBrLevel = maxQuality?.neteaseCode,
            st = if (playable) 0 else -1
        )
    } else {
        null
    }
    return Track(
        id = neteaseId,
        name = name,
        ar = artists.map { it.toLegacyNeteaseArtist() },
        al = album?.toLegacyNeteaseAlbum()?.let {
            if (it.picUrl.isBlank() && coverUrl.isNotBlank()) it.copy(picUrl = coverUrl) else it
        } ?: Album(picUrl = coverUrl),
        fee = fee,
        publishTime = publishTimeMs,
        dt = durationMs,
        privilege = legacyPrivilege
    )
}

fun ExternalTrack.toUnified(): UnifiedTrack {
    val rawTrackId = if (platform == MusicPlatform.KUGOU) {
        hash?.trim()?.takeIf { it.isNotEmpty() } ?: id
    } else {
        id
    }
    val trackId = TrackId.of(platform, rawTrackId)
    val artistObjects = artistList
        .filter { it.isNotBlank() }
        .map { UnifiedArtist(name = it) }
        .ifEmpty {
            if (artists.isNotBlank()) listOf(UnifiedArtist(name = artists)) else emptyList()
        }
    val unifiedAlbum = if (albumName.isNotBlank() || albumId.isNotBlank()) {
        UnifiedAlbum(
            id = albumId.takeIf { it.isNotBlank() }?.let { AlbumId.of(platform, it) },
            name = albumName,
            coverUrl = coverUrl
        )
    } else {
        null
    }
    val kugouMeta = if (platform == MusicPlatform.KUGOU) {
        KugouTrackMeta(
            hash = trackId.rawId,
            albumId = albumId.takeIf { it.isNotBlank() }
        )
    } else {
        null
    }

    return UnifiedTrack(
        id = trackId,
        name = name,
        artists = artistObjects,
        artistNames = artists.ifBlank { artistList.joinToString(" / ") },
        album = unifiedAlbum,
        durationMs = durationMs,
        coverUrl = coverUrl,
        kugouMeta = kugouMeta,
        qqSongId = qqSongId
    )
}

fun UnifiedTrack.toLegacyExternalTrackOrNull(): ExternalTrack? {
    if (platform == MusicPlatform.NETEASE || platform == MusicPlatform.LOCAL) return null
    val legacyArtistNames = artistNames.ifBlank { artists.joinToString(" / ") { it.name } }
    val legacyArtistList = artists.map { it.name }.filter { it.isNotBlank() }
    val legacyAlbumId = album?.id?.rawId ?: kugouMeta?.albumId.orEmpty()
    val legacyHash = if (platform == MusicPlatform.KUGOU) {
        kugouMeta?.hash ?: id.rawId
    } else {
        kugouMeta?.hash
    }
    return ExternalTrack(
        id = id.rawId,
        name = name,
        artists = legacyArtistNames,
        artistList = legacyArtistList,
        albumName = album?.name.orEmpty(),
        albumId = legacyAlbumId,
        durationMs = durationMs,
        coverUrl = coverUrl.ifBlank { album?.coverUrl.orEmpty() },
        platform = platform,
        hash = legacyHash,
        qqSongId = qqSongId
    )
}

// ======================= 歌单/专辑详情转换 =======================

fun PlaylistDetail.toUnified(platform: MusicPlatform = MusicPlatform.NETEASE): UnifiedPlaylist =
    UnifiedPlaylist(
        id = PlaylistId.of(platform, id.toString()),
        name = name,
        coverUrl = coverImgUrl,
        description = description,
        playCount = playCount,
        subscribed = subscribed,
        creator = creator?.toUnified(platform),
        tracks = tracks.map { it.toUnified() },
        trackCount = trackCount.takeIf { it > 0 } ?: maxOf(tracks.size, trackIds.size),
        trackIds = trackIds.map { TrackId.of(platform, it.id.toString()) },
        artists = artists.map { it.toUnified(platform) },
        isPublic = privacy == 0,
        isAlbum = artists.isNotEmpty()
    )

fun UnifiedPlaylist.toLegacyNeteasePlaylistOrNull(): PlaylistDetail? {
    val neteaseId = id.neteaseIdOrNull() ?: return null
    return PlaylistDetail(
        id = neteaseId,
        name = name,
        coverImgUrl = coverUrl,
        description = description,
        playCount = playCount,
        subscribed = subscribed,
        creator = creator?.toLegacyNeteaseCreator(),
        tracks = tracks.mapNotNull { it.toLegacyNeteaseTrackOrNull() },
        trackCount = trackCount,
        trackIds = trackIds.mapNotNull { trackId ->
            trackId.neteaseIdOrNull()?.let { com.lin0721.linmusic.core.model.PlaylistTrackId(id = it) }
        },
        artists = artists.map { it.toLegacyNeteaseArtist() },
        privacy = if (isPublic) 0 else 10
    )
}

// ======================= 评论与来源转换 =======================

fun CommentItem.toUnified(platform: MusicPlatform = MusicPlatform.NETEASE): UnifiedComment =
    UnifiedComment(
        id = commentId.toString(),
        platform = platform,
        author = UnifiedCreator(
            id = if (user.userId > 0L) AccountId.of(platform, user.userId.toString()) else null,
            nickname = user.nickname,
            avatarUrl = user.avatarUrl
        ),
        content = content,
        timeMs = time,
        timeText = timeStr,
        likedCount = likedCount,
        liked = liked,
        replyCount = replyCount,
        parentCommentId = parentCommentId.takeIf { it > 0L }?.toString(),
        repliedTo = beReplied.orEmpty().map { it.toUnified(platform) }
    )

private fun BeRepliedComment.toUnified(platform: MusicPlatform): UnifiedRepliedComment =
    UnifiedRepliedComment(
        author = user?.let {
            UnifiedCreator(
                id = if (it.userId > 0L) AccountId.of(platform, it.userId.toString()) else null,
                nickname = it.nickname,
                avatarUrl = it.avatarUrl
            )
        },
        content = content,
        commentId = beRepliedCommentId?.takeIf { it > 0L }?.toString()
    )

fun PlaySource.toUnified(platform: MusicPlatform = MusicPlatform.NETEASE): UnifiedPlaySource =
    UnifiedPlaySource(
        kind = when (kind) {
            PlaySource.Kind.PLAYLIST -> UnifiedPlaySource.Kind.PLAYLIST
            PlaySource.Kind.ALBUM -> UnifiedPlaySource.Kind.ALBUM
        },
        platform = platform,
        rawId = id.toString(),
        name = name,
        coverUrl = coverUrl
    )

fun UnifiedPlaySource.toLegacyNeteasePlaySourceOrNull(): PlaySource? {
    if (platform != MusicPlatform.NETEASE) return null
    val numericId = rawId.toLongOrNull() ?: return null
    val legacyKind = when (kind) {
        UnifiedPlaySource.Kind.PLAYLIST -> PlaySource.Kind.PLAYLIST
        UnifiedPlaySource.Kind.ALBUM -> PlaySource.Kind.ALBUM
        else -> return null
    }
    return PlaySource(kind = legacyKind, id = numericId, name = name, coverUrl = coverUrl)
}

private fun resolveMaxQuality(track: Track): UnifiedAudioQuality? {
    UnifiedAudioQuality.fromNeteaseCode(track.privilege?.maxBrLevel)?.let { return it }
    return when {
        track.hr != null -> UnifiedAudioQuality.HIRES
        track.sq != null -> UnifiedAudioQuality.LOSSLESS
        track.h != null -> UnifiedAudioQuality.EXHIGH
        track.m != null -> UnifiedAudioQuality.HIGHER
        track.l != null -> UnifiedAudioQuality.STANDARD
        else -> null
    }
}

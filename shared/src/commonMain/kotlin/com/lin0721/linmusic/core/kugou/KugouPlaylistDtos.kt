package com.lin0721.linmusic.core.kugou

import com.lin0721.linmusic.core.platform.AlbumId
import com.lin0721.linmusic.core.platform.ArtistId
import com.lin0721.linmusic.core.platform.KugouTrackMeta
import com.lin0721.linmusic.core.platform.PlaylistId
import com.lin0721.linmusic.core.platform.TrackId
import com.lin0721.linmusic.core.platform.UnifiedAlbum
import com.lin0721.linmusic.core.platform.UnifiedArtist
import com.lin0721.linmusic.core.platform.UnifiedAudioQuality
import com.lin0721.linmusic.core.platform.UnifiedCreator
import com.lin0721.linmusic.core.platform.UnifiedPlaylist
import com.lin0721.linmusic.core.platform.UnifiedTrack
import com.lin0721.linmusic.core.platform.AccountId
import com.lin0721.linmusic.core.source.MusicPlatform
import kotlinx.serialization.Serializable

@Serializable
data class KugouTrackArtistDto(
    val id: String = "",
    val name: String = "",
    val avatarUrl: String = ""
) {
    fun toUnifiedArtist(): UnifiedArtist = UnifiedArtist(
        id = id.takeIf { it.isNotBlank() && it != "0" }?.let { ArtistId.kugou(it) },
        name = name,
        avatarUrl = avatarUrl,
        picUrl = avatarUrl
    )
}

@Serializable
data class KugouTrackItemDto(
    val hash: String,
    val name: String,
    val artists: List<KugouTrackArtistDto> = emptyList(),
    val albumId: String = "",
    val albumName: String = "",
    val albumAudioId: Long = 0L,
    val mixSongId: Long = 0L,
    val fileId: Long = 0L,
    val durationMs: Long = 0L,
    val coverUrl: String = "",
    val maxQuality: UnifiedAudioQuality? = null,
    val fee: Int = 0,
    val playable: Boolean = true,
    val publishTimeMs: Long = 0L,
    val addTimeMs: Long = 0L
) {
    fun toUnifiedTrack(): UnifiedTrack {
        val unifiedArtists = artists.map { it.toUnifiedArtist() }
        val unifiedAlbum = if (albumName.isNotBlank() || albumId.isNotBlank()) {
            UnifiedAlbum(
                id = albumId.takeIf { it.isNotBlank() && it != "0" }?.let { AlbumId.kugou(it) },
                name = albumName,
                coverUrl = coverUrl
            )
        } else {
            null
        }
        return UnifiedTrack(
            id = TrackId.kugou(hash),
            name = name,
            artists = unifiedArtists,
            artistNames = unifiedArtists.joinToString(" / ") { it.name },
            album = unifiedAlbum,
            durationMs = durationMs,
            coverUrl = coverUrl,
            publishTimeMs = publishTimeMs,
            maxQuality = maxQuality,
            fee = fee,
            playable = playable,
            kugouMeta = KugouTrackMeta(
                hash = hash.lowercase(),
                mixSongId = mixSongId.takeIf { it > 0L },
                albumAudioId = albumAudioId.takeIf { it > 0L },
                albumId = albumId.takeIf { it.isNotBlank() && it != "0" },
                playlistFileId = fileId.takeIf { it > 0L },
                playlistAddTimeMs = addTimeMs.takeIf { it > 0L }
            )
        )
    }
}

@Serializable
data class KugouUserPlaylistDto(
    val listId: Long,
    val globalCollectionId: String = "",
    val name: String = "",
    val coverUrl: String = "",
    val intro: String = "",
    val trackCount: Int = 0,
    val listVer: Long = 0L,
    val totalVer: Long = 0L,
    val type: Int = 0,
    val source: Int = 1,
    val isDefaultLike: Boolean = false,
    val isPrivate: Boolean = false,
    val createUserId: Long = 0L,
    val createUsername: String = "",
    val createListId: Long = 0L,
    val createGid: String = "",
    val sort: Int = 0,
    val tags: String = ""
) {
    val isCollected: Boolean
        get() = type == 1

    fun preferredPlaylistId(): PlaylistId =
        PlaylistId.kugou(listId.toString())
}

@Serializable
data class KugouUserPlaylistsPage(
    val totalVer: Long = 0L,
    val playlists: List<KugouUserPlaylistDto> = emptyList(),
    val hasMore: Boolean = false
)

@Serializable
data class KugouPlaylistDetailDto(
    val globalCollectionId: String = "",
    val listId: Long = 0L,
    val name: String = "",
    val coverUrl: String = "",
    val intro: String = "",
    val trackCount: Int = 0,
    val playCount: Long = 0L,
    val createUserId: Long = 0L,
    val createUsername: String = "",
    val createAvatarUrl: String = "",
    val listVer: Long = 0L,
    val tags: List<String> = emptyList(),
    val isPrivate: Boolean = false,
    val isCollected: Boolean = false
) {
    fun toUnifiedPlaylist(tracks: List<UnifiedTrack> = emptyList()): UnifiedPlaylist {
        val rawId = globalCollectionId.trim().ifBlank {
            if (listId > 0L) "special:$listId" else ""
        }
        return UnifiedPlaylist(
            id = PlaylistId.kugou(rawId),
            name = name,
            coverUrl = coverUrl,
            description = intro.takeIf { it.isNotBlank() },
            playCount = playCount,
            subscribed = isCollected,
            creator = if (createUserId > 0L || createUsername.isNotBlank()) {
                UnifiedCreator(
                    id = createUserId.takeIf { it > 0L }?.let { AccountId.kugou(it) },
                    nickname = createUsername,
                    avatarUrl = createAvatarUrl
                )
            } else {
                null
            },
            tracks = tracks,
            trackCount = trackCount.takeIf { it > 0 } ?: tracks.size,
            trackIds = tracks.map { it.id },
            isPublic = !isPrivate,
            tags = tags,
            listVer = listVer
        )
    }
}

@Serializable
data class KugouPlaylistTracksPage(
    val listId: Long = 0L,
    val globalCollectionId: String = "",
    val listVer: Long = 0L,
    val tracks: List<KugouTrackItemDto> = emptyList(),
    val totalCount: Int = 0,
    val hasMore: Boolean = false
)

@Serializable
data class KugouPlaylistAddTrackInput(
    val name: String,
    val hash: String,
    val albumId: String? = null,
    val mixSongId: Long? = null
)

@Serializable
data class KugouPlaylistTrackSortInput(
    val fileId: Long,
    val sort: Int
)

@Serializable
data class KugouCoverUploadResult(
    val fileName: String,
    val picValue: String = "custom/$fileName"
)

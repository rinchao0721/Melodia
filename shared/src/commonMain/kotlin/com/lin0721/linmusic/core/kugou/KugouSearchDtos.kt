package com.lin0721.linmusic.core.kugou

import com.lin0721.linmusic.core.platform.AccountId
import com.lin0721.linmusic.core.platform.AlbumId
import com.lin0721.linmusic.core.platform.ArtistId
import com.lin0721.linmusic.core.platform.PlaylistId
import com.lin0721.linmusic.core.platform.UnifiedAlbum
import com.lin0721.linmusic.core.platform.UnifiedArtist
import com.lin0721.linmusic.core.platform.UnifiedCreator
import com.lin0721.linmusic.core.platform.UnifiedPlaylist
import kotlinx.serialization.Serializable

enum class KugouSearchType(val wireValue: String) {
    SONG("song"),
    ALBUM("album"),
    SPECIAL("special"),
    AUTHOR("author"),
    LYRIC("lyric"),
    MV("mv")
}

@Serializable
data class KugouSearchAlbumDto(
    val albumId: String,
    val name: String,
    val coverUrl: String = "",
    val singerName: String = "",
    val publishTimeMs: Long = 0L,
    val songCount: Int = 0,
    val intro: String = ""
) {
    fun toUnifiedAlbum(): UnifiedAlbum = UnifiedAlbum(
        id = AlbumId.kugou(albumId),
        name = name,
        coverUrl = coverUrl,
        artists = if (singerName.isNotBlank()) {
            singerName.split("、", "/", ",").map { it.trim() }.filter { it.isNotEmpty() }
                .map { UnifiedArtist(name = it) }
        } else {
            emptyList()
        },
        publishTimeMs = publishTimeMs
    )
}

@Serializable
data class KugouSearchArtistDto(
    val authorId: String,
    val name: String,
    val avatarUrl: String = "",
    val albumCount: Int = 0,
    val songCount: Int = 0,
    val fansCount: Long = 0L
) {
    fun toUnifiedArtist(): UnifiedArtist = UnifiedArtist(
        id = ArtistId.kugou(authorId),
        name = name,
        avatarUrl = avatarUrl,
        picUrl = avatarUrl
    )
}

@Serializable
data class KugouSearchPlaylistDto(
    val globalCollectionId: String,
    val specialId: Long = 0L,
    val name: String,
    val coverUrl: String = "",
    val creatorName: String = "",
    val creatorId: Long = 0L,
    val trackCount: Int = 0,
    val playCount: Long = 0L,
    val intro: String = ""
) {
    fun toUnifiedPlaylist(): UnifiedPlaylist {
        val rawId = globalCollectionId.ifBlank { specialId.toString() }
        return UnifiedPlaylist(
            id = PlaylistId.kugou(rawId),
            name = name,
            coverUrl = coverUrl,
            description = intro.takeIf { it.isNotBlank() },
            playCount = playCount,
            creator = if (creatorName.isNotBlank() || creatorId > 0L) {
                UnifiedCreator(
                    id = creatorId.takeIf { it > 0L }?.let { AccountId.kugou(it) },
                    nickname = creatorName
                )
            } else {
                null
            },
            trackCount = trackCount
        )
    }
}

@Serializable
data class KugouSearchLyricDto(
    val track: KugouTrackItemDto,
    val lyricSnippet: String = ""
)

@Serializable
data class KugouSearchMvDto(
    val hash: String,
    val name: String,
    val singerName: String = "",
    val coverUrl: String = "",
    val durationMs: Long = 0L
)

@Serializable
data class KugouSearchResultPage(
    val type: KugouSearchType,
    val totalCount: Int = 0,
    val hasMore: Boolean = false,
    val songs: List<KugouTrackItemDto> = emptyList(),
    val albums: List<KugouSearchAlbumDto> = emptyList(),
    val artists: List<KugouSearchArtistDto> = emptyList(),
    val playlists: List<KugouSearchPlaylistDto> = emptyList(),
    val lyrics: List<KugouSearchLyricDto> = emptyList(),
    val mvs: List<KugouSearchMvDto> = emptyList()
)

@Serializable
data class KugouComplexSearchResult(
    val songs: List<KugouTrackItemDto> = emptyList(),
    val albums: List<KugouSearchAlbumDto> = emptyList(),
    val artists: List<KugouSearchArtistDto> = emptyList(),
    val playlists: List<KugouSearchPlaylistDto> = emptyList()
)

@Serializable
data class KugouHotSearchItem(
    val keyword: String,
    val reason: String = "",
    val iconUrl: String? = null,
    val jumpUrl: String = ""
)

@Serializable
data class KugouSearchSuggestItem(
    val keyword: String,
    val category: String = "",
    val hot: Int = 0
)

@Serializable
data class KugouSearchSuggestResult(
    val keywords: List<KugouSearchSuggestItem> = emptyList(),
    val albums: List<KugouSearchAlbumDto> = emptyList(),
    val artists: List<KugouSearchArtistDto> = emptyList()
)

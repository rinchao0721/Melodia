package com.lin0721.linmusic.core.kugou

import com.lin0721.linmusic.core.platform.AlbumId
import com.lin0721.linmusic.core.platform.ArtistId
import com.lin0721.linmusic.core.platform.UnifiedAlbum
import com.lin0721.linmusic.core.platform.UnifiedArtist
import kotlinx.serialization.Serializable

@Serializable
data class KugouArtistDetailDto(
    val authorId: String,
    val name: String,
    val avatarUrl: String = "",
    val coverUrl: String = "",
    val intro: String = "",
    val albumCount: Int = 0,
    val songCount: Int = 0,
    val mvCount: Int = 0,
    val fansCount: Long = 0L,
    val isFollowed: Boolean = false
) {
    fun toUnifiedArtist(): UnifiedArtist = UnifiedArtist(
        id = ArtistId.kugou(authorId),
        name = name,
        avatarUrl = avatarUrl,
        picUrl = coverUrl.ifBlank { avatarUrl }
    )
}

@Serializable
data class KugouArtistAlbumDto(
    val albumId: String,
    val name: String,
    val coverUrl: String = "",
    val publishDate: String = "",
    val publishTimeMs: Long = 0L,
    val songCount: Int = 0,
    val intro: String = "",
    val authorName: String = ""
) {
    fun toUnifiedAlbum(): UnifiedAlbum = UnifiedAlbum(
        id = AlbumId.kugou(albumId),
        name = name,
        coverUrl = coverUrl,
        artists = if (authorName.isNotBlank()) {
            authorName.split("、", "/", ",").map { it.trim() }.filter { it.isNotEmpty() }
                .map { UnifiedArtist(name = it) }
        } else {
            emptyList()
        },
        publishTimeMs = publishTimeMs
    )
}

@Serializable
data class KugouArtistSongsPage(
    val songs: List<KugouTrackItemDto> = emptyList(),
    val totalCount: Int = 0,
    val hasMore: Boolean = false
)

@Serializable
data class KugouArtistAlbumsPage(
    val albums: List<KugouArtistAlbumDto> = emptyList(),
    val totalCount: Int = 0,
    val hasMore: Boolean = false
)

@Serializable
data class KugouFollowedArtistDto(
    val singerId: String,
    val authorId: String = singerId,
    val name: String = "",
    val avatarUrl: String = "",
    val intro: String = ""
) {
    fun toUnifiedArtist(): UnifiedArtist = UnifiedArtist(
        id = ArtistId.kugou(authorId.ifBlank { singerId }),
        name = name,
        avatarUrl = avatarUrl,
        picUrl = avatarUrl
    )
}

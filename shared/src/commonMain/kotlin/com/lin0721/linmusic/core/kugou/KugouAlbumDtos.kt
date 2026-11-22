package com.lin0721.linmusic.core.kugou

import com.lin0721.linmusic.core.platform.AlbumId
import com.lin0721.linmusic.core.platform.UnifiedAlbum
import kotlinx.serialization.Serializable

@Serializable
data class KugouAlbumDetailDto(
    val albumId: String,
    val name: String,
    val coverUrl: String = "",
    val intro: String = "",
    val publishDate: String = "",
    val publishTimeMs: Long = 0L,
    val language: String = "",
    val heat: Long = 0L,
    val authors: List<KugouTrackArtistDto> = emptyList(),
    val authorName: String = "",
    val songCount: Int = 0
) {
    fun toUnifiedAlbum(): UnifiedAlbum = UnifiedAlbum(
        id = AlbumId.kugou(albumId),
        name = name,
        coverUrl = coverUrl,
        artists = if (authors.isNotEmpty()) {
            authors.map { it.toUnifiedArtist() }
        } else {
            authorName.split("、", "/", ",").map { it.trim() }.filter { it.isNotEmpty() }
                .map { KugouTrackArtistDto(name = it).toUnifiedArtist() }
        },
        publishTimeMs = publishTimeMs
    )
}

@Serializable
data class KugouAlbumSongsPage(
    val songs: List<KugouTrackItemDto> = emptyList(),
    val totalCount: Int = 0,
    val hasMore: Boolean = false
)

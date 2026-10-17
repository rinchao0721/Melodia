package com.lin0721.linmusic.feature.artist.domain.catalog

import com.lin0721.linmusic.core.model.ArtistAlbum
import kotlinx.serialization.Serializable

@Serializable
data class ArtistCatalogArtist(
    val id: String,
    val name: String,
    val sortName: String = "",
    val disambiguation: String = "",
    val aliases: List<String> = emptyList(),
    val country: String? = null,
    val type: String? = null,
    val score: Int? = null
)

@Serializable
data class ArtistCatalogCredit(
    val artistId: String,
    val name: String,
    val joinPhrase: String = ""
)

@Serializable
data class ArtistCatalogReleaseGroup(
    val id: String,
    val title: String,
    val firstReleaseDate: String? = null,
    val primaryType: String? = null,
    // null means the source did not provide classification; [] explicitly means no secondary type.
    val secondaryTypes: List<String>? = null,
    val aliases: List<String> = emptyList(),
    val artistCredits: List<ArtistCatalogCredit> = emptyList()
)

@Serializable
data class ArtistCatalogPreference(
    /** A missing artist key follows the global setting; existing beta.6 keys remain explicit. */
    val enabledOverride: Boolean? = null,
    val artistMbid: String? = null,
    val artistName: String? = null,
    val defaultEnabled: Boolean = true
) {
    val enabled: Boolean get() = enabledOverride ?: defaultEnabled
}

enum class ArtistCatalogClassification { ORDINARY_ALBUM, EXCLUDED, UNKNOWN }
enum class ArtistCatalogTitleMatch { TITLE, ALIAS, EDITION }

data class ArtistCatalogAlbumMatch(
    val album: ArtistAlbum,
    val group: ArtistCatalogReleaseGroup,
    val titleMatch: ArtistCatalogTitleMatch
)

data class ArtistCatalogSelection(
    val albums: List<ArtistAlbum>,
    val matches: List<ArtistCatalogAlbumMatch>,
    val unmatchedAlbums: List<ArtistAlbum>,
    val ambiguousAlbums: List<ArtistAlbum>,
    val excludedAlbums: List<ArtistAlbum>,
    val duplicateAlbums: List<ArtistAlbum>,
    val unmatchedReleaseGroups: List<ArtistCatalogReleaseGroup>
) {
    val matchedCount: Int get() = matches.size
    val unmatchedCount: Int get() = unmatchedAlbums.size + ambiguousAlbums.size
    val duplicateCount: Int get() = duplicateAlbums.size
    val excludedCount: Int get() = excludedAlbums.size
}

data class ArtistCatalogIdentityEvidence(val matchedReleaseGroupIds: Set<String>) {
    val matchedCount: Int get() = matchedReleaseGroupIds.size
    val canAutoBind: Boolean get() = matchedCount >= 2
}

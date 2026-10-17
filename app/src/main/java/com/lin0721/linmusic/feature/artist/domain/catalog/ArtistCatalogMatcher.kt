package com.lin0721.linmusic.feature.artist.domain.catalog

import com.lin0721.linmusic.core.model.ArtistAlbum
import com.lin0721.linmusic.core.preferences.ArtistAlbumFilterSettings
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.Locale
import kotlin.math.abs

/** Only a uniquely identified catalog group can classify an online album. */
object ArtistCatalogMatcher {
    fun classify(group: ArtistCatalogReleaseGroup): ArtistCatalogClassification {
        return when (group.primaryType?.trim()?.lowercase(Locale.ROOT)) {
            "album" -> when {
                group.secondaryTypes == null -> ArtistCatalogClassification.UNKNOWN
                group.secondaryTypes.isEmpty() -> ArtistCatalogClassification.ORDINARY_ALBUM
                else -> ArtistCatalogClassification.EXCLUDED
            }
            "single", "ep", "broadcast", "other" -> ArtistCatalogClassification.EXCLUDED
            else -> ArtistCatalogClassification.UNKNOWN
        }
    }

    fun exactNameCandidates(
        artists: List<ArtistCatalogArtist>,
        sourceNames: Collection<String>,
        normalizer: ArtistCatalogTitleNormalizer = ArtistCatalogTitleNormalizer()
    ): List<ArtistCatalogArtist> {
        val names = sourceNames.map(normalizer::normalizeArtistName).filter(String::isNotBlank).toSet()
        return artists.filter { artist ->
            (listOf(artist.name) + artist.aliases).any { normalizer.normalizeArtistName(it) in names }
        }.distinctBy { it.id }
    }

    fun identityEvidence(
        albums: List<ArtistAlbum>,
        groups: List<ArtistCatalogReleaseGroup>,
        normalizer: ArtistCatalogTitleNormalizer = ArtistCatalogTitleNormalizer(),
        boundArtistMbid: String? = null
    ): ArtistCatalogIdentityEvidence = ArtistCatalogIdentityEvidence(
        select(albums, groups, normalizer, boundArtistMbid).matches.mapTo(linkedSetOf()) { it.group.id }
    )

    fun select(
        albums: List<ArtistAlbum>,
        groups: List<ArtistCatalogReleaseGroup>,
        normalizer: ArtistCatalogTitleNormalizer = ArtistCatalogTitleNormalizer(),
        boundArtistMbid: String? = null,
        minTracks: Int = ArtistAlbumFilterSettings.DEFAULT_MIN_TRACKS
    ): ArtistCatalogSelection {
        val minimum = ArtistAlbumFilterSettings.normalizeMinTracks(minTracks)
        val catalog = groups.distinctBy { it.id }.filter { group ->
            boundArtistMbid == null || group.artistCredits.any { it.artistId == boundArtistMbid }
        }
        val titleIndex = linkedMapOf<String, MutableList<ArtistCatalogReleaseGroup>>()
        catalog.forEach { group ->
            (listOf(group.title) + group.aliases).map(normalizer::normalizeTitle).filter(String::isNotBlank).distinct().forEach { title ->
                titleIndex.getOrPut(title, ::mutableListOf).add(group)
            }
        }
        val candidates = linkedMapOf<String, MutableList<ArtistCatalogAlbumMatch>>()
        val unmatched = mutableListOf<ArtistAlbum>()
        val ambiguous = mutableListOf<ArtistAlbum>()
        val excluded = mutableListOf<ArtistAlbum>()
        albums.distinctBy { it.id }.forEach { album ->
            val title = normalizer.normalizeTitle(album.name)
            val exact = titleIndex[title].orEmpty().distinctBy { it.id }
            val base = titleIndex[normalizer.baseTitle(album.name)].orEmpty().distinctBy { it.id }
            val primaryType = sourcePrimaryType(album.type)
            // The source's explicit Album/Single/EP type can distinguish same-title groups.
            // A unique exact catalog classification remains authoritative, even when the
            // streaming service labels an EP or live recording as an album.
            val named = exact.ifEmpty { base }
            val resolved = if (named.size > 1 && primaryType != null) {
                named.filter { catalogPrimaryType(it) == primaryType }.ifEmpty { named }
            } else named
            when {
                resolved.isEmpty() -> unmatched.add(album)
                resolved.size > 1 && resolved.all { classify(it) == ArtistCatalogClassification.EXCLUDED } -> excluded.add(album)
                resolved.size > 1 -> ambiguous.add(album)
                classify(resolved.single()) == ArtistCatalogClassification.UNKNOWN -> unmatched.add(album)
                primaryType != null && catalogPrimaryType(resolved.single()) != primaryType -> excluded.add(album)
                classify(resolved.single()) == ArtistCatalogClassification.EXCLUDED -> excluded.add(album)
                else -> {
                    val group = resolved.single()
                    val titleMatch = when {
                        title == normalizer.normalizeTitle(group.title) -> ArtistCatalogTitleMatch.TITLE
                        group.aliases.any { title == normalizer.normalizeTitle(it) } -> ArtistCatalogTitleMatch.ALIAS
                        else -> ArtistCatalogTitleMatch.EDITION
                    }
                    candidates.getOrPut(group.id, ::mutableListOf).add(ArtistCatalogAlbumMatch(album, group, titleMatch))
                }
            }
        }
        val matches = mutableListOf<ArtistCatalogAlbumMatch>()
        val duplicates = mutableListOf<ArtistAlbum>()
        candidates.values.forEach { variants ->
            val ordered = variants.sortedWith(compareBy<ArtistCatalogAlbumMatch>(
                // Select a qualifying edition before collapsing editions. Otherwise an
                // undersized original hides a qualifying deluxe release when the caller
                // applies the count filter. Keep all classifications for the status counts.
                { if (minimum == 0 || it.album.size >= minimum) 0 else 1 },
                { if (normalizer.isEditionTitle(it.album.name)) 1 else 0 },
                { it.titleMatch.ordinal },
                { releaseDateDistance(it.album.publishTime, it.group.firstReleaseDate) },
                { if (it.album.publishTime > 0) it.album.publishTime else Long.MAX_VALUE },
                { -it.album.size.coerceAtLeast(0) },
                { it.album.id }
            ))
            matches.add(ordered.first())
            duplicates.addAll(ordered.drop(1).map { it.album })
        }
        val matchedGroups = matches.mapTo(hashSetOf()) { it.group.id }
        // Streaming catalog order often puts an old album's recent remaster first.
        // Once editions are grouped, present the actual album chronology instead.
        val chronologicalMatches = matches.sortedWith(compareByDescending<ArtistCatalogAlbumMatch> {
            releaseDate(it.group.firstReleaseDate)?.toEpochDay() ?: Long.MIN_VALUE
        }.thenBy { normalizer.normalizeTitle(it.group.title) }.thenBy { it.group.id })
        return ArtistCatalogSelection(
            albums = chronologicalMatches.map { it.album },
            matches = chronologicalMatches,
            unmatchedAlbums = unmatched,
            ambiguousAlbums = ambiguous,
            excludedAlbums = excluded,
            duplicateAlbums = duplicates,
            unmatchedReleaseGroups = catalog.filter { classify(it) == ArtistCatalogClassification.ORDINARY_ALBUM && it.id !in matchedGroups }
        )
    }

    private fun sourcePrimaryType(value: String?): String? = when (value?.trim()?.lowercase(Locale.ROOT)) {
        "专辑", "album" -> "album"
        "single", "单曲" -> "single"
        "ep" -> "ep"
        else -> null
    }

    private fun catalogPrimaryType(group: ArtistCatalogReleaseGroup): String? =
        group.primaryType?.trim()?.lowercase(Locale.ROOT)

    private fun releaseDate(value: String?): LocalDate? = value?.takeIf(String::isNotBlank)?.let {
        runCatching {
            val parts = it.split('-')
            LocalDate.of(parts[0].toInt(), parts.getOrNull(1)?.toInt() ?: 1, parts.getOrNull(2)?.toInt() ?: 1)
        }.getOrNull()
    }

    private fun releaseDateDistance(publishTime: Long, firstReleaseDate: String?): Long {
        if (publishTime <= 0 || firstReleaseDate.isNullOrBlank()) return Long.MAX_VALUE
        return runCatching {
            val original = releaseDate(firstReleaseDate) ?: return Long.MAX_VALUE
            val published = Instant.ofEpochMilli(publishTime).atZone(ZoneOffset.UTC).toLocalDate()
            abs(published.toEpochDay() - original.toEpochDay())
        }.getOrDefault(Long.MAX_VALUE)
    }
}

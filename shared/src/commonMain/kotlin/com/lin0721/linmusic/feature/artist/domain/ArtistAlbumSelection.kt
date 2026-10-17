package com.lin0721.linmusic.feature.artist.domain

import com.lin0721.linmusic.core.model.ArtistAlbum
import com.lin0721.linmusic.core.preferences.ArtistAlbumFilterSettings

data class ArtistAlbumPageProgress(
    val albums: List<ArtistAlbum>,
    val nextOffset: Int,
    val hasMore: Boolean,
    val error: String? = null
)

/** Filtering never removes the source rows or changes the server pagination offset. */
object ArtistAlbumSelection {
    fun visibleAlbums(albums: List<ArtistAlbum>, minTracks: Int): List<ArtistAlbum> {
        val minimum = ArtistAlbumFilterSettings.normalizeMinTracks(minTracks)
        if (minimum == 0) return albums
        return albums.filter { it.size >= minimum }
    }

    fun shouldLoadMore(
        nearListEnd: Boolean,
        visibleCount: Int,
        hasMore: Boolean,
        loading: Boolean,
        error: String?,
        completeCatalog: Boolean = false
    ): Boolean = hasMore && !loading && error == null &&
        (completeCatalog || nearListEnd || visibleCount == 0)

    /** The server's continuation flag is authoritative; a stall is an error, never completion. */
    fun hasNextPage(page: ArtistAlbumPage): Boolean = page.hasMore

    /** Raw page length advances the server offset, while visible/source rows stay distinct. */
    fun applyPage(currentAlbums: List<ArtistAlbum>, requestedOffset: Int, page: ArtistAlbumPage): ArtistAlbumPageProgress {
        require(requestedOffset >= 0)
        val currentSource = if (currentAlbums.any { it.id <= 0L }) currentAlbums.filter { it.id > 0L } else currentAlbums
        val validAlbums = page.albums.filter { it.id > 0L }
        val knownIds = currentSource.mapTo(mutableSetOf()) { it.id }
        val addsNewAlbum = validAlbums.any { it.id !in knownIds }
        if (page.hasMore && !addsNewAlbum) {
            return ArtistAlbumPageProgress(currentSource, requestedOffset, true,
                "专辑分页未返回新条目，已暂停加载；可重试或稍后再试。")
        }
        val nextOffset = requestedOffset.toLong() + page.albums.size
        if (nextOffset > Int.MAX_VALUE) {
            return ArtistAlbumPageProgress(currentSource, requestedOffset, page.hasMore,
                "专辑分页位置异常，已暂停加载；请重新打开歌手页面。")
        }
        return ArtistAlbumPageProgress((currentSource + validAlbums).distinctBy { it.id },
            nextOffset.toInt(), page.hasMore)
    }
}

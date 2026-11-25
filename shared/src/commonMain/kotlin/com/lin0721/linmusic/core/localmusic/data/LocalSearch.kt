package com.lin0721.linmusic.core.localmusic.data

import com.lin0721.linmusic.core.localmusic.domain.LocalAlbum
import com.lin0721.linmusic.core.localmusic.domain.LocalArtist
import com.lin0721.linmusic.core.localmusic.domain.LocalLibraryIndex
import com.lin0721.linmusic.core.localmusic.domain.LocalTrack

data class LocalSearchResult(
    val songs: List<LocalTrack>,
    val albums: List<LocalAlbum>,
    val artists: List<LocalArtist>
) {
    val isEmpty: Boolean get() = songs.isEmpty() && albums.isEmpty() && artists.isEmpty()
}

object LocalSearch {

    fun search(query: String, index: LocalLibraryIndex): LocalSearchResult {
        val needle = normalize(query)
        if (needle.isEmpty()) return LocalSearchResult(emptyList(), emptyList(), emptyList())

        val songs = index.tracks.filter { track ->
            normalize(track.title).contains(needle) ||
                normalize(track.artist).contains(needle) ||
                (track.album != null && normalize(track.album).contains(needle))
        }

        val albums = index.knownAlbums.filter { album ->
            normalize(album.name).contains(needle) ||
                normalize(album.artist).contains(needle)
        }

        val artists = index.artists.filter { artist ->
            normalize(artist.name).contains(needle)
        }

        return LocalSearchResult(songs = songs, albums = albums, artists = artists)
    }

    internal fun normalize(raw: String): String {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) return ""
        val sb = StringBuilder(trimmed.length)
        for (ch in trimmed) {
            val half = when {
                ch == '\u3000' -> ' '
                ch in '\uFF01'..'\uFF5E' -> (ch.code - 0xFEE0).toChar()
                else -> ch
            }
            sb.append(half.lowercaseChar())
        }
        return sb.toString()
    }
}

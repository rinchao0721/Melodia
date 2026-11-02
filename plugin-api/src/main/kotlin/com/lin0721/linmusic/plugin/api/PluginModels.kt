package com.lin0721.linmusic.plugin.api

data class TrackQuery(
    val songId: String,
    val title: String,
    val artists: String,
    val album: String,
    val durationMs: Long,
    val origin: String
)

// lrc 与 ttml 至少给一项，同时给时优先用 ttml
data class LyricsResult(val lrc: String? = null, val ttml: String? = null)

data class ContentTrack(
    val id: String,
    val title: String,
    val artist: String = "",
    val album: String = "",
    val cover: String = "",
    val durationMs: Long = 0
)

data class ContentPlaylist(
    val name: String,
    val cover: String = "",
    val description: String = "",
    val tracks: List<ContentTrack> = emptyList()
)

data class NowPlayingSong(val id: String, val songId: Long?, val title: String, val artist: String, val cover: String)

data class PlaybackState(val playing: Boolean, val positionMs: Long, val durationMs: Long)

data class QueueSong(val songId: Long, val title: String = "", val artist: String = "", val cover: String = "")

data class LibraryPlaylist(val id: Long, val name: String, val cover: String, val trackCount: Int, val liked: Boolean)

data class NeteaseArtistRef(val id: Long, val name: String)

data class NeteaseTrack(
    val songId: Long?,
    val title: String,
    val artist: String,
    val artists: List<NeteaseArtistRef>,
    val album: String,
    val albumId: Long,
    val cover: String,
    val durationMs: Long
)

data class NeteaseLyricLine(val timeMs: Long, val text: String, val translation: String?)

data class NeteasePlaylist(
    val id: Long,
    val name: String,
    val cover: String,
    val description: String,
    val playCount: Long,
    val trackCount: Int,
    val subscribed: Boolean,
    val creator: String,
    val tracks: List<NeteaseTrack>
)

data class NeteaseAlbum(val id: Long, val name: String, val cover: String, val artist: String)

data class NeteaseArtist(
    val id: Long,
    val name: String,
    val cover: String,
    val avatar: String = "",
    val description: String = "",
    val albumCount: Int = 0,
    val songCount: Int = 0
)

enum class NeteaseSearchType(val key: String) {
    SONG("song"),
    ALBUM("album"),
    ARTIST("artist"),
    PLAYLIST("playlist")
}

sealed interface NeteaseSearchItem {
    data class Song(val track: NeteaseTrack) : NeteaseSearchItem
    data class Album(val album: NeteaseAlbum) : NeteaseSearchItem
    data class Artist(val artist: NeteaseArtist) : NeteaseSearchItem
    data class Playlist(val playlist: NeteasePlaylist) : NeteaseSearchItem
}

data class NeteaseSearchPage(val total: Int, val hasMore: Boolean, val items: List<NeteaseSearchItem>)

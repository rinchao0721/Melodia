package com.lin0721.linmusic.core.localmusic.domain

const val UNKNOWN_ARTIST_NAME = "未知艺术家"
const val UNKNOWN_ALBUM_NAME = "未知专辑"
const val UNKNOWN_ALBUM_KEY = "unknown"
private const val VARIOUS_ARTISTS = "多位歌手"

// 多人合唱常见分隔符
private val ARTIST_SEPARATOR = Regex("""\s*(?:,|，|、|/|;|；|&|＆|\s+feat\.?\s+|\s+ft\.?\s+)\s*""", RegexOption.IGNORE_CASE)

fun splitArtists(raw: String): List<String> {
    val names = raw.split(ARTIST_SEPARATOR).map { it.trim() }.filter { it.isNotEmpty() }.distinct()
    return names.ifEmpty { listOf(UNKNOWN_ARTIST_NAME) }
}

data class LocalArtist(
    val name: String,
    val tracks: List<LocalTrack>,
    val albumCount: Int
)

data class LocalAlbum(
    val key: String,
    val name: String,
    val artist: String,
    val year: Int?,
    val tracks: List<LocalTrack>
) {
    val totalDurationMs: Long get() = tracks.sumOf { it.durationMs }
    val isUnknown: Boolean get() = key == UNKNOWN_ALBUM_KEY

    // 未知专辑混着各歌手的歌，借用任何一首的封面都会误导
    val coverSourceLocation: String? get() = if (isUnknown) null else tracks.first().location
}

data class LocalFolderGroup(
    val path: String,
    val tracks: List<LocalTrack>
) {
    val name: String get() = path.trimEnd('/', '\\').substringAfterLast('/').substringAfterLast('\\').ifBlank { path }
}

data class LocalLibraryIndex(
    val tracks: List<LocalTrack>,
    val artists: List<LocalArtist>,
    val albums: List<LocalAlbum>,
    val folders: List<LocalFolderGroup>
) {
    private val artistsByName = artists.associateBy { it.name }
    private val albumsByKey = albums.associateBy { it.key }
    private val foldersByPath = folders.associateBy { it.path }

    val totalSizeBytes: Long get() = tracks.sumOf { it.sizeBytes }

    fun artist(name: String): LocalArtist? = artistsByName[name]
    fun album(key: String): LocalAlbum? = albumsByKey[key]
    fun folder(path: String): LocalFolderGroup? = foldersByPath[path]

    val knownAlbums: List<LocalAlbum> by lazy { albums.filterNot { it.isUnknown } }

    val recentAlbums: List<LocalAlbum> by lazy {
        knownAlbums.sortedByDescending { album -> album.tracks.maxOf { it.dateAddedMs } }
    }

    companion object {
        val EMPTY = LocalLibraryIndex(emptyList(), emptyList(), emptyList(), emptyList())
    }
}

fun albumTitleOf(album: String?): String? =
    album?.trim()?.takeIf { it.isNotEmpty() && it != "<unknown>" }

val LocalTrack.albumTitle: String? get() = albumTitleOf(album)

val LocalTrack.albumName: String get() = albumTitle ?: UNKNOWN_ALBUM_NAME

val LocalTrack.primaryArtist: String get() = splitArtists(artist).first()

private val LocalTrack.albumOwner: String get() = albumArtist?.let { splitArtists(it).first() } ?: primaryArtist

// 按专辑名 + 专辑歌手聚合
val LocalTrack.albumKey: String
    get() = albumTitle?.let { "$it|$albumOwner" } ?: UNKNOWN_ALBUM_KEY

val LocalTrack.displayTrackNumber: Int?
    get() = trackNumber?.rem(1000)?.takeIf { it > 0 }

private val albumTrackOrder = compareBy<LocalTrack>(
    { it.trackNumber == null },
    { it.trackNumber ?: 0 },
    { it.title.lowercase() }
)

fun buildLocalLibraryIndex(tracks: List<LocalTrack>): LocalLibraryIndex {
    val albums = tracks.groupBy { it.albumKey }.map { (key, albumTracks) ->
        val owners = albumTracks.groupingBy { it.albumOwner }.eachCount()
        LocalAlbum(
            key = key,
            name = albumTracks.first().albumName,
            artist = when {
                key == UNKNOWN_ALBUM_KEY && owners.size > 1 -> VARIOUS_ARTISTS
                else -> owners.maxByOrNull { it.value }?.key ?: UNKNOWN_ARTIST_NAME
            },
            year = albumTracks.mapNotNull { it.year }.maxOrNull(),
            tracks = albumTracks.sortedWith(albumTrackOrder)
        )
    }.sortedWith(compareBy<LocalAlbum> { it.isUnknown }.thenBy { it.name.lowercase() })

    val tracksByArtist = LinkedHashMap<String, MutableList<LocalTrack>>()
    tracks.forEach { track ->
        splitArtists(track.artist).forEach { name -> tracksByArtist.getOrPut(name) { mutableListOf() } += track }
    }
    val artists = tracksByArtist.map { (name, artistTracks) ->
        LocalArtist(
            name = name,
            tracks = artistTracks.sortedBy { it.title.lowercase() },
            albumCount = artistTracks.map { it.albumKey }.distinct().count { it != UNKNOWN_ALBUM_KEY }
        )
    }.sortedBy { it.name.lowercase() }

    val folders = tracks
        .mapNotNull { track -> (track.folder ?: folderPathOf(track.path))?.let { it to track } }
        .groupBy({ it.first }, { it.second })
        .map { (folderPath, folderTracks) ->
            LocalFolderGroup(
                path = folderPath,
                tracks = folderTracks.sortedBy { it.title.lowercase() }
            )
        }
        .sortedBy { it.name.lowercase() }

    return LocalLibraryIndex(tracks = tracks, artists = artists, albums = albums, folders = folders)
}

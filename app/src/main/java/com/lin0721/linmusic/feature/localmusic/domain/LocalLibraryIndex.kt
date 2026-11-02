package com.lin0721.linmusic.feature.localmusic.domain

import android.net.Uri

const val UNKNOWN_ARTIST_NAME = "未知艺术家"
const val UNKNOWN_ALBUM_NAME = "未知专辑"
const val UNKNOWN_ALBUM_KEY = "unknown"
private const val VARIOUS_ARTISTS = "多位歌手"

// 多人合唱常见分隔符；"/" 会误拆 AC/DC 这类名字，本地曲库里合唱远比这种名字常见，接受这个代价
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
    val coverSourceUri: Uri? get() = if (isUnknown) null else tracks.first().uri
}

data class LocalFolderGroup(
    val path: String,
    val tracks: List<LocalTrack>
) {
    val name: String get() = path.substringAfterLast('/').ifBlank { path }
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

    // "未知专辑"不计入专辑数，也不上首页
    val knownAlbums: List<LocalAlbum> by lazy { albums.filterNot { it.isUnknown } }

    val recentAlbums: List<LocalAlbum> by lazy {
        knownAlbums.sortedByDescending { album -> album.tracks.maxOf { it.dateAddedMs } }
    }

    companion object {
        val EMPTY = LocalLibraryIndex(emptyList(), emptyList(), emptyList(), emptyList())
    }
}

// MediaStore 的文件夹占位已在扫描层核实，展示层不能再根据同名丢弃真实标签。
fun albumTitleOf(album: String?): String? =
    album?.trim()?.takeIf { it.isNotEmpty() && it != "<unknown>" }

val LocalTrack.albumTitle: String? get() = albumTitleOf(album)

val LocalTrack.albumName: String get() = albumTitle ?: UNKNOWN_ALBUM_NAME

val LocalTrack.primaryArtist: String get() = splitArtists(artist).first()

private val LocalTrack.albumOwner: String get() = albumArtist?.let { splitArtists(it).first() } ?: primaryArtist

// 按专辑名 + 专辑歌手聚合：同名专辑按歌手区分，合辑靠专辑歌手标签归到一起
val LocalTrack.albumKey: String
    get() = albumTitle?.let { "$it|$albumOwner" } ?: UNKNOWN_ALBUM_KEY

// 去掉碟号部分
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
            tracks = artistTracks.sortedWith(compareBy({ it.albumName.lowercase() }, { it.trackNumber ?: Int.MAX_VALUE }, { it.title.lowercase() })),
            albumCount = artistTracks.mapTo(HashSet()) { it.albumKey }.count { it != UNKNOWN_ALBUM_KEY }
        )
    }.sortedWith(compareByDescending<LocalArtist> { it.tracks.size }.thenBy { it.name.lowercase() })

    val folders = tracks.groupBy { it.folderPath }
        .mapNotNull { (path, folderTracks) -> path?.let { LocalFolderGroup(it, folderTracks) } }
        .sortedBy { it.name.lowercase() }

    return LocalLibraryIndex(tracks = tracks, artists = artists, albums = albums, folders = folders)
}

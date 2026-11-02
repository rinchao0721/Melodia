package com.lin0721.linmusic.feature.localmusic.data.scan

import android.content.ContentUris
import android.content.Context
import android.database.Cursor
import android.os.Build
import android.provider.MediaStore
import com.lin0721.linmusic.core.log.AppLogger
import com.lin0721.linmusic.feature.localmusic.data.db.LocalTrackEntity
import com.lin0721.linmusic.feature.localmusic.domain.LocalTrackSource
import com.lin0721.linmusic.feature.localmusic.domain.folderPathOf

private const val TAG = "MediaStoreScanner"
private const val UNKNOWN_TITLE = "未知曲目"
private const val UNKNOWN_ARTIST = "未知艺术家"

class MediaStoreScanner(private val context: Context) {

    private val albumReader = MediaStoreAlbumReader(context)

    // 失败返回 null 而非空列表，否则同步会误删整库
    fun scan(): List<LocalTrackEntity>? {
        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ALBUM,
            MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.SIZE,
            MediaStore.Audio.Media.DATA,
            MediaStore.Audio.Media.DATE_ADDED,
            MediaStore.Audio.Media.DATE_MODIFIED,
            MediaStore.Audio.Media.YEAR,
            MediaStore.Audio.Media.TRACK
        ) + if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) arrayOf(MediaStore.Audio.Media.ALBUM_ARTIST) else emptyArray()
        val selection = "${MediaStore.Audio.Media.IS_MUSIC} != 0"
        val results = mutableListOf<LocalTrackEntity>()
        return runCatching {
            val cursor = context.contentResolver.query(
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                projection,
                selection,
                null,
                null
            ) ?: return null
            cursor.use {
                val idCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
                val titleCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
                val artistCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
                val albumCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
                val durationCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
                val sizeCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.SIZE)
                val dataCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA)
                val dateAddedCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATE_ADDED)
                val dateModifiedCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATE_MODIFIED)
                val albumArtistCol = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    cursor.getColumnIndex(MediaStore.Audio.Media.ALBUM_ARTIST)
                } else {
                    -1
                }
                val yearCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.YEAR)
                val trackCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TRACK)
                while (cursor.moveToNext()) {
                    val id = cursor.getLong(idCol)
                    val uri = ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id)
                    val path = cursor.getString(dataCol)
                    val album = resolveMediaStoreAlbum(cursor.getString(albumCol), folderPathOf(path)) {
                        albumReader.readAlbum(uri)
                    }
                    val (artist, title) = resolveArtistAndTitle(
                        rawArtist = cursor.getString(artistCol).orEmpty(),
                        rawTitle = cursor.getString(titleCol) ?: UNKNOWN_TITLE
                    )
                    results += LocalTrackEntity(
                        uri = uri.toString(),
                        mediaStoreId = id,
                        songId = null,
                        title = title,
                        artist = artist,
                        album = album,
                        durationMs = cursor.getLong(durationCol),
                        sizeBytes = cursor.getLong(sizeCol),
                        path = path,
                        dateAddedMs = cursor.getLong(dateAddedCol) * 1000,
                        dateModifiedMs = cursor.getLong(dateModifiedCol) * 1000,
                        source = LocalTrackSource.EXTERNAL.name,
                        albumArtist = albumArtistCol.takeIf { it >= 0 }?.let { cursor.getString(it) }?.trim()?.takeIf { it.isNotEmpty() },
                        year = cursor.getIntOrNull(yearCol)?.takeIf { it > 0 },
                        trackNumber = cursor.getIntOrNull(trackCol)?.takeIf { it > 0 }
                    )
                }
            }
            results.toList()
        }.onFailure { AppLogger.e(TAG, "MediaStore 音频扫描失败", it) }.getOrNull()
    }
}

private fun Cursor.getIntOrNull(index: Int): Int? = if (isNull(index)) null else getInt(index)

// 歌手缺失且标题形如"歌手 - 歌名"时，从标题拆出歌手
internal fun resolveArtistAndTitle(rawArtist: String, rawTitle: String): Pair<String, String> {
    val artistMissing = rawArtist.isBlank() || rawArtist == "<unknown>" || rawArtist == UNKNOWN_ARTIST
    if (artistMissing && rawTitle.contains(" - ")) {
        val parts = rawTitle.split(" - ", limit = 2)
        return parts[0].trim().ifBlank { UNKNOWN_ARTIST } to parts[1].trim().ifBlank { rawTitle }
    }
    return rawArtist.ifBlank { UNKNOWN_ARTIST } to rawTitle
}

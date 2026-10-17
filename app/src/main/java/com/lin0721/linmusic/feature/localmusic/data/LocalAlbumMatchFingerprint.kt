package com.lin0721.linmusic.feature.localmusic.data

import com.lin0721.linmusic.feature.localmusic.data.batch.AlbumBatchFingerprint
import com.lin0721.linmusic.feature.localmusic.domain.LocalAlbum
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * Identifies exactly the local content reviewed by a person or by a batch pass.
 * Keep this representation compatible with existing album reports. Playback song IDs and scan dates
 * are deliberately excluded: saving a reference or rescanning unchanged files must not reopen review.
 */
internal fun localAlbumMatchFingerprint(local: LocalAlbum): String = AlbumBatchFingerprint.of(buildJsonObject {
    put("key", local.key)
    put("name", local.name)
    put("artist", local.artist)
    put("tracks", buildJsonArray {
        local.tracks.sortedBy { it.uri.toString() }.forEach { track ->
            add(buildJsonObject {
                put("uri", track.uri.toString())
                put("title", track.title)
                put("artist", track.artist)
                put("album", track.album)
                put("albumArtist", track.albumArtist)
                put("duration", track.durationMs)
                put("size", track.sizeBytes)
                put("number", track.trackNumber)
                put("source", track.source.name)
                put("path", track.path)
                put("year", track.year)
            })
        }
    })
})

package com.lin0721.linmusic.core.playlistexternal

import com.lin0721.linmusic.core.model.Album
import com.lin0721.linmusic.core.model.Artist
import com.lin0721.linmusic.core.model.PlaylistTrackId
import com.lin0721.linmusic.core.model.Track
import com.lin0721.linmusic.core.player.QueueItem
import com.lin0721.linmusic.core.source.ExternalTrack

fun ExternalTrack.toPlaylistTrack(): Track = Track(
    id = queueSongId,
    name = name,
    ar = artistList.ifEmpty { listOf(artists) }.filter { it.isNotBlank() }.map { Artist(name = it) },
    al = Album(name = albumName, picUrl = coverUrl),
    dt = durationMs
)

fun ExternalTrack.toQueueItem(): QueueItem = QueueItem(
    songId = queueSongId,
    title = name,
    artist = artists,
    coverUrl = coverUrl,
    external = this
)

// 外部歌曲插到第一首比它更早加入的网易云曲目之前，与网易云新加歌曲置顶的顺序一致；
// 插入点还在未加载部分时先不显示，等分页加载到那里，避免先出现在末尾再跳走
fun mergeExternalTracks(
    neteaseTracks: List<Track>,
    trackIds: List<PlaylistTrackId>,
    entries: List<PlaylistExternalEntry>,
    fullyLoaded: Boolean
): List<Track> {
    if (entries.isEmpty()) return neteaseTracks
    val addedAt = trackIds.associate { it.id to it.at }
    val pending = entries.sortedByDescending { it.addedAt }.toMutableList()
    val result = ArrayList<Track>(neteaseTracks.size + pending.size)
    for (track in neteaseTracks) {
        val at = addedAt[track.id] ?: 0L
        while (pending.isNotEmpty() && pending.first().addedAt > at) {
            result.add(pending.removeAt(0).track.toPlaylistTrack())
        }
        result.add(track)
    }
    if (fullyLoaded) pending.forEach { result.add(it.track.toPlaylistTrack()) }
    return result
}

fun List<PlaylistExternalEntry>.toTrackIds(): List<PlaylistTrackId> =
    map { PlaylistTrackId(id = it.track.queueSongId, at = it.addedAt) }

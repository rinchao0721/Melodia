package com.lin0721.linmusic.feature.recent.domain

import com.lin0721.linmusic.feature.recent.data.RecentAlbumItem
import com.lin0721.linmusic.feature.recent.data.RecentPlaylistItem
import com.lin0721.linmusic.feature.recent.data.RecentSongItem

// DTO → 领域模型
fun RecentSongItem.toDomain(): RecentSong = RecentSong(
    track = data,
    playTime = playTime,
    playedAtText = formatClockTime(playTime)
)

fun RecentPlaylistItem.toDomain(): RecentPlaylist = RecentPlaylist(
    id = data.id,
    name = data.name,
    coverUrl = data.picUrl,
    creatorName = data.creator?.nickname.orEmpty(),
    playTime = playTime,
    playedAtText = formatClockTime(playTime)
)

fun RecentAlbumItem.toDomain(): RecentAlbum = RecentAlbum(
    id = data.id,
    name = data.name,
    coverUrl = data.picUrl,
    artistName = data.artist?.name.orEmpty(),
    playTime = playTime,
    playedAtText = formatClockTime(playTime)
)

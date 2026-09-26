package com.lin0721.linmusic.feature.cloud.domain

import com.lin0721.linmusic.feature.cloud.data.CloudSongItem

private const val MATCH_TYPE_UNMATCHED = "unmatched"

fun CloudSongItem.toDomain(nowMs: Long): CloudSong {
    val isUnmatched = matchType == MATCH_TYPE_UNMATCHED
    // 已匹配歌曲的顶层 artist/album 仍是用户最初上传时的自报值，服务端只更新了 simpleSong.ar/al，
    // 真机核实过（"未知" vs simpleSong.ar 里的真实歌手名）
    val matchedArtist = simpleSong.ar.joinToString(" / ") { it.name }.ifBlank { null }
    val matchedAlbum = simpleSong.al.name.ifBlank { null }
    return CloudSong(
        songId = songId,
        name = songName,
        artist = if (isUnmatched) artist else (matchedArtist ?: artist),
        album = if (isUnmatched) album else (matchedAlbum ?: album),
        bitrate = bitrate,
        fileSizeBytes = fileSize,
        fileSizeText = formatFileSize(fileSize),
        addedAtText = formatUploadedAt(addTime, nowMs),
        // 未识别歌曲的 al.picUrl 是网易云通用占位图，不是真封面，置空走占位图标
        coverUrl = if (isUnmatched) null else simpleSong.al.picUrl.ifBlank { null },
        isUnmatched = isUnmatched
    )
}

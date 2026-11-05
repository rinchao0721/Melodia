package com.lin0721.linmusic.core.source

// 外部平台歌曲在网易云曲库里对应的歌曲 id，远程 UNM 只认网易云 id
fun interface NeteaseSongLookup {
    suspend fun find(track: ExternalTrack): Long?
}

package com.lin0721.linmusic.core.listentogether.data

import com.lin0721.linmusic.core.player.QueueItem

// 点歌面板的曲目来源：未输入时列出我喜欢的音乐，输入后搜索网易云单曲
interface RoomSongSource {
    suspend fun liked(limit: Int): List<QueueItem>
    suspend fun search(keyword: String, limit: Int): List<QueueItem>
}

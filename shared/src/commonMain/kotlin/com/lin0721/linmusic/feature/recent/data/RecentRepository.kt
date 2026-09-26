package com.lin0721.linmusic.feature.recent.data

import com.lin0721.linmusic.feature.recent.domain.RecentAlbum
import com.lin0721.linmusic.feature.recent.domain.RecentPlaylist
import com.lin0721.linmusic.feature.recent.domain.RecentSong
import kotlinx.coroutines.flow.Flow

// 最近播放数据仓储（feature/recent），首页最近播放区块与侧边栏二级页共用
interface RecentRepository {

    // 最近播放歌曲
    fun getRecentSongs(): Flow<Result<List<RecentSong>>>

    // 最近播放歌单
    fun getRecentPlaylists(): Flow<Result<List<RecentPlaylist>>>

    // 最近播放专辑
    fun getRecentAlbums(): Flow<Result<List<RecentAlbum>>>
}

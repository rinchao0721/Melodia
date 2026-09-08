package com.lin0721.linmusic.feature.player.data

import com.lin0721.linmusic.core.model.Track
import com.lin0721.linmusic.feature.player.domain.SongWikiData
import kotlinx.coroutines.flow.Flow

// 播放器详情页数据仓储（player 业务域，仅服务于 PlayerViewModel）
interface PlayerRepository {

    fun getSongDetail(songId: Long): Flow<Result<Track>>

    // 批量获取歌曲详情，单次不要超过服务端约1000首的上限，调用方自行分批
    fun getSongDetails(songIds: List<Long>): Flow<Result<List<Track>>>

    // 获取合并后的歌曲详情与百科信息
    fun getSongWiki(songId: Long): Flow<Result<SongWikiData>>
}

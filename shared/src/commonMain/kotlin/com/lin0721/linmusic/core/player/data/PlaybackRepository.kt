package com.lin0721.linmusic.core.player.data

import com.lin0721.linmusic.core.model.Track
import com.lin0721.linmusic.core.player.domain.LyricLine
import kotlinx.coroutines.flow.Flow

// 播放引擎数据仓储（core 共享能力，服务于 PlayerManager/FloatingLyricService 及多个域的推荐入口）
interface PlaybackRepository {

    // 获取歌曲播放链接
    fun getSongUrl(songId: Long): Flow<Result<String>>

    // 获取歌曲歌词（已解析 LRC 格式）
    fun getLyrics(songId: Long): Flow<Result<List<LyricLine>>>

    fun getSimilarSongs(songId: Long): Flow<Result<List<Track>>>

    fun getIntelligenceSongs(songId: Long, playlistId: Long): Flow<Result<List<Track>>>

    // 打卡上报，sourceId 暂用 songId 本身代替；开始播放时报，进「最近播放」
    fun reportStartPlay(songId: Long): Flow<Result<Unit>>

    // 打卡上报：离开歌曲时报实际播放时长，涨「听歌排行」计数
    fun reportPlayEnd(songId: Long, playedSeconds: Long): Flow<Result<Unit>>
}

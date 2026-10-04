package com.lin0721.linmusic.core.player.data

import com.lin0721.linmusic.core.model.Track
import com.lin0721.linmusic.core.player.PlaySource
import com.lin0721.linmusic.core.player.domain.LyricLine
import com.lin0721.linmusic.core.player.domain.LyricsKind
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharedFlow

// 播放直链信息（包含直链地址与是否为 VIP 试听片段）
data class SongPlaybackInfo(
    val url: String,
    val isFreeTrial: Boolean = false
)

data class LyricsContent(
    val lines: List<LyricLine>,
    val kind: LyricsKind
)

// 播放引擎数据仓储（core 共享能力，服务于 PlayerManager/FloatingLyricService 及多个域的推荐入口）
interface PlaybackRepository {

    // 获取歌曲播放链接
    fun getSongUrl(songId: Long): Flow<Result<String>>

    // 获取歌曲播放链接详情（包含是否为试听片段）
    fun getSongPlaybackInfo(songId: Long): Flow<Result<SongPlaybackInfo>>

    // 获取歌曲歌词（已解析 LRC 格式）
    fun getLyrics(songId: Long): Flow<Result<LyricsContent>>

    // 获取歌曲原始歌词文本（优先原生 YRC 逐字，无 YRC 则回退常规 LRC）
    fun getRawLyrics(songId: Long): Flow<Result<String>>

    // 获取单曲详情
    fun getSongDetail(songId: Long): Flow<Result<Track>>

    fun getSimilarSongs(songId: Long): Flow<Result<List<Track>>>

    fun getIntelligenceSongs(songId: Long, playlistId: Long): Flow<Result<List<Track>>>

    // 打卡上报：开始播放时报，进「最近播放」；来源为歌单时以歌单 id 作容器，否则退回 songId
    fun reportStartPlay(songId: Long, source: PlaySource?): Flow<Result<Unit>>

    // 打卡上报：离开歌曲时报实际播放时长，涨「听歌排行」计数
    fun reportPlayEnd(songId: Long, playedSeconds: Long, source: PlaySource?): Flow<Result<Unit>>

    // 歌单播放记录写入本地后发出，供首页即时刷新最近播放
    val playlistRecorded: SharedFlow<Long>
}

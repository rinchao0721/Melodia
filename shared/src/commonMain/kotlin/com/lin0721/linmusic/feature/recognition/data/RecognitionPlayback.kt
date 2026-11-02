package com.lin0721.linmusic.feature.recognition.data

import com.lin0721.linmusic.feature.recognition.domain.RecognitionNowPlaying
import kotlinx.coroutines.flow.Flow

// 识别模块对播放器的全部依赖，日后独立成应用时只需替换这一个实现
interface RecognitionPlayback {
    val nowPlaying: Flow<RecognitionNowPlaying?>
    val positionMs: Flow<Long>

    fun isPlaying(): Boolean

    // 从通知等无界面入口冷启动时，先连上播放服务并等上次的队列恢复完成
    suspend fun ensureReady()
    fun pause()
    fun resume()

    // 插到"下一首"并立即从 startPositionMs 播放，保留原队列
    fun playFrom(songId: Long, title: String, artists: String, coverUrl: String, startPositionMs: Long)
}

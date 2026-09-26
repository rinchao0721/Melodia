package com.lin0721.linmusic.feature.recognition.domain

import kotlinx.serialization.Serializable

// startTimeMs 为匹配片段起点在原曲中的毫秒位置
data class RecognitionCandidate(
    val songId: Long,
    val title: String,
    val artists: String,
    val album: String,
    val coverUrl: String,
    val startTimeMs: Long,
    val durationMs: Long
)

// 麦克风识别外放声音；内录识别本机其他 App 正在播放的声音（Android 10+）
enum class RecognitionMode { MICROPHONE, PLAYBACK }

// 播放器当前曲目，用于识别结果卡片标出"正在播放"
data class RecognitionNowPlaying(
    val songId: Long,
    val isPlaying: Boolean
)

enum class AttemptStatus { MATCHING, MISSED, SILENT, HIT }

// 单个 3 秒窗口的匹配状态，startSecond 为窗口在录音中的起点
data class MatchAttempt(
    val startSecond: Int,
    val status: AttemptStatus
)

// levels 每格对应固定时长的音量 RMS，只含已录部分；round 为第几轮录制（内录未命中会自动再录一轮）
data class RecognitionProgress(
    val round: Int = 1,
    val levels: List<Float> = emptyList(),
    val recordedMs: Long = 0L,
    val attempts: List<MatchAttempt> = emptyList()
)

sealed interface RecognitionOutcome {
    data class Found(
        val candidates: List<RecognitionCandidate>,
        val windowStartSecond: Int
    ) : RecognitionOutcome

    data object NotFound : RecognitionOutcome

    // 所有窗口都低于静音阈值
    data object Silent : RecognitionOutcome
}

enum class RecognitionFailure {
    PERMISSION_DENIED,
    RECORDER_UNAVAILABLE,
    ENGINE_UNAVAILABLE,
    NETWORK
}

class RecognitionException(
    val failure: RecognitionFailure,
    message: String,
    cause: Throwable? = null
) : Exception(message, cause)

// 识别历史单条；同一首歌多次识别各记一条，id 区分
@Serializable
data class RecognitionHistoryEntry(
    val id: String,
    val songId: Long,
    val title: String,
    val artists: String,
    val coverUrl: String,
    val startTimeMs: Long,
    val recognizedAt: Long
)

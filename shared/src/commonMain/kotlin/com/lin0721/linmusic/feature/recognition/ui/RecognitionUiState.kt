package com.lin0721.linmusic.feature.recognition.ui

import com.lin0721.linmusic.feature.recognition.domain.RecognitionCandidate
import com.lin0721.linmusic.feature.recognition.domain.RecognitionMode
import com.lin0721.linmusic.feature.recognition.domain.RecognitionProgress

enum class RecognitionFailedReason {
    NOT_FOUND,
    SILENT,
    NETWORK,
    ENGINE_UNAVAILABLE,
    RECORDER_UNAVAILABLE,
    // 识别中途被用户点播打断
    STOPPED,
    UNKNOWN
}

sealed interface RecognitionUiState {
    data object Idle : RecognitionUiState

    // 进入识别页时的方式选择弹窗；lastMode 用于标出「上次使用」
    data class ChoosingMode(val lastMode: RecognitionMode?) : RecognitionUiState

    data class PermissionRequired(val permanentlyDenied: Boolean) : RecognitionUiState

    data class Listening(val progress: RecognitionProgress) : RecognitionUiState

    data class Found(
        val candidates: List<RecognitionCandidate>,
        val windowStartSecond: Int,
        val progress: RecognitionProgress
    ) : RecognitionUiState

    data class Failed(
        val reason: RecognitionFailedReason,
        val progress: RecognitionProgress
    ) : RecognitionUiState
}

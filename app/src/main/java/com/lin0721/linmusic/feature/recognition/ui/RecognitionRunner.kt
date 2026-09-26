package com.lin0721.linmusic.feature.recognition.ui

import com.lin0721.linmusic.core.log.AppLogger
import com.lin0721.linmusic.feature.recognition.data.RecognitionHistoryPreferences
import com.lin0721.linmusic.feature.recognition.domain.RecognitionException
import com.lin0721.linmusic.feature.recognition.domain.RecognitionFailure
import com.lin0721.linmusic.feature.recognition.domain.RecognitionOutcome
import com.lin0721.linmusic.feature.recognition.domain.RecognitionProgress
import com.lin0721.linmusic.feature.recognition.engine.RecognitionSession
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

private const val TAG = "RecognitionRunner"

// 识别页（麦克风）与后台内录服务共用：跑完一次识别，命中写历史，结局映射为界面状态
internal suspend fun runRecognition(
    session: RecognitionSession,
    historyPreferences: RecognitionHistoryPreferences,
    onProgress: (RecognitionProgress) -> Unit
): RecognitionUiState = coroutineScope {
    val progressJob = launch { session.progress.collect(onProgress) }
    try {
        when (val outcome = session.run()) {
            is RecognitionOutcome.Found -> {
                runCatching { historyPreferences.record(outcome.candidates.first()) }
                    .onFailure { AppLogger.e(TAG, "保存识别历史失败", it) }
                RecognitionUiState.Found(outcome.candidates, outcome.windowStartSecond, session.progress.value)
            }
            RecognitionOutcome.NotFound ->
                RecognitionUiState.Failed(RecognitionFailedReason.NOT_FOUND, session.progress.value)
            RecognitionOutcome.Silent ->
                RecognitionUiState.Failed(RecognitionFailedReason.SILENT, session.progress.value)
        }
    } catch (e: CancellationException) {
        throw e
    } catch (e: RecognitionException) {
        AppLogger.w(TAG, "识别失败 ${e.failure}: ${e.message}", e)
        when (e.failure) {
            RecognitionFailure.PERMISSION_DENIED -> RecognitionUiState.PermissionRequired(false)
            RecognitionFailure.RECORDER_UNAVAILABLE ->
                RecognitionUiState.Failed(RecognitionFailedReason.RECORDER_UNAVAILABLE, session.progress.value)
            RecognitionFailure.ENGINE_UNAVAILABLE ->
                RecognitionUiState.Failed(RecognitionFailedReason.ENGINE_UNAVAILABLE, session.progress.value)
            RecognitionFailure.NETWORK ->
                RecognitionUiState.Failed(RecognitionFailedReason.NETWORK, session.progress.value)
        }
    } catch (e: Exception) {
        AppLogger.e(TAG, "识别异常", e)
        RecognitionUiState.Failed(RecognitionFailedReason.UNKNOWN, session.progress.value)
    } finally {
        progressJob.cancel()
    }
}

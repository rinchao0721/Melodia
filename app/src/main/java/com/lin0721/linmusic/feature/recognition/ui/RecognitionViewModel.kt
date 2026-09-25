package com.lin0721.linmusic.feature.recognition.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lin0721.linmusic.core.log.AppLogger
import com.lin0721.linmusic.feature.recognition.data.RecognitionHistoryPreferences
import com.lin0721.linmusic.feature.recognition.data.RecognitionPlayback
import com.lin0721.linmusic.feature.recognition.data.RecognitionRepository
import com.lin0721.linmusic.feature.recognition.domain.RecognitionCandidate
import com.lin0721.linmusic.feature.recognition.domain.RecognitionException
import com.lin0721.linmusic.feature.recognition.domain.RecognitionFailure
import com.lin0721.linmusic.feature.recognition.domain.RecognitionHistoryEntry
import com.lin0721.linmusic.feature.recognition.domain.RecognitionNowPlaying
import com.lin0721.linmusic.feature.recognition.domain.RecognitionOutcome
import com.lin0721.linmusic.feature.recognition.domain.RecognitionProgress
import com.lin0721.linmusic.feature.recognition.engine.AfpFingerprintEngine
import com.lin0721.linmusic.feature.recognition.engine.AudioCapture
import com.lin0721.linmusic.feature.recognition.engine.RecognitionSession
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID

private const val TAG = "RecognitionViewModel"

class RecognitionViewModel(
    private val repository: RecognitionRepository,
    private val historyPreferences: RecognitionHistoryPreferences,
    private val playback: RecognitionPlayback,
    private val recorder: AudioCapture,
    private val engine: AfpFingerprintEngine
) : ViewModel() {

    private val _uiState = MutableStateFlow<RecognitionUiState>(RecognitionUiState.Idle)
    val uiState: StateFlow<RecognitionUiState> = _uiState.asStateFlow()

    val history: StateFlow<List<RecognitionHistoryEntry>> = historyPreferences.history
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val nowPlaying: StateFlow<RecognitionNowPlaying?> = playback.nowPlaying
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val playbackPosition: StateFlow<Long> = playback.positionMs
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0L)

    private var sessionJob: Job? = null

    // 仅当本界面暂停了播放、且之后用户没有点播时，关闭时才恢复
    private var pausedPlayback = false

    fun onOpened() {
        viewModelScope.launch {
            runCatching { engine.warmUp() }
                .onFailure { AppLogger.w(TAG, "指纹引擎预热失败，首次识别时重试", it) }
        }
    }

    fun onPermissionDenied(permanentlyDenied: Boolean) {
        sessionJob?.cancel()
        _uiState.value = RecognitionUiState.PermissionRequired(permanentlyDenied)
    }

    fun start() {
        sessionJob?.cancel()
        if (!pausedPlayback && playback.isPlaying()) {
            playback.pause()
            pausedPlayback = true
        }
        val session = RecognitionSession(recorder, engine, repository)
        _uiState.value = RecognitionUiState.Listening(RecognitionProgress())
        sessionJob = viewModelScope.launch {
            val progressJob = launch {
                session.progress.collect { progress ->
                    _uiState.update { state ->
                        if (state is RecognitionUiState.Listening) state.copy(progress = progress) else state
                    }
                }
            }
            val nextState = try {
                when (val outcome = session.run()) {
                    is RecognitionOutcome.Found -> {
                        saveToHistory(outcome.candidates.first())
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
            _uiState.value = nextState
        }
    }

    private fun playCandidate(candidate: RecognitionCandidate) {
        playFrom(candidate.songId, candidate.title, candidate.artists, candidate.coverUrl, candidate.startTimeMs)
    }

    // 已是当前曲目则不打断，否则从命中位置起播；播放状态由卡片与迷你播放条展示
    fun openCandidate(candidate: RecognitionCandidate) {
        if (nowPlaying.value?.songId != candidate.songId) playCandidate(candidate)
    }

    fun openHistory(entry: RecognitionHistoryEntry) {
        if (nowPlaying.value?.songId != entry.songId) playHistory(entry)
    }

    private fun playHistory(entry: RecognitionHistoryEntry) {
        playFrom(entry.songId, entry.title, entry.artists, entry.coverUrl, entry.startTimeMs)
    }

    fun removeHistory(id: String) {
        viewModelScope.launch {
            runCatching { historyPreferences.remove(id) }
                .onFailure { AppLogger.e(TAG, "删除识别历史失败", it) }
        }
    }

    fun clearHistory() {
        viewModelScope.launch {
            runCatching { historyPreferences.clear() }
                .onFailure { AppLogger.e(TAG, "清空识别历史失败", it) }
        }
    }

    fun onClosed() {
        stopSession(RecognitionUiState.Idle)
        engine.release()
        if (pausedPlayback) {
            playback.resume()
            pausedPlayback = false
        }
    }

    override fun onCleared() {
        engine.release()
        super.onCleared()
    }

    private fun playFrom(songId: Long, title: String, artists: String, coverUrl: String, startTimeMs: Long) {
        // 录音中点播会把自己的声音录进去，先停掉本次识别
        if (_uiState.value is RecognitionUiState.Listening) {
            stopSession(RecognitionUiState.Failed(RecognitionFailedReason.STOPPED, currentProgress()))
        }
        playback.playFrom(songId, title, artists, coverUrl, startTimeMs)
        pausedPlayback = false
    }

    private fun stopSession(nextState: RecognitionUiState) {
        sessionJob?.cancel()
        sessionJob = null
        _uiState.value = nextState
    }

    private fun currentProgress(): RecognitionProgress = when (val state = _uiState.value) {
        is RecognitionUiState.Listening -> state.progress
        is RecognitionUiState.Found -> state.progress
        is RecognitionUiState.Failed -> state.progress
        else -> RecognitionProgress()
    }

    private suspend fun saveToHistory(candidate: RecognitionCandidate) {
        val entry = RecognitionHistoryEntry(
            id = UUID.randomUUID().toString(),
            songId = candidate.songId,
            title = candidate.title,
            artists = candidate.artists,
            coverUrl = candidate.coverUrl,
            startTimeMs = candidate.startTimeMs,
            recognizedAt = System.currentTimeMillis()
        )
        runCatching { historyPreferences.add(entry) }
            .onFailure { AppLogger.e(TAG, "保存识别历史失败", it) }
    }
}

package com.lin0721.linmusic.feature.recognition.ui

import android.content.Intent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lin0721.linmusic.core.log.AppLogger
import com.lin0721.linmusic.feature.recognition.data.RecognitionHistoryPreferences
import com.lin0721.linmusic.feature.recognition.data.RecognitionPlayback
import com.lin0721.linmusic.feature.recognition.data.RecognitionPreferences
import com.lin0721.linmusic.feature.recognition.data.RecognitionRepository
import com.lin0721.linmusic.feature.recognition.domain.RecognitionCandidate
import com.lin0721.linmusic.feature.recognition.domain.RecognitionHistoryEntry
import com.lin0721.linmusic.feature.recognition.domain.RecognitionMode
import com.lin0721.linmusic.feature.recognition.domain.RecognitionNowPlaying
import com.lin0721.linmusic.feature.recognition.domain.RecognitionProgress
import com.lin0721.linmusic.feature.recognition.engine.AfpFingerprintEngine
import com.lin0721.linmusic.feature.recognition.engine.AudioCapture
import com.lin0721.linmusic.feature.recognition.engine.RecognitionSession
import com.lin0721.linmusic.feature.recognition.service.PlaybackRecognitionLauncher
import com.lin0721.linmusic.feature.recognition.service.PlaybackRecognitionState
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

private const val TAG = "RecognitionViewModel"

// 麦克风识别由本 ViewModel 直接驱动；内录交给后台服务，这里只订阅其状态
class RecognitionViewModel(
    private val repository: RecognitionRepository,
    private val historyPreferences: RecognitionHistoryPreferences,
    private val playback: RecognitionPlayback,
    private val recorder: AudioCapture,
    private val engine: AfpFingerprintEngine,
    private val preferences: RecognitionPreferences,
    private val playbackState: PlaybackRecognitionState,
    private val playbackLauncher: PlaybackRecognitionLauncher
) : ViewModel() {

    private val _uiState = MutableStateFlow<RecognitionUiState>(RecognitionUiState.Idle)
    val uiState: StateFlow<RecognitionUiState> = _uiState.asStateFlow()

    private val _activeMode = MutableStateFlow<RecognitionMode?>(null)
    val activeMode: StateFlow<RecognitionMode?> = _activeMode.asStateFlow()

    val playbackSupported: Boolean get() = playbackLauncher.isSupported

    val pendingOpenResult: StateFlow<Boolean> = playbackState.pendingOpenResult

    val history: StateFlow<List<RecognitionHistoryEntry>> = historyPreferences.history
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val nowPlaying: StateFlow<RecognitionNowPlaying?> = playback.nowPlaying
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val playbackPosition: StateFlow<Long> = playback.positionMs
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0L)

    private var sessionJob: Job? = null
    private var mirrorJob: Job? = null

    // 仅当本界面暂停了播放、且之后用户没有点播时，关闭时才恢复
    private var pausedPlayback = false

    // 从通知进入或后台内录仍在进行时，直接展示内录状态，否则弹出方式选择
    fun onOpened() {
        viewModelScope.launch {
            runCatching { engine.warmUp() }
                .onFailure { AppLogger.w(TAG, "指纹引擎预热失败，首次识别时重试", it) }
        }
        if (playbackState.consumeOpenResult() || playbackState.isRunning) {
            showPlaybackState()
            return
        }
        viewModelScope.launch {
            _uiState.value = RecognitionUiState.ChoosingMode(preferences.lastMode())
        }
    }

    // 识别页已打开时又点了结果通知
    fun onOpenResultRequested() {
        if (playbackState.consumeOpenResult()) showPlaybackState()
    }

    fun onPermissionDenied(permanentlyDenied: Boolean) {
        stopMicrophoneSession()
        _uiState.value = RecognitionUiState.PermissionRequired(permanentlyDenied)
    }

    fun startMicrophone() {
        if (_activeMode.value == RecognitionMode.PLAYBACK) stopPlaybackMirror(cancelService = true)
        _activeMode.value = RecognitionMode.MICROPHONE
        viewModelScope.launch { preferences.setLastMode(RecognitionMode.MICROPHONE) }

        sessionJob?.cancel()
        if (!pausedPlayback && playback.isPlaying()) {
            playback.pause()
            pausedPlayback = true
        }
        val session = RecognitionSession(capture = recorder, fingerprinter = engine, matcher = repository)
        _uiState.value = RecognitionUiState.Listening(RecognitionProgress())
        sessionJob = viewModelScope.launch {
            val result = runRecognition(session, historyPreferences) { progress ->
                _uiState.update { state ->
                    if (state is RecognitionUiState.Listening) state.copy(progress = progress) else state
                }
            }
            _uiState.value = result
        }
    }

    // resultCode / data 为录屏授权结果
    fun startPlayback(resultCode: Int, data: Intent) {
        stopMicrophoneSession()
        // 内录排除了 Melodia 自身，之前为麦克风暂停的播放可以恢复
        resumePausedPlayback()
        _activeMode.value = RecognitionMode.PLAYBACK
        playbackState.update(RecognitionUiState.Listening(RecognitionProgress()))
        mirrorPlaybackState()
        if (!playbackLauncher.start(resultCode, data)) {
            playbackState.update(RecognitionUiState.Failed(RecognitionFailedReason.RECORDER_UNAVAILABLE, RecognitionProgress()))
        }
    }

    // 录屏授权被拒：回到方式选择
    fun onPlaybackConsentDenied() {
        viewModelScope.launch {
            _uiState.value = RecognitionUiState.ChoosingMode(preferences.lastMode())
        }
    }

    // 聆听中点「取消」；麦克风会话随页面关闭一并停止，内录需要显式通知服务
    fun cancelCurrent() {
        if (_activeMode.value == RecognitionMode.PLAYBACK) playbackLauncher.cancel()
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

    // 内录会话不随页面关闭而停止，切回原 App 后继续在后台识别
    fun onClosed() {
        stopMicrophoneSession()
        stopPlaybackMirror(cancelService = false)
        _activeMode.value = null
        _uiState.value = RecognitionUiState.Idle
        engine.release()
        resumePausedPlayback()
    }

    override fun onCleared() {
        engine.release()
        super.onCleared()
    }

    private fun playFrom(songId: Long, title: String, artists: String, coverUrl: String, startTimeMs: Long) {
        // 麦克风录音中点播会把自己的声音录进去，先停掉本次识别；内录排除了自身，不受影响
        if (_activeMode.value == RecognitionMode.MICROPHONE && _uiState.value is RecognitionUiState.Listening) {
            stopMicrophoneSession()
            _uiState.value = RecognitionUiState.Failed(RecognitionFailedReason.STOPPED, currentProgress())
        }
        playback.playFrom(songId, title, artists, coverUrl, startTimeMs)
        pausedPlayback = false
    }

    private fun showPlaybackState() {
        stopMicrophoneSession()
        _activeMode.value = RecognitionMode.PLAYBACK
        mirrorPlaybackState()
    }

    private fun mirrorPlaybackState() {
        mirrorJob?.cancel()
        mirrorJob = viewModelScope.launch {
            playbackState.state.collect { _uiState.value = it }
        }
    }

    private fun stopPlaybackMirror(cancelService: Boolean) {
        mirrorJob?.cancel()
        mirrorJob = null
        if (cancelService) playbackLauncher.cancel()
    }

    private fun stopMicrophoneSession() {
        sessionJob?.cancel()
        sessionJob = null
    }

    private fun resumePausedPlayback() {
        if (pausedPlayback) {
            playback.resume()
            pausedPlayback = false
        }
    }

    private fun currentProgress(): RecognitionProgress = when (val state = _uiState.value) {
        is RecognitionUiState.Listening -> state.progress
        is RecognitionUiState.Found -> state.progress
        is RecognitionUiState.Failed -> state.progress
        else -> RecognitionProgress()
    }
}

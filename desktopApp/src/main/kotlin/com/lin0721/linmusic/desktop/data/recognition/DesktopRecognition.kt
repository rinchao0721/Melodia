package com.lin0721.linmusic.desktop.data.recognition

import com.lin0721.linmusic.core.log.AppLogger
import com.lin0721.linmusic.core.player.QueueItem
import com.lin0721.linmusic.desktop.platform.windows.audio.WasapiLoopbackCapture
import com.lin0721.linmusic.desktop.player.DesktopPlaybackController
import com.lin0721.linmusic.feature.recognition.data.RecognitionHistoryPreferences
import com.lin0721.linmusic.feature.recognition.data.RecognitionPreferences
import com.lin0721.linmusic.feature.recognition.data.RecognitionRepository
import com.lin0721.linmusic.feature.recognition.domain.RecognitionCandidate
import com.lin0721.linmusic.feature.recognition.domain.RecognitionHistoryEntry
import com.lin0721.linmusic.feature.recognition.domain.RecognitionMode
import com.lin0721.linmusic.feature.recognition.domain.RecognitionProgress
import com.lin0721.linmusic.feature.recognition.engine.AudioCapture
import com.lin0721.linmusic.feature.recognition.engine.FingerprintGenerator
import com.lin0721.linmusic.feature.recognition.engine.RecognitionSession
import com.lin0721.linmusic.feature.recognition.ui.RecognitionFailedReason
import com.lin0721.linmusic.feature.recognition.ui.RecognitionUiState
import com.lin0721.linmusic.feature.recognition.ui.runRecognition
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

private const val TAG = "DesktopRecognition"

// 识曲弹窗的状态与动作，均在主线程调用
class DesktopRecognition(
    private val repository: RecognitionRepository,
    private val historyPreferences: RecognitionHistoryPreferences,
    private val preferences: RecognitionPreferences,
    private val fingerprinter: FingerprintGenerator,
    private val controller: DesktopPlaybackController,
    private val scope: CoroutineScope,
    // 打开弹窗时提前加载指纹引擎
    private val warmUp: suspend () -> Unit = {},
    private val captureFactory: (RecognitionMode) -> AudioCapture = ::defaultCapture
) {

    val loopbackSupported: Boolean = WasapiLoopbackCapture.isSupported

    private val _uiState = MutableStateFlow<RecognitionUiState>(RecognitionUiState.Idle)
    val uiState: StateFlow<RecognitionUiState> = _uiState.asStateFlow()

    private val _mode = MutableStateFlow(RecognitionMode.MICROPHONE)
    val mode: StateFlow<RecognitionMode> = _mode.asStateFlow()

    val history: StateFlow<List<RecognitionHistoryEntry>> = historyPreferences.history
        .stateIn(scope, SharingStarted.Eagerly, emptyList())

    private var sessionJob: Job? = null
    private var openJob: Job? = null
    private var progress = RecognitionProgress()

    // 仅当识曲暂停了播放、且之后用户没有点播时，关闭时才恢复
    private var pausedPlayback = false

    // 弹窗打开即按上次方式开始
    fun open() {
        scope.launch { warmUp() }
        openJob?.cancel()
        openJob = scope.launch {
            val last = preferences.lastMode()
            start(if (last == RecognitionMode.PLAYBACK && !loopbackSupported) RecognitionMode.MICROPHONE else last ?: RecognitionMode.MICROPHONE)
        }
    }

    fun start(mode: RecognitionMode = _mode.value) {
        val target = if (mode == RecognitionMode.PLAYBACK && !loopbackSupported) RecognitionMode.MICROPHONE else mode
        sessionJob?.cancel()
        _mode.value = target
        // 外放与内录都会录进自身播放的声音
        if (controller.playWhenReady.value) {
            controller.pause()
            pausedPlayback = true
        }
        progress = RecognitionProgress()
        _uiState.value = RecognitionUiState.Listening(progress)
        sessionJob = scope.launch {
            preferences.setLastMode(target)
            val session = RecognitionSession(captureFactory(target), fingerprinter, matcher = repository)
            val result = runRecognition(session, historyPreferences) { latest ->
                progress = latest
                _uiState.value = RecognitionUiState.Listening(latest)
            }
            _uiState.value = result
        }
    }

    // 停止本次识别但保留弹窗，播放仍保持暂停直到关闭弹窗
    fun cancel() {
        openJob?.cancel()
        sessionJob?.cancel()
        sessionJob = null
        _uiState.value = RecognitionUiState.Idle
    }

    fun close() {
        openJob?.cancel()
        sessionJob?.cancel()
        sessionJob = null
        _uiState.value = RecognitionUiState.Idle
        if (pausedPlayback) {
            pausedPlayback = false
            controller.resume()
        }
    }

    fun play(candidate: RecognitionCandidate) =
        playFrom(candidate.songId, candidate.title, candidate.artists, candidate.coverUrl, candidate.startTimeMs)

    fun play(entry: RecognitionHistoryEntry) =
        playFrom(entry.songId, entry.title, entry.artists, entry.coverUrl, entry.startTimeMs)

    fun removeHistory(id: String) {
        scope.launch {
            runCatching { historyPreferences.remove(id) }.onFailure { AppLogger.e(TAG, "删除识别历史失败", it) }
        }
    }

    fun clearHistory() {
        scope.launch {
            runCatching { historyPreferences.clear() }.onFailure { AppLogger.e(TAG, "清空识别历史失败", it) }
        }
    }

    private fun playFrom(songId: Long, title: String, artists: String, coverUrl: String, startPositionMs: Long) {
        // 识别中点播会把播放声录进去，直接中止本次识别
        if (sessionJob?.isActive == true) {
            sessionJob?.cancel()
            _uiState.value = RecognitionUiState.Failed(RecognitionFailedReason.STOPPED, progress)
        }
        pausedPlayback = false
        controller.playNextNow(QueueItem(songId = songId, title = title, artist = artists, coverUrl = coverUrl), startPositionMs)
    }
}

private fun defaultCapture(mode: RecognitionMode): AudioCapture = when (mode) {
    RecognitionMode.MICROPHONE -> JavaSoundMicrophoneCapture()
    RecognitionMode.PLAYBACK -> WasapiLoopbackCapture()
}

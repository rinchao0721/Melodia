package com.lin0721.linmusic.feature.recognition.service

import com.lin0721.linmusic.feature.recognition.ui.RecognitionUiState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.getAndUpdate

// 后台内录会话的唯一状态源：服务写入，识别页订阅；保留最后一次结局供通知正文点开时展示
class PlaybackRecognitionState {

    private val _state = MutableStateFlow<RecognitionUiState>(RecognitionUiState.Idle)
    val state: StateFlow<RecognitionUiState> = _state.asStateFlow()

    val isRunning: Boolean get() = _state.value is RecognitionUiState.Listening

    private val _pendingOpenResult = MutableStateFlow(false)
    val pendingOpenResult: StateFlow<Boolean> = _pendingOpenResult.asStateFlow()

    internal fun update(state: RecognitionUiState) {
        _state.value = state
    }

    fun requestOpenResult() {
        _pendingOpenResult.value = true
    }

    // 只允许消费一次，避免识别页重复打开同一结果
    fun consumeOpenResult(): Boolean = _pendingOpenResult.getAndUpdate { false }
}

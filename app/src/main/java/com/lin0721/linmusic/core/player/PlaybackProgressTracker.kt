package com.lin0721.linmusic.core.player

import androidx.media3.common.Player
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

// 进度轮询：按可调间隔从控制器读取播放进度，并维护当前时长
class PlaybackProgressTracker(
    private val scope: CoroutineScope,
    private val controller: MediaControllerHolder
) {

    private val _currentPosition = MutableStateFlow(0L)
    val currentPosition: StateFlow<Long> = _currentPosition.asStateFlow()

    private val _duration = MutableStateFlow(0L)
    val duration: StateFlow<Long> = _duration.asStateFlow()

    private val _updateInterval = MutableStateFlow(1000L)
    val updateInterval: StateFlow<Long> = _updateInterval.asStateFlow()

    fun setUpdateInterval(intervalMs: Long) {
        _updateInterval.value = intervalMs
    }

    fun setPosition(positionMs: Long) {
        _currentPosition.value = positionMs
    }

    fun setDuration(durationMs: Long) {
        _duration.value = durationMs
    }

    // 从控制器同步最新时长，控制器未就绪时保留已有有效时长
    fun updateDurationFromController() {
        val dur = controller.duration
        if (dur > 0L) {
            _duration.value = dur
        }
    }

    // 立即重置当前进度与时长，避免上一首歌曲的数据在加载新歌期间残留导致进度条闪烁；断点续播可保留已有时长
    fun resetTo(startPosition: Long, preserveDuration: Boolean = false) {
        _currentPosition.value = startPosition
        if (!preserveDuration) {
            _duration.value = 0L
        }
    }

    fun start(isPlaying: () -> Boolean, onTick: suspend (positionMs: Long) -> Unit) {
        scope.launch {
            while (true) {
                // 只有当播放器处于就绪播放状态时，才去轮询更新当前进度，防止在切歌缓冲时读到残留或脏进度值
                val isReady = controller.playbackState == Player.STATE_READY
                if (isPlaying() && isReady) {
                    val curPos = controller.currentPosition
                    _currentPosition.value = curPos
                    onTick(curPos)
                }
                delay(_updateInterval.value)
            }
        }
    }
}

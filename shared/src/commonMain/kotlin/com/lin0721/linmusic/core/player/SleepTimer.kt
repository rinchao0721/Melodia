package com.lin0721.linmusic.core.player

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlin.time.TimeSource

private val sleepTimerClockOrigin = TimeSource.Monotonic.markNow()

private fun monotonicNowMs(): Long = sleepTimerClockOrigin.elapsedNow().inWholeMilliseconds

// 定时停止播放：按秒倒计时，归零时回调停止
class SleepTimer(
    private val scope: CoroutineScope,
    private val nowMs: () -> Long = ::monotonicNowMs,
    private val onFinish: () -> Unit
) {

    private val _remaining = MutableStateFlow(0L)
    val remaining: StateFlow<Long> = _remaining.asStateFlow()

    private var job: Job? = null

    fun start(minutes: Int) {
        job?.cancel()
        if (minutes <= 0) {
            _remaining.value = 0L
            return
        }
        val durationMs = minutes * 60 * 1000L
        val deadlineMs = nowMs() + durationMs
        _remaining.value = durationMs
        job = scope.launch {
            while (_remaining.value > 0L) {
                delay(minOf(1000L, _remaining.value))
                val nextVal = (deadlineMs - nowMs()).coerceAtLeast(0L)
                if (nextVal <= 0L) {
                    _remaining.value = 0L
                    onFinish()
                    break
                } else {
                    _remaining.value = nextVal
                }
            }
        }
    }

    fun cancel() {
        job?.cancel()
    }
}

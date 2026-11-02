package com.lin0721.linmusic.core.player.floatinglyric

import com.lin0721.linmusic.core.log.AppLogger
import com.lin0721.linmusic.core.player.LyricsResolver
import com.lin0721.linmusic.core.player.PlaybackController
import com.lin0721.linmusic.core.player.domain.LyricLine
import com.lin0721.linmusic.core.player.domain.LyricProgressTracker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlin.time.TimeSource

private const val TAG = "FloatingLyricSession"

// 后台时播放器进度默认 1s 才更新一次，外推上限需覆盖一个轮询周期
private const val MAX_AHEAD_MS = 1_200L
private const val TICK_INTERVAL_MS = 100L

// 不依赖 PlayerViewModel 的歌词进度源，供 Android 悬浮窗 Service 使用
class FloatingLyricSession(
    private val controller: PlaybackController,
    private val lyricsResolver: LyricsResolver,
    // 歌词来源开关变化时重新拉取，与播放页保持一致
    private val reloadSignal: Flow<Any> = flowOf(Unit)
) {
    private val _lines = MutableStateFlow<List<LyricLine>>(emptyList())
    val lines: StateFlow<List<LyricLine>> = _lines.asStateFlow()

    private val _primaryIndex = MutableStateFlow(-1)
    val primaryIndex: StateFlow<Int> = _primaryIndex.asStateFlow()

    private val progress = LyricProgressTracker()

    private var anchorPositionMs = Long.MIN_VALUE
    private var anchorMark = TimeSource.Monotonic.markNow()

    fun start(scope: CoroutineScope) {
        scope.launch {
            controller.nowPlaying.map { it?.songId }.distinctUntilChanged().collectLatest { songId ->
                clear()
                if (songId == null) return@collectLatest
                reloadSignal.collectLatest { load(songId) }
            }
        }
        scope.launch {
            controller.currentPosition.collect { advance(positionNow(), isSeek = false) }
        }
        // 进度两次更新之间按外推时间推进，换行不必等下一次轮询
        scope.launch {
            while (true) {
                if (controller.isPlaying.value) advance(positionNow(), isSeek = false)
                delay(TICK_INTERVAL_MS)
            }
        }
    }

    // 以最近一次进度为锚点按真实时间外推，暂停时停在锚点；只在主线程调用
    fun positionNow(): Long {
        val current = controller.currentPosition.value
        if (current != anchorPositionMs) {
            anchorPositionMs = current
            anchorMark = TimeSource.Monotonic.markNow()
        }
        if (!controller.isPlaying.value) return current
        return current + anchorMark.elapsedNow().inWholeMilliseconds.coerceIn(0L, MAX_AHEAD_MS)
    }

    private suspend fun load(songId: Long) {
        lyricsResolver.lyricsFor(songId).collect { result ->
            result.onSuccess { lines ->
                _lines.value = lines
                progress.reset()
                advance(positionNow(), isSeek = true)
            }.onFailure {
                AppLogger.w(TAG, "悬浮歌词拉取失败 songId=$songId", it)
                clear()
            }
        }
    }

    private fun advance(positionMs: Long, isSeek: Boolean) {
        val lines = _lines.value
        if (lines.isEmpty()) return
        _primaryIndex.value = progress.update(lines, positionMs, isSeek).primaryIndex
    }

    private fun clear() {
        _lines.value = emptyList()
        progress.reset()
        _primaryIndex.value = -1
    }
}

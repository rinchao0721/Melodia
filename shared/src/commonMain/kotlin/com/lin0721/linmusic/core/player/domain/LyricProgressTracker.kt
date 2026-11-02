package com.lin0721.linmusic.core.player.domain

// 跨进度回调持有歌词播放状态：advance() 的暂留判断依赖上一次状态，并按进度跳变识别跳转
class LyricProgressTracker {
    var state: LyricPlaybackState = LyricPlaybackState()
        private set

    private var lastPositionMs: Long? = null

    fun reset() {
        state = LyricPlaybackState()
        lastPositionMs = null
    }

    fun update(lines: List<LyricLine>, positionMs: Long, isSeek: Boolean = false): LyricPlaybackState {
        if (lines.isEmpty()) return state
        val previousPosition = lastPositionMs
        // 进度回退、或前进远超一个轮询周期，都视为跳转：此时不做旧行暂留，
        // 直接对齐目标位置，否则拖动进度条会看到一路残留的高亮
        val discontinuity = previousPosition != null && (
            positionMs < previousPosition - SEEK_BACKWARD_TOLERANCE_MS ||
                positionMs - previousPosition > SEEK_FORWARD_THRESHOLD_MS
            )
        val seek = isSeek || discontinuity
        // 小幅采样回退虽不算 seek，也不能继续传给时间轴，否则新句会亮起后又熄灭。
        // 仅保持已接受的位置下限，不外推时间；真实跳转仍立即对齐目标位置。
        val stablePosition = if (seek || previousPosition == null) positionMs
            else maxOf(previousPosition, positionMs)
        state = LyricTimeline.advance(
            lines = lines,
            positionMs = stablePosition,
            previous = state,
            isSeek = seek
        )
        lastPositionMs = stablePosition
        return state
    }

    private companion object {
        // 进度小幅回退不算跳转：播放器采样与四舍五入会带来 1~2ms 级别的抖动
        const val SEEK_BACKWARD_TOLERANCE_MS = 250L

        // 全屏播放页的进度轮询间隔是 50ms，取 2000ms 是留足余量避免把正常推进误判为跳转
        const val SEEK_FORWARD_THRESHOLD_MS = 2000L
    }
}

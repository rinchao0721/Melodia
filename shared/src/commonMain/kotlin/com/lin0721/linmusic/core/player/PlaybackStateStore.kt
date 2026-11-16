package com.lin0721.linmusic.core.player

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

// 播放状态持久化：封装队列、播放模式与当前曲目的读写，写入统一异步执行
class PlaybackStateStore(
    private val scope: CoroutineScope,
    private val preferences: PlaybackPreferences
) {

    val playModeChanges: Flow<PlayMode> get() = preferences.playMode

    suspend fun loadPlayMode(): PlayMode = preferences.playMode.first()

    suspend fun loadQueueState(): QueueState = preferences.queueState.first()

    // 上次退出时的曲目、进度与总时长，无历史记录时返回 null
    suspend fun loadLastTrack(): PlaybackState? =
        preferences.playbackState.first().takeIf { it.songId != -1L }

    fun savePlayMode(mode: PlayMode) {
        scope.launch { preferences.savePlayMode(mode) }
    }

    // 队列内容在协程内读取，保证落盘的是写入时刻的最新队列。
    // 保存的下标必须是 original（未打乱）顺序里的位置：currentIndex 在随机模式下是打乱后 playItems 里的下标，
    // 跟这里落盘的 original 顺序对不上，直接存会导致下次恢复时定位到完全不同的曲目
    fun saveQueue(queue: PlaybackQueue) {
        scope.launch(Dispatchers.Default) {
            preferences.saveQueueState(
                queue.original,
                queue.currentIndexInOriginal(),
                queue.playContext.value,
                queue.playSource.value
            )
        }
    }

    // 状态在协程内构建，保证落盘的是写入时刻的播放进度
    fun savePlaybackState(provider: () -> PlaybackState) {
        scope.launch { preferences.savePlaybackState(provider()) }
    }

    // 退出前挂起等待进度落盘完成
    suspend fun savePlaybackStateNow(state: PlaybackState) {
        preferences.savePlaybackState(state)
    }

    fun saveRecentFmTracks(tracks: List<RecentFmTrack>) {
        if (tracks.isEmpty()) return
        scope.launch { preferences.saveRecentFmTracks(tracks) }
    }
}

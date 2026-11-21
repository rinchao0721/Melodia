package com.lin0721.linmusic.core.player

import com.lin0721.linmusic.core.model.PlaybackQuality
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

// 正在播放曲目的平台无关快照；本地歌曲 mediaId 为负数占位
data class NowPlaying(
    val mediaId: String,
    val title: String,
    val artist: String,
    val artworkUri: String?
) {
    val songId: Long? get() = mediaId.toLongOrNull()
}

// 接口实际下发的音质档位，按 songId 绑定，界面需与当前曲目比对后再展示
data class PlaybackLevel(val songId: Long, val level: String)

private val NO_PLAYBACK_LEVEL: StateFlow<PlaybackLevel?> = MutableStateFlow(null)
private val NO_PLAYBACK_QUALITY: StateFlow<PlaybackQuality?> = MutableStateFlow(null)

// 播放门面的跨平台契约，各平台以各自播放引擎实现
interface PlaybackController {

    companion object {
        const val CONTEXT_INTELLIGENCE = "intelligence"
        const val CONTEXT_PODCAST = "podcast"
        const val CONTEXT_PERSONAL_FM = "personal_fm"
    }

    val isPlaying: StateFlow<Boolean>

    // 播放意图：点了播放即为真，弱网缓冲期间 isPlaying 仍为 false
    val playWhenReady: StateFlow<Boolean>
    val nowPlaying: StateFlow<NowPlaying?>

    // 当前曲目的实际音质，未获取到（本地、缓存、换源）时为空；默认不提供
    val playbackLevel: StateFlow<PlaybackLevel?> get() = NO_PLAYBACK_LEVEL

    // 当前曲目的实际流参数与输出音质快照，默认不提供
    val playbackQuality: StateFlow<PlaybackQuality?> get() = NO_PLAYBACK_QUALITY
    val currentPosition: StateFlow<Long>

    // 实时播放进度（毫秒），供歌词渲染等需要平滑插值的场景使用
    fun currentPositionNow(): Long = currentPosition.value

    val duration: StateFlow<Long>
    val sleepTimerRemaining: StateFlow<Long>
    val playContext: StateFlow<String?>
    val playSource: StateFlow<PlaySource?>
    val currentIndex: StateFlow<Int>
    val playMode: StateFlow<PlayMode>
    val queue: StateFlow<List<QueueItem>>
    val currentQueueItem: StateFlow<QueueItem?>
    val previousQueueItem: StateFlow<QueueItem?>
    val nextQueueItem: StateFlow<QueueItem?>

    suspend fun initController()

    // startPositionMs 为起播进度，仅对起播的那一项生效
    fun playQueue(
        items: List<QueueItem>,
        startIndex: Int,
        playContext: String? = null,
        source: PlaySource? = null,
        startPositionMs: Long = 0L
    )
    fun playAudio(
        songId: Long,
        url: String,
        title: String,
        artist: String,
        coverUrl: String,
        startPosition: Long = 0,
        playContext: String? = null
    )
    fun addToPlayNext(items: List<QueueItem>)
    fun playNextNow(item: QueueItem, startPositionMs: Long = 0L) {
        if (queue.value.isEmpty()) {
            playQueue(listOf(item), 0, startPositionMs = startPositionMs)
        } else {
            addToPlayNext(listOf(item))
            playNext()
        }
    }
    fun appendToQueue(items: List<QueueItem>)

    // 大歌单先播后补：队列仍是 source 起播的那一份时，把后台补全的曲目接到末尾
    fun extendQueue(source: PlaySource, items: List<QueueItem>)
    fun playNext()
    fun playPrevious()
    fun skipToPrevious()
    fun playAtIndex(index: Int)
    fun removeFromQueue(index: Int)
    fun moveInQueue(from: Int, to: Int)
    fun clearQueue()
    // 切换或退出账户时停止播放并清空队列与播放上下文
    fun stopAndClearQueue()
    fun toggleShuffle()
    fun toggleRepeat()
    fun rotatePlayMode()
    fun pause()
    fun resume()
    fun togglePlayPause()
    fun seekTo(positionMs: Long)
    fun reloadCurrentTrack()
    fun setSleepTimer(minutes: Int)
    fun setPositionUpdateInterval(intervalMs: Long)

    // 滑动切歌撤销：目标曲目尚未真正开播前可撤回，已切过去则返回 false
    fun cancelPendingSkip(): Boolean
    fun disableRoaming()
    fun disableIntelligence()
    fun startPersonalFm(items: List<QueueItem>)
    fun disablePersonalFm()
    fun trashCurrentFmSong()
    fun switchPersonalFmMode(mode: PersonalFmMode)
}

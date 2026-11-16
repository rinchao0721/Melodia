package com.lin0721.linmusic.core.deeplink

import com.lin0721.linmusic.core.model.Track
import com.lin0721.linmusic.core.player.PlaybackController
import com.lin0721.linmusic.core.player.QueueItem

fun Track.toLinkQueueItem(): QueueItem = QueueItem(
    songId = id,
    title = name,
    artist = ar.joinToString(" / ") { it.name },
    coverUrl = al.picUrl
)

// 非破坏性地播放外链单曲：队列为空直接建单曲队列；已在队列中则切换并续播；否则插到下一首位置并立即播放
fun PlaybackController.playLinkTrack(track: Track, playContext: String = "链接") {
    val queueItem = track.toLinkQueueItem()
    val currentQueue = queue.value
    if (currentQueue.isEmpty()) {
        playQueue(listOf(queueItem), 0, playContext, null, 0L)
        return
    }
    val existingIndex = currentQueue.indexOfFirst { it.songId == queueItem.songId }
    if (existingIndex >= 0) {
        if (existingIndex == currentIndex.value) {
            if (!playWhenReady.value) {
                togglePlayPause()
            }
        } else {
            playAtIndex(existingIndex)
        }
    } else {
        playNextNow(queueItem, 0L)
    }
}

// 将外链单曲加入当前队列的下一首位置，不打断正在播放的歌曲
fun PlaybackController.enqueueLinkTrackNext(track: Track) {
    addToPlayNext(listOf(track.toLinkQueueItem()))
}

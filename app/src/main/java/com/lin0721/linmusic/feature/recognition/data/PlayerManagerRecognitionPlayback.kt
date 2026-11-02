package com.lin0721.linmusic.feature.recognition.data

import com.lin0721.linmusic.core.player.PlayerManager
import com.lin0721.linmusic.core.player.QueueItem
import com.lin0721.linmusic.feature.recognition.domain.RecognitionNowPlaying
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

class PlayerManagerRecognitionPlayback(
    private val playerManager: PlayerManager
) : RecognitionPlayback {

    override val nowPlaying: Flow<RecognitionNowPlaying?> =
        combine(playerManager.currentTrack, playerManager.playWhenReady) { track, playing ->
            track?.mediaId?.toLongOrNull()?.let { RecognitionNowPlaying(songId = it, isPlaying = playing) }
        }

    override val positionMs: Flow<Long> = playerManager.currentPosition

    // 用 playWhenReady 而非 isPlaying：弱网缓冲中也算在播，否则识别时漏暂停
    override fun isPlaying(): Boolean = playerManager.playWhenReady.value

    override suspend fun ensureReady() {
        playerManager.initController()
        playerManager.awaitQueueRestored()
    }

    override fun pause() = playerManager.pause()

    override fun resume() = playerManager.resume()

    override fun playFrom(songId: Long, title: String, artists: String, coverUrl: String, startPositionMs: Long) {
        val item = QueueItem(songId = songId, title = title, artist = artists, coverUrl = coverUrl)
        playerManager.playNextNow(item, startPositionMs.coerceAtLeast(0L))
    }
}

package com.lin0721.linmusic.desktop.ui.local

import com.lin0721.linmusic.core.player.PlaybackController
import com.lin0721.linmusic.desktop.data.local.LocalLibraryRepository
import com.lin0721.linmusic.desktop.data.local.LocalTrack
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

// 本地歌单的播放入口，侧栏菜单与歌单页共用
class LocalMusicActions(
    val repository: LocalLibraryRepository,
    private val controller: PlaybackController,
    private val scope: CoroutineScope
) {

    fun playContext(playlistId: Long): String = "$PLAY_CONTEXT_PREFIX$playlistId"

    fun playTracks(playlistId: Long, tracks: List<LocalTrack>, startIndex: Int) {
        if (tracks.isEmpty()) return
        controller.playQueue(tracks.map(repository::toQueueItem), startIndex.coerceIn(0, tracks.lastIndex), playContext(playlistId))
        repository.markPlayed(playlistId)
    }

    fun playPlaylist(playlistId: Long, shuffle: Boolean, onEmpty: () -> Unit) {
        scope.launch {
            val tracks = repository.tracksOf(playlistId).first()
            if (tracks.isEmpty()) onEmpty() else playTracks(playlistId, if (shuffle) tracks.shuffled() else tracks, 0)
        }
    }

    fun addToPlayNext(tracks: List<LocalTrack>) {
        if (tracks.isNotEmpty()) controller.addToPlayNext(tracks.map(repository::toQueueItem))
    }

    fun addPlaylistToPlayNext(playlistId: Long, onDone: (count: Int) -> Unit) {
        scope.launch {
            val tracks = repository.tracksOf(playlistId).first()
            addToPlayNext(tracks)
            onDone(tracks.size)
        }
    }

    companion object {
        const val PLAY_CONTEXT_PREFIX = "local_playlist:"
    }
}

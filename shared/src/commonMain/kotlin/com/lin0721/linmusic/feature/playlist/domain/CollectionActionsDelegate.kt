package com.lin0721.linmusic.feature.playlist.domain

import com.lin0721.linmusic.core.download.SongDownloader
import com.lin0721.linmusic.core.download.toDownloadTrackInfo
import com.lin0721.linmusic.core.download.toToastMessage
import com.lin0721.linmusic.core.model.Track
import com.lin0721.linmusic.core.network.ResourceProvider
import com.lin0721.linmusic.core.network.toUserMessage
import com.lin0721.linmusic.core.player.PlaySource
import com.lin0721.linmusic.core.player.PlaybackController
import com.lin0721.linmusic.core.player.QueueItem
import com.lin0721.linmusic.core.playlistmutation.PlaylistMutationBus
import com.lin0721.linmusic.core.playlistmutation.PlaylistMutationEvent
import com.lin0721.linmusic.feature.playlist.data.PlaylistRepository
import kotlinx.coroutines.flow.first

// 歌单与专辑整体操作：播放、加入下一首、批量下载、收藏。首页卡片与搜索结果共用
class CollectionActionsDelegate(
    private val playlistRepository: PlaylistRepository,
    private val playerManager: PlaybackController,
    private val songDownloadManager: SongDownloader,
    private val playlistMutationBus: PlaylistMutationBus,
    private val resourceProvider: ResourceProvider
) {

    private class Loaded(val id: Long, val name: String, val coverUrl: String, val tracks: List<Track>)

    // 歌单要补全被服务端截断的曲目，补全失败退回详情自带的部分曲目
    private suspend fun load(id: Long, isAlbum: Boolean, onToast: suspend (String) -> Unit): Loaded? {
        val detailFlow = if (isAlbum) playlistRepository.getAlbumDetail(id) else playlistRepository.getPlaylistDetail(id)
        val detail = detailFlow.first().getOrElse {
            onToast(it.toUserMessage(resourceProvider))
            return null
        }
        val tracks = if (isAlbum) {
            detail.tracks
        } else {
            playlistRepository.loadAllTracks(detail).first().getOrElse { detail.tracks }
        }
        return Loaded(detail.id, detail.name, detail.coverImgUrl, tracks)
    }

    private fun Track.toQueueItem() = QueueItem(id, name, ar.joinToString { it.name }, al.picUrl)

    suspend fun play(id: Long, isAlbum: Boolean, onToast: suspend (String) -> Unit) {
        val loaded = load(id, isAlbum, onToast) ?: return
        if (loaded.tracks.isEmpty()) {
            onToast("暂无可播放的歌曲")
            return
        }
        val kind = if (isAlbum) PlaySource.Kind.ALBUM else PlaySource.Kind.PLAYLIST
        playerManager.playQueue(
            loaded.tracks.map { it.toQueueItem() },
            0,
            loaded.name,
            PlaySource(kind, loaded.id, loaded.name, loaded.coverUrl)
        )
    }

    suspend fun addToPlayNext(id: Long, isAlbum: Boolean, onToast: suspend (String) -> Unit) {
        val loaded = load(id, isAlbum, onToast) ?: return
        if (loaded.tracks.isEmpty()) {
            onToast("暂无可播放的歌曲")
            return
        }
        playerManager.addToPlayNext(loaded.tracks.map { it.toQueueItem() })
        onToast("已添加至下一首播放")
    }

    suspend fun download(
        id: Long,
        isAlbum: Boolean,
        level: String,
        batchTag: String,
        onToast: suspend (String) -> Unit
    ) {
        val loaded = load(id, isAlbum, onToast) ?: return
        if (loaded.tracks.isEmpty()) {
            onToast(if (isAlbum) "该专辑没有可下载的歌曲" else "该歌单没有可下载的歌曲")
            return
        }
        val downloadTracks = loaded.tracks.map { it.toDownloadTrackInfo() }
        val result = songDownloadManager.enqueueBatch(downloadTracks, level, batchTag = batchTag, batchLabel = loaded.name)
        onToast(result.toToastMessage())
    }

    // 成功返回 true，由调用方同步各自的已收藏状态
    suspend fun subscribePlaylist(playlistId: Long, onToast: suspend (String) -> Unit): Boolean {
        val result = playlistRepository.subscribePlaylist(playlistId, subscribe = true).first()
        return result.fold(
            onSuccess = {
                playlistMutationBus.emit(PlaylistMutationEvent.Subscribed(playlistId, true))
                onToast("已收藏歌单")
                true
            },
            onFailure = {
                onToast(it.toUserMessage(resourceProvider))
                false
            }
        )
    }
}

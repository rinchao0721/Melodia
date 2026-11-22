package com.lin0721.linmusic.feature.playlist.domain

import com.lin0721.linmusic.core.download.SongDownloader
import com.lin0721.linmusic.core.download.toDownloadTrackInfo
import com.lin0721.linmusic.core.download.toToastMessage
import com.lin0721.linmusic.core.model.Track
import com.lin0721.linmusic.core.network.ResourceProvider
import com.lin0721.linmusic.core.network.toUserMessage
import com.lin0721.linmusic.core.platform.AlbumId
import com.lin0721.linmusic.core.platform.PlaylistId
import com.lin0721.linmusic.core.platform.UnifiedTrack
import com.lin0721.linmusic.core.platform.repository.PlatformAlbumRouter
import com.lin0721.linmusic.core.platform.repository.PlatformPlaylistRouter
import com.lin0721.linmusic.core.player.PlaySource
import com.lin0721.linmusic.core.player.PlaybackController
import com.lin0721.linmusic.core.player.QueueItem
import com.lin0721.linmusic.core.playlistmutation.PlaylistMutationBus
import com.lin0721.linmusic.core.playlistmutation.PlaylistMutationEvent
import com.lin0721.linmusic.core.source.MusicPlatform
import com.lin0721.linmusic.feature.playlist.data.PlaylistRepository
import kotlinx.coroutines.flow.first

// 歌单与专辑整体操作：播放、加入下一首、批量下载、收藏。首页卡片与搜索结果共用
class CollectionActionsDelegate(
    private val playlistRepository: PlaylistRepository,
    private val playerManager: PlaybackController,
    private val songDownloadManager: SongDownloader,
    private val playlistMutationBus: PlaylistMutationBus,
    private val resourceProvider: ResourceProvider,
    private val platformPlaylistRouter: PlatformPlaylistRouter? = null,
    private val platformAlbumRouter: PlatformAlbumRouter? = null
) {

    private class Loaded(val id: Long, val name: String, val coverUrl: String, val tracks: List<Track>)
    private class LoadedUnified(val name: String, val coverUrl: String, val tracks: List<UnifiedTrack>)

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

    private suspend fun loadUnified(
        entityId: String,
        isAlbum: Boolean,
        onToast: suspend (String) -> Unit
    ): LoadedUnified? {
        if (isAlbum) {
            val router = platformAlbumRouter ?: return null
            val albumId = AlbumId.parseOrNull(entityId) ?: return null
            val detail = router.getAlbumDetail(albumId).first().getOrElse {
                onToast(it.toUserMessage(resourceProvider))
                return null
            }
            return LoadedUnified(detail.name, detail.coverUrl, detail.tracks)
        } else {
            val router = platformPlaylistRouter ?: return null
            val playlistId = PlaylistId.parseOrNull(entityId) ?: return null
            val detail = router.getPlaylistDetail(playlistId).first().getOrElse {
                onToast(it.toUserMessage(resourceProvider))
                return null
            }
            val tracks = if (detail.tracks.size >= detail.trackCount) {
                detail.tracks
            } else {
                router.loadAllTracks(detail).first().getOrElse { detail.tracks }
            }
            return LoadedUnified(detail.name, detail.coverUrl, tracks)
        }
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

    suspend fun playEntity(entityId: String, isAlbum: Boolean, onToast: suspend (String) -> Unit) {
        val numericNetease = entityId.toLongOrNull()?.takeIf { it > 0L }
            ?: (if (isAlbum) AlbumId.parseOrNull(entityId)?.neteaseIdOrNull() else PlaylistId.parseOrNull(entityId)?.neteaseIdOrNull())
        if (numericNetease != null) {
            play(numericNetease, isAlbum, onToast)
            return
        }
        val loaded = loadUnified(entityId, isAlbum, onToast) ?: return
        if (loaded.tracks.isEmpty()) {
            onToast("暂无可播放的歌曲")
            return
        }
        val kind = if (isAlbum) PlaySource.Kind.ALBUM else PlaySource.Kind.PLAYLIST
        playerManager.playQueue(
            loaded.tracks.map(QueueItem::fromUnified),
            0,
            loaded.name,
            PlaySource(kind, 0L, loaded.name, loaded.coverUrl)
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

    suspend fun addEntityToPlayNext(entityId: String, isAlbum: Boolean, onToast: suspend (String) -> Unit) {
        val numericNetease = entityId.toLongOrNull()?.takeIf { it > 0L }
            ?: (if (isAlbum) AlbumId.parseOrNull(entityId)?.neteaseIdOrNull() else PlaylistId.parseOrNull(entityId)?.neteaseIdOrNull())
        if (numericNetease != null) {
            addToPlayNext(numericNetease, isAlbum, onToast)
            return
        }
        val loaded = loadUnified(entityId, isAlbum, onToast) ?: return
        if (loaded.tracks.isEmpty()) {
            onToast("暂无可播放的歌曲")
            return
        }
        playerManager.addToPlayNext(loaded.tracks.map(QueueItem::fromUnified))
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

    suspend fun subscribeEntity(entityId: String, onToast: suspend (String) -> Unit): Boolean {
        val pid = PlaylistId.parseOrNull(entityId) ?: return false
        pid.neteaseIdOrNull()?.let { return subscribePlaylist(it, onToast) }
        val router = platformPlaylistRouter ?: return false
        return router.subscribePlaylist(pid, subscribe = true).first().fold(
            onSuccess = {
                playlistMutationBus.emit(PlaylistMutationEvent.Subscribed(0L, true, entityId = pid))
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

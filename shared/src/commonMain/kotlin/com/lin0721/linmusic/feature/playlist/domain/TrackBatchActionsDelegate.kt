package com.lin0721.linmusic.feature.playlist.domain

import com.lin0721.linmusic.core.auth.UserPreferences
import com.lin0721.linmusic.core.download.DownloadTrackInfo
import com.lin0721.linmusic.core.download.SongDownloader
import com.lin0721.linmusic.core.download.toToastMessage
import com.lin0721.linmusic.core.download.yearFromEpochMillis
import com.lin0721.linmusic.core.model.Track
import com.lin0721.linmusic.core.network.ResourceProvider
import com.lin0721.linmusic.core.network.toUserMessage
import com.lin0721.linmusic.core.player.PlaybackController
import com.lin0721.linmusic.core.player.QueueItem
import com.lin0721.linmusic.core.playlistexternal.PlaylistExternalTrackStore
import com.lin0721.linmusic.core.playlistexternal.toQueueItem
import com.lin0721.linmusic.core.source.ExternalTrack
import com.lin0721.linmusic.core.songlike.SongLikeRepository
import com.lin0721.linmusic.core.userplaylist.UserPlaylist
import com.lin0721.linmusic.core.userplaylist.UserPlaylistRepository
import com.lin0721.linmusic.feature.playlist.data.PlaylistRepository
import kotlinx.coroutines.flow.first

// 歌曲多选后的批量操作：歌单页与歌手页共用，结果文案经 onToast 回给调用方。
// externals 为所选歌曲里的外部平台歌曲（按 Track.id 索引），它们只写本机存储，不调网易云接口
class TrackBatchActionsDelegate(
    private val playlistRepository: PlaylistRepository,
    private val playerManager: PlaybackController,
    private val songDownloadManager: SongDownloader,
    private val songLikeRepository: SongLikeRepository,
    private val createPlaylistAndAddSongUseCase: CreatePlaylistAndAddSongUseCase,
    private val userPlaylistRepository: UserPlaylistRepository,
    private val userPreferences: UserPreferences,
    private val resourceProvider: ResourceProvider,
    private val externalStore: PlaylistExternalTrackStore
) {

    private fun Track.toQueueItem(externals: Map<Long, ExternalTrack>) =
        externals[id]?.toQueueItem() ?: QueueItem(id, name, ar.joinToString("/") { it.name }, al.picUrl)

    private fun Track.toDownloadInfo() = DownloadTrackInfo(
        id, name, ar.joinToString("/") { it.name },
        al.name, al.picUrl.takeIf { it.isNotBlank() },
        yearFromEpochMillis(publishTime)
    )

    // 不可播放的歌曲不入队；全部不可播放时不做任何操作
    suspend fun playNext(
        tracks: List<Track>,
        unplayableIds: Set<Long>,
        externals: Map<Long, ExternalTrack> = emptyMap(),
        onToast: suspend (String) -> Unit
    ): Boolean {
        val playable = tracks.filter { it.id !in unplayableIds }
        if (playable.isEmpty()) {
            onToast("所选歌曲暂无可播放的版本")
            return false
        }
        playerManager.addToPlayNext(playable.map { it.toQueueItem(externals) })
        val skipped = tracks.size - playable.size
        onToast(
            if (skipped > 0) "已添加 ${playable.size} 首歌曲至下一首播放，跳过 $skipped 首不可播放"
            else "已添加 ${playable.size} 首歌曲至下一首播放"
        )
        return true
    }

    suspend fun addToPlaylist(
        targetPlaylistId: Long,
        tracks: List<Track>,
        externals: Map<Long, ExternalTrack> = emptyMap(),
        onToast: suspend (String) -> Unit
    ): Boolean {
        if (tracks.isEmpty()) return false
        val (externalTracks, neteaseTracks) = tracks.partition { it.id in externals }
        if (neteaseTracks.isNotEmpty()) {
            val result = playlistRepository.manipulatePlaylistTracks("add", targetPlaylistId, neteaseTracks.map { it.id }).first()
            result.exceptionOrNull()?.let {
                onToast(it.toUserMessage(resourceProvider))
                return false
            }
        }
        externalStore.add(targetPlaylistId, externalTracks.mapNotNull { externals[it.id] })
        onToast("已添加 ${tracks.size} 首歌曲到歌单")
        return true
    }

    suspend fun createPlaylistAndAdd(
        name: String,
        privacy: Int,
        tracks: List<Track>,
        externals: Map<Long, ExternalTrack> = emptyMap(),
        onToast: suspend (String) -> Unit
    ): Boolean {
        if (tracks.isEmpty()) return false
        val (externalTracks, neteaseTracks) = tracks.partition { it.id in externals }
        return createPlaylistAndAddSongUseCase(name, neteaseTracks.map { it.id }, privacy).first().fold(
            onSuccess = { playlist ->
                externalStore.add(playlist.id, externalTracks.mapNotNull { externals[it.id] })
                onToast("已创建歌单并添加 ${tracks.size} 首歌曲")
                true
            },
            onFailure = {
                onToast(it.toUserMessage(resourceProvider))
                false
            }
        )
    }

    suspend fun download(
        tracks: List<Track>,
        level: String,
        batchTag: String,
        batchLabel: String,
        onToast: suspend (String) -> Unit
    ): Boolean {
        if (tracks.isEmpty()) return false
        // 外部平台歌曲没有网易云 id，下载管线不支持
        val downloadable = tracks.filter { it.id > 0L }
        if (downloadable.isEmpty()) {
            onToast("外部平台歌曲暂不支持下载")
            return false
        }
        val result = songDownloadManager.enqueueBatch(downloadable.map { it.toDownloadInfo() }, level, batchTag, batchLabel)
        onToast(result.toToastMessage())
        return true
    }

    // 红心接口没有批量版本，逐首请求；已喜欢的跳过，返回成功喜欢的首数
    suspend fun like(tracks: List<Track>, onToast: suspend (String) -> Unit): Int {
        val liked = songLikeRepository.likedSongIds.value
        val targets = tracks.filter { it.id !in liked }
        if (targets.isEmpty()) {
            onToast("所选歌曲都已在我喜欢的音乐中")
            return 0
        }
        var succeeded = 0
        var firstError: Throwable? = null
        for (track in targets) {
            val result = songLikeRepository.likeSong(track.id, true).first()
            if (result.isSuccess) succeeded++ else if (firstError == null) firstError = result.exceptionOrNull()
        }
        val failed = targets.size - succeeded
        onToast(
            when {
                failed == 0 -> "已喜欢 $succeeded 首歌曲"
                succeeded == 0 -> firstError?.toUserMessage(resourceProvider) ?: "操作失败"
                else -> "已喜欢 $succeeded 首歌曲，$failed 首失败"
            }
        )
        return succeeded
    }

    // externalKey 为该歌单外部歌曲在本机存储里的 key，「我喜欢的音乐」与歌单 id 不同
    suspend fun removeFromPlaylist(
        playlistId: Long,
        tracks: List<Track>,
        externalKey: Long = playlistId,
        onToast: suspend (String) -> Unit
    ): Boolean {
        if (tracks.isEmpty()) return false
        val (externalTracks, neteaseTracks) = tracks.partition { it.id < 0L }
        if (neteaseTracks.isNotEmpty()) {
            val result = playlistRepository.manipulatePlaylistTracks("del", playlistId, neteaseTracks.map { it.id }).first()
            result.exceptionOrNull()?.let {
                onToast(it.toUserMessage(resourceProvider))
                return false
            }
        }
        externalStore.remove(externalKey, externalTracks.mapTo(HashSet()) { it.id })
        onToast("已从歌单中删除 ${tracks.size} 首歌曲")
        return true
    }

    // 当前用户自建、可作为添加目标的歌单；排除指定歌单与"我喜欢的音乐"。未登录或失败时返回 null
    suspend fun loadTargetPlaylists(excludePlaylistId: Long?, onToast: suspend (String) -> Unit): List<UserPlaylist>? {
        val profile = userPreferences.userProfile.first()
        if (profile == null) {
            onToast("请先登录账号")
            return null
        }
        return userPlaylistRepository.getUserPlaylists(profile.uid).first().fold(
            onSuccess = { playlists ->
                playlists.filter { it.userId == profile.uid && it.id != excludePlaylistId && it.id != profile.uid }
            },
            onFailure = {
                onToast(it.toUserMessage(resourceProvider))
                null
            }
        )
    }
}

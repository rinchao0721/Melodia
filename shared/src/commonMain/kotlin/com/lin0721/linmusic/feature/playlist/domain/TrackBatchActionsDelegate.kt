package com.lin0721.linmusic.feature.playlist.domain

import com.lin0721.linmusic.core.auth.UserPreferences
import com.lin0721.linmusic.core.download.SongDownloader
import com.lin0721.linmusic.core.download.toDownloadTrackInfo
import com.lin0721.linmusic.core.download.toToastMessage
import com.lin0721.linmusic.core.model.Track
import com.lin0721.linmusic.core.network.ResourceProvider
import com.lin0721.linmusic.core.network.toUserMessage
import com.lin0721.linmusic.core.platform.PlaylistId
import com.lin0721.linmusic.core.platform.UnifiedTrack
import com.lin0721.linmusic.core.platform.repository.PlatformLibraryRouter
import com.lin0721.linmusic.core.platform.repository.PlatformPlaylistRouter
import com.lin0721.linmusic.core.platform.repository.UnifiedUserPlaylist
import com.lin0721.linmusic.core.platform.toUnified
import com.lin0721.linmusic.core.player.PlaybackController
import com.lin0721.linmusic.core.player.QueueItem
import com.lin0721.linmusic.core.playlistexternal.PlaylistExternalTrackStore
import com.lin0721.linmusic.core.playlistexternal.toQueueItem
import com.lin0721.linmusic.core.playlistmutation.PlaylistMutationBus
import com.lin0721.linmusic.core.playlistmutation.PlaylistMutationEvent
import com.lin0721.linmusic.core.preferences.SettingsPreferences
import com.lin0721.linmusic.core.source.ExternalTrack
import com.lin0721.linmusic.core.source.MusicPlatform
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
    private val externalStore: PlaylistExternalTrackStore,
    private val platformPlaylistRouter: PlatformPlaylistRouter? = null,
    private val platformLibraryRouter: PlatformLibraryRouter? = null,
    private val settingsPreferences: SettingsPreferences? = null,
    private val playlistMutationBus: PlaylistMutationBus? = null
) {

    private fun Track.toQueueItem(
        externals: Map<Long, ExternalTrack>,
        unifiedTracks: Map<Long, UnifiedTrack> = emptyMap()
    ): QueueItem = unifiedTracks[id]?.let(QueueItem::fromUnified)
        ?: externals[id]?.toQueueItem()
        ?: QueueItem(id, name, ar.joinToString("/") { it.name }, al.picUrl)

    private fun Track.resolveUnified(
        externals: Map<Long, ExternalTrack>,
        unifiedTracks: Map<Long, UnifiedTrack>
    ): UnifiedTrack = unifiedTracks[id] ?: externals[id]?.toUnified() ?: toUnified()

    // 不可播放的歌曲不入队；全部不可播放时不做任何操作
    suspend fun playNext(
        tracks: List<Track>,
        unplayableIds: Set<Long>,
        externals: Map<Long, ExternalTrack> = emptyMap(),
        unifiedTracks: Map<Long, UnifiedTrack> = emptyMap(),
        onToast: suspend (String) -> Unit
    ): Boolean {
        val playable = tracks.filter { it.id !in unplayableIds }
        if (playable.isEmpty()) {
            onToast("所选歌曲暂无可播放的版本")
            return false
        }
        playerManager.addToPlayNext(playable.map { it.toQueueItem(externals, unifiedTracks) })
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
        unifiedTracks: Map<Long, UnifiedTrack> = emptyMap(),
        onToast: suspend (String) -> Unit
    ): Boolean {
        if (tracks.isEmpty()) return false
        val kugouSelected = tracks.filter { unifiedTracks[it.id]?.platform == MusicPlatform.KUGOU }
        if (kugouSelected.isNotEmpty() && platformPlaylistRouter != null) {
            return addToUnifiedPlaylist(
                targetPlaylistId = PlaylistId.netease(targetPlaylistId),
                tracks = tracks,
                externals = externals,
                unifiedTracks = unifiedTracks,
                onToast = onToast
            )
        }
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

    suspend fun addToUnifiedPlaylist(
        targetPlaylistId: PlaylistId,
        tracks: List<Track>,
        externals: Map<Long, ExternalTrack> = emptyMap(),
        unifiedTracks: Map<Long, UnifiedTrack> = emptyMap(),
        onToast: suspend (String) -> Unit
    ): Boolean {
        if (tracks.isEmpty()) return false
        val router = platformPlaylistRouter ?: return if (targetPlaylistId.platform == MusicPlatform.NETEASE) {
            val id = targetPlaylistId.neteaseIdOrNull() ?: return false
            addToPlaylist(id, tracks, externals, unifiedTracks, onToast)
        } else {
            onToast("歌单路由未就绪")
            false
        }
        val unifiedList = tracks.map { it.resolveUnified(externals, unifiedTracks) }
        val result = router.addTracksToPlaylist(targetPlaylistId, unifiedList).first()
        return result.fold(
            onSuccess = {
                onToast("已添加 ${tracks.size} 首歌曲到歌单")
                true
            },
            onFailure = {
                onToast(it.toUserMessage(resourceProvider))
                false
            }
        )
    }

    suspend fun createPlaylistAndAdd(
        name: String,
        privacy: Int,
        tracks: List<Track>,
        externals: Map<Long, ExternalTrack> = emptyMap(),
        unifiedTracks: Map<Long, UnifiedTrack> = emptyMap(),
        targetPlatform: MusicPlatform = MusicPlatform.NETEASE,
        onToast: suspend (String) -> Unit
    ): Boolean {
        if (tracks.isEmpty()) return false
        val router = platformPlaylistRouter
        if (targetPlatform == MusicPlatform.KUGOU && router != null) {
            val created = router.createPlaylist(MusicPlatform.KUGOU, name, isPrivate = privacy != 0).first().getOrElse {
                onToast(it.toUserMessage(resourceProvider))
                return false
            }
            val addRes = router.addTracksToPlaylist(created.id, tracks.map { it.resolveUnified(externals, unifiedTracks) }).first()
            playlistMutationBus?.emit(PlaylistMutationEvent.Created(0L, entityId = created.id))
            return addRes.fold(
                onSuccess = {
                    onToast("已创建歌单并添加 ${tracks.size} 首歌曲")
                    true
                },
                onFailure = {
                    onToast(it.toUserMessage(resourceProvider))
                    false
                }
            )
        }
        val (kugouTracks, restTracks) = tracks.partition { unifiedTracks[it.id]?.platform == MusicPlatform.KUGOU }
        val (externalTracks, neteaseTracks) = restTracks.partition { it.id in externals }
        return createPlaylistAndAddSongUseCase(name, neteaseTracks.map { it.id }, privacy).first().fold(
            onSuccess = { playlist ->
                externalStore.add(playlist.id, externalTracks.mapNotNull { externals[it.id] })
                if (kugouTracks.isNotEmpty()) {
                    externalStore.add(
                        PlaylistId.netease(playlist.id),
                        kugouTracks.mapNotNull { unifiedTracks[it.id] }
                    )
                }
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
        val result = songDownloadManager.enqueueBatch(downloadable.map { it.toDownloadTrackInfo() }, level, batchTag, batchLabel)
        onToast(result.toToastMessage())
        return true
    }

    // 红心接口没有批量版本，逐首请求；已喜欢的跳过，返回成功喜欢的首数
    suspend fun like(
        tracks: List<Track>,
        onToast: suspend (String) -> Unit
    ): Int = like(tracks, emptyMap(), onToast)

    suspend fun like(
        tracks: List<Track>,
        unifiedTracks: Map<Long, UnifiedTrack>,
        onToast: suspend (String) -> Unit
    ): Int {
        val liked = songLikeRepository.likedSongIds.value
        val likedUnified = songLikeRepository.likedTrackIds.value
        val targets = tracks.filter { track ->
            val u = unifiedTracks[track.id]
            track.id !in liked && (u == null || u.id !in likedUnified)
        }
        if (targets.isEmpty()) {
            onToast("所选歌曲都已在我喜欢的音乐中")
            return 0
        }
        var succeeded = 0
        var firstError: Throwable? = null
        for (track in targets) {
            val u = unifiedTracks[track.id]
            val result = if (u != null && u.platform == MusicPlatform.KUGOU) {
                songLikeRepository.likeTrack(u.id, true, u.name, u.kugouMeta, track.id).first()
            } else {
                songLikeRepository.likeSong(track.id, true).first()
            }
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
        unifiedTracks: Map<Long, UnifiedTrack> = emptyMap(),
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
        if (externalTracks.isNotEmpty()) {
            externalStore.remove(externalKey, externalTracks.mapTo(HashSet()) { it.id })
            val extTrackIds = externalTracks.mapNotNullTo(HashSet()) { unifiedTracks[it.id]?.id }
            if (extTrackIds.isNotEmpty()) {
                externalStore.remove(PlaylistId.netease(externalKey), extTrackIds)
            }
        }
        onToast("已从歌单中删除 ${tracks.size} 首歌曲")
        return true
    }

    suspend fun removeFromUnifiedPlaylist(
        playlistId: PlaylistId,
        tracks: List<Track>,
        unifiedTracks: Map<Long, UnifiedTrack> = emptyMap(),
        externalKey: Long? = null,
        onToast: suspend (String) -> Unit
    ): Boolean {
        if (tracks.isEmpty()) return false
        if (playlistId.platform == MusicPlatform.NETEASE) {
            val id = playlistId.neteaseIdOrNull() ?: return false
            return removeFromPlaylist(
                playlistId = id,
                tracks = tracks,
                externalKey = externalKey ?: id,
                unifiedTracks = unifiedTracks,
                onToast = onToast
            )
        }
        val router = platformPlaylistRouter ?: return false
        val unifiedList = tracks.map { unifiedTracks[it.id] ?: it.toUnified() }
        val result = router.removeTracksFromPlaylist(playlistId, unifiedList).first()
        return result.fold(
            onSuccess = {
                onToast("已从歌单中删除 ${tracks.size} 首歌曲")
                true
            },
            onFailure = {
                onToast(it.toUserMessage(resourceProvider))
                false
            }
        )
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

    // 跨平台加载可添加的目标自建歌单（按平台配置聚合网易与酷狗）
    suspend fun loadTargetUnifiedPlaylists(
        excludePlaylistId: PlaylistId?,
        onToast: suspend (String) -> Unit
    ): List<UnifiedUserPlaylist>? {
        val profile = userPreferences.userProfile.first()
        val kugouEnabled = settingsPreferences?.kugouPlatformEnabled?.first() ?: true
        val kugouAccount = if (kugouEnabled) userPreferences.activeAccountOf(MusicPlatform.KUGOU).first() else null
        if (profile == null && kugouAccount == null) {
            onToast("请先登录账号")
            return null
        }

        val combined = mutableListOf<UnifiedUserPlaylist>()
        var firstError: Throwable? = null
        if (profile != null) {
            userPlaylistRepository.getUserPlaylists(profile.uid).first().fold(
                onSuccess = { playlists ->
                    playlists
                        .filter { it.userId == profile.uid && it.id != profile.uid && PlaylistId.netease(it.id) != excludePlaylistId }
                        .forEach { item ->
                            combined += UnifiedUserPlaylist(
                                id = PlaylistId.netease(item.id),
                                name = item.name,
                                coverUrl = item.coverImgUrl,
                                playCount = item.playCount,
                                trackCount = item.trackCount
                            )
                        }
                },
                onFailure = { firstError = it }
            )
        }
        if (kugouAccount != null && platformLibraryRouter != null) {
            platformLibraryRouter.getUserPlaylists(kugouAccount.accountId).first().fold(
                onSuccess = { playlists ->
                    playlists
                        .filter { !it.isCollected && !it.isLikedPlaylist && it.id != excludePlaylistId }
                        .let(combined::addAll)
                },
                onFailure = { if (firstError == null) firstError = it }
            )
        }
        if (combined.isEmpty() && firstError != null) {
            onToast(firstError!!.toUserMessage(resourceProvider))
            return null
        }
        return combined
    }
}

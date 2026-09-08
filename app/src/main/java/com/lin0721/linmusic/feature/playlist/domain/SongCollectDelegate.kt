package com.lin0721.linmusic.feature.playlist.domain

import com.lin0721.linmusic.core.auth.UserPreferences
import com.lin0721.linmusic.core.model.isLikedSongsPlaylist
import com.lin0721.linmusic.core.network.ResourceProvider
import com.lin0721.linmusic.core.network.toUserMessage
import com.lin0721.linmusic.core.songlike.SongLikeRepository
import com.lin0721.linmusic.core.ui.components.PlaylistCollectItem
import com.lin0721.linmusic.core.ui.components.PlaylistCollectState
import com.lin0721.linmusic.core.userplaylist.UserPlaylist
import com.lin0721.linmusic.core.userplaylist.UserPlaylistRepository
import com.lin0721.linmusic.feature.playlist.data.PlaylistRepository
import java.util.Collections
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

// “收藏到歌单”弹窗的共享状态与操作，由 artist/playlist 等域各自持有一个实例
class SongCollectDelegate(
    private val userPlaylistRepository: UserPlaylistRepository,
    private val playlistRepository: PlaylistRepository,
    private val songLikeRepository: SongLikeRepository,
    private val createPlaylistAndAddSongUseCase: CreatePlaylistAndAddSongUseCase,
    private val userPreferences: UserPreferences,
    private val resourceProvider: ResourceProvider
) {

    companion object {
        // 自建歌单列表缓存 (用户 uid -> 歌单列表)
        private val userPlaylistsCache = ConcurrentHashMap<Long, List<UserPlaylist>>()
        // 歌单包含歌曲 ID 集合缓存 (歌单 id -> 歌曲 ID Set)
        private val playlistTrackIdsCache = ConcurrentHashMap<Long, MutableSet<Long>>()

        fun clearCache() {
            userPlaylistsCache.clear()
            playlistTrackIdsCache.clear()
        }
    }

    private val _state = MutableStateFlow(PlaylistCollectState())
    val state: StateFlow<PlaylistCollectState> = _state.asStateFlow()

    // 拉取当前的歌单并逐个判定是否已包含该歌曲；未缓存歌单异步补齐
    suspend fun prepare(songId: Long, likedSongIds: Set<Long>, onToast: suspend (String) -> Unit) {
        val profile = userPreferences.userProfile.first() ?: return

        // 优先使用已缓存的自建歌单列表，避免重复网络请求
        val cachedPlaylists = userPlaylistsCache[profile.uid]
        val playlists = if (cachedPlaylists != null) {
            cachedPlaylists
        } else {
            _state.update { it.copy(songId = songId, isLoading = true, collectItems = emptyList()) }
            val result = userPlaylistRepository.getUserPlaylists(profile.uid).firstOrNull()
            val fetched = result?.getOrNull()?.filter { it.userId == profile.uid }
            if (fetched != null) {
                userPlaylistsCache[profile.uid] = fetched
                fetched
            } else {
                _state.update { it.copy(isLoading = false) }
                result?.exceptionOrNull()?.let { onToast(it.toUserMessage(resourceProvider)) }
                return
            }
        }

        // 立即渲染歌单列表，已缓存或“我喜欢的音乐”即时打勾，未缓存项先显式呈开展现
        val initialItems = playlists.map { playlist ->
            val contains = if (isLikedSongsPlaylist(playlist.name, playlist.id, profile.uid)) {
                likedSongIds.contains(songId)
            } else {
                playlistTrackIdsCache[playlist.id]?.contains(songId) ?: false
            }
            PlaylistCollectItem(
                playlistId = playlist.id,
                playlistName = playlist.name,
                coverUrl = playlist.coverImgUrl,
                isInitiallyContains = contains,
                isContains = contains
            )
        }
        _state.update { it.copy(songId = songId, isLoading = false, collectItems = initialItems) }

        // 仅对未缓存歌曲 ID 集合的歌单在后台轻量并发拉取，并动态回填勾选态
        val unvisitedPlaylists = playlists.filter { playlist ->
            !isLikedSongsPlaylist(playlist.name, playlist.id, profile.uid) && !playlistTrackIdsCache.containsKey(playlist.id)
        }
        if (unvisitedPlaylists.isEmpty()) return

        coroutineScope {
            unvisitedPlaylists.forEach { playlist ->
                launch {
                    val detailResult = playlistRepository.getPlaylistDetail(playlist.id).firstOrNull()
                    val detail = detailResult?.getOrNull()
                    if (detail != null) {
                        val trackIds = Collections.synchronizedSet(detail.tracks.map { it.id }.toMutableSet())
                        playlistTrackIdsCache[playlist.id] = trackIds
                        if (trackIds.contains(songId)) {
                            _state.update { state ->
                                if (state.songId != songId) return@update state
                                val updated = state.collectItems.map { item ->
                                    if (item.playlistId == playlist.id) {
                                        item.copy(isInitiallyContains = true, isContains = true)
                                    } else {
                                        item
                                    }
                                }
                                state.copy(collectItems = updated)
                            }
                        }
                    }
                }
            }
        }
    }

    // 提交勾选变更；红心歌单走红心接口并在内存回写红心集合，其余走歌单增删并同步更新内存缓存
    suspend fun save(
        songId: Long,
        items: List<PlaylistCollectItem>,
        likedSongIds: Set<Long>,
        onToast: suspend (String) -> Unit,
        onLikedChanged: (Set<Long>) -> Unit
    ) {
        val profile = userPreferences.userProfile.first()
        var liked = likedSongIds

        items.forEach { item ->
            if (item.isContains == item.isInitiallyContains) return@forEach

            if (profile != null && isLikedSongsPlaylist(item.playlistName, item.playlistId, profile.uid)) {
                songLikeRepository.likeSong(songId, item.isContains).firstOrNull()
                    ?.onSuccess {
                        liked = if (item.isContains) liked + songId else liked - songId
                        onLikedChanged(liked)
                    }
                    ?.onFailure { onToast(it.toUserMessage(resourceProvider)) }
            } else {
                val op = if (item.isContains) "add" else "del"
                playlistRepository.manipulatePlaylistTracks(op, item.playlistId, listOf(songId)).firstOrNull()
                    ?.onSuccess {
                        val cachedIds = playlistTrackIdsCache[item.playlistId]
                        if (cachedIds != null) {
                            if (item.isContains) cachedIds.add(songId) else cachedIds.remove(songId)
                        }
                    }
                    ?.onFailure { onToast(it.toUserMessage(resourceProvider)) }
            }
        }
        onToast("歌单收藏更新成功")
    }

    // 新建歌单并把歌曲加入，成功后清理自建歌单列表缓存并重新拉取弹窗列表以带出新歌单
    suspend fun createAndAdd(
        name: String,
        songId: Long,
        likedSongIds: Set<Long>,
        onToast: suspend (String) -> Unit
    ) {
        createPlaylistAndAddSongUseCase(name, listOf(songId)).firstOrNull()
            ?.onSuccess {
                onToast("创建并加入歌单成功")
                val profile = userPreferences.userProfile.first()
                if (profile != null) {
                    userPlaylistsCache.remove(profile.uid)
                }
                prepare(songId, likedSongIds, onToast)
            }
            ?.onFailure { onToast(it.toUserMessage(resourceProvider)) }
    }
}

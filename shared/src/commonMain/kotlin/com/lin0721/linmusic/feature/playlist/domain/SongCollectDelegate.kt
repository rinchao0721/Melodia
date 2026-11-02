package com.lin0721.linmusic.feature.playlist.domain

import com.lin0721.linmusic.core.auth.UserPreferences
import com.lin0721.linmusic.core.model.isLikedSongsPlaylist
import com.lin0721.linmusic.core.network.ResourceProvider
import com.lin0721.linmusic.core.network.toUserMessage
import com.lin0721.linmusic.core.playlistexternal.ExternalSongLikes
import com.lin0721.linmusic.core.playlistexternal.PlaylistExternalTrackStore
import com.lin0721.linmusic.core.playlistexternal.likedExternalKey
import com.lin0721.linmusic.core.source.ExternalTrack
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
    private val resourceProvider: ResourceProvider,
    private val externalStore: PlaylistExternalTrackStore,
    private val externalSongLikes: ExternalSongLikes
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
        if (songId < 0L) {
            withExternal(songId, onToast) { prepareExternal(it, onToast) }
            return
        }
        val profile = userPreferences.userProfile.first() ?: return
        val playlists = ownPlaylists(profile.uid, songId, onToast) ?: return

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
                        // 超过1000首的歌单 tracks 会被服务端截断，完整成员要以 trackIds 为准
                        val trackIds = Collections.synchronizedSet(
                            (detail.tracks.map { it.id } + detail.trackIds.map { it.id }).toMutableSet()
                        )
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

    // 优先使用已缓存的自建歌单列表，避免重复网络请求；失败返回 null 并已 toast
    private suspend fun ownPlaylists(uid: Long, songId: Long, onToast: suspend (String) -> Unit): List<UserPlaylist>? {
        userPlaylistsCache[uid]?.let { return it }
        _state.update { it.copy(songId = songId, isLoading = true, collectItems = emptyList()) }
        val result = userPlaylistRepository.getUserPlaylists(uid).firstOrNull()
        val fetched = result?.getOrNull()?.filter { it.userId == uid }
        if (fetched == null) {
            _state.update { it.copy(isLoading = false) }
            result?.exceptionOrNull()?.let { onToast(it.toUserMessage(resourceProvider)) }
            return null
        }
        userPlaylistsCache[uid] = fetched
        return fetched
    }

    // 负数 id 是外部平台歌曲，从播放队列或本机歌单里找回歌曲信息
    private suspend fun withExternal(songId: Long, onToast: suspend (String) -> Unit, block: suspend (ExternalTrack) -> Unit) {
        val track = externalSongLikes.find(songId)
        if (track == null) {
            onToast("找不到该歌曲信息")
            return
        }
        block(track)
    }

    // 外部歌曲只写本机，「我喜欢的音乐」存在账号派生的 key 下
    private fun externalKeyOf(playlist: PlaylistCollectItem, uid: Long): Long =
        if (isLikedSongsPlaylist(playlist.playlistName, playlist.playlistId, uid)) likedExternalKey(uid) else playlist.playlistId

    suspend fun prepareExternal(track: ExternalTrack, onToast: suspend (String) -> Unit) {
        val profile = userPreferences.userProfile.first()
        if (profile == null) {
            onToast("请先登录账号")
            return
        }
        val songId = track.queueSongId
        val playlists = ownPlaylists(profile.uid, songId, onToast) ?: return
        val entries = externalStore.all.first()
        val items = playlists.map { playlist ->
            val item = PlaylistCollectItem(
                playlistId = playlist.id,
                playlistName = playlist.name,
                coverUrl = playlist.coverImgUrl,
                isInitiallyContains = false,
                isContains = false
            )
            val contains = entries[externalKeyOf(item, profile.uid)].orEmpty().any { it.track.resolveKey == track.resolveKey }
            item.copy(isInitiallyContains = contains, isContains = contains)
        }
        _state.update { it.copy(songId = songId, isLoading = false, collectItems = items) }
    }

    suspend fun saveExternal(track: ExternalTrack, items: List<PlaylistCollectItem>, onToast: suspend (String) -> Unit) {
        val profile = userPreferences.userProfile.first()
        if (profile == null) {
            onToast("请先登录账号")
            return
        }
        items.forEach { item ->
            if (item.isContains == item.isInitiallyContains) return@forEach
            val key = externalKeyOf(item, profile.uid)
            if (item.isContains) externalStore.add(key, listOf(track)) else externalStore.remove(key, setOf(track.queueSongId))
        }
        onToast("歌单收藏更新成功")
    }

    suspend fun createAndAddExternal(name: String, track: ExternalTrack, onToast: suspend (String) -> Unit) {
        createPlaylistAndAddSongUseCase(name, emptyList()).firstOrNull()
            ?.onSuccess { playlist ->
                externalStore.add(playlist.id, listOf(track))
                onToast("创建并加入歌单成功")
                userPreferences.userProfile.first()?.let { userPlaylistsCache.remove(it.uid) }
                prepareExternal(track, onToast)
            }
            ?.onFailure { onToast(it.toUserMessage(resourceProvider)) }
    }

    // 提交勾选变更；红心歌单走红心接口并在内存回写红心集合，其余走歌单增删并同步更新内存缓存
    suspend fun save(
        songId: Long,
        items: List<PlaylistCollectItem>,
        likedSongIds: Set<Long>,
        onToast: suspend (String) -> Unit,
        onLikedChanged: (Set<Long>) -> Unit
    ) {
        if (songId < 0L) {
            withExternal(songId, onToast) { saveExternal(it, items, onToast) }
            return
        }
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
        if (songId < 0L) {
            withExternal(songId, onToast) { createAndAddExternal(name, it, onToast) }
            return
        }
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

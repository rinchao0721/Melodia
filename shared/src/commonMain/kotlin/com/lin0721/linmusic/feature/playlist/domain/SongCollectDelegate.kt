package com.lin0721.linmusic.feature.playlist.domain

import com.lin0721.linmusic.core.auth.UserPreferences
import com.lin0721.linmusic.core.model.isLikedSongsPlaylist
import com.lin0721.linmusic.core.network.ResourceProvider
import com.lin0721.linmusic.core.network.toUserMessage
import com.lin0721.linmusic.core.platform.AccountId
import com.lin0721.linmusic.core.platform.PlaylistId
import com.lin0721.linmusic.core.platform.TrackId
import com.lin0721.linmusic.core.platform.UnifiedTrack
import com.lin0721.linmusic.core.platform.repository.PlatformLibraryRouter
import com.lin0721.linmusic.core.platform.repository.PlatformPlaylistRouter
import com.lin0721.linmusic.core.platform.repository.UnifiedUserPlaylist
import com.lin0721.linmusic.core.platform.toLegacyExternalTrackOrNull
import com.lin0721.linmusic.core.platform.toUnified
import com.lin0721.linmusic.core.player.QueueItem
import com.lin0721.linmusic.core.playlistexternal.ExternalSongLikes
import com.lin0721.linmusic.core.playlistexternal.PlaylistExternalTrackStore
import com.lin0721.linmusic.core.playlistexternal.likedExternalKey
import com.lin0721.linmusic.core.playlistmutation.PlaylistMutationBus
import com.lin0721.linmusic.core.playlistmutation.PlaylistMutationEvent
import com.lin0721.linmusic.core.preferences.SettingsPreferences
import com.lin0721.linmusic.core.source.ExternalTrack
import com.lin0721.linmusic.core.source.MusicPlatform
import com.lin0721.linmusic.core.songlike.SongLikeRepository
import com.lin0721.linmusic.core.ui.components.CollectPromptEvent
import com.lin0721.linmusic.core.ui.components.PlaylistCollectItem
import com.lin0721.linmusic.core.ui.components.PlaylistCollectState
import com.lin0721.linmusic.core.userplaylist.UserPlaylist
import com.lin0721.linmusic.core.userplaylist.UserPlaylistRepository
import com.lin0721.linmusic.feature.playlist.data.PlaylistRepository
import java.util.Collections
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.absoluteValue
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
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
    private val externalSongLikes: ExternalSongLikes,
    private val platformLibraryRouter: PlatformLibraryRouter? = null,
    private val platformPlaylistRouter: PlatformPlaylistRouter? = null,
    private val settingsPreferences: SettingsPreferences? = null,
    private val playlistMutationBus: PlaylistMutationBus? = null
) {

    companion object {
        // 自建歌单列表缓存 (用户 uid -> 歌单列表)
        private val userPlaylistsCache = ConcurrentHashMap<Long, List<UserPlaylist>>()
        // 歌单包含歌曲 ID 集合缓存 (歌单 id -> 歌曲 ID Set)
        private val playlistTrackIdsCache = ConcurrentHashMap<Long, MutableSet<Long>>()
        // 酷狗自建歌单及曲目缓存 (按 accountKey / account+playlist 隔离)
        private val kugouPlaylistsCache = ConcurrentHashMap<String, List<UnifiedUserPlaylist>>()
        private val kugouPlaylistTrackKeysCache = ConcurrentHashMap<String, MutableSet<String>>()

        private fun kugouTrackCacheKey(accountId: AccountId, playlistId: PlaylistId): String =
            "${accountId.canonical}|${playlistId.canonical}"

        fun clearKugouCache() {
            kugouPlaylistsCache.clear()
            kugouPlaylistTrackKeysCache.clear()
        }

        fun clearNeteaseCache() {
            userPlaylistsCache.clear()
            playlistTrackIdsCache.clear()
        }

        fun clearCache() {
            clearNeteaseCache()
            clearKugouCache()
        }
    }

    private val _state = MutableStateFlow(PlaylistCollectState())
    val state: StateFlow<PlaylistCollectState> = _state.asStateFlow()

    private val _collectPromptEvent = MutableSharedFlow<CollectPromptEvent>(extraBufferCapacity = 8)
    val collectPromptEvent: SharedFlow<CollectPromptEvent> = _collectPromptEvent.asSharedFlow()

    private suspend fun isKugouActive(): Boolean =
        (settingsPreferences?.kugouPlatformEnabled?.first() ?: true) &&
            platformLibraryRouter != null &&
            platformPlaylistRouter != null &&
            userPreferences.activeAccountOf(MusicPlatform.KUGOU).first() != null

    private fun kugouUiPlaylistId(playlistId: PlaylistId): Long =
        -1L - playlistId.canonical.hashCode().toLong().absoluteValue

    // 一键收藏到“已点赞的歌曲”（复用现有喜欢接口），成功后发出 CollectPromptEvent
    suspend fun quickLike(songId: Long, onToast: suspend (String) -> Unit) {
        if (songId == -1L) return
        if (songId < 0L) {
            withExternal(songId, onToast) { track ->
                if (track.platform == MusicPlatform.KUGOU && isKugouActive()) {
                    quickLikeUnified(track.toUnified(), onToast)
                } else {
                    quickLikeExternal(track, onToast)
                }
            }
            return
        }
        val profile = userPreferences.userProfile.first()
        if (profile == null) {
            onToast("请先登录账号")
            return
        }
        songLikeRepository.likeSong(songId, like = true).firstOrNull()
            ?.onSuccess {
                _collectPromptEvent.emit(CollectPromptEvent(songId))
            }
            ?.onFailure { onToast(it.toUserMessage(resourceProvider)) }
    }

    // 统一曲目一键喜欢：酷狗启用且已登录时走酷狗云端红心，否则按平台或外部存储回退
    suspend fun quickLikeUnified(track: UnifiedTrack, onToast: suspend (String) -> Unit) {
        if (track.platform == MusicPlatform.NETEASE) {
            val id = track.id.neteaseIdOrNull() ?: return
            quickLike(id, onToast)
            return
        }
        val legacySongId = QueueItem.fromUnified(track).songId
        if (track.platform == MusicPlatform.KUGOU && isKugouActive()) {
            songLikeRepository.likeTrack(
                trackId = track.id,
                like = true,
                title = track.name,
                kugouMeta = track.kugouMeta,
                legacyExternalId = legacySongId
            ).firstOrNull()
                ?.onSuccess {
                    _collectPromptEvent.emit(CollectPromptEvent(legacySongId))
                }
                ?.onFailure { onToast(it.toUserMessage(resourceProvider)) }
            return
        }
        track.toLegacyExternalTrackOrNull()?.let { quickLikeExternal(it, onToast) }
            ?: onToast("暂不支持收藏该歌曲")
    }

    // 外部平台搜索结果的快速收藏：直接写入本机“我喜欢的音乐”外部条目
    suspend fun quickLikeExternal(track: ExternalTrack, onToast: suspend (String) -> Unit) {
        val profile = userPreferences.userProfile.first()
        if (profile == null) {
            onToast("请先登录账号")
            return
        }
        externalStore.add(likedExternalKey(profile.uid), listOf(track))
        songLikeRepository.syncLikedSongIds(songLikeRepository.likedSongIds.value + track.queueSongId)
        _collectPromptEvent.emit(CollectPromptEvent(track.queueSongId))
    }

    // 拉取当前的歌单并逐个判定是否已包含该歌曲；未缓存歌单异步补齐
    suspend fun prepare(songId: Long, likedSongIds: Set<Long>, onToast: suspend (String) -> Unit) {
        if (songId < 0L) {
            withExternal(songId, onToast) { prepareExternal(it, onToast) }
            return
        }
        val profile = userPreferences.userProfile.first()
        val kugouAccount = if (isKugouActive()) userPreferences.activeAccountOf(MusicPlatform.KUGOU).first() else null
        if (profile == null && kugouAccount == null) return

        val neteasePlaylists = if (profile != null) {
            ownPlaylists(profile.uid, songId, onToast) ?: emptyList()
        } else {
            emptyList()
        }
        val kugouPlaylists = if (kugouAccount != null) {
            ownKugouPlaylists(kugouAccount.accountId, songId)
        } else {
            emptyList()
        }
        if (neteasePlaylists.isEmpty() && kugouPlaylists.isEmpty() && profile == null) return

        val neteaseItems = if (profile != null) {
            neteasePlaylists.map { playlist ->
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
                    isContains = contains,
                    trackCount = playlist.trackCount,
                    entityId = PlaylistId.netease(playlist.id),
                    platform = MusicPlatform.NETEASE,
                    isLikedPlaylist = isLikedSongsPlaylist(playlist.name, playlist.id, profile.uid)
                )
            }
        } else {
            emptyList()
        }

        val trackId = TrackId.netease(songId)
        val unifiedEntries = if (kugouPlaylists.isNotEmpty()) externalStore.allUnified.first() else emptyMap()
        val kugouItems = kugouPlaylists.map { playlist ->
            val key = kugouAccount?.let { kugouTrackCacheKey(it.accountId, playlist.id) }
            val contains = (key != null && kugouPlaylistTrackKeysCache[key]?.contains(trackId.canonical) == true) ||
                unifiedEntries[playlist.id].orEmpty().any { it.track.id == trackId }
            PlaylistCollectItem(
                playlistId = kugouUiPlaylistId(playlist.id),
                playlistName = playlist.name,
                coverUrl = playlist.coverUrl,
                isInitiallyContains = contains,
                isContains = contains,
                trackCount = playlist.trackCount,
                entityId = playlist.id,
                platform = MusicPlatform.KUGOU,
                isLikedPlaylist = playlist.isLikedPlaylist
            )
        }

        _state.update {
            it.copy(
                songId = songId,
                isLoading = false,
                collectItems = neteaseItems + kugouItems,
                trackId = trackId
            )
        }

        val unvisitedPlaylists = if (profile != null) {
            neteasePlaylists.filter { playlist ->
                !isLikedSongsPlaylist(playlist.name, playlist.id, profile.uid) && !playlistTrackIdsCache.containsKey(playlist.id)
            }
        } else {
            emptyList()
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
                                    if (item.platform == MusicPlatform.NETEASE && item.playlistId == playlist.id) {
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

    // 统一曲目收藏弹窗准备：同时展示网易云与酷狗自建歌单，支持同平台及跨平台混排加歌
    suspend fun prepareUnified(track: UnifiedTrack, likedSongIds: Set<Long>, onToast: suspend (String) -> Unit) {
        if (track.platform == MusicPlatform.NETEASE) {
            val id = track.id.neteaseIdOrNull()
            if (id != null) {
                prepare(id, likedSongIds, onToast)
                return
            }
        }
        val profile = userPreferences.userProfile.first()
        val kugouAccount = if (isKugouActive()) userPreferences.activeAccountOf(MusicPlatform.KUGOU).first() else null
        if (profile == null && kugouAccount == null) {
            onToast("请先登录账号")
            return
        }

        val displaySongId = QueueItem.fromUnified(track).songId
        val neteasePlaylists = if (profile != null) {
            ownPlaylists(profile.uid, displaySongId, onToast) ?: emptyList()
        } else {
            emptyList()
        }
        val kugouPlaylists = if (kugouAccount != null) {
            ownKugouPlaylists(kugouAccount.accountId, displaySongId)
        } else {
            emptyList()
        }

        val unifiedEntries = externalStore.allUnified.first()
        val legacyEntries = externalStore.all.first()
        val legacyTrack = track.toLegacyExternalTrackOrNull()

        val neteaseItems = if (profile != null) {
            neteasePlaylists.map { playlist ->
                val likedPl = isLikedSongsPlaylist(playlist.name, playlist.id, profile.uid)
                val targetKey = if (likedPl) likedExternalKey(profile.uid) else playlist.id
                val inLegacy = legacyTrack != null &&
                    legacyEntries[targetKey].orEmpty().any { it.track.resolveKey == legacyTrack.resolveKey }
                val inUnified = unifiedEntries[PlaylistId.netease(targetKey)].orEmpty().any { it.track.id == track.id }
                val contains = inLegacy || inUnified
                PlaylistCollectItem(
                    playlistId = playlist.id,
                    playlistName = playlist.name,
                    coverUrl = playlist.coverImgUrl,
                    isInitiallyContains = contains,
                    isContains = contains,
                    trackCount = playlist.trackCount,
                    entityId = PlaylistId.netease(playlist.id),
                    platform = MusicPlatform.NETEASE,
                    isLikedPlaylist = likedPl
                )
            }
        } else {
            emptyList()
        }

        val likedTracks = songLikeRepository.likedTrackIds.value
        val kugouAccountId = kugouAccount?.accountId
        val kugouItems = kugouPlaylists.map { playlist ->
            val key = kugouAccountId?.let { kugouTrackCacheKey(it, playlist.id) }
            val contains = when {
                playlist.isLikedPlaylist && track.platform == MusicPlatform.KUGOU -> track.id in likedTracks
                else -> (key != null && kugouPlaylistTrackKeysCache[key]?.contains(track.id.canonical) == true) ||
                    unifiedEntries[playlist.id].orEmpty().any { it.track.id == track.id }
            }
            PlaylistCollectItem(
                playlistId = kugouUiPlaylistId(playlist.id),
                playlistName = playlist.name,
                coverUrl = playlist.coverUrl,
                isInitiallyContains = contains,
                isContains = contains,
                trackCount = playlist.trackCount,
                entityId = playlist.id,
                platform = MusicPlatform.KUGOU,
                isLikedPlaylist = playlist.isLikedPlaylist
            )
        }

        _state.update {
            it.copy(
                songId = displaySongId,
                isLoading = false,
                collectItems = neteaseItems + kugouItems,
                trackId = track.id
            )
        }

        // 对未缓存的酷狗歌单后台补齐是否已包含该酷狗曲目
        val unvisitedKugou = if (track.platform == MusicPlatform.KUGOU && kugouAccountId != null) {
            kugouPlaylists.filter {
                !it.isLikedPlaylist && !kugouPlaylistTrackKeysCache.containsKey(kugouTrackCacheKey(kugouAccountId, it.id))
            }
        } else {
            emptyList()
        }
        val router = platformPlaylistRouter
        if (unvisitedKugou.isEmpty() || router == null || kugouAccountId == null) return
        coroutineScope {
            unvisitedKugou.forEach { playlist ->
                launch {
                    val detail = router.getPlaylistDetail(playlist.id).firstOrNull()?.getOrNull() ?: return@launch
                    val trackKeys = Collections.synchronizedSet(detail.tracks.mapTo(HashSet()) { it.id.canonical })
                    kugouPlaylistTrackKeysCache[kugouTrackCacheKey(kugouAccountId, playlist.id)] = trackKeys
                    if (trackKeys.contains(track.id.canonical)) {
                        _state.update { state ->
                            if (state.trackId != track.id) return@update state
                            val updated = state.collectItems.map { item ->
                                if (item.entityId == playlist.id) {
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

    private suspend fun ownKugouPlaylists(accountId: AccountId, songId: Long): List<UnifiedUserPlaylist> {
        val router = platformLibraryRouter ?: return emptyList()
        kugouPlaylistsCache[accountId.canonical]?.let { return it }
        val fetched = router.getUserPlaylists(accountId).firstOrNull()?.getOrNull()
            ?.filter { !it.isCollected }
            ?: emptyList()
        if (fetched.isNotEmpty()) {
            kugouPlaylistsCache[accountId.canonical] = fetched
        }
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
        if (track.platform == MusicPlatform.KUGOU && isKugouActive()) {
            prepareUnified(track.toUnified(), songLikeRepository.likedSongIds.value, onToast)
            return
        }
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
                isContains = false,
                trackCount = playlist.trackCount
            )
            val contains = entries[externalKeyOf(item, profile.uid)].orEmpty().any { it.track.resolveKey == track.resolveKey }
            item.copy(isInitiallyContains = contains, isContains = contains)
        }
        _state.update { it.copy(songId = songId, isLoading = false, collectItems = items) }
    }

    // silent 为 true 时不提示成功（Android 弹窗逐项即时保存），失败照常提示
    suspend fun saveExternal(
        track: ExternalTrack,
        items: List<PlaylistCollectItem>,
        silent: Boolean = false,
        onToast: suspend (String) -> Unit
    ) {
        if (items.any { it.platform == MusicPlatform.KUGOU } || (track.platform == MusicPlatform.KUGOU && isKugouActive())) {
            saveUnified(track.toUnified(), items, songLikeRepository.likedSongIds.value, onToast, {}, silent)
            return
        }
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
        if (!silent) onToast("歌单收藏更新成功")
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

    suspend fun saveUnified(
        track: UnifiedTrack,
        items: List<PlaylistCollectItem>,
        likedSongIds: Set<Long>,
        onToast: suspend (String) -> Unit,
        onLikedChanged: (Set<Long>) -> Unit,
        silent: Boolean = false
    ) {
        if (track.platform == MusicPlatform.NETEASE && items.all { it.platform == MusicPlatform.NETEASE }) {
            val id = track.id.neteaseIdOrNull()
            if (id != null) {
                save(id, items, likedSongIds, onToast, onLikedChanged, silent)
                return
            }
        }
        val profile = userPreferences.userProfile.first()
        val kugouAccountId = userPreferences.activeAccountOf(MusicPlatform.KUGOU).first()?.accountId
        val router = platformPlaylistRouter
        var liked = likedSongIds

        items.forEach { item ->
            if (item.isContains == item.isInitiallyContains) return@forEach
            if (item.platform == MusicPlatform.KUGOU) {
                val targetPlaylistId = item.entityId ?: return@forEach
                if (item.isLikedPlaylist && track.platform == MusicPlatform.KUGOU) {
                    songLikeRepository.likeTrack(
                        trackId = track.id,
                        like = item.isContains,
                        title = track.name,
                        kugouMeta = track.kugouMeta,
                        legacyExternalId = QueueItem.fromUnified(track).songId
                    ).firstOrNull()?.onFailure { onToast(it.toUserMessage(resourceProvider)) }
                } else if (router != null) {
                    val res = if (item.isContains) {
                        router.addTracksToPlaylist(targetPlaylistId, listOf(track)).firstOrNull()
                    } else {
                        router.removeTracksFromPlaylist(targetPlaylistId, listOf(track)).firstOrNull()
                    }
                    res?.onSuccess {
                        val cached = kugouAccountId?.let { kugouPlaylistTrackKeysCache[kugouTrackCacheKey(it, targetPlaylistId)] }
                        if (cached != null) {
                            if (item.isContains) cached.add(track.id.canonical) else cached.remove(track.id.canonical)
                        }
                    }?.onFailure { onToast(it.toUserMessage(resourceProvider)) }
                }
                return@forEach
            }

            // 网易目标歌单
            val isLikedPl = profile != null && isLikedSongsPlaylist(item.playlistName, item.playlistId, profile.uid)
            if (track.platform == MusicPlatform.NETEASE) {
                val songId = track.id.neteaseIdOrNull() ?: return@forEach
                if (isLikedPl) {
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
            } else {
                val targetId = PlaylistId.netease(
                    if (isLikedPl) likedExternalKey(profile.uid) else item.playlistId
                )
                if (router != null && !isLikedPl) {
                    val res = if (item.isContains) {
                        router.addTracksToPlaylist(targetId, listOf(track)).firstOrNull()
                    } else {
                        router.removeTracksFromPlaylist(targetId, listOf(track)).firstOrNull()
                    }
                    res?.onFailure { onToast(it.toUserMessage(resourceProvider)) }
                } else {
                    if (item.isContains) externalStore.add(targetId, listOf(track))
                    else externalStore.remove(targetId, setOf(track.id))
                }
            }
        }
        if (!silent) onToast("歌单收藏更新成功")
    }

    // 提交勾选变更；红心歌单走红心接口并在内存回写红心集合，其余走歌单增删并同步更新内存缓存
    suspend fun save(
        songId: Long,
        items: List<PlaylistCollectItem>,
        likedSongIds: Set<Long>,
        onToast: suspend (String) -> Unit,
        onLikedChanged: (Set<Long>) -> Unit,
        silent: Boolean = false
    ) {
        if (songId < 0L) {
            withExternal(songId, onToast) { saveExternal(it, items, silent, onToast) }
            return
        }
        if (items.any { it.platform == MusicPlatform.KUGOU } && platformPlaylistRouter != null) {
            saveUnified(
                track = UnifiedTrack(id = TrackId.netease(songId), name = ""),
                items = items,
                likedSongIds = likedSongIds,
                onToast = onToast,
                onLikedChanged = onLikedChanged,
                silent = silent
            )
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
        if (!silent) onToast("歌单收藏更新成功")
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

    suspend fun createAndAddUnified(
        name: String,
        track: UnifiedTrack,
        targetPlatform: MusicPlatform = track.platform.takeIf { it == MusicPlatform.KUGOU } ?: MusicPlatform.NETEASE,
        likedSongIds: Set<Long>,
        onToast: suspend (String) -> Unit
    ) {
        val router = platformPlaylistRouter
        if (targetPlatform == MusicPlatform.KUGOU && isKugouActive() && router != null) {
            router.createPlaylist(MusicPlatform.KUGOU, name, isPrivate = false).firstOrNull()
                ?.onSuccess { created ->
                    router.addTracksToPlaylist(created.id, listOf(track)).firstOrNull()
                    playlistMutationBus?.emit(PlaylistMutationEvent.Created(0L, entityId = created.id))
                    userPreferences.activeAccountOf(MusicPlatform.KUGOU).first()?.let {
                        kugouPlaylistsCache.remove(it.accountId.canonical)
                    }
                    onToast("创建并加入歌单成功")
                    prepareUnified(track, likedSongIds, onToast)
                }
                ?.onFailure { onToast(it.toUserMessage(resourceProvider)) }
            return
        }
        if (track.platform == MusicPlatform.NETEASE) {
            val id = track.id.neteaseIdOrNull()
            if (id != null) {
                createAndAdd(name, id, likedSongIds, onToast)
                return
            }
        }
        createPlaylistAndAddSongUseCase(name, emptyList()).firstOrNull()
            ?.onSuccess { playlist ->
                val targetId = PlaylistId.netease(playlist.id)
                if (router != null) {
                    router.addTracksToPlaylist(targetId, listOf(track)).firstOrNull()
                } else {
                    externalStore.add(targetId, listOf(track))
                }
                onToast("创建并加入歌单成功")
                userPreferences.userProfile.first()?.let { userPlaylistsCache.remove(it.uid) }
                prepareUnified(track, likedSongIds, onToast)
            }
            ?.onFailure { onToast(it.toUserMessage(resourceProvider)) }
    }
}

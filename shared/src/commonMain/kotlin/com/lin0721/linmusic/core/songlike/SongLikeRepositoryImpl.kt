package com.lin0721.linmusic.core.songlike

import com.lin0721.linmusic.core.auth.AccountEvents
import com.lin0721.linmusic.core.auth.UserPreferences
import com.lin0721.linmusic.core.kugou.KugouLikeService
import com.lin0721.linmusic.core.network.apiFlow
import com.lin0721.linmusic.core.platform.KugouTrackMeta
import com.lin0721.linmusic.core.platform.TrackId
import com.lin0721.linmusic.core.playlistexternal.ExternalSongLikes
import com.lin0721.linmusic.core.source.ExternalTrack
import com.lin0721.linmusic.core.source.MusicPlatform
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.absoluteValue

// 歌曲红心仓储：对外状态只反映当前账号平台（网易=网易红心+本机外部红心，酷狗=酷狗云端红心），写入路由不受平台影响
class SongLikeRepositoryImpl(
    private val apiService: SongLikeApi,
    private val externalLikes: ExternalSongLikes? = null,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
    private val kugouLikeService: KugouLikeService? = null,
    private val userPreferences: UserPreferences? = null,
    private val resolveTrackByLegacyId: (suspend (Long) -> ExternalTrack?)? = null,
    private val isKugouEnabled: suspend () -> Boolean = { true }
) : SongLikeRepository {

    private val stateLock = Any()
    private var neteaseLikedIds: Set<Long> = emptySet()
    private var manualNegativeIds: Set<Long> = emptySet()
    private var localExternalLikedIds: Set<Long> = emptySet()
    private var kugouLikedTracks: Set<TrackId> = emptySet()
    private var activePlatform: MusicPlatform = MusicPlatform.NETEASE
    private val localTrackByLegacyId = ConcurrentHashMap<Long, TrackId>()
    private val kugouLegacyIdsByTrack = ConcurrentHashMap<TrackId, MutableSet<Long>>()

    private val _likedSongIds = MutableStateFlow<Set<Long>>(emptySet())
    override val likedSongIds: StateFlow<Set<Long>> = _likedSongIds.asStateFlow()

    private val _likedTrackIds = MutableStateFlow<Set<TrackId>>(emptySet())
    override val likedTrackIds: StateFlow<Set<TrackId>> = _likedTrackIds.asStateFlow()

    init {
        if (externalLikes != null) {
            scope.launch {
                externalLikes.likedIds.collect { external ->
                    synchronized(stateLock) {
                        localExternalLikedIds = external
                        rebuildStatesLocked()
                    }
                }
            }
        }
        if (userPreferences != null) {
            scope.launch {
                userPreferences.currentPlatform.collect { platform ->
                    synchronized(stateLock) {
                        activePlatform = platform
                        rebuildStatesLocked()
                    }
                }
            }
        }
        if (kugouLikeService != null && userPreferences != null) {
            scope.launch {
                if (userPreferences.currentPlatform.first() != MusicPlatform.LOCAL &&
                    isKugouEnabled() &&
                    userPreferences.activeAccountOf(MusicPlatform.KUGOU).first() != null
                ) {
                    syncKugouLikedTracks().first()
                }
            }
            scope.launch {
                AccountEvents.platformSwitched.collect { event ->
                    if (event.platform != MusicPlatform.KUGOU) return@collect
                    kugouLikeService.clearCache()
                    // 切号或退出时必须先在锁内清空旧号酷狗红心与映射，避免新号同步失败残留旧号状态
                    synchronized(stateLock) {
                        kugouLikedTracks = emptySet()
                        kugouLegacyIdsByTrack.clear()
                        rebuildStatesLocked()
                    }
                    if (event.currentAccountId != null && isKugouEnabled()) {
                        syncKugouLikedTracks().first()
                    }
                }
            }
        }
    }

    override fun getLikedSongIds(uid: Long): Flow<Result<List<Long>>> = apiFlow(
        request = { apiService.getLikedSongIds(LikeSongListRequest(uid = uid)) },
        isSuccess = { it.isSuccess },
        code = { it.code },
        transform = { it.ids }
    ).onEach { result ->
        result.onSuccess { ids ->
            synchronized(stateLock) {
                neteaseLikedIds = ids.filterTo(HashSet()) { it > 0L }
                rebuildStatesLocked()
            }
        }
    }

    override fun likeSong(songId: Long, like: Boolean): Flow<Result<Unit>> = flow {
        if (songId < 0L) {
            // 负数 ID 优先尝试按已登录且启用中的酷狗云端曲目路由，未启用/未登录或非酷狗则平滑回退本机外部红心
            if (kugouLikeService != null && isKugouEnabled() && isKugouLoggedIn()) {
                val track = externalLikes?.find(songId) ?: resolveTrackByLegacyId?.invoke(songId)
                if (track != null && track.platform == MusicPlatform.KUGOU) {
                    val hash = track.hash?.trim()?.takeIf { it.isNotEmpty() } ?: track.id
                    val trackId = TrackId.kugou(hash)
                    val meta = KugouTrackMeta(
                        hash = trackId.rawId,
                        albumId = track.albumId.takeIf { it.isNotBlank() }
                    )
                    performKugouLike(
                        trackId = trackId,
                        like = like,
                        title = track.name,
                        kugouMeta = meta,
                        legacyExternalId = songId
                    ).let { result ->
                        emit(result)
                        return@flow
                    }
                }
            }

            val (wasExternalBefore, wasManualBefore) = synchronized(stateLock) {
                val wasExt = songId in localExternalLikedIds
                val wasMan = songId in manualNegativeIds
                if (like) {
                    localExternalLikedIds = localExternalLikedIds + songId
                } else {
                    localExternalLikedIds = localExternalLikedIds - songId
                    manualNegativeIds = manualNegativeIds - songId
                }
                rebuildStatesLocked()
                wasExt to wasMan
            }
            val result = externalLikes?.setLiked(songId, like)
                ?: Result.failure(IllegalStateException("该歌曲不支持收藏"))
            if (result.isFailure) {
                synchronized(stateLock) {
                    if (like && !wasExternalBefore && !wasManualBefore) {
                        localExternalLikedIds = localExternalLikedIds - songId
                    } else if (!like) {
                        if (wasExternalBefore) localExternalLikedIds = localExternalLikedIds + songId
                        if (wasManualBefore) manualNegativeIds = manualNegativeIds + songId
                    }
                    rebuildStatesLocked()
                }
            }
            emit(result)
            return@flow
        }

        val wasLikedBefore = synchronized(stateLock) {
            val prev = songId in neteaseLikedIds
            neteaseLikedIds = if (like) neteaseLikedIds + songId else neteaseLikedIds - songId
            rebuildStatesLocked()
            prev
        }

        apiFlow(
            request = { apiService.likeSong(LikeSongRequest(trackId = songId, like = like)) },
            isSuccess = { it.isSuccess },
            code = { it.code },
            transform = { Unit }
        ).collect { result ->
            result.onFailure {
                synchronized(stateLock) {
                    neteaseLikedIds = when {
                        like && !wasLikedBefore -> neteaseLikedIds - songId
                        !like && wasLikedBefore -> neteaseLikedIds + songId
                        else -> neteaseLikedIds
                    }
                    rebuildStatesLocked()
                }
            }
            emit(result)
        }
    }

    override fun syncLikedSongIds(ids: Set<Long>) {
        synchronized(stateLock) {
            // 酷狗账号下暴露集合不含网易数据，回写会污染网易状态
            if (activePlatform == MusicPlatform.KUGOU) return
            neteaseLikedIds = ids.filterTo(HashSet()) { it > 0L }
            manualNegativeIds = ids.filterTo(HashSet()) { it < 0L }
            rebuildStatesLocked()
        }
    }

    override fun syncKugouLikedTracks(): Flow<Result<Set<TrackId>>> = flow {
        val service = kugouLikeService
        if (service == null) {
            emit(Result.success(emptySet()))
            return@flow
        }
        if (!isKugouEnabled() || !isKugouLoggedIn()) {
            synchronized(stateLock) {
                kugouLikedTracks = emptySet()
                kugouLegacyIdsByTrack.clear()
                rebuildStatesLocked()
            }
            emit(Result.success(emptySet()))
            return@flow
        }
        val result = service.fetchLikedTrackMap().map { map ->
            map.keys.mapTo(LinkedHashSet()) { TrackId.kugou(it) }
        }
        result.onSuccess { synced ->
            synchronized(stateLock) {
                kugouLikedTracks = synced
                rebuildStatesLocked()
            }
        }
        emit(result)
    }

    override fun likeTrack(
        trackId: TrackId,
        like: Boolean,
        title: String,
        kugouMeta: KugouTrackMeta?,
        legacyExternalId: Long?
    ): Flow<Result<Unit>> = flow {
        when (trackId.platform) {
            MusicPlatform.NETEASE -> {
                val neteaseId = trackId.neteaseIdOrNull()
                if (neteaseId == null) {
                    emit(Result.failure(IllegalArgumentException("无效网易云曲目 ID: ${trackId.canonical}")))
                    return@flow
                }
                likeSong(neteaseId, like).collect { emit(it) }
            }

            MusicPlatform.KUGOU -> {
                val kugouEnabled = isKugouEnabled()
                if (kugouLikeService != null && kugouEnabled && isKugouLoggedIn()) {
                    emit(performKugouLike(trackId, like, title, kugouMeta, legacyExternalId))
                    return@flow
                }
                if (legacyExternalId != null && externalLikes != null) {
                    localTrackByLegacyId[legacyExternalId] = trackId
                    emit(setExternalLikedLocked(legacyExternalId, like))
                    return@flow
                }
                val reason = if (!kugouEnabled) "酷狗音乐平台已关闭" else "请先登录酷狗账号"
                emit(Result.failure(IllegalStateException(reason)))
            }

            else -> {
                if (legacyExternalId != null && externalLikes != null) {
                    localTrackByLegacyId[legacyExternalId] = trackId
                    emit(setExternalLikedLocked(legacyExternalId, like))
                } else {
                    emit(Result.failure(IllegalStateException("该平台歌曲暂不支持云端收藏")))
                }
            }
        }
    }

    private suspend fun setExternalLikedLocked(legacyExternalId: Long, like: Boolean): Result<Unit> {
        val likesStore = externalLikes ?: return Result.failure(IllegalStateException("该歌曲不支持收藏"))
        val (wasExternalBefore, wasManualBefore) = synchronized(stateLock) {
            val wasExt = legacyExternalId in localExternalLikedIds
            val wasMan = legacyExternalId in manualNegativeIds
            if (like) {
                localExternalLikedIds = localExternalLikedIds + legacyExternalId
            } else {
                localExternalLikedIds = localExternalLikedIds - legacyExternalId
                manualNegativeIds = manualNegativeIds - legacyExternalId
            }
            rebuildStatesLocked()
            wasExt to wasMan
        }
        val result = likesStore.setLiked(legacyExternalId, like)
        if (result.isFailure) {
            synchronized(stateLock) {
                if (like && !wasExternalBefore && !wasManualBefore) {
                    localExternalLikedIds = localExternalLikedIds - legacyExternalId
                } else if (!like) {
                    if (wasExternalBefore) localExternalLikedIds = localExternalLikedIds + legacyExternalId
                    if (wasManualBefore) manualNegativeIds = manualNegativeIds + legacyExternalId
                }
                rebuildStatesLocked()
            }
        }
        return result
    }

    private suspend fun performKugouLike(
        trackId: TrackId,
        like: Boolean,
        title: String,
        kugouMeta: KugouTrackMeta?,
        legacyExternalId: Long?
    ): Result<Unit> {
        val service = kugouLikeService ?: return Result.failure(IllegalStateException("酷狗收藏服务未就绪"))
        val effectiveHash = kugouMeta?.hash?.trim()?.takeIf { it.isNotEmpty() } ?: trackId.rawId
        val normalizedTrackId = TrackId.kugou(effectiveHash)

        val (wasLikedBefore, addedLegacyId) = synchronized(stateLock) {
            var addedLegacy = false
            if (legacyExternalId != null && legacyExternalId < 0L) {
                val legacySet = kugouLegacyIdsByTrack.computeIfAbsent(normalizedTrackId) {
                    ConcurrentHashMap.newKeySet()
                }
                addedLegacy = legacySet.add(legacyExternalId)
            }
            val wasLiked = normalizedTrackId in kugouLikedTracks
            kugouLikedTracks = if (like) {
                kugouLikedTracks + normalizedTrackId
            } else {
                kugouLikedTracks - normalizedTrackId
            }
            rebuildStatesLocked()
            wasLiked to addedLegacy
        }

        val result = if (like) {
            service.likeTrack(
                trackName = title,
                hash = normalizedTrackId.rawId,
                albumId = kugouMeta?.albumId,
                mixSongId = kugouMeta?.mixSongId
            )
        } else {
            service.unlikeTrack(normalizedTrackId.rawId)
        }

        result.onFailure {
            synchronized(stateLock) {
                if (like && !wasLikedBefore) {
                    kugouLikedTracks = kugouLikedTracks - normalizedTrackId
                    if (addedLegacyId && legacyExternalId != null) {
                        kugouLegacyIdsByTrack[normalizedTrackId]?.remove(legacyExternalId)
                        if (kugouLegacyIdsByTrack[normalizedTrackId]?.isEmpty() == true) {
                            kugouLegacyIdsByTrack.remove(normalizedTrackId)
                        }
                    }
                } else if (!like && wasLikedBefore) {
                    kugouLikedTracks = kugouLikedTracks + normalizedTrackId
                    if (legacyExternalId != null && legacyExternalId < 0L) {
                        kugouLegacyIdsByTrack.computeIfAbsent(normalizedTrackId) {
                            ConcurrentHashMap.newKeySet()
                        }.add(legacyExternalId)
                    }
                }
                rebuildStatesLocked()
            }
        }
        return result
    }

    private suspend fun isKugouLoggedIn(): Boolean =
        userPreferences?.activeAccountOf(MusicPlatform.KUGOU)?.first() != null

    private fun rebuildStatesLocked() {
        val songIds = HashSet<Long>()
        val trackIds = LinkedHashSet<TrackId>()
        if (activePlatform == MusicPlatform.KUGOU) {
            kugouLikedTracks.forEach { trackId ->
                trackIds.add(trackId)
                songIds.add(canonicalLegacySongId(trackId))
                kugouLegacyIdsByTrack[trackId]?.let(songIds::addAll)
            }
        } else {
            songIds.addAll(neteaseLikedIds)
            songIds.addAll(localExternalLikedIds)
            songIds.addAll(manualNegativeIds)
            neteaseLikedIds.forEach { id -> trackIds.add(TrackId.netease(id)) }
            localExternalLikedIds.forEach { legacyId ->
                localTrackByLegacyId[legacyId]?.let(trackIds::add)
            }
        }
        _likedSongIds.value = songIds
        _likedTrackIds.value = trackIds
    }

    private fun canonicalLegacySongId(trackId: TrackId): Long =
        -1L - trackId.canonical.hashCode().toLong().absoluteValue
}

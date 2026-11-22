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

// 跨平台歌曲红心仓储：合并网易红心 + 酷狗云端红心 + 本机外部红心，并对外提供双视图状态流
class SongLikeRepositoryImpl(
    private val apiService: SongLikeApi,
    private val externalLikes: ExternalSongLikes? = null,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
    private val kugouLikeService: KugouLikeService? = null,
    private val userPreferences: UserPreferences? = null,
    private val resolveTrackByLegacyId: (suspend (Long) -> ExternalTrack?)? = null
) : SongLikeRepository {

    private val stateLock = Any()
    private var neteaseLikedIds: Set<Long> = emptySet()
    private var manualNegativeIds: Set<Long> = emptySet()
    private var localExternalLikedIds: Set<Long> = emptySet()
    private var kugouLikedTracks: Set<TrackId> = emptySet()
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
        if (kugouLikeService != null && userPreferences != null) {
            scope.launch {
                if (userPreferences.activeAccountOf(MusicPlatform.KUGOU).first() != null) {
                    syncKugouLikedTracks().first()
                }
            }
            scope.launch {
                AccountEvents.platformSwitched.collect { event ->
                    if (event.platform != MusicPlatform.KUGOU) return@collect
                    kugouLikeService.clearCache()
                    if (event.currentAccountId == null) {
                        synchronized(stateLock) {
                            kugouLikedTracks = emptySet()
                            rebuildStatesLocked()
                        }
                    } else {
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
            // 负数 ID 优先尝试按已登录酷狗云端曲目路由，未登录或非酷狗则平滑回退本机外部红心
            if (kugouLikeService != null && isKugouLoggedIn()) {
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

            val previousLegacy = _likedSongIds.value
            _likedSongIds.value = if (like) previousLegacy + songId else previousLegacy - songId
            val result = externalLikes?.setLiked(songId, like)
                ?: Result.failure(IllegalStateException("该歌曲不支持收藏"))
            if (result.isFailure) {
                _likedSongIds.value = previousLegacy
            }
            emit(result)
            return@flow
        }

        val previousNetease = synchronized(stateLock) {
            val prev = neteaseLikedIds
            neteaseLikedIds = if (like) prev + songId else prev - songId
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
                    neteaseLikedIds = previousNetease
                    rebuildStatesLocked()
                }
            }
            emit(result)
        }
    }

    override fun syncLikedSongIds(ids: Set<Long>) {
        synchronized(stateLock) {
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
        if (!isKugouLoggedIn()) {
            synchronized(stateLock) {
                kugouLikedTracks = emptySet()
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
                if (kugouLikeService != null && isKugouLoggedIn()) {
                    emit(performKugouLike(trackId, like, title, kugouMeta, legacyExternalId))
                    return@flow
                }
                if (legacyExternalId != null && externalLikes != null) {
                    localTrackByLegacyId[legacyExternalId] = trackId
                    val result = externalLikes.setLiked(legacyExternalId, like)
                    emit(result)
                    return@flow
                }
                emit(Result.failure(IllegalStateException("请先登录酷狗账号")))
            }

            else -> {
                if (legacyExternalId != null && externalLikes != null) {
                    localTrackByLegacyId[legacyExternalId] = trackId
                    emit(externalLikes.setLiked(legacyExternalId, like))
                } else {
                    emit(Result.failure(IllegalStateException("该平台歌曲暂不支持云端收藏")))
                }
            }
        }
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
        if (legacyExternalId != null && legacyExternalId < 0L) {
            kugouLegacyIdsByTrack.computeIfAbsent(normalizedTrackId) { ConcurrentHashMap.newKeySet() }
                .add(legacyExternalId)
        }

        val previousKugou = synchronized(stateLock) {
            val prev = kugouLikedTracks
            kugouLikedTracks = if (like) prev + normalizedTrackId else prev - normalizedTrackId
            rebuildStatesLocked()
            prev
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
                kugouLikedTracks = previousKugou
                rebuildStatesLocked()
            }
        }
        return result
    }

    private suspend fun isKugouLoggedIn(): Boolean =
        userPreferences?.activeAccountOf(MusicPlatform.KUGOU)?.first() != null

    private fun rebuildStatesLocked() {
        val mergedSongIds = HashSet<Long>(
            neteaseLikedIds.size + localExternalLikedIds.size + kugouLikedTracks.size + manualNegativeIds.size
        )
        mergedSongIds.addAll(neteaseLikedIds)
        mergedSongIds.addAll(localExternalLikedIds)
        mergedSongIds.addAll(manualNegativeIds)

        val mergedTrackIds = LinkedHashSet<TrackId>(
            neteaseLikedIds.size + localExternalLikedIds.size + kugouLikedTracks.size
        )
        neteaseLikedIds.forEach { id -> mergedTrackIds.add(TrackId.netease(id)) }
        localExternalLikedIds.forEach { legacyId ->
            localTrackByLegacyId[legacyId]?.let(mergedTrackIds::add)
        }
        kugouLikedTracks.forEach { trackId ->
            mergedTrackIds.add(trackId)
            mergedSongIds.add(canonicalLegacySongId(trackId))
            kugouLegacyIdsByTrack[trackId]?.let(mergedSongIds::addAll)
        }

        _likedSongIds.value = mergedSongIds
        _likedTrackIds.value = mergedTrackIds
    }

    private fun canonicalLegacySongId(trackId: TrackId): Long =
        -1L - trackId.canonical.hashCode().toLong().absoluteValue
}

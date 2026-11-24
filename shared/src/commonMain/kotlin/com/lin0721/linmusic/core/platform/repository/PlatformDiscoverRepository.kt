package com.lin0721.linmusic.core.platform.repository

import com.lin0721.linmusic.core.kugou.runCatchingCancellable

import com.lin0721.linmusic.core.kugou.KugouDiscoverService
import com.lin0721.linmusic.core.platform.AlbumId
import com.lin0721.linmusic.core.platform.PlaylistId
import com.lin0721.linmusic.core.platform.TrackId
import com.lin0721.linmusic.core.platform.UnifiedAlbum
import com.lin0721.linmusic.core.platform.UnifiedArtist
import com.lin0721.linmusic.core.platform.UnifiedAudioQuality
import com.lin0721.linmusic.core.platform.UnifiedPlaylist
import com.lin0721.linmusic.core.platform.UnifiedTrack
import com.lin0721.linmusic.core.platform.toUnified
import com.lin0721.linmusic.core.player.PersonalFmMode
import com.lin0721.linmusic.core.player.data.PlaybackRepository
import com.lin0721.linmusic.core.source.MusicPlatform
import com.lin0721.linmusic.feature.home.data.DailySong
import com.lin0721.linmusic.feature.home.data.HomeRepository
import com.lin0721.linmusic.feature.playlist.data.PlaylistRepository
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

data class UnifiedDailySong(
    val track: UnifiedTrack,
    val reason: String? = null
)

data class UnifiedRank(
    val platform: MusicPlatform,
    val id: String,
    val rawRankId: Long,
    val rankCid: Long = 0L,
    val name: String,
    val coverUrl: String,
    val updateFrequency: String = "",
    val topSongs: List<String> = emptyList(),
    val intro: String = ""
)

data class UnifiedRankTracksPage(
    val rank: UnifiedRank,
    val tracks: List<UnifiedTrack>,
    val totalCount: Int = tracks.size,
    val page: Int = 1,
    val hasMore: Boolean = false
)

data class UnifiedDiscoverPlaylistPage(
    val playlists: List<UnifiedPlaylist>,
    val page: Int = 1,
    val hasMore: Boolean = false
)

data class UnifiedStyleRecommend(
    val title: String = "",
    val tracks: List<UnifiedTrack> = emptyList()
)

data class UnifiedDiscoverFeed(
    val platform: MusicPlatform,
    val dailySongs: List<UnifiedDailySong> = emptyList(),
    val recommendPlaylists: List<UnifiedPlaylist> = emptyList(),
    val toplists: List<UnifiedRank> = emptyList(),
    val discoverTracks: List<UnifiedTrack> = emptyList(),
    val newSongs: List<UnifiedTrack> = emptyList(),
    val newAlbums: List<UnifiedAlbum> = emptyList(),
    val styleRecommend: UnifiedStyleRecommend? = null
)

// 跨平台发现、每日推荐、私人 FM、歌单广场与排行榜契约
interface PlatformDiscoverRepository {
    fun getDailyRecommendSongs(platform: MusicPlatform): Flow<Result<List<UnifiedDailySong>>>

    fun getPersonalFmSongs(
        platform: MusicPlatform,
        mode: PersonalFmMode = PersonalFmMode.DEFAULT
    ): Flow<Result<List<UnifiedTrack>>>

    fun trashFmSong(
        platform: MusicPlatform,
        track: UnifiedTrack,
        playedSeconds: Long = 0L
    ): Flow<Result<Unit>>

    fun getRecommendedPlaylists(
        platform: MusicPlatform,
        page: Int = 1,
        pageSize: Int = 30
    ): Flow<Result<UnifiedDiscoverPlaylistPage>>

    fun getRankList(platform: MusicPlatform): Flow<Result<List<UnifiedRank>>>

    fun getRankTracks(
        rank: UnifiedRank,
        page: Int = 1,
        pageSize: Int = 30
    ): Flow<Result<UnifiedRankTracksPage>>

    fun getDiscoverFeed(
        platform: MusicPlatform,
        refresh: Boolean = false
    ): Flow<Result<UnifiedDiscoverFeed>>
}

class NeteasePlatformDiscoverRepository(
    private val homeRepository: HomeRepository,
    private val playbackRepository: PlaybackRepository,
    private val playlistRepository: PlaylistRepository
) : PlatformDiscoverRepository {

    override fun getDailyRecommendSongs(platform: MusicPlatform): Flow<Result<List<UnifiedDailySong>>> =
        homeRepository.getDailyRecommendSongs().map { res ->
            res.map { list -> list.map { UnifiedDailySong(track = it.toUnifiedTrack(), reason = it.reason) } }
        }

    override fun getPersonalFmSongs(
        platform: MusicPlatform,
        mode: PersonalFmMode
    ): Flow<Result<List<UnifiedTrack>>> =
        playbackRepository.getPersonalFmSongs(mode).map { res ->
            res.map { list -> list.map { it.toUnified() } }
        }

    override fun trashFmSong(
        platform: MusicPlatform,
        track: UnifiedTrack,
        playedSeconds: Long
    ): Flow<Result<Unit>> {
        val songId = track.id.neteaseIdOrNull() ?: return flowOf(Result.success(Unit))
        return playbackRepository.trashFmSong(songId, playedSeconds)
    }

    override fun getRecommendedPlaylists(
        platform: MusicPlatform,
        page: Int,
        pageSize: Int
    ): Flow<Result<UnifiedDiscoverPlaylistPage>> =
        homeRepository.getPersonalizedPlaylists().map { res ->
            res.map { data ->
                UnifiedDiscoverPlaylistPage(
                    playlists = data.playlists.map { p ->
                        UnifiedPlaylist(
                            id = PlaylistId.netease(p.id),
                            name = p.name,
                            coverUrl = p.picUrl
                        )
                    },
                    page = page,
                    hasMore = false
                )
            }
        }

    override fun getRankList(platform: MusicPlatform): Flow<Result<List<UnifiedRank>>> =
        homeRepository.getToplistDetail().map { res ->
            res.map { list ->
                list.map { top ->
                    UnifiedRank(
                        platform = MusicPlatform.NETEASE,
                        id = top.id.toString(),
                        rawRankId = top.id,
                        name = top.name,
                        coverUrl = top.coverUrl,
                        updateFrequency = top.updateDesc,
                        topSongs = top.topSongs
                    )
                }
            }
        }

    override fun getRankTracks(
        rank: UnifiedRank,
        page: Int,
        pageSize: Int
    ): Flow<Result<UnifiedRankTracksPage>> {
        val rankId = rank.rawRankId.takeIf { it > 0L } ?: rank.id.toLongOrNull()
            ?: return flowOf(Result.failure(IllegalArgumentException("无效网易云榜单 ID: ${rank.id}")))
        return playlistRepository.getPlaylistDetail(rankId).map { res ->
            res.map { detail ->
                val tracks = detail.tracks.map { it.toUnified() }
                UnifiedRankTracksPage(
                    rank = rank,
                    tracks = tracks,
                    totalCount = detail.trackCount.takeIf { it > 0 } ?: tracks.size,
                    page = 1,
                    hasMore = false
                )
            }
        }
    }

    override fun getDiscoverFeed(
        platform: MusicPlatform,
        refresh: Boolean
    ): Flow<Result<UnifiedDiscoverFeed>> = flow {
        val result = runCatchingCancellable {
            coroutineScope {
                val dailyDeferred = async { runCatchingCancellable { getDailyRecommendSongs(platform).first() }.getOrNull() }
                val playlistDeferred = async { runCatchingCancellable { getRecommendedPlaylists(platform).first() }.getOrNull() }
                val rankDeferred = async { runCatchingCancellable { getRankList(platform).first() }.getOrNull() }

                val dailyRes = dailyDeferred.await()
                val playlistRes = playlistDeferred.await()
                val rankRes = rankDeferred.await()
                val daily = dailyRes?.getOrNull().orEmpty()
                val playlists = playlistRes?.getOrNull()?.playlists.orEmpty()
                val ranks = rankRes?.getOrNull().orEmpty()

                if (dailyRes?.isSuccess != true && playlistRes?.isSuccess != true && rankRes?.isSuccess != true) {
                    val cause = playlistRes?.exceptionOrNull()
                        ?: rankRes?.exceptionOrNull()
                        ?: dailyRes?.exceptionOrNull()
                        ?: IllegalStateException("获取网易云发现页数据失败")
                    throw cause
                }
                UnifiedDiscoverFeed(
                    platform = MusicPlatform.NETEASE,
                    dailySongs = daily,
                    recommendPlaylists = playlists,
                    toplists = ranks
                )
            }
        }
        emit(result)
    }

    private fun DailySong.toUnifiedTrack(): UnifiedTrack {
        val unifiedArtists = ar.map { it.toUnified(MusicPlatform.NETEASE) }
        val unifiedAlbum = UnifiedAlbum(
            id = if (al.id > 0L) com.lin0721.linmusic.core.platform.AlbumId.netease(al.id) else null,
            name = al.name,
            coverUrl = al.picUrl,
            artists = al.artists.map { it.toUnified(MusicPlatform.NETEASE) },
            publishTimeMs = al.publishTime
        )
        return UnifiedTrack(
            id = TrackId.netease(id),
            name = name,
            artists = unifiedArtists,
            artistNames = unifiedArtists.joinToString(" / ") { it.name },
            album = unifiedAlbum,
            coverUrl = al.picUrl,
            maxQuality = UnifiedAudioQuality.fromNeteaseCode(privilege?.maxBrLevel),
            fee = fee,
            playable = privilege?.st?.let { it >= 0 } ?: true
        )
    }
}

class KugouPlatformDiscoverRepository(
    private val discoverService: KugouDiscoverService,
    private val isEnabled: suspend () -> Boolean = { true }
) : PlatformDiscoverRepository {

    override fun getDailyRecommendSongs(platform: MusicPlatform): Flow<Result<List<UnifiedDailySong>>> = flow {
        if (!isEnabled()) {
            emit(Result.failure(IllegalStateException("酷狗音乐平台已关闭")))
            return@flow
        }
        emit(
            discoverService.getDailyRecommendSongs().map { res ->
                res.tracks.map { UnifiedDailySong(track = it.toUnifiedTrack()) }
            }
        )
    }

    override fun getPersonalFmSongs(
        platform: MusicPlatform,
        mode: PersonalFmMode
    ): Flow<Result<List<UnifiedTrack>>> = flow {
        if (!isEnabled()) {
            emit(Result.failure(IllegalStateException("酷狗音乐平台已关闭")))
            return@flow
        }
        val kugouMode = when (mode) {
            PersonalFmMode.DEFAULT -> "normal"
            else -> "normal"
        }
        emit(
            discoverService.getPersonalFm(mode = kugouMode).map { res ->
                res.tracks.map { it.toUnifiedTrack() }
            }
        )
    }

    override fun trashFmSong(
        platform: MusicPlatform,
        track: UnifiedTrack,
        playedSeconds: Long
    ): Flow<Result<Unit>> = flow {
        if (!isEnabled()) {
            emit(Result.success(Unit))
            return@flow
        }
        val hash = (track.kugouMeta?.hash ?: track.id.rawId).trim().lowercase()
        if (hash.isBlank()) {
            emit(Result.success(Unit))
            return@flow
        }
        emit(
            discoverService.getPersonalFm(
                action = "dislike",
                hash = hash,
                songId = track.kugouMeta?.mixSongId,
                playTime = playedSeconds.coerceAtLeast(0L)
            ).map { }
        )
    }

    override fun getRecommendedPlaylists(
        platform: MusicPlatform,
        page: Int,
        pageSize: Int
    ): Flow<Result<UnifiedDiscoverPlaylistPage>> = flow {
        if (!isEnabled()) {
            emit(Result.failure(IllegalStateException("酷狗音乐平台已关闭")))
            return@flow
        }
        emit(
            discoverService.getRecommendedPlaylists(page = page, pageSize = pageSize).map { res ->
                UnifiedDiscoverPlaylistPage(
                    playlists = res.playlists.map { dto ->
                        UnifiedPlaylist(
                            id = dto.preferredPlaylistId(),
                            name = dto.name,
                            coverUrl = dto.coverUrl,
                            description = dto.intro.takeIf { it.isNotBlank() },
                            playCount = dto.playCount,
                            creator = dto.toCreatorOrNull(),
                            tags = dto.tags
                        )
                    },
                    page = res.page,
                    hasMore = res.hasMore
                )
            }
        )
    }

    override fun getRankList(platform: MusicPlatform): Flow<Result<List<UnifiedRank>>> = flow {
        if (!isEnabled()) {
            emit(Result.failure(IllegalStateException("酷狗音乐平台已关闭")))
            return@flow
        }
        emit(
            discoverService.getRankList().map { list ->
                list.map { r ->
                    UnifiedRank(
                        platform = MusicPlatform.KUGOU,
                        id = r.rankId.toString(),
                        rawRankId = r.rankId,
                        rankCid = r.rankCid,
                        name = r.name,
                        coverUrl = r.coverUrl,
                        updateFrequency = r.updateFrequency,
                        topSongs = r.topSongs,
                        intro = r.intro
                    )
                }
            }
        )
    }

    override fun getRankTracks(
        rank: UnifiedRank,
        page: Int,
        pageSize: Int
    ): Flow<Result<UnifiedRankTracksPage>> = flow {
        if (!isEnabled()) {
            emit(Result.failure(IllegalStateException("酷狗音乐平台已关闭")))
            return@flow
        }
        val rankId = rank.rawRankId.takeIf { it > 0L } ?: rank.id.toLongOrNull()
        if (rankId == null || rankId <= 0L) {
            emit(Result.failure(IllegalArgumentException("无效酷狗排行榜 ID: ${rank.id}")))
            return@flow
        }
        emit(
            discoverService.getRankTracks(
                rankId = rankId,
                rankCid = rank.rankCid,
                page = page,
                pageSize = pageSize
            ).map { res ->
                UnifiedRankTracksPage(
                    rank = rank,
                    tracks = res.tracks.map { it.toUnifiedTrack() },
                    totalCount = res.totalCount,
                    page = res.page,
                    hasMore = res.hasMore
                )
            }
        )
    }

    override fun getDiscoverFeed(
        platform: MusicPlatform,
        refresh: Boolean
    ): Flow<Result<UnifiedDiscoverFeed>> = flow {
        if (!isEnabled()) {
            emit(Result.failure(IllegalStateException("酷狗音乐平台已关闭")))
            return@flow
        }
        val result = runCatchingCancellable {
            coroutineScope {
                val discoverDeferred = async { discoverService.getHomeDiscover(pageSize = 20) }
                val dailyDeferred = async { discoverService.getDailyRecommendSongs() }
                val playlistDeferred = async { discoverService.getRecommendedPlaylists(page = 1, pageSize = 30) }
                val rankDeferred = async { discoverService.getRankList() }
                val newSongsDeferred = async { discoverService.getNewSongs(page = 1, pageSize = 30) }
                val newAlbumsDeferred = async { discoverService.getNewAlbums(page = 1, pageSize = 30) }
                val styleRecDeferred = async { discoverService.getStyleRecommend() }

                val discoverRes = discoverDeferred.await()
                val dailyRes = dailyDeferred.await()
                val playlistRes = playlistDeferred.await()
                val rankRes = rankDeferred.await()
                val newSongsRes = newSongsDeferred.await()
                val newAlbumsRes = newAlbumsDeferred.await()
                val styleRecRes = styleRecDeferred.await()

                val allFailed = discoverRes.isFailure &&
                    dailyRes.isFailure &&
                    playlistRes.isFailure &&
                    rankRes.isFailure &&
                    newSongsRes.isFailure &&
                    newAlbumsRes.isFailure &&
                    styleRecRes.isFailure
                if (allFailed) {
                    throw (discoverRes.exceptionOrNull()
                        ?: playlistRes.exceptionOrNull()
                        ?: rankRes.exceptionOrNull()
                        ?: dailyRes.exceptionOrNull()
                        ?: newSongsRes.exceptionOrNull()
                        ?: newAlbumsRes.exceptionOrNull()
                        ?: styleRecRes.exceptionOrNull()
                        ?: IllegalStateException("获取酷狗发现页数据失败"))
                }

                UnifiedDiscoverFeed(
                    platform = MusicPlatform.KUGOU,
                    dailySongs = dailyRes.getOrNull()?.tracks.orEmpty().map { UnifiedDailySong(track = it.toUnifiedTrack()) },
                    recommendPlaylists = playlistRes.getOrNull()?.playlists.orEmpty().map { dto ->
                        UnifiedPlaylist(
                            id = dto.preferredPlaylistId(),
                            name = dto.name,
                            coverUrl = dto.coverUrl,
                            description = dto.intro.takeIf { it.isNotBlank() },
                            playCount = dto.playCount,
                            creator = dto.toCreatorOrNull(),
                            tags = dto.tags
                        )
                    },
                    toplists = rankRes.getOrNull().orEmpty().map { r ->
                        UnifiedRank(
                            platform = MusicPlatform.KUGOU,
                            id = r.rankId.toString(),
                            rawRankId = r.rankId,
                            rankCid = r.rankCid,
                            name = r.name,
                            coverUrl = r.coverUrl,
                            updateFrequency = r.updateFrequency,
                            topSongs = r.topSongs,
                            intro = r.intro
                        )
                    },
                    discoverTracks = discoverRes.getOrNull().orEmpty().map { it.track.toUnifiedTrack() },
                    newSongs = newSongsRes.getOrNull().orEmpty().map { it.toUnifiedTrack() },
                    newAlbums = newAlbumsRes.getOrNull().orEmpty().map { dto ->
                        UnifiedAlbum(
                            id = AlbumId.kugou(dto.albumId.toString()),
                            name = dto.albumName,
                            coverUrl = dto.coverUrl,
                            artists = dto.singerName.split("、", "/", ",")
                                .map { it.trim() }
                                .filter { it.isNotEmpty() }
                                .map { UnifiedArtist(name = it) }
                        )
                    },
                    styleRecommend = styleRecRes.getOrNull()?.let { res ->
                        UnifiedStyleRecommend(
                            title = res.title,
                            tracks = res.tracks.map { it.toUnifiedTrack() }
                        )
                    }
                )
            }
        }
        emit(result)
    }
}

// 跨平台发现路由器：kugouPlatformEnabled 关闭时自动回退网易并跳过酷狗请求
class PlatformDiscoverRouter(
    private val netease: PlatformDiscoverRepository,
    private val kugou: PlatformDiscoverRepository,
    private val isKugouEnabled: suspend () -> Boolean = { true }
) : PlatformDiscoverRepository {

    suspend fun resolveBrowsePlatform(requested: MusicPlatform): MusicPlatform =
        when (requested) {
            MusicPlatform.KUGOU -> if (isKugouEnabled()) MusicPlatform.KUGOU else MusicPlatform.NETEASE
            MusicPlatform.NETEASE -> MusicPlatform.NETEASE
            else -> MusicPlatform.NETEASE
        }

    override fun getDailyRecommendSongs(platform: MusicPlatform): Flow<Result<List<UnifiedDailySong>>> =
        routeWithFallback(platform) { repo, target -> repo.getDailyRecommendSongs(target) }

    override fun getPersonalFmSongs(
        platform: MusicPlatform,
        mode: PersonalFmMode
    ): Flow<Result<List<UnifiedTrack>>> =
        routeWithFallback(platform) { repo, target -> repo.getPersonalFmSongs(target, mode) }

    override fun trashFmSong(
        platform: MusicPlatform,
        track: UnifiedTrack,
        playedSeconds: Long
    ): Flow<Result<Unit>> = when (platform) {
        MusicPlatform.KUGOU -> flow {
            if (!isKugouEnabled()) {
                emit(Result.success(Unit))
            } else {
                emit(kugou.trashFmSong(MusicPlatform.KUGOU, track, playedSeconds).first())
            }
        }
        MusicPlatform.NETEASE -> netease.trashFmSong(MusicPlatform.NETEASE, track, playedSeconds)
        else -> flowOf(Result.success(Unit))
    }

    override fun getRecommendedPlaylists(
        platform: MusicPlatform,
        page: Int,
        pageSize: Int
    ): Flow<Result<UnifiedDiscoverPlaylistPage>> =
        routeWithFallback(platform) { repo, target -> repo.getRecommendedPlaylists(target, page, pageSize) }

    override fun getRankList(platform: MusicPlatform): Flow<Result<List<UnifiedRank>>> =
        routeWithFallback(platform) { repo, target -> repo.getRankList(target) }

    override fun getRankTracks(
        rank: UnifiedRank,
        page: Int,
        pageSize: Int
    ): Flow<Result<UnifiedRankTracksPage>> = when (rank.platform) {
        MusicPlatform.NETEASE -> netease.getRankTracks(rank, page, pageSize)
        MusicPlatform.KUGOU -> flow {
            if (!isKugouEnabled()) {
                emit(Result.failure(IllegalStateException("酷狗音乐平台已关闭")))
            } else {
                emit(kugou.getRankTracks(rank, page, pageSize).first())
            }
        }
        else -> flowOf(Result.failure(UnsupportedOperationException("暂不支持该平台榜单: ${rank.platform.key}")))
    }

    override fun getDiscoverFeed(
        platform: MusicPlatform,
        refresh: Boolean
    ): Flow<Result<UnifiedDiscoverFeed>> =
        routeWithFallback(platform) { repo, target -> repo.getDiscoverFeed(target, refresh) }

    private fun <T> routeWithFallback(
        requested: MusicPlatform,
        call: (PlatformDiscoverRepository, MusicPlatform) -> Flow<Result<T>>
    ): Flow<Result<T>> = flow {
        val target = resolveBrowsePlatform(requested)
        val repo = if (target == MusicPlatform.KUGOU) kugou else netease
        emit(call(repo, target).first())
    }
}

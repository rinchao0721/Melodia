package com.lin0721.linmusic.core.platform.repository

import com.lin0721.linmusic.core.kugou.KugouArtistService
import com.lin0721.linmusic.core.kugou.KugouPlaybackService
import com.lin0721.linmusic.core.kugou.KugouPlaylistService
import com.lin0721.linmusic.core.kugou.KugouUserPlaylistDto
import com.lin0721.linmusic.core.platform.AccountId
import com.lin0721.linmusic.core.platform.AlbumId
import com.lin0721.linmusic.core.platform.ArtistId
import com.lin0721.linmusic.core.platform.KugouTrackMeta
import com.lin0721.linmusic.core.platform.PlaylistId
import com.lin0721.linmusic.core.platform.TrackId
import com.lin0721.linmusic.core.platform.UnifiedAlbum
import com.lin0721.linmusic.core.platform.UnifiedArtist
import com.lin0721.linmusic.core.platform.UnifiedCreator
import com.lin0721.linmusic.core.platform.UnifiedTrack
import com.lin0721.linmusic.core.platform.toUnified
import com.lin0721.linmusic.core.source.MusicPlatform
import com.lin0721.linmusic.core.userartist.UserArtistRepository
import com.lin0721.linmusic.core.userplaylist.UserPlaylistRepository
import com.lin0721.linmusic.feature.library.data.LibraryRepository
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

data class UnifiedUserPlaylist(
    val id: PlaylistId,
    val name: String,
    val coverUrl: String = "",
    val playCount: Long = 0L,
    val trackCount: Int = 0,
    val ownerId: AccountId? = null,
    val creator: UnifiedCreator? = null,
    val updateTimeMs: Long = 0L,
    val isTop: Boolean = false,
    val isLikedPlaylist: Boolean = false,
    val isPrivate: Boolean = false,
    val isCollected: Boolean = false,
    val listId: Long? = null,
    val globalCollectionId: String? = null,
    val listVer: Long = 0L,
    val totalVer: Long = 0L
)

data class UnifiedCollectedAlbum(
    val id: AlbumId,
    val name: String,
    val coverUrl: String = "",
    val artists: List<UnifiedArtist> = emptyList(),
    val size: Int = 0,
    val subTimeMs: Long = 0L
)

data class UnifiedUserRecordTrack(
    val track: UnifiedTrack,
    val playCount: Int
)

data class UnifiedUserSubcount(
    val artistCount: Int = 0,
    val createdPlaylistCount: Int = 0,
    val subPlaylistCount: Int = 0,
    val mvCount: Int = 0
) {
    val playlistCount: Int get() = createdPlaylistCount + subPlaylistCount
}

interface PlatformLibraryRepository {
    fun getUserPlaylists(accountId: AccountId, limit: Int = 1000): Flow<Result<List<UnifiedUserPlaylist>>>
    fun getFavoriteArtists(platform: MusicPlatform): Flow<Result<List<UnifiedArtist>>>
    fun getCollectedAlbums(platform: MusicPlatform, limit: Int = 1000): Flow<Result<List<UnifiedCollectedAlbum>>>
    fun getUserRecord(accountId: AccountId, type: Int = 0): Flow<Result<List<UnifiedUserRecordTrack>>>
    fun getUserSubcount(platform: MusicPlatform): Flow<Result<UnifiedUserSubcount>>
    fun updatePlaylistOrder(platform: MusicPlatform, playlistIds: List<PlaylistId>): Flow<Result<Unit>>
    fun setPlaylistTop(playlistId: PlaylistId, top: Boolean): Flow<Result<Unit>>
}

class NeteasePlatformLibraryRepository(
    private val userPlaylistRepository: UserPlaylistRepository,
    private val userArtistRepository: UserArtistRepository,
    private val libraryRepository: LibraryRepository
) : PlatformLibraryRepository {

    override fun getUserPlaylists(accountId: AccountId, limit: Int): Flow<Result<List<UnifiedUserPlaylist>>> {
        val uid = accountId.neteaseIdOrNull()
            ?: return flowOf(Result.failure(IllegalArgumentException("无效网易云账号 ID: ${accountId.canonical}")))
        return userPlaylistRepository.getUserPlaylists(uid, limit).map { result ->
            result.map { list ->
                list.map { item ->
                    UnifiedUserPlaylist(
                        id = PlaylistId.netease(item.id),
                        name = item.name,
                        coverUrl = item.coverImgUrl,
                        playCount = item.playCount,
                        trackCount = item.trackCount,
                        ownerId = item.userId.takeIf { it > 0L }?.let { AccountId.netease(it) },
                        creator = item.creator?.toUnified(MusicPlatform.NETEASE),
                        updateTimeMs = item.updateTime,
                        isTop = item.top,
                        isLikedPlaylist = item.isLikedPlaylist,
                        isCollected = item.userId > 0L && item.userId != uid
                    )
                }
            }
        }
    }

    override fun getFavoriteArtists(platform: MusicPlatform): Flow<Result<List<UnifiedArtist>>> =
        userArtistRepository.getFavoriteArtists().map { result ->
            result.map { list ->
                list.map { artist ->
                    UnifiedArtist(
                        id = if (artist.id > 0L) ArtistId.netease(artist.id) else null,
                        name = artist.name,
                        avatarUrl = artist.avatarUrl,
                        picUrl = artist.avatarUrl
                    )
                }
            }
        }

    override fun getCollectedAlbums(platform: MusicPlatform, limit: Int): Flow<Result<List<UnifiedCollectedAlbum>>> =
        libraryRepository.getCollectedAlbums(limit).map { result ->
            result.map { list ->
                list.map { item ->
                    UnifiedCollectedAlbum(
                        id = AlbumId.netease(item.id),
                        name = item.name,
                        coverUrl = item.picUrl,
                        artists = item.artists.map { it.toUnified(MusicPlatform.NETEASE) },
                        size = item.size,
                        subTimeMs = item.subTime
                    )
                }
            }
        }

    override fun getUserRecord(accountId: AccountId, type: Int): Flow<Result<List<UnifiedUserRecordTrack>>> {
        val uid = accountId.neteaseIdOrNull()
            ?: return flowOf(Result.failure(IllegalArgumentException("无效网易云账号 ID: ${accountId.canonical}")))
        return libraryRepository.getUserRecord(uid, type).map { result ->
            result.map { list ->
                list.map { item ->
                    UnifiedUserRecordTrack(
                        track = item.track.toUnified(),
                        playCount = item.playCount
                    )
                }
            }
        }
    }

    override fun getUserSubcount(platform: MusicPlatform): Flow<Result<UnifiedUserSubcount>> =
        libraryRepository.getUserSubcount().map { result ->
            result.map { resp ->
                UnifiedUserSubcount(
                    artistCount = resp.artistCount,
                    createdPlaylistCount = resp.createdPlaylistCount,
                    subPlaylistCount = resp.subPlaylistCount,
                    mvCount = resp.mvCount
                )
            }
        }

    override fun updatePlaylistOrder(platform: MusicPlatform, playlistIds: List<PlaylistId>): Flow<Result<Unit>> {
        val numericIds = playlistIds.mapNotNull { it.neteaseIdOrNull() }
        return userPlaylistRepository.updatePlaylistOrder(numericIds)
    }

    override fun setPlaylistTop(playlistId: PlaylistId, top: Boolean): Flow<Result<Unit>> {
        val id = playlistId.neteaseIdOrNull()
            ?: return flowOf(Result.failure(IllegalArgumentException("无效网易云歌单 ID: ${playlistId.canonical}")))
        return userPlaylistRepository.setPlaylistTop(id, top)
    }
}

class KugouPlatformLibraryRepository(
    private val playlistService: KugouPlaylistService,
    private val artistService: KugouArtistService,
    private val playbackService: KugouPlaybackService,
    private val isEnabled: suspend () -> Boolean = { true }
) : PlatformLibraryRepository {

    override fun getUserPlaylists(accountId: AccountId, limit: Int): Flow<Result<List<UnifiedUserPlaylist>>> = flow {
        if (!isEnabled()) {
            emit(Result.failure(IllegalStateException("酷狗音乐平台已关闭")))
            return@flow
        }
        val safeLimit = limit.coerceAtLeast(1)
        val collected = fetchAllUserPlaylists(maxCount = safeLimit).getOrElse {
            emit(Result.failure(it))
            return@flow
        }
        val items = collected.take(safeLimit)
        val semaphore = Semaphore(3)
        val fallbackCovers: Map<Long, String> = coroutineScope {
            items.filter { it.coverUrl.isBlank() && it.trackCount > 0 && it.listId > 0L }
                .map { dto ->
                    async {
                        semaphore.withPermit {
                            val cover = playlistService.getCloudPlaylistTracks(
                                listId = dto.listId,
                                type = dto.type,
                                page = 1,
                                pageSize = 1
                            ).getOrNull()?.tracks?.firstOrNull()?.toUnifiedTrack()?.coverUrl.orEmpty()
                            dto.listId to cover
                        }
                    }
                }
                .awaitAll()
                .toMap()
        }
        emit(
            Result.success(
                items.map { dto ->
                    val resolvedCover = dto.coverUrl.ifBlank { fallbackCovers[dto.listId].orEmpty() }
                    UnifiedUserPlaylist(
                        id = dto.preferredPlaylistId(),
                        name = dto.name,
                        coverUrl = resolvedCover,
                        trackCount = dto.trackCount,
                        ownerId = dto.createUserId.takeIf { it > 0L }?.let { AccountId.kugou(it) } ?: accountId,
                        creator = if (dto.createUsername.isNotBlank() || dto.createUserId > 0L) {
                            UnifiedCreator(
                                id = dto.createUserId.takeIf { it > 0L }?.let { AccountId.kugou(it) },
                                nickname = dto.createUsername
                            )
                        } else {
                            null
                        },
                        isTop = dto.sort > 0,
                        isLikedPlaylist = dto.isDefaultLike,
                        isPrivate = dto.isPrivate,
                        isCollected = dto.isCollected,
                        listId = dto.listId,
                        globalCollectionId = dto.globalCollectionId.takeIf { it.isNotBlank() },
                        listVer = dto.listVer,
                        totalVer = dto.totalVer
                    )
                }
            )
        )
    }

    override fun getFavoriteArtists(platform: MusicPlatform): Flow<Result<List<UnifiedArtist>>> = flow {
        if (!isEnabled()) {
            emit(Result.failure(IllegalStateException("酷狗音乐平台已关闭")))
            return@flow
        }
        emit(artistService.getUserFollowedArtists().map { list -> list.map { it.toUnifiedArtist() } })
    }

    override fun getCollectedAlbums(platform: MusicPlatform, limit: Int): Flow<Result<List<UnifiedCollectedAlbum>>> =
        flowOf(Result.success(emptyList()))

    override fun getUserRecord(accountId: AccountId, type: Int): Flow<Result<List<UnifiedUserRecordTrack>>> = flow {
        if (!isEnabled()) {
            emit(Result.failure(IllegalStateException("酷狗音乐平台已关闭")))
            return@flow
        }
        val res = playbackService.getUserPlayHistory().map { page ->
            page.items.mapNotNull { item ->
                val cleanHash = item.hash.trim().lowercase()
                if (cleanHash.isEmpty()) return@mapNotNull null
                val artists = item.singerName
                    .split("、", "/", ",")
                    .map { it.trim() }
                    .filter { it.isNotEmpty() }
                    .map { UnifiedArtist(name = it) }
                UnifiedUserRecordTrack(
                    track = UnifiedTrack(
                        id = TrackId.kugou(cleanHash),
                        name = item.songName.ifBlank { cleanHash },
                        artists = artists,
                        artistNames = item.singerName,
                        album = if (item.albumName.isNotBlank() || item.albumId.isNotBlank()) {
                            UnifiedAlbum(
                                id = item.albumId.takeIf { it.isNotBlank() && it != "0" }?.let { AlbumId.kugou(it) },
                                name = item.albumName,
                                coverUrl = item.coverUrl
                            )
                        } else {
                            null
                        },
                        durationMs = item.durationMs,
                        coverUrl = item.coverUrl,
                        kugouMeta = KugouTrackMeta(
                            hash = cleanHash,
                            mixSongId = item.mixSongId.takeIf { it > 0L },
                            albumAudioId = item.albumAudioId.takeIf { it > 0L },
                            albumId = item.albumId.takeIf { it.isNotBlank() && it != "0" }
                        )
                    ),
                    playCount = item.playCount
                )
            }
        }
        emit(res)
    }

    override fun getUserSubcount(platform: MusicPlatform): Flow<Result<UnifiedUserSubcount>> = flow {
        if (!isEnabled()) {
            emit(Result.failure(IllegalStateException("酷狗音乐平台已关闭")))
            return@flow
        }
        val playlists = fetchAllUserPlaylists().getOrElse {
            emit(Result.failure(it))
            return@flow
        }
        val artists = artistService.getUserFollowedArtists().getOrDefault(emptyList())
        val createdCount = playlists.count { !it.isCollected }
        val subCount = playlists.count { it.isCollected }
        emit(
            Result.success(
                UnifiedUserSubcount(
                    artistCount = artists.size,
                    createdPlaylistCount = createdCount,
                    subPlaylistCount = subCount
                )
            )
        )
    }

    override fun updatePlaylistOrder(platform: MusicPlatform, playlistIds: List<PlaylistId>): Flow<Result<Unit>> =
        flowOf(Result.failure(UnsupportedOperationException("酷狗暂不支持批量重排歌单列表")))

    override fun setPlaylistTop(playlistId: PlaylistId, top: Boolean): Flow<Result<Unit>> = flow {
        if (!isEnabled()) {
            emit(Result.failure(IllegalStateException("酷狗音乐平台已关闭")))
            return@flow
        }
        val target = parseKugouPlaylistTarget(playlistId.rawId)
        val allPlaylists = fetchAllUserPlaylists().getOrNull().orEmpty()
        val meta = when (target) {
            is KugouPlaylistTarget.CloudList -> allPlaylists.firstOrNull { it.listId == target.listId }
            is KugouPlaylistTarget.BareNumeric -> allPlaylists.firstOrNull { it.listId == target.numericId }
            is KugouPlaylistTarget.PublicCollection -> allPlaylists.firstOrNull {
                it.globalCollectionId.equals(target.globalCollectionId, ignoreCase = true) ||
                    it.createGid.equals(target.globalCollectionId, ignoreCase = true)
            }
            is KugouPlaylistTarget.PublicSpecial,
            is KugouPlaylistTarget.Unknown -> null
        }
        val listId = when (target) {
            is KugouPlaylistTarget.CloudList -> target.listId
            is KugouPlaylistTarget.BareNumeric -> target.numericId
            else -> meta?.listId
        }
        if (listId == null || listId <= 0L) {
            emit(Result.failure(IllegalArgumentException("无效酷狗云歌单 listid: ${playlistId.rawId}")))
            return@flow
        }
        emit(
            playlistService.updatePlaylist(
                listId = listId,
                totalVer = meta?.totalVer ?: 0L,
                type = meta?.type ?: 0,
                name = meta?.name,
                intro = meta?.intro.orEmpty(),
                tags = meta?.tags.orEmpty(),
                sort = if (top) 1 else 0
            )
        )
    }

    private suspend fun fetchAllUserPlaylists(maxCount: Int = Int.MAX_VALUE): Result<List<KugouUserPlaylistDto>> {
        val pageSize = minOf(maxCount.coerceAtLeast(1), USER_PLAYLIST_PAGE_SIZE)
        val collected = ArrayList<KugouUserPlaylistDto>()
        var page = 1
        while (page <= MAX_USER_PLAYLIST_PAGES && collected.size < maxCount) {
            val res = playlistService.getUserPlaylists(page = page, pageSize = pageSize)
            val pageData = res.getOrElse { err ->
                return if (collected.isEmpty()) Result.failure(err) else Result.success(collected)
            }
            collected += pageData.playlists
            if (!pageData.hasMore || pageData.playlists.isEmpty()) break
            page++
        }
        return Result.success(collected)
    }

    private companion object {
        private const val USER_PLAYLIST_PAGE_SIZE = 100
        private const val MAX_USER_PLAYLIST_PAGES = 20
    }
}

class PlatformLibraryRouter(
    private val netease: PlatformLibraryRepository,
    private val kugou: PlatformLibraryRepository,
    private val isKugouEnabled: suspend () -> Boolean = { true }
) : PlatformLibraryRepository {

    override fun getUserPlaylists(accountId: AccountId, limit: Int): Flow<Result<List<UnifiedUserPlaylist>>> =
        route(accountId.platform) { it.getUserPlaylists(accountId, limit) }

    override fun getFavoriteArtists(platform: MusicPlatform): Flow<Result<List<UnifiedArtist>>> =
        route(platform) { it.getFavoriteArtists(platform) }

    override fun getCollectedAlbums(platform: MusicPlatform, limit: Int): Flow<Result<List<UnifiedCollectedAlbum>>> =
        route(platform) { it.getCollectedAlbums(platform, limit) }

    override fun getUserRecord(accountId: AccountId, type: Int): Flow<Result<List<UnifiedUserRecordTrack>>> =
        route(accountId.platform) { it.getUserRecord(accountId, type) }

    override fun getUserSubcount(platform: MusicPlatform): Flow<Result<UnifiedUserSubcount>> =
        route(platform) { it.getUserSubcount(platform) }

    override fun updatePlaylistOrder(platform: MusicPlatform, playlistIds: List<PlaylistId>): Flow<Result<Unit>> =
        route(platform) { it.updatePlaylistOrder(platform, playlistIds) }

    override fun setPlaylistTop(playlistId: PlaylistId, top: Boolean): Flow<Result<Unit>> =
        route(playlistId.platform) { it.setPlaylistTop(playlistId, top) }

    private fun <T> route(
        platform: MusicPlatform,
        call: (PlatformLibraryRepository) -> Flow<Result<T>>
    ): Flow<Result<T>> = when (platform) {
        MusicPlatform.NETEASE -> call(netease)
        MusicPlatform.KUGOU -> flow {
            if (!isKugouEnabled()) {
                emit(Result.failure(IllegalStateException("酷狗音乐平台已关闭")))
            } else {
                emit(call(kugou).first())
            }
        }
        else -> flowOf(Result.failure(UnsupportedOperationException("暂不支持该平台的曲库操作: ${platform.key}")))
    }
}

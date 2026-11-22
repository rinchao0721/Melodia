package com.lin0721.linmusic.core.platform.repository

import com.lin0721.linmusic.core.kugou.KugouArtistService
import com.lin0721.linmusic.core.platform.ArtistId
import com.lin0721.linmusic.core.platform.UnifiedAlbum
import com.lin0721.linmusic.core.platform.UnifiedArtist
import com.lin0721.linmusic.core.platform.UnifiedTrack
import com.lin0721.linmusic.core.platform.toUnified
import com.lin0721.linmusic.core.source.MusicPlatform
import com.lin0721.linmusic.feature.artist.data.ArtistRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

data class UnifiedArtistDetail(
    val id: ArtistId,
    val name: String,
    val coverUrl: String = "",
    val avatarUrl: String = "",
    val briefDesc: String = "",
    val albumSize: Int = 0,
    val musicSize: Int = 0,
    val fansCount: Long = 0L,
    val isFollowed: Boolean = false,
    val alias: List<String> = emptyList()
)

data class UnifiedArtistSongsPage(
    val songs: List<UnifiedTrack>,
    val hasMore: Boolean
)

data class UnifiedArtistAlbumsPage(
    val albums: List<UnifiedAlbum>,
    val hasMore: Boolean
)

interface PlatformArtistRepository {
    fun getArtistDetail(artistId: ArtistId): Flow<Result<UnifiedArtistDetail>>
    fun getArtistTopSongs(artistId: ArtistId): Flow<Result<List<UnifiedTrack>>>
    fun getArtistAllSongs(
        artistId: ArtistId,
        offset: Int = 0,
        limit: Int = 30,
        order: String = "hot"
    ): Flow<Result<UnifiedArtistSongsPage>>
    fun getArtistAlbums(
        artistId: ArtistId,
        offset: Int = 0,
        limit: Int = 30,
        order: String = "hot"
    ): Flow<Result<UnifiedArtistAlbumsPage>>
    fun subscribeArtist(artistId: ArtistId, subscribe: Boolean): Flow<Result<Unit>>
    fun checkArtistFollowed(artistId: ArtistId): Flow<Result<Boolean>>
    fun getArtistFansCount(artistId: ArtistId): Flow<Result<Long>>
    fun getSimilarArtists(artistId: ArtistId): Flow<Result<List<UnifiedArtist>>>
}

class NeteasePlatformArtistRepository(
    private val artistRepository: ArtistRepository
) : PlatformArtistRepository {

    override fun getArtistDetail(artistId: ArtistId): Flow<Result<UnifiedArtistDetail>> {
        val id = artistId.neteaseIdOrNull()
            ?: return flowOf(Result.failure(IllegalArgumentException("无效网易云歌手 ID: ${artistId.canonical}")))
        return artistRepository.getArtistDetail(id).map { result ->
            result.map { info ->
                UnifiedArtistDetail(
                    id = ArtistId.netease(info.id.takeIf { it > 0L } ?: id),
                    name = info.name,
                    coverUrl = info.cover,
                    avatarUrl = info.avatar,
                    briefDesc = info.briefDesc,
                    albumSize = info.albumSize,
                    musicSize = info.musicSize,
                    alias = info.alias.orEmpty()
                )
            }
        }
    }

    override fun getArtistTopSongs(artistId: ArtistId): Flow<Result<List<UnifiedTrack>>> {
        val id = artistId.neteaseIdOrNull()
            ?: return flowOf(Result.failure(IllegalArgumentException("无效网易云歌手 ID: ${artistId.canonical}")))
        return artistRepository.getArtistTopSongs(id).map { result ->
            result.map { tracks -> tracks.map { it.toUnified() } }
        }
    }

    override fun getArtistAllSongs(
        artistId: ArtistId,
        offset: Int,
        limit: Int,
        order: String
    ): Flow<Result<UnifiedArtistSongsPage>> {
        val id = artistId.neteaseIdOrNull()
            ?: return flowOf(Result.failure(IllegalArgumentException("无效网易云歌手 ID: ${artistId.canonical}")))
        return artistRepository.getArtistAllSongs(id, offset, limit, order).map { result ->
            result.map { page ->
                UnifiedArtistSongsPage(
                    songs = page.songs.map { it.toUnified() },
                    hasMore = page.hasMore
                )
            }
        }
    }

    override fun getArtistAlbums(
        artistId: ArtistId,
        offset: Int,
        limit: Int,
        order: String
    ): Flow<Result<UnifiedArtistAlbumsPage>> {
        val id = artistId.neteaseIdOrNull()
            ?: return flowOf(Result.failure(IllegalArgumentException("无效网易云歌手 ID: ${artistId.canonical}")))
        return artistRepository.getArtistAlbums(id, limit = limit, offset = offset).map { result ->
            result.map { page ->
                UnifiedArtistAlbumsPage(
                    albums = page.albums.map { album ->
                        UnifiedAlbum(
                            id = if (album.id > 0L) com.lin0721.linmusic.core.platform.AlbumId.netease(album.id) else null,
                            name = album.name,
                            coverUrl = album.picUrl,
                            publishTimeMs = album.publishTime
                        )
                    },
                    hasMore = page.hasMore
                )
            }
        }
    }

    override fun subscribeArtist(artistId: ArtistId, subscribe: Boolean): Flow<Result<Unit>> {
        val id = artistId.neteaseIdOrNull()
            ?: return flowOf(Result.failure(IllegalArgumentException("无效网易云歌手 ID: ${artistId.canonical}")))
        return artistRepository.subscribeArtist(id, subscribe)
    }

    override fun checkArtistFollowed(artistId: ArtistId): Flow<Result<Boolean>> {
        val id = artistId.neteaseIdOrNull()
            ?: return flowOf(Result.failure(IllegalArgumentException("无效网易云歌手 ID: ${artistId.canonical}")))
        return artistRepository.checkArtistFollowed(id)
    }

    override fun getArtistFansCount(artistId: ArtistId): Flow<Result<Long>> {
        val id = artistId.neteaseIdOrNull()
            ?: return flowOf(Result.failure(IllegalArgumentException("无效网易云歌手 ID: ${artistId.canonical}")))
        return artistRepository.getArtistFansCount(id)
    }

    override fun getSimilarArtists(artistId: ArtistId): Flow<Result<List<UnifiedArtist>>> {
        val id = artistId.neteaseIdOrNull()
            ?: return flowOf(Result.failure(IllegalArgumentException("无效网易云歌手 ID: ${artistId.canonical}")))
        return artistRepository.getSimilarArtists(id).map { result ->
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
    }
}

class KugouPlatformArtistRepository(
    private val artistService: KugouArtistService,
    private val isEnabled: suspend () -> Boolean = { true }
) : PlatformArtistRepository {

    override fun getArtistDetail(artistId: ArtistId): Flow<Result<UnifiedArtistDetail>> = flow {
        if (!isEnabled()) {
            emit(Result.failure(IllegalStateException("酷狗音乐平台已关闭")))
            return@flow
        }
        val res = artistService.getArtistDetail(artistId.rawId).map { dto ->
            UnifiedArtistDetail(
                id = ArtistId.kugou(dto.authorId),
                name = dto.name,
                coverUrl = dto.coverUrl,
                avatarUrl = dto.avatarUrl,
                briefDesc = dto.intro,
                albumSize = dto.albumCount,
                musicSize = dto.songCount,
                fansCount = dto.fansCount,
                isFollowed = dto.isFollowed
            )
        }
        emit(res)
    }

    override fun getArtistTopSongs(artistId: ArtistId): Flow<Result<List<UnifiedTrack>>> =
        getArtistAllSongs(artistId, offset = 0, limit = 50, order = "hot")
            .map { result -> result.map { it.songs } }

    override fun getArtistAllSongs(
        artistId: ArtistId,
        offset: Int,
        limit: Int,
        order: String
    ): Flow<Result<UnifiedArtistSongsPage>> = flow {
        if (!isEnabled()) {
            emit(Result.failure(IllegalStateException("酷狗音乐平台已关闭")))
            return@flow
        }
        val pageSize = limit.coerceIn(1, 50)
        val page = (offset.coerceAtLeast(0) / pageSize) + 1
        val res = artistService.getArtistAudios(
            authorId = artistId.rawId,
            page = page,
            pageSize = pageSize,
            sort = order
        ).map { p ->
            UnifiedArtistSongsPage(
                songs = p.songs.map { it.toUnifiedTrack() },
                hasMore = p.hasMore
            )
        }
        emit(res)
    }

    override fun getArtistAlbums(
        artistId: ArtistId,
        offset: Int,
        limit: Int,
        order: String
    ): Flow<Result<UnifiedArtistAlbumsPage>> = flow {
        if (!isEnabled()) {
            emit(Result.failure(IllegalStateException("酷狗音乐平台已关闭")))
            return@flow
        }
        val pageSize = limit.coerceIn(1, 50)
        val page = (offset.coerceAtLeast(0) / pageSize) + 1
        val res = artistService.getArtistAlbums(
            authorId = artistId.rawId,
            page = page,
            pageSize = pageSize,
            sort = order
        ).map { p ->
            UnifiedArtistAlbumsPage(
                albums = p.albums.map { it.toUnifiedAlbum() },
                hasMore = p.hasMore
            )
        }
        emit(res)
    }

    override fun subscribeArtist(artistId: ArtistId, subscribe: Boolean): Flow<Result<Unit>> = flow {
        if (!isEnabled()) {
            emit(Result.failure(IllegalStateException("酷狗音乐平台已关闭")))
            return@flow
        }
        val res = if (subscribe) {
            artistService.followArtist(artistId.rawId)
        } else {
            artistService.unfollowArtist(artistId.rawId)
        }
        emit(res)
    }

    override fun checkArtistFollowed(artistId: ArtistId): Flow<Result<Boolean>> = flow {
        if (!isEnabled()) {
            emit(Result.failure(IllegalStateException("酷狗音乐平台已关闭")))
            return@flow
        }
        val followedListRes = artistService.getUserFollowedArtists()
        val matched = followedListRes.getOrNull()?.any {
            it.authorId.equals(artistId.rawId, ignoreCase = true) ||
                it.singerId.equals(artistId.rawId, ignoreCase = true)
        }
        if (matched != null) {
            emit(Result.success(matched))
        } else {
            emit(artistService.getArtistDetail(artistId.rawId).map { it.isFollowed })
        }
    }

    override fun getArtistFansCount(artistId: ArtistId): Flow<Result<Long>> =
        getArtistDetail(artistId).map { result -> result.map { it.fansCount } }

    override fun getSimilarArtists(artistId: ArtistId): Flow<Result<List<UnifiedArtist>>> =
        flowOf(Result.success(emptyList()))
}

class PlatformArtistRouter(
    private val netease: PlatformArtistRepository,
    private val kugou: PlatformArtistRepository,
    private val isKugouEnabled: suspend () -> Boolean = { true }
) : PlatformArtistRepository {

    override fun getArtistDetail(artistId: ArtistId): Flow<Result<UnifiedArtistDetail>> =
        route(artistId) { it.getArtistDetail(artistId) }

    override fun getArtistTopSongs(artistId: ArtistId): Flow<Result<List<UnifiedTrack>>> =
        route(artistId) { it.getArtistTopSongs(artistId) }

    override fun getArtistAllSongs(
        artistId: ArtistId,
        offset: Int,
        limit: Int,
        order: String
    ): Flow<Result<UnifiedArtistSongsPage>> =
        route(artistId) { it.getArtistAllSongs(artistId, offset, limit, order) }

    override fun getArtistAlbums(
        artistId: ArtistId,
        offset: Int,
        limit: Int,
        order: String
    ): Flow<Result<UnifiedArtistAlbumsPage>> =
        route(artistId) { it.getArtistAlbums(artistId, offset, limit, order) }

    override fun subscribeArtist(artistId: ArtistId, subscribe: Boolean): Flow<Result<Unit>> =
        route(artistId) { it.subscribeArtist(artistId, subscribe) }

    override fun checkArtistFollowed(artistId: ArtistId): Flow<Result<Boolean>> =
        route(artistId) { it.checkArtistFollowed(artistId) }

    override fun getArtistFansCount(artistId: ArtistId): Flow<Result<Long>> =
        route(artistId) { it.getArtistFansCount(artistId) }

    override fun getSimilarArtists(artistId: ArtistId): Flow<Result<List<UnifiedArtist>>> =
        route(artistId) { it.getSimilarArtists(artistId) }

    private fun <T> route(
        artistId: ArtistId,
        call: (PlatformArtistRepository) -> Flow<Result<T>>
    ): Flow<Result<T>> = when (artistId.platform) {
        MusicPlatform.NETEASE -> call(netease)
        MusicPlatform.KUGOU -> flow {
            if (!isKugouEnabled()) {
                emit(Result.failure(IllegalStateException("酷狗音乐平台已关闭")))
            } else {
                emit(call(kugou).first())
            }
        }
        else -> flowOf(Result.failure(UnsupportedOperationException("暂不支持该平台歌手详情: ${artistId.platform.key}")))
    }
}

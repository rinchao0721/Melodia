package com.lin0721.linmusic.core.platform.repository

import com.lin0721.linmusic.core.kugou.KugouAlbumService
import com.lin0721.linmusic.core.platform.AlbumId
import com.lin0721.linmusic.core.platform.ArtistId
import com.lin0721.linmusic.core.platform.UnifiedArtist
import com.lin0721.linmusic.core.platform.UnifiedTrack
import com.lin0721.linmusic.core.platform.toUnified
import com.lin0721.linmusic.core.source.MusicPlatform
import com.lin0721.linmusic.feature.playlist.data.PlaylistRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

data class UnifiedAlbumDetail(
    val id: AlbumId,
    val name: String,
    val coverUrl: String = "",
    val description: String = "",
    val publishTimeMs: Long = 0L,
    val artists: List<UnifiedArtist> = emptyList(),
    val tracks: List<UnifiedTrack> = emptyList(),
    val trackCount: Int = tracks.size,
    val isSubscribed: Boolean = false,
    val language: String = ""
)

interface PlatformAlbumRepository {
    fun getAlbumDetail(albumId: AlbumId): Flow<Result<UnifiedAlbumDetail>>
    fun getAlbumSongs(albumId: AlbumId, offset: Int = 0, limit: Int = 30): Flow<Result<List<UnifiedTrack>>>
    fun subscribeAlbum(albumId: AlbumId, subscribe: Boolean): Flow<Result<Unit>>
}

class NeteasePlatformAlbumRepository(
    private val playlistRepository: PlaylistRepository
) : PlatformAlbumRepository {

    override fun getAlbumDetail(albumId: AlbumId): Flow<Result<UnifiedAlbumDetail>> {
        val id = albumId.neteaseIdOrNull()
            ?: return flowOf(Result.failure(IllegalArgumentException("无效网易云专辑 ID: ${albumId.canonical}")))
        return playlistRepository.getAlbumDetail(id).map { result ->
            result.map { detail ->
                val tracks = detail.tracks.map { it.toUnified() }
                UnifiedAlbumDetail(
                    id = AlbumId.netease(detail.id.takeIf { it > 0L } ?: id),
                    name = detail.name,
                    coverUrl = detail.coverImgUrl,
                    description = detail.description.orEmpty(),
                    artists = detail.artists.map { it.toUnified(MusicPlatform.NETEASE) },
                    tracks = tracks,
                    trackCount = detail.trackCount.takeIf { it > 0 } ?: tracks.size,
                    isSubscribed = detail.subscribed
                )
            }
        }
    }

    override fun getAlbumSongs(albumId: AlbumId, offset: Int, limit: Int): Flow<Result<List<UnifiedTrack>>> =
        getAlbumDetail(albumId).map { result ->
            result.map { detail ->
                val start = offset.coerceAtLeast(0)
                if (start >= detail.tracks.size) emptyList()
                else detail.tracks.subList(start, minOf(start + limit.coerceAtLeast(1), detail.tracks.size))
            }
        }

    override fun subscribeAlbum(albumId: AlbumId, subscribe: Boolean): Flow<Result<Unit>> {
        val id = albumId.neteaseIdOrNull()
            ?: return flowOf(Result.failure(IllegalArgumentException("无效网易云专辑 ID: ${albumId.canonical}")))
        return playlistRepository.subscribeAlbum(id, subscribe)
    }
}

class KugouPlatformAlbumRepository(
    private val albumService: KugouAlbumService,
    private val isEnabled: suspend () -> Boolean = { true }
) : PlatformAlbumRepository {

    override fun getAlbumDetail(albumId: AlbumId): Flow<Result<UnifiedAlbumDetail>> = flow {
        if (!isEnabled()) {
            emit(Result.failure(IllegalStateException("酷狗音乐平台已关闭")))
            return@flow
        }
        val detail = albumService.getAlbumDetail(albumId.rawId).getOrElse {
            emit(Result.failure(it))
            return@flow
        }
        val firstPage = albumService.getAlbumSongs(albumId.rawId, page = 1, pageSize = KUGOU_ALBUM_PAGE_SIZE).getOrElse {
            emit(Result.failure(it))
            return@flow
        }
        val songs = firstPage.songs.toMutableList()
        var page = 1
        while (songs.size < firstPage.totalCount && page < KUGOU_ALBUM_MAX_PAGES) {
            page++
            val next = albumService.getAlbumSongs(albumId.rawId, page = page, pageSize = KUGOU_ALBUM_PAGE_SIZE)
                .getOrNull()?.songs.orEmpty()
            if (next.isEmpty()) break
            songs += next
        }
        val songsPage = firstPage
        val tracks = songs.map { it.toUnifiedTrack() }
        val unifiedArtists = if (detail.authors.isNotEmpty()) {
            detail.authors.map { it.toUnifiedArtist() }
        } else {
            detail.authorName.split("、", "/", ",")
                .map { it.trim() }
                .filter { it.isNotEmpty() }
                .map { UnifiedArtist(id = null, name = it) }
        }
        emit(
            Result.success(
                UnifiedAlbumDetail(
                    id = AlbumId.kugou(detail.albumId),
                    name = detail.name,
                    coverUrl = detail.coverUrl,
                    description = detail.intro,
                    publishTimeMs = detail.publishTimeMs,
                    artists = unifiedArtists,
                    tracks = tracks,
                    trackCount = detail.songCount.takeIf { it > 0 }
                        ?: songsPage.totalCount.takeIf { it > 0 }
                        ?: tracks.size,
                    language = detail.language
                )
            )
        )
    }

    override fun getAlbumSongs(albumId: AlbumId, offset: Int, limit: Int): Flow<Result<List<UnifiedTrack>>> = flow {
        if (!isEnabled()) {
            emit(Result.failure(IllegalStateException("酷狗音乐平台已关闭")))
            return@flow
        }
        val pageSize = limit.coerceIn(1, KUGOU_ALBUM_PAGE_SIZE)
        val page = (offset.coerceAtLeast(0) / pageSize) + 1
        emit(albumService.getAlbumSongs(albumId.rawId, page = page, pageSize = pageSize).map { p ->
            p.songs.map { it.toUnifiedTrack() }
        })
    }

    override fun subscribeAlbum(albumId: AlbumId, subscribe: Boolean): Flow<Result<Unit>> =
        flowOf(Result.failure(UnsupportedOperationException("酷狗暂不支持专辑收藏操作")))
}

class PlatformAlbumRouter(
    private val netease: PlatformAlbumRepository,
    private val kugou: PlatformAlbumRepository,
    private val isKugouEnabled: suspend () -> Boolean = { true }
) : PlatformAlbumRepository {

    override fun getAlbumDetail(albumId: AlbumId): Flow<Result<UnifiedAlbumDetail>> =
        route(albumId) { it.getAlbumDetail(albumId) }

    override fun getAlbumSongs(albumId: AlbumId, offset: Int, limit: Int): Flow<Result<List<UnifiedTrack>>> =
        route(albumId) { it.getAlbumSongs(albumId, offset, limit) }

    override fun subscribeAlbum(albumId: AlbumId, subscribe: Boolean): Flow<Result<Unit>> =
        route(albumId) { it.subscribeAlbum(albumId, subscribe) }

    private fun <T> route(
        albumId: AlbumId,
        call: (PlatformAlbumRepository) -> Flow<Result<T>>
    ): Flow<Result<T>> = when (albumId.platform) {
        MusicPlatform.NETEASE -> call(netease)
        MusicPlatform.KUGOU -> flow {
            if (!isKugouEnabled()) {
                emit(Result.failure(IllegalStateException("酷狗音乐平台已关闭")))
            } else {
                emit(call(kugou).first())
            }
        }
        else -> flowOf(Result.failure(UnsupportedOperationException("暂不支持该平台专辑详情: ${albumId.platform.key}")))
    }
}

// album_audio/lite 的 pagesize 超过 50 会返回 20010 invalid param（实测）
private const val KUGOU_ALBUM_PAGE_SIZE = 50
private const val KUGOU_ALBUM_MAX_PAGES = 20

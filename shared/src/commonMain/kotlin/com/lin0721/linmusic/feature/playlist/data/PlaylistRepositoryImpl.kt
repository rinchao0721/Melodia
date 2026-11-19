package com.lin0721.linmusic.feature.playlist.data

import com.lin0721.linmusic.core.cache.OfflineFallback
import com.lin0721.linmusic.core.contentfilter.ContentFilter
import com.lin0721.linmusic.core.model.PlaylistDetail
import com.lin0721.linmusic.core.model.Track
import com.lin0721.linmusic.core.model.mergePrivileges
import com.lin0721.linmusic.core.network.apiFlow
import com.lin0721.linmusic.feature.player.data.PlayerRepository
import com.lin0721.linmusic.feature.playlist.domain.pendingTrackIds
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map

// 对齐服务端 song/detail 单次上限
private const val TRACK_CHUNK_SIZE = 1000

class PlaylistRepositoryImpl(
    private val apiService: PlaylistApi,
    private val contentFilter: ContentFilter,
    private val playerRepository: PlayerRepository,
    private val offline: OfflineFallback
) : PlaylistRepository {

    override fun getPlaylistDetail(id: Long): Flow<Result<PlaylistDetail>> =
        offline.cached("playlist_detail_$id") {
            apiFlow(
                request = { apiService.getPlaylistDetail(PlaylistDetailRequest(id = id)) },
                isSuccess = { it.isSuccess && it.playlist != null },
                code = { it.code },
                transform = { response ->
                    val playlist = response.playlist!!
                    val mergedTracks = playlist.tracks.mergePrivileges(response.privileges)
                    val filteredTracks = contentFilter.filterBlockedArtists(mergedTracks) { it.ar.map { a -> a.id } }
                    // 被屏蔽歌手过滤掉的曲目同步从 trackIds 剔除，避免后续分页按数量对齐时错位、总数也与可见曲目一致
                    val keptIds = filteredTracks.mapTo(HashSet()) { it.id }
                    val blockedIds = mergedTracks.mapNotNullTo(HashSet()) { t -> t.id.takeIf { it !in keptIds } }
                    playlist.copy(
                        tracks = filteredTracks,
                        trackIds = if (blockedIds.isEmpty()) playlist.trackIds else playlist.trackIds.filter { it.id !in blockedIds }
                    )
                }
            )
        }

    override fun loadMoreTracks(trackIds: List<Long>): Flow<Result<List<Track>>> =
        playerRepository.getSongDetails(trackIds).map { result ->
            result.map { tracks -> contentFilter.filterBlockedArtists(tracks) { it.ar.map { a -> a.id } } }
        }

    override fun loadAllTracks(detail: PlaylistDetail): Flow<Result<List<Track>>> =
        offline.cached("playlist_tracks_${detail.id}") { remoteLoadAllTracks(detail) }

    private fun remoteLoadAllTracks(detail: PlaylistDetail): Flow<Result<List<Track>>> = flow {
        // 按 id 而非数量对齐：tracks 经屏蔽歌手过滤后可能少于服务端实际下发的数量
        val missingIds = pendingTrackIds(detail)
        val all = detail.tracks.toMutableList()
        for (chunk in missingIds.chunked(TRACK_CHUNK_SIZE)) {
            val result = loadMoreTracks(chunk).first()
            val tracks = result.getOrElse { e ->
                emit(Result.failure(e))
                return@flow
            }
            all.addAll(tracks)
        }
        emit(Result.success(all))
    }

    override fun getAlbumDetail(id: Long): Flow<Result<PlaylistDetail>> =
        offline.cached("album_detail_$id") {
            apiFlow(
                // 专辑 ID 需作为 URL 路径参数传入，不使用 AlbumDetailRequest 请求体
                request = { apiService.getAlbumDetail(id = id) },
                isSuccess = { it.isSuccess },
                code = { it.code },
                transform = { response ->
                    val album = response.album
                    val filteredTracks = contentFilter.filterBlockedArtists(response.songs) { it.ar.map { a -> a.id } }
                    PlaylistDetail(
                        id = album.id,
                        name = album.name,
                        coverImgUrl = album.picUrl,
                        description = album.description,
                        playCount = 0L,
                        tracks = filteredTracks,
                        artists = album.artists
                    )
                }
            )
        }

    override fun subscribePlaylist(playlistId: Long, subscribe: Boolean): Flow<Result<Unit>> = apiFlow(
        request = {
            apiService.subscribePlaylist(
                op = if (subscribe) "subscribe" else "unsubscribe",
                body = PlaylistSubscribeRequest(id = playlistId)
            )
        },
        isSuccess = { it.isSuccess },
        code = { it.code },
        transform = { Unit }
    )

    override fun subscribeAlbum(albumId: Long, subscribe: Boolean): Flow<Result<Unit>> = apiFlow(
        request = {
            apiService.subscribeAlbum(
                op = if (subscribe) "sub" else "unsub",
                body = AlbumSubscribeRequest(id = albumId)
            )
        },
        isSuccess = { it.isSuccess },
        code = { it.code },
        msg = { it.message },
        transform = { Unit }
    )

    override fun manipulatePlaylistTracks(op: String, playlistId: Long, trackIds: List<Long>): Flow<Result<Unit>> = apiFlow(
        request = {
            apiService.manipulatePlaylistTracks(
                PlaylistTracksManipulateRequest(
                    op = op,
                    pid = playlistId,
                    trackIds = trackIds.joinToString(prefix = "[", postfix = "]") { "\"$it\"" }
                )
            )
        },
        isSuccess = { it.isSuccess },
        code = { it.code },
        msg = { it.message },
        transform = { Unit }
    )

    override fun renamePlaylist(playlistId: Long, name: String): Flow<Result<Unit>> = apiFlow(
        request = {
            apiService.updatePlaylistName(
                PlaylistUpdateNameRequest(id = playlistId, name = name)
            )
        },
        isSuccess = { it.isSuccess },
        code = { it.code },
        msg = { it.message },
        transform = { Unit }
    )

    override fun updateDescription(playlistId: Long, desc: String): Flow<Result<Unit>> = apiFlow(
        request = {
            apiService.updatePlaylistDesc(
                PlaylistUpdateDescRequest(id = playlistId, desc = desc)
            )
        },
        isSuccess = { it.isSuccess },
        code = { it.code },
        msg = { it.message },
        transform = { Unit }
    )

    override fun deletePlaylist(playlistId: Long): Flow<Result<Unit>> = apiFlow(
        request = {
            apiService.deletePlaylist(
                PlaylistDeleteRequest(ids = "[$playlistId]")
            )
        },
        isSuccess = { it.isSuccess },
        code = { it.code },
        msg = { it.message },
        transform = { Unit }
    )

    override fun updatePlaylistCover(playlistId: Long, coverImgId: Long): Flow<Result<String>> = apiFlow(
        request = {
            apiService.updatePlaylistCover(
                PlaylistUpdateCoverRequest(id = playlistId, coverImgId = coverImgId)
            )
        },
        isSuccess = { it.isSuccess && !it.url.isNullOrBlank() },
        code = { it.code },
        msg = { it.message },
        transform = { it.url.orEmpty() }
    )

}

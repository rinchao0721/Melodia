package com.lin0721.linmusic.feature.playlist.domain

import com.lin0721.linmusic.core.download.BatchEnqueueResult
import com.lin0721.linmusic.core.download.DownloadTrackInfo
import com.lin0721.linmusic.core.download.SongDownloader
import com.lin0721.linmusic.core.model.PlaylistDetail
import com.lin0721.linmusic.core.model.Track
import com.lin0721.linmusic.core.songlike.SongLikeRepository
import com.lin0721.linmusic.core.userplaylist.UserPlaylist
import com.lin0721.linmusic.core.userplaylist.UserPlaylistRepository
import com.lin0721.linmusic.feature.create.data.CreateRepository
import com.lin0721.linmusic.feature.playlist.data.PlaylistRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flowOf
import java.util.UUID

// 只实现批量操作用到的 manipulatePlaylistTracks，其余接口测试里不应被调用
class FakePlaylistRepository : PlaylistRepository {

    data class ManipulateCall(val op: String, val playlistId: Long, val trackIds: List<Long>)

    val manipulateCalls = mutableListOf<ManipulateCall>()
    var manipulateResult: Result<Unit> = Result.success(Unit)

    override fun manipulatePlaylistTracks(op: String, playlistId: Long, trackIds: List<Long>): Flow<Result<Unit>> {
        manipulateCalls += ManipulateCall(op, playlistId, trackIds)
        return flowOf(manipulateResult)
    }

    override fun getPlaylistDetail(id: Long): Flow<Result<PlaylistDetail>> = unsupported()
    override fun loadMoreTracks(trackIds: List<Long>): Flow<Result<List<Track>>> = unsupported()
    override fun loadAllTracks(detail: PlaylistDetail): Flow<Result<List<Track>>> = unsupported()
    override fun getAlbumDetail(id: Long): Flow<Result<PlaylistDetail>> = unsupported()
    override fun subscribePlaylist(playlistId: Long, subscribe: Boolean): Flow<Result<Unit>> = unsupported()
    override fun subscribeAlbum(albumId: Long, subscribe: Boolean): Flow<Result<Unit>> = unsupported()
    override fun renamePlaylist(playlistId: Long, name: String): Flow<Result<Unit>> = unsupported()
    override fun updateDescription(playlistId: Long, desc: String): Flow<Result<Unit>> = unsupported()
    override fun deletePlaylist(playlistId: Long): Flow<Result<Unit>> = unsupported()
    override fun updatePlaylistCover(playlistId: Long, coverImgId: Long): Flow<Result<String>> = unsupported()

    private fun <T> unsupported(): Flow<T> = throw UnsupportedOperationException("测试未预期调用")
}

class FakeSongDownloader : SongDownloader {

    data class BatchCall(val tracks: List<DownloadTrackInfo>, val level: String, val batchTag: String, val batchLabel: String)

    val batchCalls = mutableListOf<BatchCall>()

    override fun enqueueSingle(track: DownloadTrackInfo, level: String): UUID = UUID.randomUUID()

    override suspend fun enqueueBatch(
        tracks: List<DownloadTrackInfo>,
        level: String,
        batchTag: String,
        batchLabel: String
    ): BatchEnqueueResult {
        batchCalls += BatchCall(tracks, level, batchTag, batchLabel)
        return BatchEnqueueResult(enqueuedCount = tracks.size, skippedCount = 0)
    }
}

// 可指定哪些歌曲喜欢失败；成功的会并入 likedSongIds
class FakeSongLikeRepository(initialLiked: Set<Long> = emptySet()) : SongLikeRepository {

    private val liked = MutableStateFlow(initialLiked)
    private val likedTracks = MutableStateFlow<Set<com.lin0721.linmusic.core.platform.TrackId>>(
        initialLiked.filter { it >= 0L }.mapTo(LinkedHashSet()) {
            com.lin0721.linmusic.core.platform.TrackId.netease(it)
        }
    )
    val failingIds = mutableSetOf<Long>()
    val likeCalls = mutableListOf<Long>()

    override val likedSongIds: StateFlow<Set<Long>> = liked
    override val likedTrackIds: StateFlow<Set<com.lin0721.linmusic.core.platform.TrackId>> = likedTracks

    override fun getLikedSongIds(uid: Long): Flow<Result<List<Long>>> = flowOf(Result.success(liked.value.toList()))

    override fun likeSong(songId: Long, like: Boolean): Flow<Result<Unit>> {
        likeCalls += songId
        if (songId in failingIds) return flowOf(Result.failure(IllegalStateException("喜欢失败")))
        liked.value = if (like) liked.value + songId else liked.value - songId
        if (songId >= 0L) {
            val tid = com.lin0721.linmusic.core.platform.TrackId.netease(songId)
            likedTracks.value = if (like) likedTracks.value + tid else likedTracks.value - tid
        }
        return flowOf(Result.success(Unit))
    }

    override fun syncLikedSongIds(ids: Set<Long>) {
        liked.value = ids
        likedTracks.value = ids.filter { it >= 0L }.mapTo(LinkedHashSet()) {
            com.lin0721.linmusic.core.platform.TrackId.netease(it)
        }
    }
}

class FakeUserPlaylistRepository(private val playlists: List<UserPlaylist> = emptyList()) : UserPlaylistRepository {

    override fun getUserPlaylists(uid: Long, limit: Int): Flow<Result<List<UserPlaylist>>> =
        flowOf(Result.success(playlists))

    override fun updatePlaylistOrder(playlistIds: List<Long>): Flow<Result<Unit>> = flowOf(Result.success(Unit))

    override fun setPlaylistTop(playlistId: Long, top: Boolean): Flow<Result<Unit>> = flowOf(Result.success(Unit))
}

class FakeCreateRepository : CreateRepository {

    data class CreateCall(val name: String, val privacy: Int)

    val createCalls = mutableListOf<CreateCall>()

    override fun createPlaylist(name: String, privacy: Int): Flow<Result<PlaylistDetail>> {
        createCalls += CreateCall(name, privacy)
        return flowOf(Result.success(PlaylistDetail(id = NEW_PLAYLIST_ID, name = name)))
    }

    companion object {
        const val NEW_PLAYLIST_ID = 900L
    }
}

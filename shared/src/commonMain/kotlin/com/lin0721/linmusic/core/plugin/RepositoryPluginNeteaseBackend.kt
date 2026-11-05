package com.lin0721.linmusic.core.plugin

import com.lin0721.linmusic.core.auth.UserPreferences
import com.lin0721.linmusic.core.model.ArtistDetailInfo
import com.lin0721.linmusic.core.model.PlaylistDetail
import com.lin0721.linmusic.core.model.Track
import com.lin0721.linmusic.core.player.data.PlaybackRepository
import com.lin0721.linmusic.core.player.domain.LyricLine
import com.lin0721.linmusic.core.playlistmutation.PlaylistMutationBus
import com.lin0721.linmusic.core.playlistmutation.PlaylistMutationEvent
import com.lin0721.linmusic.core.songlike.SongLikeRepository
import com.lin0721.linmusic.core.userplaylist.UserPlaylist
import com.lin0721.linmusic.core.userplaylist.UserPlaylistRepository
import com.lin0721.linmusic.feature.artist.data.ArtistRepository
import com.lin0721.linmusic.feature.create.data.CreateRepository
import com.lin0721.linmusic.feature.player.data.PlayerRepository
import com.lin0721.linmusic.feature.playlist.data.PlaylistRepository
import com.lin0721.linmusic.feature.search.data.SearchRepository
import com.lin0721.linmusic.feature.search.domain.SearchPageResult
import com.lin0721.linmusic.feature.search.domain.SearchType
import kotlinx.coroutines.flow.first

private const val OP_ADD = "add"
private const val OP_REMOVE = "del"

// 复用现有 Repository，不新增网易接口；依赖按需取出，未用到插件时不触发初始化
class RepositoryPluginNeteaseBackend(
    private val user: () -> UserPreferences,
    private val player: () -> PlayerRepository,
    private val playback: () -> PlaybackRepository,
    private val search: () -> SearchRepository,
    private val playlists: () -> PlaylistRepository,
    private val artists: () -> ArtistRepository,
    private val userPlaylists: () -> UserPlaylistRepository,
    private val likes: () -> SongLikeRepository,
    private val create: () -> CreateRepository,
    private val mutations: () -> PlaylistMutationBus
) : PluginNeteaseBackend {

    override suspend fun uid(): Long? = user().userProfile.first()?.uid?.takeIf { it > 0 }

    override suspend fun songDetails(ids: List<Long>): Result<List<Track>> = player().getSongDetails(ids).first()

    override suspend fun lyrics(id: Long): Result<List<LyricLine>> = playback().getLyrics(id).first()

    override suspend fun search(keyword: String, type: SearchType, offset: Int, limit: Int): Result<SearchPageResult> =
        search().search(keyword, type, offset, limit).first()

    override suspend fun playlistDetail(id: Long): Result<PlaylistDetail> = playlists().getPlaylistDetail(id).first()

    override suspend fun albumDetail(id: Long): Result<PlaylistDetail> = playlists().getAlbumDetail(id).first()

    override suspend fun artistDetail(id: Long): Result<ArtistDetailInfo> = artists().getArtistDetail(id).first()

    override suspend fun artistTopSongs(id: Long): Result<List<Track>> = artists().getArtistTopSongs(id).first()

    override suspend fun userPlaylists(uid: Long): Result<List<UserPlaylist>> = userPlaylists().getUserPlaylists(uid).first()

    override fun likedSongIds(): Set<Long> = likes().likedSongIds.value

    override suspend fun likeSong(id: Long, like: Boolean): Result<Unit> = likes().likeSong(id, like).first()

    // 歌单写入要通知音乐库等页面，否则界面停留在旧数据
    override suspend fun createPlaylist(name: String, privacy: Int): Result<PlaylistDetail> =
        create().createPlaylist(name, privacy).first().onSuccess { mutations().emit(PlaylistMutationEvent.Created(it.id)) }

    override suspend fun addTracks(playlistId: Long, trackIds: List<Long>): Result<Unit> =
        playlists().manipulatePlaylistTracks(OP_ADD, playlistId, trackIds).first()

    override suspend fun removeTracks(playlistId: Long, trackIds: List<Long>): Result<Unit> =
        playlists().manipulatePlaylistTracks(OP_REMOVE, playlistId, trackIds).first()

    override suspend fun subscribePlaylist(id: Long, subscribe: Boolean): Result<Unit> =
        playlists().subscribePlaylist(id, subscribe).first().onSuccess { mutations().emit(PlaylistMutationEvent.Subscribed(id, subscribe)) }

    override suspend fun subscribeAlbum(id: Long, subscribe: Boolean): Result<Unit> =
        playlists().subscribeAlbum(id, subscribe).first()

    override suspend fun followArtist(id: Long, follow: Boolean): Result<Unit> =
        artists().subscribeArtist(id, follow).first()

    override suspend fun deletePlaylist(id: Long): Result<Unit> =
        playlists().deletePlaylist(id).first().onSuccess { mutations().emit(PlaylistMutationEvent.Deleted(id)) }
}

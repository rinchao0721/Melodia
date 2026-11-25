package com.lin0721.linmusic.feature.recent.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lin0721.linmusic.core.auth.UserPreferences
import com.lin0721.linmusic.core.kugou.runCatchingCancellable
import com.lin0721.linmusic.core.log.AppLogger
import com.lin0721.linmusic.core.network.ResourceProvider
import com.lin0721.linmusic.core.network.toUserMessage
import com.lin0721.linmusic.core.platform.AccountId
import com.lin0721.linmusic.core.platform.AlbumId
import com.lin0721.linmusic.core.platform.PlaylistId
import com.lin0721.linmusic.core.platform.repository.PlatformLibraryRepository
import com.lin0721.linmusic.core.platform.toDeterministicLegacyId
import com.lin0721.linmusic.core.platform.toDisplayTrack
import com.lin0721.linmusic.core.player.LocalRecentPlaylist
import com.lin0721.linmusic.core.player.PlaySource
import com.lin0721.linmusic.core.player.PlaybackController
import com.lin0721.linmusic.core.player.PlaybackPreferences
import com.lin0721.linmusic.core.player.QueueItem
import com.lin0721.linmusic.core.songquality.SongQualityCache
import com.lin0721.linmusic.core.source.MusicPlatform
import com.lin0721.linmusic.feature.recent.data.RecentRepository
import com.lin0721.linmusic.feature.recent.domain.RecentAlbum
import com.lin0721.linmusic.feature.recent.domain.RecentPlaylist
import com.lin0721.linmusic.feature.recent.domain.RecentSong
import com.lin0721.linmusic.feature.recent.domain.formatClockTime
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

private const val TAG = "RecentPlayViewModel"

class RecentPlayViewModel(
    private val recentRepository: RecentRepository,
    val playerManager: PlaybackController,
    private val resourceProvider: ResourceProvider,
    private val songQualityCache: SongQualityCache? = null,
    private val userPreferences: UserPreferences? = null,
    private val playbackPreferences: PlaybackPreferences? = null,
    private val platformLibraryRepository: PlatformLibraryRepository? = null
) : ViewModel() {

    private val _uiState = MutableStateFlow<RecentPlayUiState>(RecentPlayUiState.Loading)
    val uiState: StateFlow<RecentPlayUiState> = _uiState.asStateFlow()

    private val _selectedTab = MutableStateFlow(RecentTab.SONG)
    val selectedTab: StateFlow<RecentTab> = _selectedTab.asStateFlow()

    init {
        load()
    }

    fun selectTab(tab: RecentTab) {
        _selectedTab.value = tab
    }

    fun load() {
        viewModelScope.launch {
            _uiState.value = RecentPlayUiState.Loading

            val platform = currentPlatform()
            if (platform == MusicPlatform.LOCAL) {
                _uiState.value = RecentPlayUiState.Success(
                    songs = emptyList(),
                    playlists = emptyList(),
                    albums = emptyList(),
                    platform = MusicPlatform.LOCAL
                )
                return@launch
            }
            val isKugou = platform == MusicPlatform.KUGOU
            val kugouAccount = if (isKugou) activeKugouAccount() else null

            val songsDeferred = async {
                if (isKugou) fetch("酷狗听歌历史") { kugouSongs(kugouAccount) }
                else fetch("最近播放歌曲") { recentRepository.getRecentSongs().first() }
            }
            val playlistsDeferred = async {
                if (isKugou) fetch("酷狗最近歌单") { kugouRecentPlaylists(kugouAccount) }
                else fetch("最近播放歌单") { recentRepository.getRecentPlaylists().first() }
            }
            val albumsDeferred = async {
                if (isKugou) fetch("酷狗最近专辑") { kugouRecentAlbums(kugouAccount) }
                else fetch("最近播放专辑") { recentRepository.getRecentAlbums().first() }
            }

            val songs = songsDeferred.await()
            val playlists = playlistsDeferred.await()
            val albums = albumsDeferred.await()

            // 三类全军覆没才算失败；只挂一两类时仍把拿到的展示出来，对应 Tab 走空态
            val allFailed = songs.isFailure && playlists.isFailure && albums.isFailure
            if (allFailed) {
                val cause = songs.exceptionOrNull() ?: Exception("加载失败")
                _uiState.value = RecentPlayUiState.Error(cause.toUserMessage(resourceProvider))
                return@launch
            }

            val songList = songs.getOrDefault(emptyList())
            if (!isKugou) songQualityCache?.prefetch(songList.map { it.track })

            _uiState.value = RecentPlayUiState.Success(
                songs = songList,
                playlists = playlists.getOrDefault(emptyList()),
                albums = albums.getOrDefault(emptyList()),
                platform = platform
            )
        }
    }

    private suspend fun currentPlatform(): MusicPlatform =
        runCatchingCancellable { userPreferences?.currentPlatform?.first() }.getOrNull() ?: MusicPlatform.NETEASE

    private suspend fun activeKugouAccount() =
        runCatchingCancellable { userPreferences?.activeAccountOf(MusicPlatform.KUGOU)?.first()?.accountId }.getOrNull()

    // 酷狗听歌历史，未登录按空列表处理
    private suspend fun kugouSongs(account: AccountId?): Result<List<RecentSong>> {
        val repo = platformLibraryRepository
        if (account == null || repo == null) return Result.success(emptyList())
        return repo.getUserRecord(account, 0).first().map { records ->
            records.map { record ->
                RecentSong(
                    track = record.track.toDisplayTrack(),
                    playTime = 0L,
                    playedAtText = "",
                    unified = record.track
                )
            }.distinctBy { it.track.id }
        }
    }

    // 酷狗歌单与专辑只有本机记录，按账号过滤；榜单归入歌单
    private suspend fun kugouLocalRecords(
        account: AccountId?,
        albums: Boolean
    ): List<LocalRecentPlaylist> {
        val prefs = playbackPreferences
        if (account == null || prefs == null) return emptyList()
        val accountKey = account.canonical
        return prefs.recentPlaylists.first()
            .filter { it.platform == MusicPlatform.KUGOU && it.accountKey == accountKey }
            .filter { (it.kind == PlaySource.Kind.ALBUM) == albums }
            .sortedByDescending { it.playTime }
    }

    private suspend fun kugouRecentPlaylists(
        account: AccountId?
    ): Result<List<RecentPlaylist>> = Result.success(
        kugouLocalRecords(account, albums = false).map { record ->
            val playlistId = PlaylistId.of(MusicPlatform.KUGOU, record.effectiveRawId)
            RecentPlaylist(
                id = playlistId.toDeterministicLegacyId(),
                name = record.name,
                coverUrl = record.coverUrl,
                creatorName = "",
                playTime = record.playTime,
                playedAtText = formatClockTime(record.playTime),
                // 榜单沿用首页的约定，直接用原始 id 作为实体 id
                entityId = if (record.kind == PlaySource.Kind.RANK) record.effectiveRawId else playlistId.canonical
            )
        }
    )

    private suspend fun kugouRecentAlbums(
        account: AccountId?
    ): Result<List<RecentAlbum>> = Result.success(
        kugouLocalRecords(account, albums = true).map { record ->
            val albumId = AlbumId.of(MusicPlatform.KUGOU, record.effectiveRawId)
            RecentAlbum(
                id = albumId.toDeterministicLegacyId(),
                name = record.name,
                coverUrl = record.coverUrl,
                artistName = "",
                playTime = record.playTime,
                playedAtText = formatClockTime(record.playTime),
                entityId = albumId.canonical
            )
        }
    )

    // 单类请求的异常兜底，避免任一类抛出后另外两类的结果一起丢失
    private suspend fun <T> fetch(label: String, block: suspend () -> Result<List<T>>): Result<List<T>> =
        runCatching { block() }
            .getOrElse { e ->
                AppLogger.w(TAG, "$label 加载异常", e)
                Result.failure(e)
            }
            .onFailure { AppLogger.w(TAG, "$label 加载失败", it) }

    // 以当前歌曲列表为播放队列，从点击项开始播
    fun playSong(song: RecentSong) {
        val state = _uiState.value as? RecentPlayUiState.Success ?: return
        if (state.songs.isEmpty()) return

        val queueItems = state.songs.map { item ->
            item.unified?.let { QueueItem.fromUnified(it) } ?: QueueItem(
                songId = item.track.id,
                title = item.track.name,
                artist = item.track.ar.joinToString(" / ") { artist -> artist.name },
                coverUrl = item.track.al.picUrl
            )
        }
        val startIndex = state.songs.indexOfFirst { it.track.id == song.track.id }.coerceAtLeast(0)
        playerManager.playQueue(queueItems, startIndex, "最近播放")
    }
}

package com.lin0721.linmusic.feature.playlist.domain

import com.lin0721.linmusic.core.auth.UserPreferences
import com.lin0721.linmusic.core.auth.UserProfile
import com.lin0721.linmusic.core.model.Album
import com.lin0721.linmusic.core.model.Artist
import com.lin0721.linmusic.core.model.Track
import com.lin0721.linmusic.core.playlistexternal.PlaylistExternalTrackStore
import com.lin0721.linmusic.core.playlistmutation.PlaylistMutationBus
import com.lin0721.linmusic.core.userplaylist.UserPlaylist
import com.lin0721.linmusic.feature.podcast.ui.FakePlaybackController
import com.lin0721.linmusic.feature.podcast.ui.InMemoryPreferencesStore
import com.lin0721.linmusic.feature.podcast.ui.testResourceProvider
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TrackBatchActionsDelegateTest {

    private val playlistRepository = FakePlaylistRepository()
    private val player = FakePlaybackController()
    private val downloader = FakeSongDownloader()
    private val likeRepository = FakeSongLikeRepository(initialLiked = setOf(2L))
    private val createRepository = FakeCreateRepository()
    private val userPreferences = UserPreferences(InMemoryPreferencesStore())
    private val userPlaylists = listOf(
        UserPlaylist(id = 10, name = "自建 A", userId = 1),
        UserPlaylist(id = 11, name = "自建 B", userId = 1),
        UserPlaylist(id = 1, name = "我喜欢的音乐", userId = 1),
        UserPlaylist(id = 20, name = "别人的", userId = 2)
    )

    private val delegate = TrackBatchActionsDelegate(
        playlistRepository = playlistRepository,
        playerManager = player,
        songDownloadManager = downloader,
        songLikeRepository = likeRepository,
        createPlaylistAndAddSongUseCase = CreatePlaylistAndAddSongUseCase(
            createRepository, playlistRepository, PlaylistMutationBus()
        ),
        userPlaylistRepository = FakeUserPlaylistRepository(userPlaylists),
        userPreferences = userPreferences,
        resourceProvider = testResourceProvider,
        externalStore = PlaylistExternalTrackStore(InMemoryPreferencesStore())
    )

    private val toasts = mutableListOf<String>()
    private val onToast: suspend (String) -> Unit = { toasts += it }

    private fun track(id: Long) = Track(id = id, name = "歌$id", ar = listOf(Artist(id = 7, name = "歌手")), al = Album(name = "专辑"))

    private val tracks = listOf(track(1), track(2), track(3))

    @Test
    fun playNextSkipsUnplayableTracks() = runBlocking {
        val ok = delegate.playNext(tracks, unplayableIds = setOf(2L), onToast = onToast)

        assertTrue(ok)
        assertEquals(listOf(1L, 3L), player.playNextCalls.single().map { it.songId })
        assertEquals("已添加 2 首歌曲至下一首播放，跳过 1 首不可播放", toasts.single())
    }

    @Test
    fun playNextDoesNothingWhenAllUnplayable() = runBlocking {
        val ok = delegate.playNext(tracks, unplayableIds = setOf(1L, 2L, 3L), onToast = onToast)

        assertFalse(ok)
        assertTrue(player.playNextCalls.isEmpty())
    }

    @Test
    fun addToPlaylistSendsAllIdsInOneRequest() = runBlocking {
        val ok = delegate.addToPlaylist(10, tracks, onToast = onToast)

        assertTrue(ok)
        assertEquals(listOf(FakePlaylistRepository.ManipulateCall("add", 10, listOf(1, 2, 3))), playlistRepository.manipulateCalls)
        assertEquals("已添加 3 首歌曲到歌单", toasts.single())
    }

    @Test
    fun addToPlaylistReportsFailureAndKeepsSelectionRetryable() = runBlocking {
        playlistRepository.manipulateResult = Result.failure(IllegalStateException("网络错误"))

        val ok = delegate.addToPlaylist(10, tracks, onToast = onToast)

        assertFalse(ok)
        assertEquals(1, toasts.size)
    }

    @Test
    fun emptySelectionIsNoOp() = runBlocking {
        assertFalse(delegate.addToPlaylist(10, emptyList(), onToast = onToast))
        assertFalse(delegate.removeFromPlaylist(10, emptyList(), onToast = onToast))
        assertFalse(delegate.download(emptyList(), "exhigh", "tag", "label", onToast))
        assertTrue(playlistRepository.manipulateCalls.isEmpty())
        assertTrue(downloader.batchCalls.isEmpty())
    }

    @Test
    fun createPlaylistAndAddCreatesThenAddsAllTracks() = runBlocking {
        val ok = delegate.createPlaylistAndAdd("新歌单", privacy = 10, tracks = tracks, onToast = onToast)

        assertTrue(ok)
        assertEquals(listOf(FakeCreateRepository.CreateCall("新歌单", 10)), createRepository.createCalls)
        assertEquals(
            FakePlaylistRepository.ManipulateCall("add", FakeCreateRepository.NEW_PLAYLIST_ID, listOf(1, 2, 3)),
            playlistRepository.manipulateCalls.single()
        )
        assertEquals("已创建歌单并添加 3 首歌曲", toasts.single())
    }

    @Test
    fun downloadEnqueuesWholeBatchWithTagAndLabel() = runBlocking {
        val ok = delegate.download(tracks, "lossless", "playlist_5", "我的歌单", onToast)

        assertTrue(ok)
        val call = downloader.batchCalls.single()
        assertEquals(listOf(1L, 2L, 3L), call.tracks.map { it.songId })
        assertEquals("lossless", call.level)
        assertEquals("playlist_5", call.batchTag)
        assertEquals("我的歌单", call.batchLabel)
    }

    @Test
    fun likeSkipsAlreadyLikedAndCountsSuccesses() = runBlocking {
        val succeeded = delegate.like(tracks, onToast)

        assertEquals(2, succeeded)
        assertEquals(listOf(1L, 3L), likeRepository.likeCalls)
        assertEquals("已喜欢 2 首歌曲", toasts.single())
    }

    @Test
    fun likeReportsPartialFailure() = runBlocking {
        likeRepository.failingIds += 3L

        val succeeded = delegate.like(tracks, onToast)

        assertEquals(1, succeeded)
        assertEquals("已喜欢 1 首歌曲，1 首失败", toasts.single())
    }

    @Test
    fun likeWhenEverythingAlreadyLikedMakesNoRequest() = runBlocking {
        val succeeded = delegate.like(listOf(track(2)), onToast)

        assertEquals(0, succeeded)
        assertTrue(likeRepository.likeCalls.isEmpty())
    }

    @Test
    fun removeFromPlaylistSendsDelWithAllIds() = runBlocking {
        val ok = delegate.removeFromPlaylist(10, tracks.take(2), onToast = onToast)

        assertTrue(ok)
        assertEquals(FakePlaylistRepository.ManipulateCall("del", 10, listOf(1, 2)), playlistRepository.manipulateCalls.single())
        assertEquals("已从歌单中删除 2 首歌曲", toasts.single())
    }

    @Test
    fun loadTargetPlaylistsRequiresLogin() = runBlocking {
        val result = delegate.loadTargetPlaylists(excludePlaylistId = null, onToast = onToast)

        assertNull(result)
        assertEquals("请先登录账号", toasts.single())
    }

    @Test
    fun loadTargetPlaylistsKeepsOwnPlaylistsOnly() = runBlocking {
        userPreferences.saveUserProfile(UserProfile(uid = 1, nickname = "我", avatarUrl = ""))

        val result = delegate.loadTargetPlaylists(excludePlaylistId = 10, onToast = onToast)

        assertNotNull(result)
        assertEquals(listOf(11L), result!!.map { it.id })
    }
}

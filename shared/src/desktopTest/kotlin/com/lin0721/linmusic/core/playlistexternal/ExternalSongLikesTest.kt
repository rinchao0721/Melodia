package com.lin0721.linmusic.core.playlistexternal

import com.lin0721.linmusic.core.auth.UserPreferences
import com.lin0721.linmusic.core.auth.UserProfile
import com.lin0721.linmusic.core.player.QueueItem
import com.lin0721.linmusic.core.songlike.LikeSongListRequest
import com.lin0721.linmusic.core.songlike.LikeSongListResponse
import com.lin0721.linmusic.core.songlike.LikeSongRequest
import com.lin0721.linmusic.core.songlike.LikeSongResponse
import com.lin0721.linmusic.core.songlike.SongLikeApi
import com.lin0721.linmusic.core.songlike.SongLikeRepositoryImpl
import com.lin0721.linmusic.core.source.ExternalTrack
import com.lin0721.linmusic.core.source.MusicPlatform
import com.lin0721.linmusic.feature.podcast.ui.InMemoryPreferencesStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ExternalSongLikesTest {

    private class CountingApi : SongLikeApi {
        var likeCalls = 0
        override suspend fun likeSong(body: LikeSongRequest): LikeSongResponse {
            likeCalls++
            return LikeSongResponse(code = 200)
        }
        override suspend fun getLikedSongIds(body: LikeSongListRequest) = LikeSongListResponse(code = 200, ids = listOf(5L))
    }

    private val track = ExternalTrack(id = "k1", name = "歌", artists = "人", platform = MusicPlatform.KUGOU)

    @Test
    fun likingExternalSongWritesLocalLikedKeyWithoutCallingNetease() = runBlocking {
        val prefs = UserPreferences(InMemoryPreferencesStore())
        prefs.saveUserProfile(UserProfile(uid = 42, nickname = "我", avatarUrl = ""))
        val store = PlaylistExternalTrackStore(InMemoryPreferencesStore())
        val likes = ExternalSongLikes(store, prefs) { listOf(QueueItem(track.queueSongId, track.name, track.artists, "", external = track)) }
        val api = CountingApi()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        try {
            val repo = SongLikeRepositoryImpl(api, likes, scope)

            assertTrue(repo.likeSong(track.queueSongId, true).first().isSuccess)
            assertEquals(listOf(track.resolveKey), store.entriesOf(likedExternalKey(42)).first().map { it.track.resolveKey })
            assertEquals(0, api.likeCalls)

            // 拉取网易云红心列表时保留外部歌曲的红心
            repo.getLikedSongIds(42).first()
            assertTrue(track.queueSongId in repo.likedSongIds.value)
            assertTrue(5L in repo.likedSongIds.value)

            assertTrue(repo.likeSong(track.queueSongId, false).first().isSuccess)
            assertTrue(store.entriesOf(likedExternalKey(42)).first().isEmpty())
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun likingWithoutLoginFailsAndRollsBack() = runBlocking {
        val prefs = UserPreferences(InMemoryPreferencesStore())
        val store = PlaylistExternalTrackStore(InMemoryPreferencesStore())
        val likes = ExternalSongLikes(store, prefs) { emptyList() }
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        try {
            val repo = SongLikeRepositoryImpl(CountingApi(), likes, scope)

            val result = repo.likeSong(track.queueSongId, true).first()

            assertTrue(result.isFailure)
            assertTrue(track.queueSongId !in repo.likedSongIds.value)
        } finally {
            scope.cancel()
        }
    }
}

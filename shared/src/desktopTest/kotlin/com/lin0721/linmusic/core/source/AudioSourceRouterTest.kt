package com.lin0721.linmusic.core.source

import com.lin0721.linmusic.core.model.Track
import com.lin0721.linmusic.core.network.NetworkStateProvider
import com.lin0721.linmusic.core.player.PlaySource
import com.lin0721.linmusic.core.player.data.PlaybackRepository
import com.lin0721.linmusic.core.player.data.SongPlaybackInfo
import com.lin0721.linmusic.core.player.domain.LyricLine
import com.lin0721.linmusic.core.preferences.SettingsPreferences
import com.lin0721.linmusic.feature.podcast.ui.InMemoryPreferencesStore
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AudioSourceRouterTest {

    private class FakeProvider(
        private val result: SourceResult?,
        private val hangMs: Long = 0
    ) : AudioSourceProvider {
        override val platform = MusicPlatform.LX
        var calls = 0

        override suspend fun resolveUrl(
            songId: String,
            songName: String,
            artists: String,
            albumName: String?,
            durationMs: Long,
            quality: String,
            origin: MusicPlatform
        ): SourceResult? {
            calls++
            if (hangMs > 0) delay(hangMs)
            return result
        }

        override suspend fun search(keyword: String, offset: Int, limit: Int): List<ExternalTrack> = emptyList()
    }

    private class FakeOfficial(private val info: SongPlaybackInfo) : PlaybackRepository {
        var calls = 0

        override fun getSongPlaybackInfo(songId: Long): Flow<Result<SongPlaybackInfo>> = flow {
            calls++
            emit(Result.success(info))
        }

        override fun getSongDetail(songId: Long): Flow<Result<Track>> = flowOf(Result.success(Track(id = songId, name = "歌")))
        override fun getSongUrl(songId: Long): Flow<Result<String>> = emptyFlow()
        override fun getLyrics(songId: Long): Flow<Result<List<LyricLine>>> = emptyFlow()
        override fun getRawLyrics(songId: Long): Flow<Result<String>> = emptyFlow()
        override fun getSimilarSongs(songId: Long): Flow<Result<List<Track>>> = emptyFlow()
        override fun getIntelligenceSongs(songId: Long, playlistId: Long): Flow<Result<List<Track>>> = emptyFlow()
        override fun reportStartPlay(songId: Long, source: PlaySource?): Flow<Result<Unit>> = emptyFlow()
        override fun reportPlayEnd(songId: Long, playedSeconds: Long, source: PlaySource?): Flow<Result<Unit>> = emptyFlow()
        override val playlistRecorded: SharedFlow<Long> = MutableSharedFlow()
    }

    private val fullOfficial = SongPlaybackInfo(url = "http://official/full.mp3", level = "lossless", gainDb = -3.0)
    private val trialOfficial = SongPlaybackInfo(url = "http://official/trial.mp3", isFreeTrial = true)
    private val pluginHit = SourceResult(url = "http://plugin/ok.mp3", platform = MusicPlatform.LX)

    private suspend fun router(
        official: PlaybackRepository,
        providers: List<AudioSourceProvider>,
        fallback: Boolean = true,
        pluginFirst: Boolean = true
    ): AudioSourceRouter {
        val prefs = SourcePreferences(InMemoryPreferencesStore()).apply {
            saveFallbackEnabled(fallback)
            savePluginBeforeOfficial(pluginFirst)
        }
        return AudioSourceRouter(
            delegate = official,
            unmApiClient = UnmApiClient(),
            sourcePreferences = prefs,
            settingsPreferences = SettingsPreferences(InMemoryPreferencesStore()),
            networkStateProvider = NetworkStateProvider { true },
            providers = providers
        )
    }

    @Test
    fun switchOffKeepsCompleteOfficialLinkWithoutAskingPlugins() = runTest {
        val plugin = FakeProvider(pluginHit)
        val routed = router(FakeOfficial(fullOfficial), listOf(plugin), pluginFirst = false)

        assertEquals(fullOfficial, routed.getSongPlaybackInfo(1L).first().getOrThrow())
        assertEquals(0, plugin.calls)
    }

    @Test
    fun pluginHitReplacesCompleteOfficialLink() = runTest {
        val official = FakeOfficial(fullOfficial)
        val routed = router(official, listOf(FakeProvider(pluginHit)))

        val info = routed.getSongPlaybackInfo(1L).first().getOrThrow()

        assertEquals("http://plugin/ok.mp3", info.url)
        assertNull(info.level)
        assertNull(info.gainDb)
        assertEquals(0, official.calls)
    }

    @Test
    fun pluginMissFallsBackToOfficial() = runTest {
        val routed = router(FakeOfficial(fullOfficial), listOf(FakeProvider(null)))

        assertEquals(fullOfficial, routed.getSongPlaybackInfo(1L).first().getOrThrow())
    }

    @Test
    fun pluginTrialLinkCountsAsMiss() = runTest {
        val trial = SourceResult(url = "http://plugin/full.mp3", platform = MusicPlatform.LX, isTrial = true)
        val routed = router(FakeOfficial(fullOfficial), listOf(FakeProvider(trial)))

        assertEquals(fullOfficial, routed.getSongPlaybackInfo(1L).first().getOrThrow())
    }

    @Test
    fun switchIsIgnoredWhenFallbackIsOff() = runTest {
        val plugin = FakeProvider(pluginHit)
        val routed = router(FakeOfficial(fullOfficial), listOf(plugin), fallback = false)

        assertEquals(fullOfficial, routed.getSongPlaybackInfo(1L).first().getOrThrow())
        assertEquals(0, plugin.calls)
    }

    @Test
    fun budgetStopsWaitingAndSkipsRemainingProviders() = runTest {
        val hung = FakeProvider(pluginHit, hangMs = 60_000)
        val next = FakeProvider(pluginHit)
        val routed = router(FakeOfficial(fullOfficial), listOf(hung, next))

        assertEquals(fullOfficial, routed.getSongPlaybackInfo(1L).first().getOrThrow())
        assertEquals(10_000L, testScheduler.currentTime)
        assertEquals(0, next.calls)
    }

    @Test
    fun officialTrialDoesNotAskPluginsTwice() = runTest {
        val plugin = FakeProvider(null)
        val routed = router(FakeOfficial(trialOfficial), listOf(plugin))

        val info = routed.getSongPlaybackInfo(1L).first().getOrThrow()

        assertEquals("http://official/trial.mp3", info.url)
        assertEquals(true, info.trialFallback)
        assertEquals(1, plugin.calls)
    }

    @Test
    fun officialTrialStillUsesPluginsWhenSwitchIsOff() = runTest {
        val routed = router(FakeOfficial(trialOfficial), listOf(FakeProvider(pluginHit)), pluginFirst = false)

        assertEquals("http://plugin/ok.mp3", routed.getSongPlaybackInfo(1L).first().getOrThrow().url)
    }
}

package com.lin0721.linmusic.core.source

import com.lin0721.linmusic.core.network.NetworkStateProvider
import com.lin0721.linmusic.core.preferences.SettingsPreferences
import com.lin0721.linmusic.feature.podcast.ui.InMemoryPreferencesStore
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ExternalTrackResolverTest {

    private class FakeProvider(private val result: SourceResult?) : AudioSourceProvider {
        override val platform = MusicPlatform.LX
        val qualities = mutableListOf<String>()
        override suspend fun resolveUrl(
            songId: String,
            songName: String,
            artists: String,
            albumName: String?,
            durationMs: Long,
            quality: String,
            origin: MusicPlatform
        ): SourceResult? {
            qualities += quality
            return result
        }

        override suspend fun search(keyword: String, offset: Int, limit: Int): List<ExternalTrack> = emptyList()
    }

    private val track = ExternalTrack(id = "k1", name = "歌", artists = "人", platform = MusicPlatform.KUGOU)

    @Test
    fun trialResultsAreSkippedForNextProvider() = runBlocking {
        val trial = FakeProvider(SourceResult(url = "http://a/full.mp3", platform = MusicPlatform.KUGOU, isTrial = true))
        val trialUrl = FakeProvider(SourceResult(url = "http://a/musicrep-ts/x.mp3", platform = MusicPlatform.KUGOU))
        val ok = FakeProvider(SourceResult(url = "http://b/ok.mp3", platform = MusicPlatform.KUGOU))
        val resolver = ExternalTrackResolver(listOf(trial, trialUrl, ok), null)

        assertEquals("http://b/ok.mp3", resolver.resolve(track))
    }

    @Test
    fun onlyTrialResultsMeanFailure() = runBlocking {
        val trial = FakeProvider(SourceResult(url = "http://a/full.mp3", platform = MusicPlatform.KUGOU, isTrial = true))
        assertNull(ExternalTrackResolver(listOf(trial), null).resolve(track))
    }

    @Test
    fun qualityFollowsNetworkSetting() = runBlocking {
        val settings = SettingsPreferences(InMemoryPreferencesStore())
        settings.saveWifiQuality("hires")
        settings.saveMobileQuality("exhigh")
        val provider = FakeProvider(SourceResult(url = "http://b/ok.mp3", platform = MusicPlatform.KUGOU))

        ExternalTrackResolver(listOf(provider), null, settings, NetworkStateProvider { true }).resolve(track)
        ExternalTrackResolver(listOf(provider), null, settings, NetworkStateProvider { false }).resolve(track)

        assertEquals(listOf("hires", "exhigh"), provider.qualities)
    }

    private fun unmBody(url: String) = """{"code":200,"data":{"url":"$url","source":"kuwo"}}"""

    private suspend fun unmPrefs(serverUrl: String, communityFirst: Boolean): SourcePreferences =
        SourcePreferences(InMemoryPreferencesStore()).apply {
            saveUnmRemoteFallbackEnabled(true)
            saveUnmServerUrl(serverUrl)
            saveCommunityPriority(communityFirst)
        }

    @Test
    fun unmUsesMatchedNeteaseIdWhenCommunityFails() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setBody(unmBody("http://unm/ok.mp3")))
            val prefs = unmPrefs(server.url("/").toString(), communityFirst = true)
            val resolver = ExternalTrackResolver(
                listOf(FakeProvider(null)), prefs, unmApiClient = UnmApiClient(), neteaseLookup = { 186016L }
            )

            assertEquals("http://unm/ok.mp3", resolver.resolve(track))
            assertEquals("186016", server.takeRequest().requestUrl!!.queryParameter("id"))
        }
    }

    @Test
    fun unmGoesFirstWhenCommunityIsNotPrioritized() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setBody(unmBody("http://unm/first.mp3")))
            val community = FakeProvider(SourceResult(url = "http://b/ok.mp3", platform = MusicPlatform.KUGOU))
            val prefs = unmPrefs(server.url("/").toString(), communityFirst = false)
            val resolver = ExternalTrackResolver(
                listOf(community), prefs, unmApiClient = UnmApiClient(), neteaseLookup = { 1L }
            )

            assertEquals("http://unm/first.mp3", resolver.resolve(track))
            assertTrue(community.qualities.isEmpty())
        }
    }

    @Test
    fun unmIsSkippedWithoutNeteaseMatch() = runBlocking {
        MockWebServer().use { server ->
            val prefs = unmPrefs(server.url("/").toString(), communityFirst = false)
            val resolver = ExternalTrackResolver(emptyList(), prefs, unmApiClient = UnmApiClient(), neteaseLookup = { null })

            assertNull(resolver.resolve(track))
            assertEquals(0, server.requestCount)
        }
    }

    @Test
    fun failureMessagePointsToSettingsWhenNothingConfigured() = runBlocking {
        val resolver = ExternalTrackResolver(emptyList(), SourcePreferences(InMemoryPreferencesStore()))

        assertTrue(resolver.explainFailure(track).contains("社区源插件或远程 UNM"))
    }
}

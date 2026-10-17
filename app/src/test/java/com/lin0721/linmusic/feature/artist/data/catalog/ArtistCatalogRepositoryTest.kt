package com.lin0721.linmusic.feature.artist.data.catalog

import com.lin0721.linmusic.BuildConfig
import com.lin0721.linmusic.feature.artist.domain.catalog.ArtistCatalogPreference
import java.io.File
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import okhttp3.HttpUrl
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class ArtistCatalogRepositoryTest {
    @get:Rule val temporary = TemporaryFolder()
    private val artistId = "a3bce26f-8e2b-4f24-9d2d-8f41f88ac2ee"
    private val otherArtistId = "aacbf46f-7171-4f7a-9222-d71f60180048"
    private var wallTime = 1_000_000L

    private class Preferences : ArtistCatalogPreferenceStorage {
        val data = MutableStateFlow<Map<Long, ArtistCatalogPreference>>(emptyMap())
        override val defaultEnabled = MutableStateFlow(true)
        override fun observe(artistId: Long) = combine(data, defaultEnabled) { values, default ->
            (values[artistId] ?: ArtistCatalogPreference()).copy(defaultEnabled = default)
        }
        override suspend fun setDefaultEnabled(enabled: Boolean) { defaultEnabled.value = enabled }
        override suspend fun setEnabled(artistId: Long, enabled: Boolean) {
            data.value += artistId to (data.value[artistId] ?: ArtistCatalogPreference()).copy(enabledOverride = enabled)
        }
        override suspend fun clearEnabledOverride(artistId: Long) {
            data.value += artistId to (data.value[artistId] ?: ArtistCatalogPreference()).copy(enabledOverride = null)
        }
        override suspend fun bind(artistId: Long, mbid: String, name: String?) {
            data.value += artistId to (data.value[artistId] ?: ArtistCatalogPreference()).copy(artistMbid = mbid, artistName = name)
        }
        override suspend fun clearBinding(artistId: Long) {
            data.value += artistId to (data.value[artistId] ?: ArtistCatalogPreference()).copy(artistMbid = null, artistName = null)
        }
    }

    private fun TestScope.repository(
        directory: File = temporary.newFolder(),
        preferences: Preferences = Preferences(),
        gate: ArtistCatalogRequestGate = IntervalArtistCatalogGate({ testScheduler.currentTime }, { delay(it) }),
        transport: ArtistCatalogTransport
    ) = ArtistCatalogRepository(directory, preferences, transport, gate, { wallTime }, StandardTestDispatcher(testScheduler))

    private fun group(id: Int, title: String = "Album $id", secondary: String? = "[]") = """
        {"id":"00000000-0000-4000-8000-${id.toString().padStart(12, '0')}","title":"$title",
         "first-release-date":"2011-06-17","primary-type":"Album"${secondary?.let { ",\"secondary-types\":$it" }.orEmpty()},
         "artist-credit":[{"name":"Bon Iver","artist":{"id":"$artistId","name":"Bon Iver"},"joinphrase":""}]}
    """.trimIndent()

    private fun groups(total: Int, offset: Int, rows: List<String>) = """
        {"release-group-count":$total,"release-group-offset":$offset,"release-groups":[${rows.joinToString(",")}]}
    """.trimIndent()

    private fun artists(total: Int = 1, offset: Int = 0, rows: String = """
        {"id":"$artistId","name":"Bon Iver","sort-name":"Bon Iver","score":"100","country":"US",
         "type":"Group","disambiguation":"American indie folk band","aliases":[{"name":"邦·艾弗"}]}
    """.trimIndent()) = """{"count":$total,"offset":$offset,"artists":[$rows]}"""

    @Test fun browseUsesAllTypesSupportedIncludesAndActualPageOffsets() = runTest {
        val urls = mutableListOf<HttpUrl>()
        val repo = repository(transport = ArtistCatalogTransport { url ->
            urls += url
            val offset = url.queryParameter("offset")!!.toInt()
            ArtistCatalogHttpResponse(200, if (offset == 0) groups(101, 0, (1..100).map { group(it) }) else groups(101, 100, listOf(group(101))))
        })
        val result = repo.albums(artistId).getOrThrow()
        assertEquals(101, result.size)
        assertEquals(listOf("0", "100"), urls.map { it.queryParameter("offset") })
        assertTrue(urls.all { it.encodedPath.endsWith("/release-group") && it.queryParameter("limit") == "100" })
        assertTrue(urls.all { it.queryParameter("type") == null && it.queryParameter("inc") == "artist-credits" })
        assertTrue(urls.all { it.queryParameter("release-group-status") == "website-default" && it.queryParameter("fmt") == "json" })
    }

    @Test fun searchEscapesLiteralLuceneSyntaxAndPreservesArtistAliasesAndDisambiguation() = runTest {
        val urls = mutableListOf<HttpUrl>()
        val repo = repository(transport = ArtistCatalogTransport { url -> urls += url; ArtistCatalogHttpResponse(200, artists()) })
        val result = repo.findArtists("AC/DC \"Name\"").getOrThrow().single()
        assertEquals("artist:\"AC\\/DC \\\"Name\\\"\" OR alias:\"AC\\/DC \\\"Name\\\"\"", urls.single().queryParameter("query"))
        assertEquals(listOf("邦·艾弗"), result.aliases)
        assertEquals("American indie folk band", result.disambiguation)
        assertEquals("US", result.country)
        assertEquals(100, result.score)
    }

    @Test fun completeSuccessSurvivesRepositoryRestartWithoutNetworkAndLastsSevenDays() = runTest {
        val directory = temporary.newFolder()
        var calls = 0
        val network = ArtistCatalogTransport { calls++; ArtistCatalogHttpResponse(200, groups(1, 0, listOf(group(1)))) }
        repository(directory, transport = network).albums(artistId).getOrThrow()
        wallTime += ArtistCatalogRepository.SUCCESS_TTL_MS - 1L
        val restarted = repository(directory, transport = network)
        assertEquals(1, restarted.cachedAlbums(artistId)?.size)
        assertEquals(1, restarted.albums(artistId).getOrThrow().size)
        assertEquals(1, calls)
        wallTime += 1L
        restarted.albums(artistId).getOrThrow()
        assertEquals(2, calls)
    }

    @Test fun failedRefreshAndPersistentCooldownKeepTheLastCompleteCatalog() = runTest {
        val directory = temporary.newFolder()
        val original = repository(directory, transport = ArtistCatalogTransport {
            ArtistCatalogHttpResponse(200, groups(1, 0, listOf(group(1))))
        }).albums(artistId).getOrThrow()
        wallTime += ArtistCatalogRepository.SUCCESS_TTL_MS + 1L
        var calls = 0
        val failing = ArtistCatalogTransport { calls++; ArtistCatalogHttpResponse(503, "busy", 90L) }
        val restarted = repository(directory, transport = failing)
        assertEquals(503, (restarted.albums(artistId).exceptionOrNull() as ArtistCatalogException).httpStatus)
        assertEquals(original, restarted.cachedAlbums(artistId))
        val afterAnotherRestart = repository(directory, transport = failing)
        assertTrue(afterAnotherRestart.albums(artistId, force = true).isFailure)
        assertEquals(1, calls)
        assertEquals(original, afterAnotherRestart.cachedAlbums(artistId))
        wallTime += 90_001L
        assertTrue(afterAnotherRestart.albums(artistId, force = true).isFailure)
        assertEquals(2, calls)
    }

    @Test fun successfulEmptyCatalogIsDifferentFromAnUncachedNetworkFailure() = runTest {
        val empty = repository(transport = ArtistCatalogTransport { ArtistCatalogHttpResponse(200, groups(0, 0, emptyList())) })
        assertNull(empty.cachedAlbums(artistId))
        assertEquals(emptyList<Any>(), empty.albums(artistId).getOrThrow())
        assertNotNull(empty.cachedAlbums(artistId))
        assertTrue(empty.cachedAlbums(artistId)!!.isEmpty())
        val failed = repository(transport = ArtistCatalogTransport { throw IOException("offline") })
        assertTrue(failed.albums(artistId).isFailure)
        assertNull(failed.cachedAlbums(artistId))
    }

    @Test fun aPartialBrowseDoesNotOverwritePreviousCompleteData() = runTest {
        val directory = temporary.newFolder()
        val original = repository(directory, transport = ArtistCatalogTransport {
            ArtistCatalogHttpResponse(200, groups(1, 0, listOf(group(1))))
        }).albums(artistId).getOrThrow()
        var calls = 0
        val partial = repository(directory, transport = ArtistCatalogTransport { url ->
            calls++
            ArtistCatalogHttpResponse(200, if (url.queryParameter("offset") == "0") groups(101, 0, (1..100).map { group(it) })
                else groups(101, 100, emptyList()))
        })
        assertTrue(partial.albums(artistId, force = true).isFailure)
        assertEquals(2, calls)
        assertEquals(original, partial.cachedAlbums(artistId))
    }

    @Test fun inconsistentTotalsRepeatedIdsWrongOffsetsAndOversizedCatalogsAreExplicitFailures() = runTest {
        val malformed = listOf(
            groups(2, 0, listOf(group(1), group(1))),
            groups(1, 100, listOf(group(1))),
            groups(10_001, 0, listOf(group(1))),
            """{"error":"unexpected response"}"""
        )
        malformed.forEach { body ->
            val repo = repository(transport = ArtistCatalogTransport { ArtistCatalogHttpResponse(200, body) })
            assertTrue(body, repo.albums(artistId).isFailure)
            assertNull(repo.cachedAlbums(artistId))
        }
        val changing = repository(transport = ArtistCatalogTransport { url -> ArtistCatalogHttpResponse(200,
            if (url.queryParameter("offset") == "0") groups(2, 0, listOf(group(1))) else groups(3, 1, listOf(group(2)))) })
        assertTrue(changing.albums(artistId).isFailure)
        assertNull(changing.cachedAlbums(artistId))
    }

    @Test fun a503GetsOneRequestAndDoesNotBecomeAnAutomaticRetryLoop() = runTest {
        var calls = 0
        val repo = repository(transport = ArtistCatalogTransport { calls++; ArtistCatalogHttpResponse(503, "busy") })
        repeat(5) { assertTrue(repo.albums(artistId, force = true).isFailure) }
        assertEquals(1, calls)
        assertNull(repo.cachedAlbums(artistId))
    }

    @Test fun oneSharedGateSerializesDifferentInstancesAndSearchBrowseKinds() = runTest {
        val startTimes = mutableListOf<Long>()
        var active = 0
        var maxActive = 0
        val transport = ArtistCatalogTransport { url ->
            startTimes += testScheduler.currentTime
            active++
            maxActive = maxOf(maxActive, active)
            delay(50L)
            active--
            ArtistCatalogHttpResponse(200, if (url.encodedPath.endsWith("/artist")) artists() else groups(0, 0, emptyList()))
        }
        val gate = IntervalArtistCatalogGate({ testScheduler.currentTime }, { delay(it) })
        val first = repository(gate = gate, transport = transport)
        val second = repository(gate = gate, transport = transport)
        val one = async { first.findArtists("Bon Iver").getOrThrow() }
        val two = async { second.albums(artistId).getOrThrow() }
        val three = async { second.albums(otherArtistId).getOrThrow() }
        one.await(); two.await(); three.await()
        assertEquals(listOf(0L, 1_100L, 2_200L), startTimes)
        assertEquals(1, maxActive)
    }

    @Test fun cancellationIsNotStoredAsAPersistentFailure() = runTest {
        var cancelled = true
        var calls = 0
        val repo = repository(transport = ArtistCatalogTransport {
            calls++
            if (cancelled) throw CancellationException("user left")
            ArtistCatalogHttpResponse(200, groups(0, 0, emptyList()))
        })
        try { repo.albums(artistId); fail("Cancellation must propagate") } catch (_: CancellationException) { }
        cancelled = false
        assertTrue(repo.albums(artistId).isSuccess)
        assertEquals(2, calls)
    }

    @Test fun settingsAreIsolatedByNeteaseArtistAndBindingDoesNotEnableOrDownloadAnything() = runTest {
        val prefs = Preferences()
        val repo = repository(preferences = prefs, transport = ArtistCatalogTransport { error("Settings must not fetch metadata") })
        assertEquals(ArtistCatalogPreference(), repo.selection(123L).first())
        repo.bindArtist(123L, artistId.uppercase(), " Bon Iver ")
        assertEquals(ArtistCatalogPreference(artistMbid = artistId, artistName = "Bon Iver"), repo.selection(123L).first())
        repo.setEnabled(123L, true)
        repo.setEnabled(456L, true)
        repo.bindArtist(456L, otherArtistId, "Other")
        repo.setEnabled(123L, false)
        assertEquals(artistId, repo.selection(123L).first().artistMbid)
        assertEquals(otherArtistId, repo.selection(456L).first().artistMbid)
        repo.clearBinding(456L)
        assertEquals(ArtistCatalogPreference(enabledOverride = true), repo.selection(456L).first())
        assertEquals("Bon Iver", repo.selection(123L).first().artistName)
        repo.setEnabled(0L, true)
        assertTrue(repo.selection(0L).first().enabled)
    }

    @Test fun invalidIdentifiersAndBlankSearchesCannotReachTheNetworkOrFilesystem() = runTest {
        val repo = repository(transport = ArtistCatalogTransport { error("Invalid input must not fetch") })
        assertTrue(repo.findArtists(" ").isFailure)
        assertTrue(repo.findArtists("x".repeat(201)).isFailure)
        assertTrue(repo.albums("../../artist").isFailure)
        assertNull(repo.cachedAlbums("../../artist"))
        try { repo.bindArtist(1L, "../../artist"); fail("Invalid binding must fail") } catch (_: IllegalArgumentException) { }
        assertEquals(ArtistCatalogPreference(), repo.selection(1L).first())
    }

    @Test fun bareHttpTransportIdentifiesTheAppAndNeverSendsNeteaseCredentials() = runTest {
        val server = MockWebServer()
        server.enqueue(MockResponse().setBody("{}"))
        server.start()
        try {
            assertEquals(200, OkHttpArtistCatalogTransport().get(server.url("/ws/2/artist?fmt=json")).status)
            val request = server.takeRequest()
            assertEquals(ArtistCatalogRepository.USER_AGENT, request.getHeader("User-Agent"))
            assertTrue(request.getHeader("User-Agent")!!.startsWith("Melodia/${BuildConfig.VERSION_NAME} "))
            assertTrue(request.getHeader("User-Agent")!!.contains("https://github.com/rinchao0721/Melodia"))
            assertEquals("application/json", request.getHeader("Accept"))
            assertEquals("GET", request.method)
            assertNull(request.getHeader("Cookie"))
            assertNull(request.getHeader("Authorization"))
        } finally { server.shutdown() }
    }

    @Test fun codecDistinguishesAbsentSecondaryTypeFromKnownEmptyAndKeepsCredits() {
        val parsed = ArtistCatalogApiCodec.releaseGroupPage(groups(3, 0, listOf(group(1), group(2, secondary = null), group(3, secondary = "[\"Live\"]"))))
        assertEquals(emptyList<String>(), parsed.rows[0].secondaryTypes)
        assertNull(parsed.rows[1].secondaryTypes)
        assertEquals(listOf("Live"), parsed.rows[2].secondaryTypes)
        assertEquals(artistId, parsed.rows[0].artistCredits.single().artistId)
        assertEquals("Bon Iver", parsed.rows[0].artistCredits.single().name)
        assertTrue(parsed.rows.all { it.aliases.isEmpty() })
    }

    @Test fun httpTransportDoesNotFollowRedirectsOutsideTheRequestRateGate() = runTest {
        val server = MockWebServer()
        server.start()
        server.enqueue(MockResponse().setResponseCode(302).setHeader("Location", server.url("/redirected")))
        server.enqueue(MockResponse().setBody("{}"))
        try {
            assertEquals(302, OkHttpArtistCatalogTransport().get(server.url("/ws/2/artist?fmt=json")).status)
            assertEquals(1, server.requestCount)
        } finally { server.shutdown() }
    }
}

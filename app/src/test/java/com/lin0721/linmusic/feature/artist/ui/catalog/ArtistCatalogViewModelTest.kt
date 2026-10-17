package com.lin0721.linmusic.feature.artist.ui.catalog

import androidx.lifecycle.ViewModelStore
import com.lin0721.linmusic.core.model.ArtistAlbum
import com.lin0721.linmusic.core.model.ArtistDetailInfo
import com.lin0721.linmusic.feature.artist.data.catalog.ArtistCatalogHttpResponse
import com.lin0721.linmusic.feature.artist.data.catalog.ArtistCatalogPreferenceStorage
import com.lin0721.linmusic.feature.artist.data.catalog.ArtistCatalogRepository
import com.lin0721.linmusic.feature.artist.data.catalog.ArtistCatalogRequestGate
import com.lin0721.linmusic.feature.artist.data.catalog.ArtistCatalogTransport
import com.lin0721.linmusic.feature.artist.domain.catalog.ArtistCatalogPreference
import java.io.IOException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.setMain
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

@OptIn(ExperimentalCoroutinesApi::class)
class ArtistCatalogViewModelTest {
    @get:Rule val temporary = TemporaryFolder()
    private val artist = ArtistDetailInfo(id = 123L, name = "Example Artist")
    private val albums = listOf(ArtistAlbum(id = 1L, name = "First Album"), ArtistAlbum(id = 2L, name = "Second Album"))

    @Test fun disabledSettingDoesNotRequestCatalogueWhenAlbumsOrArtistContextLoads() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(dispatcher)
        val transport = FakeTransport()
        val preferences = FakePreferences(ArtistCatalogPreference(enabledOverride = false))
        val repository = repository(preferences, transport, dispatcher)
        val model = ArtistCatalogViewModel(repository)
        val store = ViewModelStore().apply { put("catalog", model) }
        try {
            model.updateArtistContext(artist, albums.take(1))
            advanceUntilIdle()
            model.updateArtistContext(artist, albums)
            advanceUntilIdle()
            assertFalse(model.state.value.enabled)
            assertEquals(0, transport.requests)
        } finally { store.clear(); Dispatchers.resetMain() }
    }

    @Test fun uniqueExactNameNeedsTwoDistinctAlbumMatchesBeforeAutomaticBinding() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(dispatcher)
        val transport = FakeTransport()
        val preferences = FakePreferences()
        val model = ArtistCatalogViewModel(repository(preferences, transport, dispatcher))
        val store = ViewModelStore().apply { put("catalog", model) }
        try {
            model.updateArtistContext(artist, albums)
            advanceUntilIdle()
            assertEquals(MBID, model.state.value.artistMbid)
            assertEquals("Example Artist", model.state.value.artistName)
            assertEquals(2, model.state.value.groups?.size)
            assertFalse(model.state.value.choosingArtist)
            assertNull(model.state.value.error)
        } finally { store.clear(); Dispatchers.resetMain() }
    }

    @Test fun choosingTheSameBoundArtistResumesAnInterruptedCatalogueRead() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(dispatcher)
        val started = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val transport = FakeTransport().apply {
            beforeGet = { if (requests == 1) { started.complete(Unit); release.await() } }
        }
        val preferences = FakePreferences(ArtistCatalogPreference(true, MBID, "Example Artist"))
        val model = ArtistCatalogViewModel(repository(preferences, transport, dispatcher))
        val store = ViewModelStore().apply { put("catalog", model) }
        try {
            model.updateArtistContext(artist, albums)
            runCurrent()
            assertTrue(started.isCompleted)
            assertTrue(model.state.value.loading)
            model.changeBinding()
            advanceUntilIdle()
            assertNull(model.state.value.groups)
            assertTrue(model.state.value.choosingArtist)
            model.bind(model.state.value.candidates.single())
            advanceUntilIdle()
            assertEquals(MBID, model.state.value.artistMbid)
            assertEquals(2, model.state.value.groups?.size)
            assertFalse(model.state.value.loading)
            assertFalse(model.state.value.saving)
            assertFalse(model.state.value.choosingArtist)
            assertNull(model.state.value.error)
            assertEquals(3, transport.requests)
        } finally { release.complete(Unit); store.clear(); Dispatchers.resetMain() }
    }

    @Test fun oneAlbumLeavesCandidatesForExplicitChoiceAndLaterPageCanConfirmIdentity() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(dispatcher)
        val transport = FakeTransport()
        val preferences = FakePreferences()
        val model = ArtistCatalogViewModel(repository(preferences, transport, dispatcher))
        val store = ViewModelStore().apply { put("catalog", model) }
        try {
            model.updateArtistContext(artist, albums.take(1))
            advanceUntilIdle()
            assertNull(model.state.value.artistMbid)
            assertNull(model.state.value.groups)
            assertEquals(1, model.state.value.candidates.size)
            assertTrue(model.state.value.choosingArtist)
            val requested = transport.requests
            model.updateArtistContext(artist, albums)
            advanceUntilIdle()
            assertEquals(MBID, model.state.value.artistMbid)
            assertEquals(requested, transport.requests)
        } finally { store.clear(); Dispatchers.resetMain() }
    }

    @Test fun failedRefreshKeepsUsableCatalogueAndTurningOffMakesNoFurtherRequest() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(dispatcher)
        val transport = FakeTransport()
        val preferences = FakePreferences(ArtistCatalogPreference(true, MBID, "Example Artist"))
        val repository = repository(preferences, transport, dispatcher)
        repository.albums(MBID).getOrThrow()
        val model = ArtistCatalogViewModel(repository)
        val store = ViewModelStore().apply { put("catalog", model) }
        try {
            model.updateArtistContext(artist, albums)
            advanceUntilIdle()
            transport.fail = true
            model.retry()
            advanceUntilIdle()
            assertEquals(2, model.state.value.groups?.size)
            assertNotNull(model.state.value.error)
            assertFalse(model.state.value.loading)
            val requested = transport.requests
            model.setEnabled(false)
            advanceUntilIdle()
            model.updateArtistContext(artist, albums + ArtistAlbum(id = 3L, name = "Unknown Album"))
            advanceUntilIdle()
            assertFalse(model.state.value.enabled)
            assertEquals(requested, transport.requests)
        } finally { store.clear(); Dispatchers.resetMain() }
    }

    @Test fun failureWithoutCacheLeavesCatalogueUnavailableInsteadOfAnEmptySuccessfulList() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(dispatcher)
        val transport = FakeTransport().apply { fail = true }
        val preferences = FakePreferences(ArtistCatalogPreference(true, MBID, "Example Artist"))
        val model = ArtistCatalogViewModel(repository(preferences, transport, dispatcher))
        val store = ViewModelStore().apply { put("catalog", model) }
        try {
            model.updateArtistContext(artist, albums)
            advanceUntilIdle()
            assertNull(model.state.value.groups)
            assertNotNull(model.state.value.error)
            assertTrue(model.state.value.enabled)
            assertFalse(model.state.value.loading)
        } finally { store.clear(); Dispatchers.resetMain() }
    }

    @Test fun globalChangesRefreshOpenArtistWhileExplicitOffRemainsAndResetFollowsGlobal() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(dispatcher)
        val transport = FakeTransport()
        val preferences = FakePreferences(ArtistCatalogPreference(artistMbid = MBID, artistName = "Example Artist"))
        val model = ArtistCatalogViewModel(repository(preferences, transport, dispatcher))
        val store = ViewModelStore().apply { put("catalog", model) }
        try {
            model.updateArtistContext(artist, albums)
            advanceUntilIdle()
            assertTrue(model.state.value.enabled)
            assertNull(model.state.value.enabledOverride)
            assertEquals(2, model.state.value.groups?.size)
            preferences.setDefaultEnabled(false)
            advanceUntilIdle()
            assertFalse(model.state.value.enabled)
            assertFalse(model.state.value.defaultEnabled)
            assertEquals(MBID, model.state.value.artistMbid)
            model.setEnabled(false)
            advanceUntilIdle()
            preferences.setDefaultEnabled(true)
            advanceUntilIdle()
            assertFalse(model.state.value.enabled)
            assertTrue(model.state.value.defaultEnabled)
            assertEquals(false, model.state.value.enabledOverride)
            val requests = transport.requests
            model.followDefault()
            advanceUntilIdle()
            assertTrue(model.state.value.enabled)
            assertNull(model.state.value.enabledOverride)
            assertEquals(MBID, model.state.value.artistMbid)
            assertEquals(2, model.state.value.groups?.size)
            assertEquals(requests, transport.requests)
        } finally { store.clear(); Dispatchers.resetMain() }
    }

    @Test fun explicitOnSurvivesGlobalOffAndArtistChangesUseTheirOwnChoice() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(dispatcher)
        val transport = FakeTransport()
        val preferences = FakePreferences(ArtistCatalogPreference(true, MBID, "Example Artist"))
        preferences.setDefaultEnabled(false)
        val model = ArtistCatalogViewModel(repository(preferences, transport, dispatcher))
        val store = ViewModelStore().apply { put("catalog", model) }
        try {
            model.updateArtistContext(artist, albums)
            advanceUntilIdle()
            assertTrue(model.state.value.enabled)
            assertEquals(true, model.state.value.enabledOverride)
            val requests = transport.requests
            model.updateArtistContext(artist.copy(id = 456L), albums)
            advanceUntilIdle()
            assertFalse(model.state.value.enabled)
            assertNull(model.state.value.enabledOverride)
            assertEquals(requests, transport.requests)
            model.updateArtistContext(artist, albums)
            advanceUntilIdle()
            assertTrue(model.state.value.enabled)
            assertEquals(MBID, model.state.value.artistMbid)
        } finally { store.clear(); Dispatchers.resetMain() }
    }

    @Test fun lateBindSuccessForPreviousArtistPreservesTheCurrentArtistAndItsProposal() = runTest {
        assertLateBindIsIsolated(fail = false)
    }

    @Test fun enablingOrFollowingGlobalResumesAutomaticIdentitySearchAfterItsOwnSave() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(dispatcher)
        try {
            listOf(false, true).forEach { followDefault ->
                val preferences = FakePreferences(ArtistCatalogPreference(enabledOverride = false))
                val model = ArtistCatalogViewModel(repository(preferences, FakeTransport(), dispatcher))
                val store = ViewModelStore().apply { put("catalog", model) }
                try {
                    model.updateArtistContext(artist, albums)
                    advanceUntilIdle()
                    assertFalse(model.state.value.enabled)
                    if (followDefault) model.followDefault() else model.setEnabled(true)
                    advanceUntilIdle()
                    assertTrue(model.state.value.enabled)
                    assertFalse(model.state.value.saving)
                    assertEquals(MBID, model.state.value.artistMbid)
                    assertEquals(2, model.state.value.groups?.size)
                } finally { store.clear() }
            }
        } finally { Dispatchers.resetMain() }
    }

    @Test fun lateBindFailureForPreviousArtistDoesNotShowAnErrorOnTheCurrentArtist() = runTest {
        assertLateBindIsIsolated(fail = true)
    }

    private suspend fun TestScope.assertLateBindIsIsolated(fail: Boolean) {
        val dispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(dispatcher)
        val release = CompletableDeferred<Unit>()
        val entered = CompletableDeferred<Unit>()
        val preferences = FakePreferences().apply {
            beforeBind = { id -> if (id == artist.id) {
                entered.complete(Unit)
                release.await()
                if (fail) throw IOException("old artist binding failed")
            } }
        }
        val model = ArtistCatalogViewModel(repository(preferences, FakeTransport(), dispatcher))
        val store = ViewModelStore().apply { put("catalog", model) }
        try {
            model.updateArtistContext(artist, albums.take(1))
            advanceUntilIdle()
            model.bind(model.state.value.candidates.single())
            runCurrent()
            assertTrue(entered.isCompleted)
            model.updateArtistContext(artist.copy(id = 456L), albums.take(1))
            advanceUntilIdle()
            val current = model.state.value
            assertTrue(current.choosingArtist)
            release.complete(Unit)
            advanceUntilIdle()
            assertEquals(current, model.state.value)
            assertEquals(if (fail) null else MBID, preferences.snapshot(artist.id).artistMbid)
            // The old save must not clear the new artist's proposed identity. Its later
            // second source album should bind automatically without another artist search.
            model.updateArtistContext(artist.copy(id = 456L), albums)
            advanceUntilIdle()
            assertEquals(MBID, preferences.snapshot(456L).artistMbid)
            assertEquals(456L, model.state.value.artistId)
        } finally { release.complete(Unit); store.clear(); Dispatchers.resetMain() }
    }

    @Test fun lateEnabledSaveForPreviousArtistDoesNotClearTheCurrentPendingSave() = runTest {
        assertLateSettingIsIsolated(followDefault = false, fail = false)
    }

    @Test fun lateEnabledSaveFailureForPreviousArtistDoesNotClearTheCurrentPendingSave() = runTest {
        assertLateSettingIsIsolated(followDefault = false, fail = true)
    }

    @Test fun lateFollowDefaultSaveForPreviousArtistDoesNotClearTheCurrentPendingSave() = runTest {
        assertLateSettingIsIsolated(followDefault = true, fail = false)
    }

    @Test fun lateFollowDefaultFailureForPreviousArtistDoesNotClearTheCurrentPendingSave() = runTest {
        assertLateSettingIsIsolated(followDefault = true, fail = true)
    }

    private suspend fun TestScope.assertLateSettingIsIsolated(followDefault: Boolean, fail: Boolean) {
        val dispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(dispatcher)
        val releaseOld = CompletableDeferred<Unit>()
        val releaseCurrent = CompletableDeferred<Unit>()
        val oldEntered = CompletableDeferred<Unit>()
        val currentEntered = CompletableDeferred<Unit>()
        val preferences = FakePreferences(ArtistCatalogPreference(enabledOverride = false)).apply {
            val oldWrite: suspend (Long) -> Unit = { id -> if (id == artist.id) {
                oldEntered.complete(Unit)
                releaseOld.await()
                if (fail) throw IOException("old artist setting failed")
            } }
            beforeEnabled = { id ->
                if (id == 456L) { currentEntered.complete(Unit); releaseCurrent.await() }
                else oldWrite(id)
            }
            beforeClearEnabled = oldWrite
        }
        val model = ArtistCatalogViewModel(repository(preferences, FakeTransport(), dispatcher))
        val store = ViewModelStore().apply { put("catalog", model) }
        try {
            model.updateArtistContext(artist, albums.take(1))
            advanceUntilIdle()
            if (followDefault) model.followDefault() else model.setEnabled(true)
            runCurrent()
            assertTrue(oldEntered.isCompleted)
            model.updateArtistContext(artist.copy(id = 456L), albums.take(1))
            advanceUntilIdle()
            model.setEnabled(false)
            runCurrent()
            assertTrue(currentEntered.isCompleted)
            val current = model.state.value
            assertTrue(current.saving)
            releaseOld.complete(Unit)
            advanceUntilIdle()
            assertEquals(current, model.state.value)
            assertNull(model.state.value.error)
            assertTrue(model.state.value.saving)
            assertEquals(if (fail) false else if (followDefault) null else true, preferences.snapshot(artist.id).enabledOverride)
            releaseCurrent.complete(Unit)
            advanceUntilIdle()
            assertFalse(model.state.value.saving)
            assertFalse(model.state.value.enabled)
            assertEquals(false, preferences.snapshot(456L).enabledOverride)
        } finally { releaseOld.complete(Unit); releaseCurrent.complete(Unit); store.clear(); Dispatchers.resetMain() }
    }

    @Test fun returningToTheSameArtistStillIgnoresAFailureFromItsEarlierPageGeneration() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(dispatcher)
        val release = CompletableDeferred<Unit>()
        val preferences = FakePreferences().apply {
            beforeBind = { release.await(); throw IOException("outdated page save failed") }
        }
        val model = ArtistCatalogViewModel(repository(preferences, FakeTransport(), dispatcher))
        val store = ViewModelStore().apply { put("catalog", model) }
        try {
            model.updateArtistContext(artist, albums.take(1))
            advanceUntilIdle()
            model.bind(model.state.value.candidates.single())
            runCurrent()
            model.updateArtistContext(artist.copy(id = 456L), albums.take(1))
            advanceUntilIdle()
            model.updateArtistContext(artist, albums.take(1))
            advanceUntilIdle()
            val current = model.state.value
            release.complete(Unit)
            advanceUntilIdle()
            assertEquals(current, model.state.value)
            assertNull(model.state.value.error)
        } finally { release.complete(Unit); store.clear(); Dispatchers.resetMain() }
    }

    private fun repository(preferences: FakePreferences, transport: FakeTransport,
        dispatcher: CoroutineDispatcher) = ArtistCatalogRepository(
        cacheDirectory = temporary.newFolder(), preferences = preferences, transport = transport,
        gate = object : ArtistCatalogRequestGate {
            override suspend fun <T> request(action: suspend () -> T): T = action()
        }, ioDispatcher = dispatcher)

    private class FakePreferences(initial: ArtistCatalogPreference = ArtistCatalogPreference()) : ArtistCatalogPreferenceStorage {
        private val values = MutableStateFlow(mapOf(123L to initial))
        override val defaultEnabled = MutableStateFlow(true)
        private fun value(id: Long) = values.value[id] ?: ArtistCatalogPreference()
        fun snapshot(id: Long) = value(id)
        var beforeEnabled: suspend (Long) -> Unit = {}
        var beforeClearEnabled: suspend (Long) -> Unit = {}
        var beforeBind: suspend (Long) -> Unit = {}
        override fun observe(artistId: Long) = combine(values, defaultEnabled) { current, default ->
            (current[artistId] ?: ArtistCatalogPreference()).copy(defaultEnabled = default)
        }
        override suspend fun setDefaultEnabled(enabled: Boolean) { defaultEnabled.value = enabled }
        override suspend fun setEnabled(artistId: Long, enabled: Boolean) {
            beforeEnabled(artistId)
            values.value += artistId to value(artistId).copy(enabledOverride = enabled)
        }
        override suspend fun clearEnabledOverride(artistId: Long) {
            beforeClearEnabled(artistId)
            values.value += artistId to value(artistId).copy(enabledOverride = null)
        }
        override suspend fun bind(artistId: Long, mbid: String, name: String?) {
            beforeBind(artistId)
            values.value += artistId to value(artistId).copy(artistMbid = mbid, artistName = name)
        }
        override suspend fun clearBinding(artistId: Long) {
            values.value += artistId to value(artistId).copy(artistMbid = null, artistName = null)
        }
    }

    private class FakeTransport : ArtistCatalogTransport {
        var requests = 0
        var fail = false
        var beforeGet: suspend () -> Unit = {}
        override suspend fun get(url: okhttp3.HttpUrl): ArtistCatalogHttpResponse {
            requests++
            beforeGet()
            if (fail) return ArtistCatalogHttpResponse(503, "{}")
            if (url.encodedPath.endsWith("/artist")) return ArtistCatalogHttpResponse(200,
                """{"count":1,"offset":0,"artists":[{"id":"$MBID","name":"Example Artist","aliases":[]}]}""")
            return ArtistCatalogHttpResponse(200,
                """{"release-group-count":2,"release-group-offset":0,"release-groups":[
                    {"id":"21111111-1111-4111-8111-111111111111","title":"First Album","primary-type":"Album","secondary-types":[],"artist-credit":[{"artist":{"id":"$MBID","name":"Example Artist"}}]},
                    {"id":"31111111-1111-4111-8111-111111111111","title":"Second Album","primary-type":"Album","secondary-types":[],"artist-credit":[{"artist":{"id":"$MBID","name":"Example Artist"}}]}
                ]}""")
        }
    }

    companion object { private const val MBID = "11111111-1111-4111-8111-111111111111" }
}

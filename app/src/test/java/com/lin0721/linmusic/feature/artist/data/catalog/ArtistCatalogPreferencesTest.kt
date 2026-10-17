package com.lin0721.linmusic.feature.artist.data.catalog

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.core.DataStore
import androidx.datastore.core.okio.OkioStorage
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.PreferencesSerializer
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import okio.FileSystem
import okio.Path.Companion.toPath

/** Uses the production DataStore reader/writer, including beta.6 preference key names. */
@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class ArtistCatalogPreferencesTest {
    @get:Rule val temporary = TemporaryFolder()
    private val mbid = "11111111-1111-4111-8111-111111111111"

    // The Android factory's FileStorage uses File.renameTo, which cannot replace an existing
    // file in Windows JVM tests. Keep the real preferences protobuf/DataStore and production
    // preference reader, using Okio's supported atomic replacement backend for this host.
    private fun createStore(scope: CoroutineScope,
        file: File = File(temporary.newFolder(), "catalog.preferences_pb")): DataStore<Preferences> {
        val fixedPath = file.absolutePath.toPath()
        return PreferenceDataStoreFactory.create(
            storage = OkioStorage(FileSystem.SYSTEM, PreferencesSerializer, producePath = { fixedPath }),
            scope = scope)
    }

    @Test fun freshAndPreviouslyUnconfiguredArtistsFollowEnabledGlobalDefault() = runTest {
        val store = createStore(backgroundScope)
        val preferences = AndroidArtistCatalogPreferences(store)
        assertTrue(preferences.defaultEnabled.first())
        for (id in listOf(0L, 123L, 456L)) {
            val selection = preferences.observe(id).first()
            assertTrue(selection.enabled)
            assertTrue(selection.defaultEnabled)
            assertNull(selection.enabledOverride)
            assertNull(selection.artistMbid)
        }
    }

    @Test fun upgradedExplicitOffAndOnKeysRemainOverridesAndBindingIsPreserved() = runTest {
        val store = createStore(backgroundScope)
        store.edit {
            it[booleanPreferencesKey("artist_123_catalog_enabled")] = false
            it[booleanPreferencesKey("artist_456_catalog_enabled")] = true
            it[stringPreferencesKey("artist_123_catalog_mbid")] = mbid
            it[stringPreferencesKey("artist_123_catalog_name")] = "U2"
        }
        val preferences = AndroidArtistCatalogPreferences(store)
        assertFalse(preferences.observe(123L).first().enabled)
        assertEquals(false, preferences.observe(123L).first().enabledOverride)
        assertEquals(mbid, preferences.observe(123L).first().artistMbid)
        assertEquals("U2", preferences.observe(123L).first().artistName)
        assertTrue(preferences.observe(456L).first().enabled)
        preferences.setDefaultEnabled(false)
        assertFalse(preferences.observe(789L).first().enabled)
        assertTrue(preferences.observe(456L).first().enabled)
        assertEquals(true, preferences.observe(456L).first().enabledOverride)
    }

    @Test fun artistSwitchesAreIndependentAndGlobalChangesAffectOnlyFollowers() = runTest {
        val store = createStore(backgroundScope)
        val preferences = AndroidArtistCatalogPreferences(store)
        preferences.setEnabled(123L, false)
        preferences.setEnabled(456L, true)
        preferences.setDefaultEnabled(false)
        assertFalse(preferences.observe(123L).first().enabled)
        assertTrue(preferences.observe(456L).first().enabled)
        assertFalse(preferences.observe(789L).first().enabled)
        preferences.setDefaultEnabled(true)
        assertFalse(preferences.observe(123L).first().enabled)
        assertTrue(preferences.observe(456L).first().enabled)
        assertTrue(preferences.observe(789L).first().enabled)
        assertNull(preferences.observe(789L).first().enabledOverride)
    }

    @Test fun restoringFollowGlobalClearsOnlySwitchOverrideAndKeepsBindingAndOtherSettings() = runTest {
        val store = createStore(backgroundScope)
        val unrelatedMinimum = stringPreferencesKey("sentinel_artist_minimum")
        store.edit { it[unrelatedMinimum] = "8" }
        val preferences = AndroidArtistCatalogPreferences(store)
        preferences.bind(123L, mbid, "U2")
        preferences.setEnabled(123L, false)
        preferences.clearEnabledOverride(123L)
        val restored = preferences.observe(123L).first()
        assertTrue(restored.enabled)
        assertNull(restored.enabledOverride)
        assertEquals(mbid, restored.artistMbid)
        assertEquals("U2", restored.artistName)
        assertEquals("8", store.data.first()[unrelatedMinimum])
        preferences.setDefaultEnabled(false)
        assertFalse(preferences.observe(123L).first().enabled)
        assertEquals(mbid, preferences.observe(123L).first().artistMbid)
    }

    @Test fun bindingAndClearingIdentityDoNotTurnOffGlobalFollowingOrOverrideChoice() = runTest {
        val store = createStore(backgroundScope)
        val preferences = AndroidArtistCatalogPreferences(store)
        preferences.bind(123L, mbid, "U2")
        assertNull(preferences.observe(123L).first().enabledOverride)
        preferences.setEnabled(123L, false)
        preferences.clearBinding(123L)
        assertFalse(preferences.observe(123L).first().enabled)
        assertEquals(false, preferences.observe(123L).first().enabledOverride)
        assertNull(preferences.observe(123L).first().artistMbid)
        assertTrue(preferences.observe(456L).first().enabled)
    }

    @Test fun globalChoiceOverridesAndBindingsSurviveStoreRecreation() = runTest {
        val file = File(temporary.newFolder(), "catalog.preferences_pb")
        val firstScope = CoroutineScope(SupervisorJob() + StandardTestDispatcher(testScheduler))
        val firstStore = createStore(firstScope, file)
        val first = AndroidArtistCatalogPreferences(firstStore)
        first.setDefaultEnabled(false)
        first.setEnabled(123L, true)
        first.bind(123L, mbid, "U2")
        firstScope.cancel()
        firstScope.coroutineContext[Job]!!.join()
        val restarted = AndroidArtistCatalogPreferences(createStore(backgroundScope, file))
        assertFalse(restarted.defaultEnabled.first())
        assertTrue(restarted.observe(123L).first().enabled)
        assertEquals(true, restarted.observe(123L).first().enabledOverride)
        assertEquals(mbid, restarted.observe(123L).first().artistMbid)
        assertFalse(restarted.observe(456L).first().enabled)
    }
}

package com.lin0721.linmusic.core.preferences

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.io.File
import java.nio.file.Files

class ArtistAlbumFilterPreferencesTest {
    private lateinit var directory: File
    private lateinit var scope: CoroutineScope
    private lateinit var preferences: SettingsPreferences

    @Before
    fun setUp() {
        directory = Files.createTempDirectory("melodia-artist-album-filter").toFile()
        openStore()
    }

    private fun openStore() {
        scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        preferences = SettingsPreferences(PreferenceDataStoreFactory.create(
            scope = scope,
            produceFile = { File(directory, "settings.preferences_pb") }
        ))
    }

    private suspend fun effective(id: Long): Int = withTimeout(5000) {
        combine(preferences.artistAlbumMinTracks, preferences.artistAlbumMinTracksOverride(id)) { default, override ->
            override ?: default
        }.first()
    }

    @After
    fun tearDown() = runBlocking {
        scope.cancel()
        scope.coroutineContext[Job]!!.join()
        directory.deleteRecursively()
        Unit
    }

    @Test
    fun initialThresholdIsZeroAndUnknownArtistUsesDefault() = runBlocking {
        assertEquals(0, effective(1))
        assertNull(preferences.artistAlbumMinTracksOverride(1).first())
        assertNull(preferences.artistAlbumMinTracksOverride(0).first())
    }

    @Test
    fun twoArtistsHaveIndependentOverridesKeyedById() = runBlocking {
        preferences.saveArtistAlbumMinTracksOverride(1, 8)
        preferences.saveArtistAlbumMinTracksOverride(2, 12)
        assertEquals(8, effective(1))
        assertEquals(12, effective(2))
        assertEquals(0, effective(3))
    }

    @Test
    fun changingDefaultDoesNotOverwriteAnArtistOverride() = runBlocking {
        preferences.saveArtistAlbumMinTracksOverride(1, 8)
        preferences.saveArtistAlbumMinTracks(10)
        assertEquals(8, effective(1))
        assertEquals(10, effective(2))
    }

    @Test
    fun zeroOverrideExplicitlyShowsAllWhenDefaultFiltersAlbums() = runBlocking {
        preferences.saveArtistAlbumMinTracks(8)
        preferences.saveArtistAlbumMinTracksOverride(1, 0)
        assertEquals(0, effective(1))
        assertEquals(8, effective(2))
    }

    @Test
    fun resetRemovesOnlyTheSelectedArtistOverrideAndUsesLatestDefault() = runBlocking {
        preferences.saveArtistAlbumMinTracks(6)
        preferences.saveArtistAlbumMinTracksOverride(1, 8)
        preferences.saveArtistAlbumMinTracksOverride(2, 12)
        preferences.saveArtistAlbumMinTracks(10)
        preferences.clearArtistAlbumMinTracksOverride(1)
        assertEquals(10, effective(1))
        assertNull(preferences.artistAlbumMinTracksOverride(1).first())
        assertEquals(12, effective(2))
    }

    @Test
    fun defaultAndPerArtistValuesSurviveClosingAndReopeningTheDatastore() = runBlocking {
        preferences.saveArtistAlbumMinTracks(6)
        preferences.saveArtistAlbumMinTracksOverride(1, 0)
        preferences.saveArtistAlbumMinTracksOverride(2, 12)
        scope.cancel()
        scope.coroutineContext[Job]!!.join()
        openStore()
        assertEquals(0, effective(1))
        assertEquals(12, effective(2))
        assertEquals(6, effective(3))
    }

    @Test
    fun outOfRangeValuesAreClampedOnPersistence() = runBlocking {
        preferences.saveArtistAlbumMinTracks(-2)
        preferences.saveArtistAlbumMinTracksOverride(1, 999)
        assertEquals(0, effective(2))
        assertEquals(100, effective(1))
    }
}

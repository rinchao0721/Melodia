package com.lin0721.linmusic.feature.artist.data.catalog

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.lin0721.linmusic.feature.artist.domain.catalog.ArtistCatalogPreference
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

private val Context.artistCatalogDataStore by preferencesDataStore(name = "artist_album_catalog")

internal interface ArtistCatalogPreferenceStorage {
    val defaultEnabled: Flow<Boolean>
    fun observe(artistId: Long): Flow<ArtistCatalogPreference>
    suspend fun setDefaultEnabled(enabled: Boolean)
    suspend fun setEnabled(artistId: Long, enabled: Boolean)
    suspend fun clearEnabledOverride(artistId: Long)
    suspend fun bind(artistId: Long, mbid: String, name: String?)
    suspend fun clearBinding(artistId: Long)
}

/** Existing artist keys remain overrides. No migration rewrites an artist binding or cache. */
internal class AndroidArtistCatalogPreferences(private val store: DataStore<Preferences>) : ArtistCatalogPreferenceStorage {
    constructor(context: Context) : this(context.applicationContext.artistCatalogDataStore)

    private val globalEnabled = booleanPreferencesKey("catalog_default_enabled")
    private fun enabled(id: Long) = booleanPreferencesKey("artist_${id}_catalog_enabled")
    private fun mbid(id: Long) = stringPreferencesKey("artist_${id}_catalog_mbid")
    private fun name(id: Long) = stringPreferencesKey("artist_${id}_catalog_name")

    override val defaultEnabled = store.data.map { it[globalEnabled] ?: true }.distinctUntilChanged()

    override fun observe(artistId: Long) = store.data.map {
        ArtistCatalogPreference(enabledOverride = it[enabled(artistId)],
            artistMbid = it[mbid(artistId)], artistName = it[name(artistId)],
            defaultEnabled = it[globalEnabled] ?: true)
    }.distinctUntilChanged()

    override suspend fun setDefaultEnabled(enabled: Boolean) {
        store.edit { it[globalEnabled] = enabled }
    }

    override suspend fun setEnabled(artistId: Long, enabled: Boolean) {
        store.edit { it[this.enabled(artistId)] = enabled }
    }

    override suspend fun clearEnabledOverride(artistId: Long) {
        store.edit { it.remove(enabled(artistId)) }
    }

    override suspend fun bind(artistId: Long, mbid: String, name: String?) {
        store.edit {
            it[this.mbid(artistId)] = mbid
            it.remove(this.name(artistId))
            name?.let { value -> it[this.name(artistId)] = value }
        }
    }

    override suspend fun clearBinding(artistId: Long) {
        store.edit { it.remove(mbid(artistId)); it.remove(name(artistId)) }
    }
}

package com.lin0721.linmusic.di

import com.lin0721.linmusic.core.auth.UserPreferences
import com.lin0721.linmusic.core.contentfilter.ContentFilter
import com.lin0721.linmusic.core.network.ResourceProvider
import com.lin0721.linmusic.core.player.PlaybackPreferences
import com.lin0721.linmusic.core.preferences.SettingsPreferences
import com.lin0721.linmusic.feature.search.data.SearchHistoryPreferences
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module

val localModule = module {
    single { PlaybackPreferences(androidContext()) }
    single { UserPreferences(androidContext()) }
    single { SettingsPreferences(androidContext()) }
    single { ContentFilter(get()) }
    single { ResourceProvider(androidContext()) }
    single { SearchHistoryPreferences(androidContext()) }
    single<com.lin0721.linmusic.core.network.crypto.XeapiKeyStore> {
        com.lin0721.linmusic.core.network.crypto.XeapiKeyStoreImpl(androidContext())
    }
}

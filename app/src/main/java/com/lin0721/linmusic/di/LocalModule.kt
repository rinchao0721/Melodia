package com.lin0721.linmusic.di

import com.lin0721.linmusic.core.auth.UserPreferences
import com.lin0721.linmusic.core.contentfilter.ContentFilter
import com.lin0721.linmusic.core.download.DownloadPreferences
import com.lin0721.linmusic.core.network.AndroidNetworkStateProvider
import com.lin0721.linmusic.core.network.AndroidResourceProvider
import com.lin0721.linmusic.core.network.NetworkStateProvider
import com.lin0721.linmusic.core.network.ResourceProvider
import com.lin0721.linmusic.core.network.crypto.XeapiKeyStore
import com.lin0721.linmusic.core.network.crypto.XeapiKeyStoreImpl
import com.lin0721.linmusic.core.player.PlaybackPreferences
import com.lin0721.linmusic.core.preferences.PreferencesStores
import com.lin0721.linmusic.core.preferences.SettingsPreferences
import com.lin0721.linmusic.core.preferences.get
import com.lin0721.linmusic.feature.cloud.upload.CloudUploadManager
import com.lin0721.linmusic.feature.search.data.SearchHistoryPreferences
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module

val localModule = module {
    single { PlaybackPreferences(androidContext()) }
    single { UserPreferences(PreferencesStores.get(androidContext(), PreferencesStores.USER)) }
    single { SettingsPreferences(PreferencesStores.get(androidContext(), PreferencesStores.SETTINGS)) }
    single { ContentFilter(get()) }
    single<ResourceProvider> { AndroidResourceProvider(androidContext()) }
    single<NetworkStateProvider> { AndroidNetworkStateProvider(androidContext()) }
    single { SearchHistoryPreferences(PreferencesStores.get(androidContext(), PreferencesStores.SEARCH_HISTORY)) }
    single<XeapiKeyStore> { XeapiKeyStoreImpl(PreferencesStores.get(androidContext(), PreferencesStores.XEAPI_KEY)) }
    single { DownloadPreferences(androidContext()) }
    // 云盘上传队列状态源，依赖 Android Uri，留在应用层
    single { CloudUploadManager() }
}

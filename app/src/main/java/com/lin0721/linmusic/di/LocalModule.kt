package com.lin0721.linmusic.di

import com.lin0721.linmusic.core.auth.UserPreferences
import com.lin0721.linmusic.core.cache.MetadataCache
import com.lin0721.linmusic.core.contentfilter.ContentFilter
import com.lin0721.linmusic.core.download.DownloadPreferences
import com.lin0721.linmusic.core.network.AndroidNetworkStateProvider
import com.lin0721.linmusic.core.network.AndroidOnlineStateProvider
import com.lin0721.linmusic.core.network.OnlineStateProvider
import com.lin0721.linmusic.core.network.AndroidResourceProvider
import com.lin0721.linmusic.core.network.NetworkStateProvider
import com.lin0721.linmusic.feature.library.data.AndroidLibraryPreferences
import com.lin0721.linmusic.feature.library.data.LibraryPreferences
import com.lin0721.linmusic.core.network.ResourceProvider
import com.lin0721.linmusic.core.network.crypto.XeapiKeyStore
import com.lin0721.linmusic.core.network.crypto.XeapiKeyStoreImpl
import com.lin0721.linmusic.core.player.PlaybackPreferences
import com.lin0721.linmusic.core.preferences.PreferencesStores
import com.lin0721.linmusic.core.preferences.SettingsPreferences
import com.lin0721.linmusic.core.preferences.get
import com.lin0721.linmusic.core.source.SourcePreferences
import com.lin0721.linmusic.feature.cloud.upload.CloudUploadManager
import com.lin0721.linmusic.feature.podcast.data.PodcastProgressPreferences
import com.lin0721.linmusic.feature.podcast.data.PodcastSeenPreferences
import com.lin0721.linmusic.feature.search.data.SearchHistoryPreferences
import com.lin0721.linmusic.feature.artist.data.catalog.ArtistCatalogRepository
import kotlinx.coroutines.flow.first
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module
import java.io.File

val localModule = module {
    single { PlaybackPreferences(PreferencesStores.get(androidContext(), PreferencesStores.PLAYBACK)) }
    single { UserPreferences(PreferencesStores.get(androidContext(), PreferencesStores.USER)) }
    single { SettingsPreferences(PreferencesStores.get(androidContext(), PreferencesStores.SETTINGS)) }
    single { SourcePreferences(PreferencesStores.get(androidContext(), PreferencesStores.SOURCE)) }
    single { ContentFilter(get()) }
    single<ResourceProvider> { AndroidResourceProvider(androidContext()) }
    single<NetworkStateProvider> { AndroidNetworkStateProvider(androidContext()) }
    single<OnlineStateProvider> { AndroidOnlineStateProvider(androidContext()) }
    single {
        val userPreferences = get<UserPreferences>()
        MetadataCache(File(androidContext().filesDir, "meta_cache")) { userPreferences.userProfile.first()?.uid ?: 0L }
    }
    single<LibraryPreferences> { AndroidLibraryPreferences(androidContext()) }
    single { SearchHistoryPreferences(PreferencesStores.get(androidContext(), PreferencesStores.SEARCH_HISTORY)) }
    single { ArtistCatalogRepository(androidContext()) }
    single { PodcastProgressPreferences(PreferencesStores.get(androidContext(), PreferencesStores.PODCAST)) }
    single { PodcastSeenPreferences(PreferencesStores.get(androidContext(), PreferencesStores.PODCAST)) }
    single<XeapiKeyStore> { XeapiKeyStoreImpl(PreferencesStores.get(androidContext(), PreferencesStores.XEAPI_KEY)) }
    single { DownloadPreferences(androidContext()) }
    // 云盘上传队列状态源，依赖 Android Uri，留在应用层
    single { CloudUploadManager() }
}

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
import com.lin0721.linmusic.core.ui.skin.SkinImageCodec
import com.lin0721.linmusic.core.ui.skin.SkinRepository
import com.lin0721.linmusic.core.ui.skin.SkinStore
import com.lin0721.linmusic.core.ui.theme.AndroidSkinImageCodec
import com.lin0721.linmusic.core.ui.theme.CoverColorMemory
import com.lin0721.linmusic.feature.library.data.AndroidLibraryPreferences
import com.lin0721.linmusic.feature.library.data.LibraryPreferences
import com.lin0721.linmusic.core.network.ResourceProvider
import com.lin0721.linmusic.core.network.crypto.XeapiKeyStore
import com.lin0721.linmusic.core.network.crypto.XeapiKeyStoreImpl
import com.lin0721.linmusic.core.player.PlaybackPreferences
import com.lin0721.linmusic.core.preferences.PreferencesStores
import com.lin0721.linmusic.core.preferences.SettingsPreferences
import com.lin0721.linmusic.core.preferences.get
import com.lin0721.linmusic.core.playlistexternal.PlaylistExternalTrackStore
import com.lin0721.linmusic.core.source.SourcePreferences
import com.lin0721.linmusic.feature.podcast.data.PodcastProgressPreferences
import com.lin0721.linmusic.feature.podcast.data.PodcastSeenPreferences
import com.lin0721.linmusic.feature.message.data.PrivateReadPreferences
import com.lin0721.linmusic.feature.search.data.SearchHistoryPreferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module
import java.io.File

val localModule = module {
    single { PlaybackPreferences(PreferencesStores.get(androidContext(), PreferencesStores.PLAYBACK)) }
    single { UserPreferences(PreferencesStores.get(androidContext(), PreferencesStores.USER)) }
    single { SettingsPreferences(PreferencesStores.get(androidContext(), PreferencesStores.SETTINGS), userPreferences = get()) }
    single<SkinImageCodec> { AndroidSkinImageCodec() }
    single { SkinStore(File(androidContext().filesDir, "skins"), get()) }
    single { SkinRepository(get(), get()) }
    single { SourcePreferences(PreferencesStores.get(androidContext(), PreferencesStores.SOURCE)) }
    single { PlaylistExternalTrackStore(PreferencesStores.get(androidContext(), PreferencesStores.PLAYLIST_EXTERNAL)) }
    single { ContentFilter(get()) }
    single<ResourceProvider> { AndroidResourceProvider(androidContext()) }
    single<NetworkStateProvider> { AndroidNetworkStateProvider(androidContext()) }
    single<OnlineStateProvider> { AndroidOnlineStateProvider(androidContext()) }
    single {
        val userPreferences = get<UserPreferences>()
        MetadataCache(File(androidContext().filesDir, "meta_cache")) { userPreferences.userProfile.first()?.uid ?: 0L }
    }
    single<LibraryPreferences> { AndroidLibraryPreferences(androidContext()) }
    single { SearchHistoryPreferences(PreferencesStores.get(androidContext(), PreferencesStores.SEARCH_HISTORY), userPreferences = get()) }
    single { PodcastProgressPreferences(PreferencesStores.get(androidContext(), PreferencesStores.PODCAST)) }
    single { PodcastSeenPreferences(PreferencesStores.get(androidContext(), PreferencesStores.PODCAST)) }
    single { PrivateReadPreferences(PreferencesStores.get(androidContext(), PreferencesStores.MESSAGE)) }
    single<XeapiKeyStore> { XeapiKeyStoreImpl(PreferencesStores.get(androidContext(), PreferencesStores.XEAPI_KEY)) }
    single { DownloadPreferences(androidContext()) }
    // 启动即预读，首页首帧前备好封面卡颜色
    single(createdAtStart = true) {
        CoverColorMemory(
            PreferencesStores.get(androidContext(), PreferencesStores.COVER_COLOR),
            CoroutineScope(SupervisorJob() + Dispatchers.IO)
        )
    }
}
